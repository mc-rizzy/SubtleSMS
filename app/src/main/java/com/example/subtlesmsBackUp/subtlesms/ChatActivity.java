package com.example.subtlesmsBackUp.subtlesms;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.subtlesms.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView rvMessages;
    private ChatAdapter adapter;
    private List<SmsMessage> messagesList = new ArrayList<>();
    private EditText etMessageInput;
    private String recipientAddress;
    private String threadId;
    private ContentObserver smsObserver;

    private static final String PREFS_NAME = "SubtleSMS_Prefs";
    private static final String KEY_AUTO_MSG_IDS = "auto_message_ids";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final Uri URI_MMS_SMS = Uri.parse("content://mms-sms/");
    private static final Uri URI_SMS = Uri.parse("content://sms/");
    private static final Uri URI_MMS_PART = Uri.parse("content://mms/part");
    private static final Uri URI_MMS_ADDR = Uri.parse("content://mms/addr");
    private static final Uri URI_CONVERSATIONS = Uri.parse("content://mms-sms/conversations/");

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    sendMediaMessage(uri);
                }
            });
    public void markMessageAsAutomated(String messageIdOrBody) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> autoSet = new HashSet<>(prefs.getStringSet(KEY_AUTO_MSG_IDS, new HashSet<>()));
        autoSet.add(messageIdOrBody);
        prefs.edit().putStringSet(KEY_AUTO_MSG_IDS, autoSet).apply();
    }

    private final BroadcastReceiver deliveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            for (SmsMessage msg : messagesList) {
                if (msg.isSent() && msg.getStatus() == MessageStatus.SENT_PENDING) {
                    if (msg.isAutomated()) {
                        msg.setStatus(MessageStatus.AUTO_SENT_DELIVERED);
                    } else {
                        msg.setStatus(MessageStatus.MANUAL_SENT_DELIVERED);
                    }
                }
            }
            adapter.notifyDataSetChanged();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        setContentView(R.layout.activity_chat);

        ImageButton btnAttachMedia = findViewById(R.id.btnAttachMedia);
        btnAttachMedia.setOnClickListener(v -> {
            pickMediaLauncher.launch(
                    new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                            .build()
            );
        });

        String contactName = getIntent().getStringExtra("CONTACT_NAME");
        threadId = getIntent().getStringExtra("THREAD_ID");
        recipientAddress = getIntent().getStringExtra("ADDRESS");

        TextView tvChatTitle = findViewById(R.id.tvChatTitle);
        if (tvChatTitle != null && contactName != null) {
            tvChatTitle.setText(contactName);
        }

        rvMessages = findViewById(R.id.rvMessages);
        etMessageInput = findViewById(R.id.etMessageInput);
        ImageButton btnSend = findViewById(R.id.btnSend);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);

        rvMessages.setHasFixedSize(true);
        rvMessages.setItemViewCacheSize(20);

        adapter = new ChatAdapter(messagesList);
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());

        View rootLayout = findViewById(R.id.rootChatLayout);
        LinearLayout layoutInputArea = findViewById(R.id.layoutInputArea);

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            int bottomInset = Math.max(navigationBars.bottom, ime.bottom);

            // Apply window inset directly as margin/translation, NOT stacking padding
            layoutInputArea.setTranslationY(-bottomInset);

            return insets;
        });

        smsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                refreshMessages();
            }
        };

        refreshMessages();
    }

    private void scrollToBottom() {
        if (adapter != null && adapter.getItemCount() > 0) {
            rvMessages.post(() -> rvMessages.smoothScrollToPosition(adapter.getItemCount() - 1));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(
                Uri.parse("content://mms-sms/"),
                true,
                smsObserver
        );

        try {
            ContextCompat.registerReceiver(
                    this,
                    deliveryReceiver,
                    new IntentFilter("SMS_DELIVERED"),
                    ContextCompat.RECEIVER_EXPORTED
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(smsObserver);
        unregisterReceiver(deliveryReceiver);
    }

    private void refreshMessages() {
        executor.execute(() -> {
            List<SmsMessage> updatedList = loadSmsThread(threadId, recipientAddress);
            runOnUiThread(() -> {
                messagesList.clear();
                messagesList.addAll(updatedList);
                adapter.notifyDataSetChanged();
                if (!messagesList.isEmpty()) {
                    rvMessages.scrollToPosition(messagesList.size() - 1);
                }
            });
        });
    }

    private void sendMessage() {
        String messageText = etMessageInput.getText().toString().trim();

        if (messageText.isEmpty()) {
            return;
        }

        if (recipientAddress == null || recipientAddress.isEmpty()) {
            Toast.makeText(this, "Recipient phone number unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SEND_SMS permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            SmsManager smsManager = SmsManager.getDefault();
            Intent deliveryIntent = new Intent("SMS_DELIVERED");
            PendingIntent deliveredPI = PendingIntent.getBroadcast(
                    this, 0, deliveryIntent, PendingIntent.FLAG_IMMUTABLE
            );

            boolean isGroup = recipientAddress.contains(",") || isGroupThread(getContentResolver(), threadId);

            if (isGroup) {
                String[] recipients = recipientAddress.split(",");
                for (String recipient : recipients) {
                    String cleanNumber = recipient.trim();
                    if (!cleanNumber.isEmpty()) {
                        smsManager.sendTextMessage(cleanNumber, null, messageText, null, deliveredPI);
                    }
                }
            } else {
                smsManager.sendTextMessage(recipientAddress, null, messageText, null, deliveredPI);
            }

            long nowMs = System.currentTimeMillis();

            SmsMessage sentMsg = new SmsMessage(
                    String.valueOf(nowMs),
                    messageText,
                    nowMs,
                    true,
                    false,
                    -1,
                    "Me",
                    isGroup,
                    null,
                    "text"
            );

            messagesList.add(sentMsg);
            adapter.notifyItemInserted(messagesList.size() - 1);
            rvMessages.scrollToPosition(messagesList.size() - 1);

            etMessageInput.setText("");
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to send SMS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private List<SmsMessage> loadSmsThread(String threadId, String address) {
        List<SmsMessage> messages = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return messages;
        }

        ContentResolver cr = getContentResolver();

        boolean isGroup = (recipientAddress != null && recipientAddress.contains(","))
                || (threadId != null && !threadId.isEmpty() && isGroupThread(cr, threadId));

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> autoSet = prefs.getStringSet(KEY_AUTO_MSG_IDS, new HashSet<>());

        // Pre-fetch contacts and MMS details using pre-parsed URIs
        Map<String, String> contactCache = buildContactCache(cr);
        Map<String, MmsPartData> mmsPartMap = buildMmsPartMap(cr);
        Map<String, String> mmsSenderMap = buildMmsSenderMap(cr, contactCache);

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
                    String id = cursor.getString(idIdx);
                    long dateMs = dateIdx != -1 ? cursor.getLong(dateIdx) : System.currentTimeMillis();

                    if (dateMs < 100000000000L) {
                        dateMs *= 1000;
                    }

                    int type = typeIdx != -1 ? cursor.getInt(typeIdx) : -1;
                    int msgBox = msgBoxIdx != -1 ? cursor.getInt(msgBoxIdx) : -1;
                    boolean isSent = (type == Telephony.Sms.MESSAGE_TYPE_SENT || msgBox == Telephony.Mms.MESSAGE_BOX_SENT);

                    String sender;
                    if (isSent) {
                        sender = "Me";
                    } else {
                        String rawAddress = addressIdx != -1 ? cursor.getString(addressIdx) : null;
                        if (rawAddress == null || rawAddress.isEmpty()) {
                            sender = mmsSenderMap.get(id);
                        } else {
                            sender = contactCache.getOrDefault(rawAddress, rawAddress);
                        }
                    }
                    if (sender == null) sender = "Unknown";

                    String body = bodyIdx != -1 ? cursor.getString(bodyIdx) : null;
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

                    int systemStatus = statusIdx != -1 ? cursor.getInt(statusIdx) : -1;
                    boolean isAutomated = isSent && (autoSet.contains(body) || autoSet.contains(String.valueOf(dateMs)));

                    if (!body.isEmpty() || mediaUrl != null) {
                        messages.add(new SmsMessage(
                                id,
                                body,
                                dateMs,
                                isSent,
                                isAutomated,
                                systemStatus,
                                sender,
                                isGroup,
                                mediaUrl,
                                mediaType
                        ));
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return messages;
    }

    private void sendMediaMessage(Uri imageUri) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + recipientAddress));
        intent.putExtra("address", recipientAddress);
        intent.putExtra(Intent.EXTRA_STREAM, imageUri);
        intent.setType("image/*");

        // Grant read permission for the URI to the target app
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "No app available to handle MMS send", Toast.LENGTH_SHORT).show();
        }
    }

    private Map<String, MmsPartData> buildMmsPartMap(ContentResolver cr) {
        Map<String, MmsPartData> partMap = new HashMap<>();

        // Query all parts without restrictive SQL selection to avoid missing parts
        Uri partUri = URI_MMS_PART;
        String[] projection = new String[]{"_id", "mid", "ct", "text"};

        try (Cursor cursor = cr.query(partUri, projection, null, null, null)) {
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

    // Simple wrapper class for batch result
    public static class MmsPartData {
        public final String content;
        public final String type;
        public MmsPartData(String content, String type) {
            this.content = content;
            this.type = type;
        }
    }

    private Map<String, String> buildMmsSenderMap(ContentResolver cr, Map<String, String> contactCache) {
        Map<String, String> senderMap = new HashMap<>();
        try (Cursor cursor = cr.query(URI_MMS_ADDR, new String[]{"msg_id", "address", "type"}, "type=137 OR type=151", null, null)) {
            if (cursor != null) {
                int msgIdIdx = cursor.getColumnIndex("msg_id");
                int addrIdx = cursor.getColumnIndex("address");
                int typeIdx = cursor.getColumnIndex("type");

                while (cursor.moveToNext()) {
                    String msgId = cursor.getString(msgIdIdx);
                    String address = cursor.getString(addrIdx);
                    int type = cursor.getInt(typeIdx);

                    if (address != null && !address.equals("insert-address-token")) {
                        String resolvedName = contactCache.getOrDefault(address, address);
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

    private boolean isGroupThread(ContentResolver cr, String threadId) {
        if (threadId == null || threadId.isEmpty()) return false;
        Uri uri = Uri.withAppendedPath(URI_CONVERSATIONS, threadId + "/recipients");
        try (Cursor cursor = cr.query(uri, new String[]{"_id"}, null, null, null)) {
            if (cursor != null) {
                return cursor.getCount() > 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // Single query to build a map of Phone Number -> Display Name
    private Map<String, String> buildContactCache(ContentResolver cr) {
        Map<String, String> cache = new HashMap<>();
        try (Cursor cursor = cr.query(
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
                        String cleanNum = number.replaceAll("[^0-9+]", "");
                        cache.put(cleanNum, name);
                        cache.put(number, name);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cache;
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}