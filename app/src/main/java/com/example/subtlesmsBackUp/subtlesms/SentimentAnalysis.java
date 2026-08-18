package com.example.subtlesmsBackUp.subtlesms;

public class SentimentAnalysis {
    public static String analyzeSentimentLocal(String text) {
        if (text == null) return "NEUTRAL";
        String lower = text.toLowerCase();
        if (lower.contains("thanks") || lower.contains("great") || lower.contains("good") || lower.contains("love")) {
            return "POSITIVE";
        } else if (lower.contains("urgent") || lower.contains("bad") || lower.contains("late") || lower.contains("sorry")) {
            return "NEGATIVE";
        }
        return "NEUTRAL";
    }
}
