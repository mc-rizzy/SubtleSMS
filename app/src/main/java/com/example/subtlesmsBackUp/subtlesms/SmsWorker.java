package com.example.subtlesmsBackUp.subtlesms;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.SmsManager;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.HashSet;
import java.util.Set;

public class SmsWorker extends Worker {

    private static final String PREFS_NAME = "SubtleSMS_Prefs";
    private static final String KEY_AUTO_MSG_IDS = "auto_message_ids";

    public SmsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String sender = getInputData().getString("SENDER");
        String responseMessage = getInputData().getString("RESPONSE_MESSAGE");

        if (sender == null || responseMessage == null) {
            return Result.failure();
        }

        try {
            // 1. Mark as automated in SharedPreferences BEFORE dispatching
            markAsAutomated(context, responseMessage);

            // 2. Get SmsManager instance
            SmsManager smsManager;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            // 3. Create PendingIntent for delivery receipt
            Intent deliveryIntent = new Intent("SMS_DELIVERED");
            PendingIntent deliveredPI = PendingIntent.getBroadcast(
                    context,
                    0,
                    deliveryIntent,
                    PendingIntent.FLAG_IMMUTABLE
            );

            // 4. Dispatch the automated SMS (single send with delivery receipt)
            smsManager.sendTextMessage(sender, null, responseMessage, null, deliveredPI);

            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }
    }

    private void markAsAutomated(Context context, String messageText) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> autoSet = new HashSet<>(prefs.getStringSet(KEY_AUTO_MSG_IDS, new HashSet<>()));
        autoSet.add(messageText);
        prefs.edit().putStringSet(KEY_AUTO_MSG_IDS, autoSet).apply();
    }
}