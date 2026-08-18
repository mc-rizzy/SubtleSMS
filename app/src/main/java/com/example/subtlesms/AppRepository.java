package com.example.subtlesms;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.text.format.DateFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single source of truth for SMS/MMS + contact data.
 *
 * Design goal: nothing is queried until a screen actually asks for it
 * (getConversations / getMessages), and once fetched it stays cached in
 * memory so other screens can reuse it instead of re-hitting the SMS
 * provider. Callers that know the underlying data changed (a new message
 * arrived, a message was sent, contacts changed) call the matching
 * invalidate/refresh method so the *next* request re-fetches - we never
 * eagerly reload in the background.
 *
 * All query methods run on a background executor; results come back via
 * the Callback on that same background thread's caller convention below
 * (callers post to the main thread themselves, matching how the Activities
 * already do it).
 */
public class AppRepository {

    private static volatile AppRepository instance;

    public static AppRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (AppRepository.class) {
                if (instance == null) {
                    instance = new AppRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public interface Callback<T> {
        void onResult(T result);
    }

    private static final String PREFS_NAME = "SubtleSMS_Prefs";
    private static final String KEY_AUTO_MSG_IDS = "auto_message_ids";

    private static final Uri URI_MMS_PART = Uri.parse("content://mms/part");
    private static final Uri URI_MMS_ADDR = Uri.parse("content://mms/addr");
    private static final Uri URI_CONVERSATIONS = Uri.parse("content://mms-sms/conversations/");
    private static final Uri URI_CANONICAL = Uri.parse("content://mms-sms/canonical-addresses");
    private static final Uri URI_SMS = Uri.parse("content://sms/");
    private static final Uri URI_CONVERSATIONS_SIMPLE = Uri.parse("content://mms-sms/conversations?simple=true");

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ---- lazy in-memory caches; null/empty = "not loaded yet", not "empty result" ----
    private volatile List<Conversation> conversationCache;
    private final Map<String, List<SmsMessage>> messageCache = new HashMap<>();
    private volatile Map<String, String> contactCache; // number -> display name
    private final Map<String, String> sentimentCache = new HashMap<>(); // threadId -> sentiment

    private AppRepository(Context context) {
        this.appContext = context;
    }

    // =================================================================
    // Conversations (MainActivity list)
    // =================================================================

    public void getConversations(Callback<List<Conversation>> callback) {
        if (conversationCache != null) {
            callback.onResult(conversationCache);
            return;
        }
        executor.execute(() -> {
            List<Conversation> loaded = queryConversations();
            applyCachedSentiments(loaded);
            conversationCache = loaded;
            callback.onResult(loaded);
        });
    }

    public void refreshConversations(Callback<List<Conversation>> callback) {
        conversationCache = null;
        getConversations(callback);
    }

    /** Resolves contact display names for whatever conversations are currently cached. */
    public void resolveContactNames(Callback<List<Conversation>> callback) {
        executor.execute(() -> {
            List<Conversation> conversations = conversationCache;
            if (conversations == null) {
                callback.onResult(null);
                return;
            }
            Map<String, String> contacts = getContactCache();
            boolean updated = false;
            for (Conversation conv : conversations) {
                Result resolved = resolveNames(conv.getAddress(), contacts);
                if (resolved.foundAny) {
                    conv.setContactName(resolved.names);
                    conv.setRando(false);
                    updated = true;
                }
            }
            callback.onResult(updated ? conversations : null);
        });
    }

    /** Computes and caches sentiment for whatever conversations are currently cached. */
    public void analyzeSentiments(Callback<List<Conversation>> callback) {
        executor.execute(() -> {
            List<Conversation> conversations = conversationCache;
            if (conversations == null) {
                callback.onResult(null);
                return;
            }
            boolean updated = false;
            for (Conversation conv : conversations) {
                String threadId = conv.getThreadId();
                String cached = sentimentCache.get(threadId);
                if (cached != null) {
                    conv.setSentiment(cached);
                    continue;
                }
                String body = conv.getLastMessage();
                if (body != null && !body.isEmpty()) {
                    String sentiment = SentimentAnalyzer.analyze(body);
                    sentimentCache.put(threadId, sentiment);
                    conv.setSentiment(sentiment);
                    updated = true;
                }
            }
            callback.onResult(updated ? conversations : null);
        });
    }

    private void applyCachedSentiments(List<Conversation> conversations) {
        for (Conversation conv : conversations) {
            String cached = sentimentCache.get(conv.getThreadId());
            if (cached != null) {
                conv.setSentiment(cached);
            }
        }
    }

    // =================================================================
    // Messages (ChatActivity thread view)
    // =================================================================

    public void getMessages(String threadId, String address, Callback<List<SmsMessage>> callback) {
        String key = cacheKey(threadId, address);
        List<SmsMessage> cached = messageCache.get(key);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }
        executor.execute(() -> {
            List<SmsMessage> loaded = queryMessages(threadId, address);
            messageCache.put(key, loaded);
            callback.onResult(loaded);
        });
    }

    public void refreshMessages(String threadId, String address, Callback<List<SmsMessage>> callback) {
        messageCache.remove(cacheKey(threadId, address));
        getMessages(threadId, address, callback);
    }

    /** Appends a just-sent message to the in-memory cache so the UI can show it instantly. */
    public void appendLocalMessage(String threadId, String address, SmsMessage message) {
        List<SmsMessage> list = messageCache.get(cacheKey(threadId, address));
        if (list != null) {
            list.add(message);
        }
        conversationCache = null; // list-screen snippet/timestamp is now stale
    }

    private String cacheKey(String threadId, String address) {
        return (threadId != null && !threadId.isEmpty()) ? "thread:" + threadId : "addr:" + address;
    }

    // =================================================================
    // Automated-message bookkeeping (shared by ChatActivity + SmsWorker)
    // =================================================================

    public void markMessageAsAutomated(String messageIdOrBody) {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> autoSet = new HashSet<>(prefs.getStringSet(KEY_AUTO_MSG_IDS, new HashSet<>()));
        autoSet.add(messageIdOrBody);
        prefs.edit().putStringSet(KEY_AUTO_MSG_IDS, autoSet).apply();
    }

    private Set<String> getAutomatedIds() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getStringSet(KEY_AUTO_MSG_IDS, new HashSet<>());
    }

