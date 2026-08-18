package com.example.subtlesms;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import java.util.ArrayList;
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
    private String recipientAddress;
    private String threadId;
    private ContentObserver smsObserver;
    private AppRepository repository;

    private static final Uri URI_MMS_SMS = Uri.parse("content://mms-sms/");

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) sendMediaMessage(uri);
            });

    private final BroadcastReceiver deliveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            for (SmsMessage msg : messagesList) {
                if (msg.isSent() && msg.getStatus() == MessageStatus.SENT_PENDING) {
                    msg.setStatus(msg.isAutomated() ? MessageStatus.AUTO_SENT_DELIVERED : MessageStatus.MANUAL_SENT_DELIVERED);
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

        repository = AppRepository.getInstance(this);

        ImageButton btnAttachMedia = findViewById(R.id.btnAttachMedia);
        btnAttachMedia.setOnClickListener(v -> pickMediaLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                        .build()
        ));

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
            Insets navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(navigationBars.bottom, ime.bottom);
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

        loadMessages();
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(smsObserver);
        unregisterReceiver(deliveryReceiver);
    }

    /** First load: use whatever the repository already has cached, if anything. */
    private void loadMessages() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        repository.getMessages(threadId, recipientAddress, this::onMessagesLoaded);
    }

    /** The provider changed underneath us (new message, status update) - force a re-fetch. */
    private void refreshMessages() {
        repository.refreshMessages(threadId, recipientAddress, this::onMessagesLoaded);
    }

    private void onMessagesLoaded(List<SmsMessage> messages) {
        runOnUiThread(() -> {
            messagesList.clear();
            messagesList.addAll(messages);
            adapter.refresh();
            if (adapter.getItemCount() > 0) {
                rvMessages.scrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    private void sendMessage() {
        String messageText = etMessageInput.getText().toString().trim();
        if (messageText.isEmpty()) return;

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
            PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0, deliveryIntent, PendingIntent.FLAG_IMMUTABLE);

            boolean isGroup = recipientAddress.contains(",");
            if (isGroup) {
                for (String recipient : recipientAddress.split(",")) {
                    String cleanNumber = recipient.trim();
                    if (!cleanNumber.isEmpty()) {
                        smsManager.sendTextMessage(cleanNumber, null, messageText, null, deliveredPI);
                    }
                }
            } else {
                smsManager.sendTextMessage(recipientAddress, null, messageText, null, deliveredPI);
            }

            long nowMs = System.currentTimeMillis();
            SmsMessage sentMsg = new SmsMessage(String.valueOf(nowMs), messageText, nowMs, true, false, -1, "Me", isGroup, null, "text");

            messagesList.add(sentMsg);
            adapter.refresh();
            rvMessages.scrollToPosition(adapter.getItemCount() - 1);
            repository.appendLocalMessage(threadId, recipientAddress, sentMsg);

            etMessageInput.setText("");
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to send SMS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendMediaMessage(Uri imageUri) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + recipientAddress));
        intent.putExtra("address", recipientAddress);
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