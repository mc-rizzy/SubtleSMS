package com.example.subtlesms;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.List;

/**
 * Decision logic for automatic replies: whether an incoming message should
 * get one, and what the reply text should be. Does NOT send or schedule
 * anything itself - see AutoReplyScheduler for that.
 */
public class AutoReplyManager {

    private final Context context;

    public AutoReplyManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Whether an incoming message from this address should trigger an auto-reply. */
    public boolean shouldAutoReply(String senderAddress) {
        boolean isSavedContact = isContactSaved(senderAddress);
        // Flip this (or wire it up to a settings screen) to turn auto-reply on.
        boolean autoReplyEnabled = false;
        return isSavedContact && autoReplyEnabled;
    }

    /** Builds the reply text, optionally using recent conversation context. */
    public String buildReplyText(String senderAddress, String incomingBody) {
        List<String> previousMessages = getPreviousMessages(senderAddress, 3);
        // Replace with real generation logic (template, on-device model, API call...).
        return "Auto-reply: " + incomingBody;
    }

    private List<String> getPreviousMessages(String address, int limit) {
        List<String> messages = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();
        Uri uri = Uri.parse("content://sms/inbox");

        String selection = "address = ?";
        String[] selectionArgs = new String[]{address};
        String sortOrder = "date DESC LIMIT " + limit;

        try (Cursor cursor = cr.query(uri, new String[]{"body"}, selection, selectionArgs, sortOrder)) {
            if (cursor != null) {
                int bodyIdx = cursor.getColumnIndex("body");
                while (cursor.moveToNext()) {
                    messages.add(cursor.getString(bodyIdx));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return messages;
    }

    private boolean isContactSaved(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) return false;

        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
        );
        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};

        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                if (nameIdx != -1) {
                    String name = cursor.getString(nameIdx);
                    return name != null && !name.trim().isEmpty();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