    // =================================================================
    // Contacts - loaded once, lazily, reused by both queryConversations
    // and queryMessages instead of every screen re-querying it.
    // =================================================================

    private Map<String, String> getContactCache() {
        if (contactCache == null) {
            contactCache = queryContactCache();
        }
        return contactCache;
    }

    public void invalidateContactCache() {
        contactCache = null;
    }

    private Map<String, String> queryContactCache() {
        Map<String, String> cache = new HashMap<>();
        try (Cursor cursor = appContext.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null) {
                int numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                while (cursor.moveToNext()) {
                    String number = cursor.getString(numberIdx);
                    String name = cursor.getString(nameIdx);
                    if (number != null && name != null) {
                        cache.put(number.replaceAll("[^0-9+]", ""), name);
                        cache.put(number, name);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cache;
    }

    private static class Result {
        String names;
        boolean foundAny;
    }

    private Result resolveNames(String rawAddresses, Map<String, String> contacts) {
        Result result = new Result();
        if (rawAddresses == null || rawAddresses.isEmpty()) {
            result.names = "Unknown";
            return result;
        }
        String[] addresses = rawAddresses.split(", ");
        StringBuilder names = new StringBuilder();
        for (String address : addresses) {
            String normalized = address.replaceAll("[^0-9+]", "");
            String resolvedName = contacts.get(normalized);
            if (resolvedName == null) resolvedName = contacts.get(address);

            if (names.length() > 0) names.append(", ");
            if (resolvedName != null) {
                result.foundAny = true;
                names.append(resolvedName);
            } else {
                names.append(address);
            }
        }
        result.names = names.toString();
        return result;
    }

    // =================================================================
    // Internal ContentResolver queries (logic preserved from the old
    // MainActivity / ChatActivity, just relocated here)
    // =================================================================

    private List<Conversation> queryConversations() {
        List<Conversation> list = new ArrayList<>();
        ContentResolver cr = appContext.getContentResolver();

        Map<String, String> canonicalAddressMap = fetchAllCanonicalAddresses(cr);

        String[] projection = new String[]{"_id", "snippet", "date", "recipient_ids"};

        try (Cursor cursor = cr.query(URI_CONVERSATIONS_SIMPLE, projection, null, null, "date DESC")) {
            if (cursor != null) {
                int threadIdx = cursor.getColumnIndex("_id");
                int snippetIdx = cursor.getColumnIndex("snippet");
                int dateIdx = cursor.getColumnIndex("date");
                int recipientIdx = cursor.getColumnIndex("recipient_ids");

                while (cursor.moveToNext()) {
                    String threadId = cursor.getString(threadIdx);
                    String body = cursor.getString(snippetIdx);
                    long dateMs = cursor.getLong(dateIdx);
                    String timestamp = DateFormat.format("hh:mm a", dateMs).toString();

                    String recipientIds = cursor.getString(recipientIdx);
                    String rawAddress = resolveAddressesFromMap(recipientIds, canonicalAddressMap);

                    // The "snippet" column on this provider is unreliable for MMS/group
                    // threads (it's frequently blank even though the thread has messages),
                    // so fall back to reading the actual last message when it's empty.
                    if (body == null || body.trim().isEmpty()) {
                        body = queryLastMessageSnippet(cr, threadId);
                    }

                    list.add(new Conversation(rawAddress, body, timestamp, "", threadId, rawAddress, true));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Fallback for threads whose "snippet" column came back empty (mainly MMS/group
     * threads). Looks up the most recent message directly and, for MMS rows whose text
     * lives in mms/part rather than the unified body column, reads it from there.
     */
    private String queryLastMessageSnippet(ContentResolver cr, String threadId) {
        Uri uri = Uri.withAppendedPath(URI_CONVERSATIONS, threadId);
        String[] projection = new String[]{"_id", "body", "msg_box"};

        try (Cursor cursor = cr.query(uri, projection, null, null, "date DESC LIMIT 1")) {
            if (cursor != null && cursor.moveToFirst()) {
                int bodyIdx = cursor.getColumnIndex("body");
                String body = bodyIdx != -1 ? cursor.getString(bodyIdx) : null;
                if (body != null && !body.trim().isEmpty()) {
                    return body;
                }

                int idIdx = cursor.getColumnIndex("_id");
                int msgBoxIdx = cursor.getColumnIndex("msg_box");
                boolean isMms = msgBoxIdx != -1 && cursor.getInt(msgBoxIdx) != 0 && !cursor.isNull(msgBoxIdx);
                String msgId = idIdx != -1 ? cursor.getString(idIdx) : null;
                if (isMms && msgId != null) {
                    return queryMmsSnippet(cr, msgId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /** Reads the text (or a media placeholder) for a single MMS message's parts. */
    private String queryMmsSnippet(ContentResolver cr, String mid) {
        try (Cursor cursor = cr.query(URI_MMS_PART, new String[]{"ct", "text"}, "mid = ?", new String[]{mid}, null)) {
            if (cursor != null) {
                String mediaPlaceholder = null;
                int ctIdx = cursor.getColumnIndex("ct");
                int textIdx = cursor.getColumnIndex("text");
                while (cursor.moveToNext()) {
                    String ct = ctIdx != -1 ? cursor.getString(ctIdx) : null;
                    if (ct == null) continue;
                    if (ct.equalsIgnoreCase("text/plain")) {
                        String text = textIdx != -1 ? cursor.getString(textIdx) : null;
                        if (text != null && !text.isEmpty()) return text;
                    } else if (mediaPlaceholder == null && ct.startsWith("image/")) {
                        mediaPlaceholder = "\uD83D\uDCF7 Photo";
                    } else if (mediaPlaceholder == null && ct.startsWith("video/")) {
                        mediaPlaceholder = "\uD83C\uDFA5 Video";
                    }
                }
                if (mediaPlaceholder != null) return mediaPlaceholder;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private Map<String, String> fetchAllCanonicalAddresses(ContentResolver cr) {
        Map<String, String> map = new HashMap<>();
        try (Cursor cursor = cr.query(URI_CANONICAL, new String[]{"_id", "address"}, null, null, null)) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndex("_id");
                int addrIdx = cursor.getColumnIndex("address");
                while (cursor.moveToNext()) {
                    map.put(cursor.getString(idIdx), cursor.getString(addrIdx));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    private String resolveAddressesFromMap(String recipientIds, Map<String, String> addressMap) {
        if (recipientIds == null || recipientIds.trim().isEmpty()) return "";
        String[] ids = recipientIds.split(" ");
        StringBuilder addressList = new StringBuilder();
        for (String id : ids) {
            if (id.isEmpty()) continue;
            String address = addressMap.get(id);
            if (address != null) {
                if (addressList.length() > 0) addressList.append(", ");
                addressList.append(address);
            }
        }
        return addressList.toString();
    }

    private List<SmsMessage> queryMessages(String threadId, String address) {
        List<SmsMessage> messages = new ArrayList<>();
        ContentResolver cr = appContext.getContentResolver();

        boolean isGroup = (address != null && address.contains(","))
                || (threadId != null && !threadId.isEmpty() && isGroupThread(cr, threadId));

        Set<String> autoSet = getAutomatedIds();
        Map<String, String> contacts = getContactCache();

        Uri uri;
        String selection = null;
        String[] selectionArgs = null;

        if (threadId != null && !threadId.isEmpty()) {
            uri = Uri.withAppendedPath(URI_CONVERSATIONS, threadId);
        } else {
            uri = URI_SMS;
            selection = "address = ?";
            selectionArgs = new String[]{address};
        }

        String[] projection = new String[]{"_id", "body", "date", "type", "msg_box", "status", "address"};

        // First pass: read the raw rows and note which ones are MMS, so the
        // mms/part and mms/addr lookups below can be scoped to just this
        // thread's messages instead of scanning every MMS on the device.
        List<Row> rows = new ArrayList<>();
        List<String> mmsIdsInThread = new ArrayList<>();

        try (Cursor cursor = cr.query(uri, projection, selection, selectionArgs, "date ASC")) {
            if (cursor != null && cursor.moveToFirst()) {
                int idIdx = cursor.getColumnIndex("_id");
                int bodyIdx = cursor.getColumnIndex("body");
                int dateIdx = cursor.getColumnIndex("date");
                int typeIdx = cursor.getColumnIndex("type");
                int msgBoxIdx = cursor.getColumnIndex("msg_box");
                int statusIdx = cursor.getColumnIndex("status");
                int addressIdx = cursor.getColumnIndex("address");

                do {
                    Row row = new Row();
                    row.id = cursor.getString(idIdx);
                    row.body = bodyIdx != -1 ? cursor.getString(bodyIdx) : null;
                    row.dateMs = dateIdx != -1 ? cursor.getLong(dateIdx) : System.currentTimeMillis();
                    if (row.dateMs < 100000000000L) row.dateMs *= 1000;

                    int type = typeIdx != -1 ? cursor.getInt(typeIdx) : -1;
                    row.msgBox = msgBoxIdx != -1 ? cursor.getInt(msgBoxIdx) : -1;
                    row.isMms = msgBoxIdx != -1 && !cursor.isNull(msgBoxIdx);
                    row.isSent = (type == Telephony.Sms.MESSAGE_TYPE_SENT || row.msgBox == Telephony.Mms.MESSAGE_BOX_SENT);
                    row.status = statusIdx != -1 ? cursor.getInt(statusIdx) : -1;
                    row.address = addressIdx != -1 ? cursor.getString(addressIdx) : null;

                    rows.add(row);
                    if (row.isMms && row.id != null) {
                        mmsIdsInThread.add(row.id);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // These now only touch rows belonging to this thread (or run zero
        // queries at all for a pure-SMS thread with no MMS messages).
        Map<String, MmsPartData> mmsPartMap = buildMmsPartMap(cr, mmsIdsInThread);
        Map<String, String> mmsSenderMap = buildMmsSenderMap(cr, mmsIdsInThread, contacts);

        for (Row row : rows) {
            String id = row.id;
            String sender;
            if (row.isSent) {
                sender = "Me";
            } else {
                sender = (row.address == null || row.address.isEmpty())
                        ? mmsSenderMap.get(id)
                        : contacts.getOrDefault(row.address, row.address);
            }
            if (sender == null) sender = "Unknown";

            String body = row.body;
            String mediaUrl = null;
            String mediaType = "text";

            if (id != null) {
                MmsPartData textPart = mmsPartMap.get(id + "_text");
                if ((body == null || body.isEmpty()) && textPart != null) {
                    body = textPart.content;
                }
                MmsPartData mediaPart = mmsPartMap.get(id + "_media");
                if (mediaPart != null) {
                    mediaUrl = mediaPart.content;
                    mediaType = mediaPart.type;
                }
            }
            if (body == null) body = "";

            boolean isAutomated = row.isSent && (autoSet.contains(body) || autoSet.contains(String.valueOf(row.dateMs)));

            if (!body.isEmpty() || mediaUrl != null) {
                messages.add(new SmsMessage(id, body, row.dateMs, row.isSent, isAutomated, row.status, sender, isGroup, mediaUrl, mediaType));
            }
        }

        return messages;
    }

    private static class Row {
        String id;
        String body;
        long dateMs;
        boolean isSent;
        boolean isMms;
        int msgBox;
        int status;
        String address;
    }

    private boolean isGroupThread(ContentResolver cr, String threadId) {
        if (threadId == null || threadId.isEmpty()) return false;
        Uri uri = Uri.withAppendedPath(URI_CONVERSATIONS, threadId + "/recipients");
        try (Cursor cursor = cr.query(uri, new String[]{"_id"}, null, null, null)) {
            if (cursor != null) return cursor.getCount() > 1;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Scoped to {@code midsInThread} - returns immediately (no query at all) for a pure-SMS thread. */
    private Map<String, MmsPartData> buildMmsPartMap(ContentResolver cr, List<String> midsInThread) {
        Map<String, MmsPartData> partMap = new HashMap<>();
        if (midsInThread.isEmpty()) return partMap;

        String[] projection = new String[]{"_id", "mid", "ct", "text"};
        String selection = "mid IN (" + placeholders(midsInThread.size()) + ")";
        String[] selectionArgs = midsInThread.toArray(new String[0]);

        try (Cursor cursor = cr.query(URI_MMS_PART, projection, selection, selectionArgs, null)) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndex("_id");
                int midIdx = cursor.getColumnIndex("mid");
                int ctIdx = cursor.getColumnIndex("ct");
                int textIdx = cursor.getColumnIndex("text");

                while (cursor.moveToNext()) {
                    String partId = cursor.getString(idIdx);
                    String mid = cursor.getString(midIdx);
                    String mimeType = cursor.getString(ctIdx);
                    if (mid == null || mimeType == null) continue;

                    if ("text/plain".equalsIgnoreCase(mimeType)) {
                        String text = cursor.getString(textIdx);
                        if (text != null && !text.isEmpty()) {
                            partMap.put(mid + "_text", new MmsPartData(text, "text"));
                        }
                    } else if (mimeType.startsWith("image/")) {
                        Uri contentUri = Uri.withAppendedPath(URI_MMS_PART, partId);
                        partMap.put(mid + "_media", new MmsPartData(contentUri.toString(), "image"));
                    } else if (mimeType.startsWith("video/")) {
                        Uri contentUri = Uri.withAppendedPath(URI_MMS_PART, partId);
                        partMap.put(mid + "_media", new MmsPartData(contentUri.toString(), "video"));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return partMap;
    }

    /** Scoped to {@code midsInThread} - returns immediately (no query at all) for a pure-SMS thread. */
    private Map<String, String> buildMmsSenderMap(ContentResolver cr, List<String> midsInThread, Map<String, String> contacts) {
        Map<String, String> senderMap = new HashMap<>();
        if (midsInThread.isEmpty()) return senderMap;

        String selection = "(type=137 OR type=151) AND msg_id IN (" + placeholders(midsInThread.size()) + ")";
        String[] selectionArgs = midsInThread.toArray(new String[0]);

        try (Cursor cursor = cr.query(URI_MMS_ADDR, new String[]{"msg_id", "address", "type"}, selection, selectionArgs, null)) {
            if (cursor != null) {
                int msgIdIdx = cursor.getColumnIndex("msg_id");
                int addrIdx = cursor.getColumnIndex("address");
                int typeIdx = cursor.getColumnIndex("type");

                while (cursor.moveToNext()) {
                    String msgId = cursor.getString(msgIdIdx);
                    String address = cursor.getString(addrIdx);
                    int type = cursor.getInt(typeIdx);

                    if (address != null && !address.equals("insert-address-token")) {
                        String resolvedName = contacts.getOrDefault(address, address);
                        if (type == 137) {
                            senderMap.put(msgId, resolvedName);
                        } else if (!senderMap.containsKey(msgId)) {
                            senderMap.put(msgId, resolvedName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return senderMap;
    }

    private String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        return sb.toString();
    }

    private static class MmsPartData {
        final String content;
        final String type;
        MmsPartData(String content, String type) {
            this.content = content;
            this.type = type;
        }
    }
}