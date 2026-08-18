package com.example.subtlesmsBackUp.subtlesms;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView rvConversations;
    private ConversationAdapter adapter;
    private final List<Conversation> conversationList = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, String> contactCache = new HashMap<>();
    private final Map sentimentCache = new HashMap<>();

    private final ActivityResultLauncher<String[]> smsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean isGranted : result.values()) {
                    if (!isGranted) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show();
                    loadAndDisplaySmsData();
                } else {
                    Toast.makeText(this, "Some permissions denied.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        rvConversations = findViewById(R.id.rvConversations);
        rvConversations.setLayoutManager(new LinearLayoutManager(this));

        rvConversations.setHasFixedSize(true);
        rvConversations.setItemViewCacheSize(20);

        setupWindowInsets();
        setupNewConvoButton();

        if (checkAndRequestPermissions()) {
            loadAndDisplaySmsData();
        }
    }

    private void setupWindowInsets() {
        View tvTitle = findViewById(R.id.tvMainTitle);
        if (tvTitle != null) {
            ViewCompat.setOnApplyWindowInsetsListener(tvTitle, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                int basePadding = (int) (16 * getResources().getDisplayMetrics().density);
                v.setPadding(
                        v.getPaddingLeft(),
                        systemBars.top + basePadding,
                        v.getPaddingRight(),
                        v.getPaddingBottom()
                );
                return insets;
            });
        }

        View fab = findViewById(R.id.fabNewConversation);
        if (fab != null) {
            ViewCompat.setOnApplyWindowInsetsListener(fab, (view, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) view.getLayoutParams();
                int marginInPx = (int) (16 * getResources().getDisplayMetrics().density);
                params.bottomMargin = systemBars.bottom + marginInPx;
                view.setLayoutParams(params);
                return insets;
            });
        }
    }
    private void setupNewConvoButton() {
        View fab = findViewById(R.id.fabNewConversation);
        if (fab == null) return;

        fab.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(100).start();
                    break;
                case MotionEvent.ACTION_UP:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                    v.performClick();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                    break;
            }
            return true;
        });

        fab.setOnClickListener(v -> {
            // Handle new conversation logic
        });
    }
    private boolean checkAndRequestPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CONTACTS
        };

        List<String> neededPermissions = new ArrayList<>();
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(perm);
            }
        }

        if (!neededPermissions.isEmpty()) {
            smsPermissionLauncher.launch(neededPermissions.toArray(new String[0]));
            return false;
        } else {
            return true;
        }
    }

    private void analyzeSentimentsAsync() {
        executor.execute(() -> {
            boolean updated = false;

            for (Conversation conv : conversationList) {
                String threadId = conv.getThreadId();
                String body = conv.getLastMessage(); // Or whatever getter returns message body

                // Skip if already in cache
                if (!sentimentCache.containsKey(threadId) && body != null && !body.isEmpty()) {
                    String calculatedSentiment = SentimentAnalysis.analyzeSentimentLocal(body);

                    sentimentCache.put(threadId, calculatedSentiment);
                    conv.setSentiment(calculatedSentiment);
                    updated = true;

                    // Optional: Update UI incrementally every few items if list is long
                } else if (sentimentCache.containsKey(threadId)) {
                    conv.setSentiment((String) sentimentCache.get(threadId));
                }
            }

            // Refresh UI once background sentiment calculations finish
            if (updated) {
                runOnUiThread(() -> {
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }
    private void loadAndDisplaySmsData() {
        executor.execute(() -> {
            List<Conversation> conversations = loadSmsConversationsFast();

            runOnUiThread(() -> {
                conversationList.clear();
                conversationList.addAll(conversations);

                if (adapter == null) {
                    adapter = new ConversationAdapter(conversationList, conversation -> {
                        Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                        intent.putExtra("CONTACT_NAME", conversation.getContactName());
                        intent.putExtra("THREAD_ID", conversation.getThreadId());
                        intent.putExtra("ADDRESS", conversation.getAddress());
                        startActivity(intent);
                    });
                    rvConversations.setAdapter(adapter);
                } else {
                    adapter.notifyDataSetChanged();
                }
            });

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                resolveContactNamesAsync();
            }

            analyzeSentimentsAsync();
        });
    }

    private List<Conversation> loadSmsConversationsFast() {
        List<Conversation> list = new ArrayList<>();
        ContentResolver cr = getContentResolver();

        Map<String, String> canonicalAddressMap = fetchAllCanonicalAddresses(cr);

        Uri uri = Uri.parse("content://mms-sms/conversations?simple=true");
        String[] projection = new String[]{
                "_id",          // thread_id
                "snippet",      // last message body
                "date",         // timestamp
                "recipient_ids" // space-separated recipient address IDs
        };

        try (Cursor cursor = cr.query(uri, projection, null, null, "date DESC")) {
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

                    String sentiment = sentimentCache.getOrDefault(threadId, "").toString();

                    // Initially use raw address for instant display, marking saved contact status as false
                    list.add(new Conversation(rawAddress, body, timestamp, sentiment, threadId, rawAddress, true));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
    private void resolveContactNamesAsync() {
        ContentResolver cr = getContentResolver();

        // Batch query Contacts database directly
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        };

        try (Cursor cursor = cr.query(uri, projection, null, null, null)) {
            if (cursor != null) {
                int numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);

                while (cursor.moveToNext()) {
                    String number = cursor.getString(numIdx);
                    String name = cursor.getString(nameIdx);

                    if (number != null && name != null) {
                        // Normalize phone number (strip spaces/dashes) for reliable matching
                        String normalized = number.replaceAll("[^0-9+]", "");
                        contactCache.put(normalized, name);
                        contactCache.put(number, name);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("SmsDebug", "Error loading bulk contacts", e);
        }

        // Apply resolved names back onto the conversation list
        boolean updated = false;
        for (Conversation conv : conversationList) {
            String rawAddresses = conv.getAddress();
            Pair<String, Boolean> resolved = getContactNamesFromBulkCache(rawAddresses);

            if (resolved.second) { // Name was found in contacts!
                conv.setContactName(resolved.first); // Ensure your Conversation model has a setContactName setter
                conv.setRando(!resolved.second);     // Update your boolean status setter accordingly
                updated = true;
            }
        }

        if (updated) {
            runOnUiThread(() -> {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        }
    }
    private Pair<String, Boolean> getContactNamesFromBulkCache(String rawAddresses) {
        if (rawAddresses == null || rawAddresses.isEmpty()) return new Pair<>("Unknown", false);

        String[] addresses = rawAddresses.split(", ");
        StringBuilder names = new StringBuilder();
        boolean foundAtLeastOneName = false;

        for (String address : addresses) {
            String normalized = address.replaceAll("[^0-9+]", "");
            String resolvedName = contactCache.get(normalized);

            if (resolvedName == null) {
                resolvedName = contactCache.get(address);
            }

            if (resolvedName != null) {
                foundAtLeastOneName = true;
                if (names.length() > 0) names.append(", ");
                names.append(resolvedName);
            } else {
                if (names.length() > 0) names.append(", ");
                names.append(address);
            }
        }

        return new Pair<>(names.toString(), foundAtLeastOneName);
    }
    private Map<String, String> fetchAllCanonicalAddresses(ContentResolver cr) {
        Map<String, String> map = new HashMap<>();
        Uri canonicalUri = Uri.parse("content://mms-sms/canonical-addresses");
        try (Cursor cursor = cr.query(canonicalUri, new String[]{"_id", "address"}, null, null, null)) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndex("_id");
                int addrIdx = cursor.getColumnIndex("address");
                while (cursor.moveToNext()) {
                    map.put(cursor.getString(idIdx), cursor.getString(addrIdx));
                }
            }
        } catch (Exception e) {
            Log.e("SmsDebug", "Failed batch fetch canonical addresses", e);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}