package com.example.subtlesms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                String format = bundle.getString("format");

                if (pdus != null) {
                    for (Object pdu : pdus) {
                        SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu, format);
                        String sender = message.getDisplayOriginatingAddress();
                        String body = message.getMessageBody();

                        // Enqueue work off the main thread
                        scheduleAnalysisAndReply(context, sender, body);
                    }
                }
            }
        }
    }

    private void scheduleAnalysisAndReply(Context context, String sender, String body) {
        Data inputData = new Data.Builder()
                .putString("SENDER", sender)
                .putString("BODY", body)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(SmsWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(context).enqueue(workRequest);
    }
}