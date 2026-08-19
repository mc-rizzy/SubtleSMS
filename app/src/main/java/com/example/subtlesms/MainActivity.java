package com.example.subtlesms;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.List;
import com.example.subtlesms.Conversation;

/**
 * Conversation list screen. All data access goes through AppRepository -
 * this class no longer talks to ContentResolver/ContactsContract directly.
 */
public class MainActivity extends AppCompatActivity {

    private RecyclerView rvConversations;
    private ConversationAdapter adapter;
    private final List<Conversation> conversationList = new ArrayList<>();
    private AppRepository repository;

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
                    loadConversations();
                } else {
                    Toast.makeText(this, "Some permissions denied.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        repository = AppRepository.getInstance();

        rvConversations = findViewById(R.id.rvConversations);
        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setHasFixedSize(true);
        rvConversations.setItemViewCacheSize(20);

//        setupNewConvoButton();

        if (checkAndRequestPermissions()) {
            loadConversations();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cheap: getConversations() returns the cached list instantly if nothing
        // invalidated it (e.g. no new message arrived while we were away).
        if (!conversationList.isEmpty()) {
            repository.getConversations(this::onConversationsLoaded);
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
        }
        return true;
    }

    /** Kicks off the initial load. Everything after this reacts to repository callbacks. */
    private void loadConversations() {
        repository.getConversations(this::onConversationsLoaded);
    }

    private void onConversationsLoaded(List<Conversation> conversations) {
        runOnUiThread(() -> {
            conversationList.clear();
            conversationList.addAll(conversations);

            if (adapter == null) {
                adapter = new ConversationAdapter(conversationList, conversation -> {
                    Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                    intent.putExtra("CONTACT_NAME", conversation.getConversationName());
                    intent.putExtra("THREAD_ID", conversation.getThreadId());
//                    intent.putExtra("ADDRESS", conversation.getAddress());
                    startActivity(intent);
                });
                rvConversations.setAdapter(adapter);
            } else {
                adapter.notifyDataSetChanged();
            }

//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
//                repository.resolveContactNames(updated -> {
//                    if (updated != null) runOnUiThread(() -> adapter.notifyDataSetChanged());
//                });
//            }

            repository.analyzeSentiments(updated -> {
                if (updated != null) runOnUiThread(() -> adapter.notifyDataSetChanged());
            });
        });
    }
}
