package com.example.subtlesms;

import android.content.Context;
import android.telephony.SmsManager;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class SmsWorker extends Worker {

    public SmsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String sender = getInputData().getString("SENDER");
        String body = getInputData().getString("BODY");

        if (sender == null || body == null) {
            return Result.failure();
        }

        // 1. Perform Sentiment Analysis
        String sentiment = analyzeSentiment(body);

        // 2. Generate Automated Response
        String responseMessage = generateResponse(sentiment);

        // 3. Dispatch SMS
        sendSms(sender, responseMessage);

        return Result.success();
    }

    private String analyzeSentiment(String text) {
        // Simple heuristic rules (Replace with TensorFlow Lite or Remote REST API)
        String lower = text.toLowerCase();
        if (lower.contains("urgent") || lower.contains("bad") || lower.contains("help") || lower.contains("error")) {
            return "NEGATIVE";
        } else if (lower.contains("thanks") || lower.contains("great") || lower.contains("good") || lower.contains("awesome")) {
            return "POSITIVE";
        }
        return "NEUTRAL";
    }

    private String generateResponse(String sentiment) {
        switch (sentiment) {
            case "POSITIVE":
                return "Thanks for the positive message! We will get back to you shortly.";
            case "NEGATIVE":
                return "We noticed your message seems urgent. Our support team has been notified.";
            default:
                return "Thank you for reaching out. Message received.";
        }
    }

    private void sendSms(String phoneNumber, String message) {
        try {
            SmsManager smsManager;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                smsManager = getApplicationContext().getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}