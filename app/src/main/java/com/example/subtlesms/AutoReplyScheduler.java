package com.example.subtlesms;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

/**
 * Owns all WorkManager plumbing for dispatching an automated reply.
 * SmsReceiver decides *whether* to reply (AutoReplyManager); this class
 * is only responsible for getting that reply enqueued and eventually sent
 * (SmsWorker).
 */
public class AutoReplyScheduler {

    public static final String KEY_SENDER = "SENDER";
    public static final String KEY_RESPONSE_MESSAGE = "RESPONSE_MESSAGE";

    public static void scheduleReply(Context context, String senderAddress, String responseMessage) {
        Data inputData = new Data.Builder()
                .putString(KEY_SENDER, senderAddress)
                .putString(KEY_RESPONSE_MESSAGE, responseMessage)
                .build();

        OneTimeWorkRequest smsWorkRequest = new OneTimeWorkRequest.Builder(SmsWorker.class)
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build();

        WorkManager.getInstance(context).enqueue(smsWorkRequest);
    }
}
