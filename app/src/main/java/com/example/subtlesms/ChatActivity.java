package com.example.subtlesms;

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
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
            Insets imeAndNav = insets.getInsets(WindowInsetsCompat.Type.ime() | WindowInsetsCompat.Type.navigationBars());

            int padding16Px = (int) (16 * getResources().getDisplayMetrics().density);
            tvChatTitle.setPadding(padding16Px, statusBars.top + padding16Px, padding16Px, padding16Px);

            // Apply bottom padding instead of margin so the black background extends behind nav bar
            layoutInputArea.setPadding(
                    layoutInputArea.getPaddingLeft(),
                    layoutInputArea.getPaddingTop(),
                    layoutInputArea.getPaddingRight(),
                    imeAndNav.bottom + (int) (8 * getResources().getDisplayMetrics().density)
            );

            scrollToBottom();
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

    private void setupWindowInsets(){
        View rootLayout = findViewById(R.id.rootChatLayout);

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Apply top padding for status bar and bottom padding for navigation bar / keyboard
            v.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
            );
            return insets;
        });
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
                Uri.parse("content://sms/"),
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

            smsManager.sendTextMessage(recipientAddress, null, messageText, null, deliveredPI);

            long nowMs = System.currentTimeMillis();
            SmsMessage sentMsg = new SmsMessage(
                    String.valueOf(nowMs),
                    messageText,
                    nowMs,
                    true,
                    false,
                    -1
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

        // Fetch SharedPreferences set ONCE outside the loop
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> autoSet = prefs.getStringSet(KEY_AUTO_MSG_IDS, new HashSet<>());

        ContentResolver cr = getContentResolver();
        Uri uri = Uri.parse("content://sms/");

        String selection = (threadId != null && !threadId.isEmpty()) ? "thread_id = ?" : "address = ?";
        String[] selectionArgs = (threadId != null && !threadId.isEmpty()) ? new String[]{threadId} : new String[]{address};

        String[] projection = new String[]{"_id", "body", "date", "type", "read", "seen", "status"};

        try (Cursor cursor = cr.query(uri, projection, selection, selectionArgs, "date ASC")) {
            if (cursor != null && cursor.moveToFirst()) {
                int idIdx = cursor.getColumnIndex("_id");
                int bodyIdx = cursor.getColumnIndex("body");
                int dateIdx = cursor.getColumnIndex("date");
                int typeIdx = cursor.getColumnIndex("type");
                int statusIdx = cursor.getColumnIndex("status");

                do {
                    String id = cursor.getString(idIdx);
                    String body = cursor.getString(bodyIdx);
                    long dateMs = cursor.getLong(dateIdx);
                    int type = cursor.getInt(typeIdx);
                    int systemStatus = cursor.getInt(statusIdx);

                    boolean isSent = (type == Telephony.Sms.MESSAGE_TYPE_SENT);

                    // In-memory set lookup
                    boolean isAutomated = isSent && (autoSet.contains(body) || autoSet.contains(String.valueOf(dateMs)));

                    messages.add(new SmsMessage(
                            id,
                            body,
                            dateMs,
                            isSent,
                            isAutomated,
                            systemStatus
                    ));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return messages;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}