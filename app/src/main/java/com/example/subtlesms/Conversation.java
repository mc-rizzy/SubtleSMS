package com.example.subtlesms;

import java.util.List;

/**
 * Plain data holder for a row in the conversation list.
 * Moved out of ConversationAdapter.java so the AppRepository (and any other
 * component) can build/return these without depending on adapter/UI code.
 */
public class Conversation {

    private List<Integer> recipientIds;
    private List<String> recipientAddresses;
    private String conversationName;
    private final String lastMessage;
    private final String timestamp;
    private String sentiment;
    private final String threadId;
    private boolean rando;
    private boolean archived;
    private int messageCount;

    public Conversation(List<Integer> recipientIds, String mostRecentMessage, String timestamp,
                        String sentiment, String threadId, boolean rando, boolean archived, int messageCount) {
        this.recipientIds = recipientIds;
        this.lastMessage = mostRecentMessage;
        this.timestamp = timestamp;
        this.sentiment = sentiment;
        this.threadId = threadId;
        this.rando = rando;
        this.archived = archived;
        this.messageCount = messageCount;
    }

    public String getConversationName() {
        if(!conversationName.trim().isEmpty())
            return conversationName;
        StringBuilder name = new StringBuilder();
        for(int id : recipientIds) {
            if(name.length() > 0) name.append(", ");
            name.append(AppRepository.getInstance().recipientNameLookup(id));
        }
        conversationName = name.toString();
        return conversationName;
    }
    public List<String> getRecipientAddresses(){
        if(!recipientAddresses.isEmpty()) return recipientAddresses;
        for(int id : recipientIds) {
            recipientAddresses.add(AppRepository.getInstance().recipientLookup(id).first);
        }
        return recipientAddresses;
    }
    public String getLastMessage() { return lastMessage; }
    public String getTimestamp() { return timestamp; }
    public String getSentiment() { return sentiment; }
    public String getThreadId() { return threadId; }
    public boolean getRando() { return rando; }
    public boolean getArchived() { return archived; }
    public int getMessageCount() { return messageCount; }

    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
}
