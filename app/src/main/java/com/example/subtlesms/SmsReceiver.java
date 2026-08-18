package com.example.subtlesms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/**
 * Entry point for incoming SMS. All auto-reply *decision* logic lives in
 * AutoReplyManager and all *scheduling* logic lives in AutoReplyScheduler -
 * this class just wires the two together.
 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            return;
        }

        String senderPhoneNumber = messages[0].getOriginatingAddress();
        String incomingBody = messages[0].getMessageBody();

        AutoReplyManager autoReplyManager = new AutoReplyManager(context);
        if (!autoReplyManager.shouldAutoReply(senderPhoneNumber)) {
            return;
        }

        String responseMessageText = autoReplyManager.buildReplyText(senderPhoneNumber, incomingBody);
        AutoReplyScheduler.scheduleReply(context, senderPhoneNumber, responseMessageText);
    }
}
