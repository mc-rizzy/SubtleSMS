package com.example.subtlesms;

/**
 * Plain data holder for a row in the conversation list.
 * Moved out of ConversationAdapter.java so the AppRepository (and any other
 * component) can build/return these without depending on adapter/UI code.
 */
public class Conversation {

    private String contactName;
    private final String lastMessage;
    private final String timestamp;
    private String sentiment;
    private final String threadId;
    private final String address;
    private boolean rando; // true = address is not a saved contact yet

    public Conversation(String contactName, String lastMessage, String timestamp,
                         String sentiment, String threadId, String address, boolean rando) {
        this.contactName = contactName;
        this.lastMessage = lastMessage;
        this.timestamp = timestamp;
        this.sentiment = sentiment;
        this.threadId = threadId;
        this.address = address;
        this.rando = rando;
    }

    public String getContactName() { return contactName; }
    public String getLastMessage() { return lastMessage; }
    public String getTimestamp() { return timestamp; }
    public String getSentiment() { return sentiment; }
    public String getThreadId() { return threadId; }
    public String getAddress() { return address; }
    public boolean getRando() { return rando; }

    public void setContactName(String contactName) { this.contactName = contactName; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public void setRando(boolean isRando) { this.rando = isRando; }
}
