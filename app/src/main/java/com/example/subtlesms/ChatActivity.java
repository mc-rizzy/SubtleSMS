package com.example.subtlesms;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Message thread screen. All reads/writes to SMS data go through
 * AppRepository so the conversation list screen sees consistent data
 * without re-querying the provider itself.
 */
public class ChatActivity extends AppCompatActivity {

    private RecyclerView rvMessages;
    private ChatAdapter adapter;
    private final List<SmsMessage> messagesList = new ArrayList<>();
    private EditText etMessageInput;
    private String threadId;
    private ContentObserver smsObserver;
    private AppRepository repository;
    private Conversation conversation;
    private SmsMessage mostRecentMessage;
    private static final String ACTION_SMS_SENT = "com.example.subtlesms.SMS_SENT";
    private long placeholderIdCounter = -1;
    private final List<SmsMessage> pendingSentMessages = new ArrayList<>();

    private static final Uri URI_MMS_SMS = Uri.parse("content://mms-sms/");

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) sendMediaMessage(uri);
            });

    private final BroadcastReceiver deliveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            for (SmsMessage msg : messagesList) {
                if (msg.getStatus() == SmsMessage.QUEUED_SEND) {
                    msg.setStatus(SmsMessage.SENT_MESSAGE);
                    msg.setTimestamp(System.currentTimeMillis());
                }
            }
            adapter.notifyDataSetChanged();
        }
    };

    private final BroadcastReceiver sentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Iterator<SmsMessage> it = pendingSentMessages.iterator();
            while (it.hasNext()) {
                SmsMessage msg = it.next();
                String realId = findJustSentMessageId(context, msg.getAddress(), msg.getBody());
                if (realId != null) {
                    long oldId = msg.getId();
                    msg.setId(Long.parseLong(realId));
                    repository.updateLocalMessageId(oldId, msg);
                    it.remove();
                }
            }
            adapter.refresh();
        }
    };

    private String findJustSentMessageId(Context context, String address, String body) {
        Uri uri = Uri.parse("content://sms/sent");
        String selection = "address = ? AND body = ?";
        String[] selectionArgs = new String[]{address, body};
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{"_id"}, selection, selectionArgs, "date DESC LIMIT 1")) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow("_id"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_chat);

        repository = AppRepository.getInstance();

        resizeWhenTexting();

        ImageButton btnAttachMedia = findViewById(R.id.btnAttachMedia);
        btnAttachMedia.setOnClickListener(v -> pickMediaLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                        .build()
        ));

        String contactName = getIntent().getStringExtra("CONTACT_NAME");
        threadId = getIntent().getStringExtra("THREAD_ID");

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



        smsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                refreshMessages();
            }
        };

        loadMessages();
    }

    private void resizeWhenTexting(){
        View root = findViewById(R.id.rootChatLayout);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset);

            if (ime.bottom > 0 && adapter != null && adapter.getItemCount() > 0) {
                rvMessages.post(() -> rvMessages.scrollToPosition(adapter.getItemCount() - 1));
            }
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(URI_MMS_SMS, true, smsObserver);
        try {
            ContextCompat.registerReceiver(this, deliveryReceiver, new IntentFilter("SMS_DELIVERED"), ContextCompat.RECEIVER_EXPORTED);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            ContextCompat.registerReceiver(this, sentReceiver, new IntentFilter(ACTION_SMS_SENT), ContextCompat.RECEIVER_EXPORTED);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(smsObserver);
        unregisterReceiver(deliveryReceiver);
        unregisterReceiver(sentReceiver);
    }

    /** First load: use whatever the repository already has cached, if anything. */
    private void loadMessages() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        repository.getMessages(threadId, this::onMessagesLoaded);
    }

    /** The provider changed underneath us (new message, status update) - force a re-fetch. */
    private void refreshMessages() {
        repository.refreshMessages(threadId, this::onMessagesLoaded);
    }

    private void onMessagesLoaded(List<SmsMessage> messages) {
        runOnUiThread(() -> {
            messagesList.clear();
            messagesList.addAll(messages);
            mostRecentMessage = messagesList.get(messagesList.size()-1);
            conversation = repository.conversationLookUp(mostRecentMessage.getThreadId());
            adapter.refresh();
            if (adapter.getItemCount() > 0) {
                rvMessages.scrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    private void sendMessage() {
        String messageText = etMessageInput.getText().toString().trim();
        if (messageText.isEmpty()) return;
        if (conversation == null) return;

        if (conversation.getRecipientAddresses().isEmpty()) {
            Toast.makeText(this, "Recipient phone number unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SEND_SMS permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            SmsManager smsManager = SmsManager.getDefault();
            Intent sentIntentObj = new Intent(ACTION_SMS_SENT);
            PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, sentIntentObj, PendingIntent.FLAG_IMMUTABLE);
            Intent deliveryIntent = new Intent("SMS_DELIVERED");
            PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0, deliveryIntent, PendingIntent.FLAG_IMMUTABLE);

            if (conversation.getIsGroup()) {
                for (String recipient : conversation.getRecipientAddresses()) {
                    if (!recipient.trim().isEmpty()) {
                        smsManager.sendTextMessage(recipient, null, messageText, sentPI, deliveredPI);
                    }
                }
            } else {
                smsManager.sendTextMessage(conversation.getRecipientAddresses().get(0), null, messageText, sentPI, deliveredPI);
            }

            long nowMs = System.currentTimeMillis();

            SmsMessage sentMsg = new SmsMessage(placeholderIdCounter--, Long.parseLong(conversation.getThreadId()),
                    conversation.getRecipientAddresses().get(0), messageText, "Me",
                    0, nowMs,
                    true, false, false,
                    SmsMessage.SIMPLE_PENDING, SmsMessage.QUEUED_SEND, 0);

            messagesList.add(sentMsg);
            pendingSentMessages.add(sentMsg);
            adapter.refresh();
            rvMessages.scrollToPosition(adapter.getItemCount() - 1);
            repository.appendLocalMessage(threadId, sentMsg);
            etMessageInput.setText("");
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to send SMS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendMediaMessage(Uri imageUri) {
        List<String> addresses = conversation.getRecipientAddresses();
        if (addresses == null || addresses.isEmpty()) {
            Toast.makeText(this, "No valid recipients found", Toast.LENGTH_SHORT).show();
            return;
        }
        String joinedAddresses = TextUtils.join(",", addresses);

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + Uri.encode(joinedAddresses)));
        intent.putExtra("address", joinedAddresses);
        intent.putExtra(Intent.EXTRA_STREAM, imageUri);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "No app available to handle MMS send", Toast.LENGTH_SHORT).show();
        }
    }
}