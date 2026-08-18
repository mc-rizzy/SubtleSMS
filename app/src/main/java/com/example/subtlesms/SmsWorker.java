package com.example.subtlesms;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Actually sends the automated reply that AutoReplyManager/AutoReplyScheduler
 * decided on. Automated-message bookkeeping goes through AppRepository so
 * ChatActivity's message list (via the same repository) reflects it too.
 */
public class SmsWorker extends Worker {

    public SmsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String sender = getInputData().getString(AutoReplyScheduler.KEY_SENDER);
        String responseMessage = getInputData().getString(AutoReplyScheduler.KEY_RESPONSE_MESSAGE);

        if (sender == null || responseMessage == null) {
            return Result.failure();
        }

        try {
            AppRepository.getInstance(context).markMessageAsAutomated(responseMessage);

            SmsManager smsManager;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            Intent deliveryIntent = new Intent("SMS_DELIVERED");
            PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0, deliveryIntent, PendingIntent.FLAG_IMMUTABLE);

            smsManager.sendTextMessage(sender, null, responseMessage, null, deliveredPI);

            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }
    }
}
