package com.example.subtlesms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);

            if (messages != null && messages.length > 0) {
                String senderPhoneNumber = messages[0].getOriginatingAddress();
                String incomingBody = messages[0].getMessageBody();

                // Generate your automated response
                String responseMessageText = "Auto-reply: " + incomingBody;

                // --- PUT WORKMANAGER ENQUEUE CODE HERE ---
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