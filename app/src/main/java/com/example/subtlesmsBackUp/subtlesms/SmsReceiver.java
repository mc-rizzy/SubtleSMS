package com.example.subtlesmsBackUp.subtlesms;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);

            if (messages != null && messages.length > 0) {
                String senderPhoneNumber = messages[0].getOriginatingAddress();
                String incomingBody = messages[0].getMessageBody();


                boolean isSavedContact = isContactSaved(context, senderPhoneNumber);
                boolean DontSend = true;
                if(isSavedContact && !DontSend){
                    // 3. Get the last 3 incoming messages for context
                    List<String> previousMsgs = getPreviousMessages(context, senderPhoneNumber, 3);



                    // Generate your automated response
                    String responseMessageText = "Auto-reply: " + incomingBody;

                    Data inputData = new Data.Builder()
                            .putString("SENDER", senderPhoneNumber)
                            .putString("RESPONSE_MESSAGE", responseMessageText)
                            .build();

                    OneTimeWorkRequest smsWorkRequest = new OneTimeWorkRequest.Builder(SmsWorker.class)
                            .setInputData(inputData)
                            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .build();

                    WorkManager.getInstance(context).enqueue(smsWorkRequest);
                }
            }
        }
    }
    private List<String> getPreviousMessages(Context context, String address, int limit) {
        List<String> messages = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();
        Uri uri = Uri.parse("content://sms/inbox");

        String selection = "address = ?";
        String[] selectionArgs = new String[]{ address };
        String sortOrder = "date DESC LIMIT " + limit;

        try (Cursor cursor = cr.query(uri, new String[]{"body"}, selection, selectionArgs, sortOrder)) {
            if (cursor != null && cursor.moveToFirst()) {
                int bodyIdx = cursor.getColumnIndex("body");
                do {
                    messages.add(cursor.getString(bodyIdx));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return messages;
    }

    public static boolean isContactSaved(Context context, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
        );

        String[] projection = new String[]{ ContactsContract.PhoneLookup.DISPLAY_NAME };

        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                if (nameIdx != -1) {
                    String name = cursor.getString(nameIdx);
                    // Saved contact found if name is not null or empty
                    return name != null && !name.trim().isEmpty();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false; // Number is not saved in contacts
    }
}