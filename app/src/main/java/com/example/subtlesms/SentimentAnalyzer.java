package com.example.subtlesms;

/**
 * Everything related to scoring the sentiment of a message body lives here.
 * Anything that needs a sentiment value (MainActivity, AppRepository, future
 * screens) should call into this class instead of re-implementing the rules.
 */
public class SentimentAnalyzer {

    public static final String POSITIVE = "POSITIVE";
    public static final String NEGATIVE = "NEGATIVE";
    public static final String NEUTRAL = "NEUTRAL";

    public static String analyze(String text) {
        if (text == null) return NEUTRAL;
        String lower = text.toLowerCase();
        if (lower.contains("thanks") || lower.contains("great") || lower.contains("good") || lower.contains("love")) {
            return POSITIVE;
        } else if (lower.contains("urgent") || lower.contains("bad") || lower.contains("late") || lower.contains("sorry")) {
            return NEGATIVE;
        }
        return NEUTRAL;
    }
}
