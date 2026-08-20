package com.example.subtlesms;

import android.text.format.DateFormat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain data holder for a row in the conversation list.
 * Moved out of ConversationAdapter.java so the AppRepository (and any other
 * component) can build/return these without depending on adapter/UI code.
 */
public class Conversation {

    private List<Long> recipientIds;
    private List<String> recipientAddresses;
    private String conversationName = "";
    private String lastMessage;
    private final Long rawTime;
    private String sentiment;
    private final String threadId;
    private boolean rando;
    private boolean archived;
    private Long messageCount;

    public Conversation(List<Long> recipientIds, String mostRecentMessage, Long rawTime,
                        String sentiment, String threadId, boolean rando, boolean archived, Long messageCount) {
        this.recipientIds = recipientIds;
        this.lastMessage = mostRecentMessage;
        this.rawTime = rawTime;
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
        for(Long id : recipientIds) {
            if(name.length() > 0) name.append(", ");
            name.append(AppRepository.getInstance().recipientNameLookup(id));
        }
        conversationName = name.toString();
        return conversationName;
    }
    public List<String> getRecipientAddresses(){
        if(recipientAddresses != null && !recipientAddresses.isEmpty()) return recipientAddresses;
        recipientAddresses = new ArrayList<>();
        for(Long id : recipientIds) {
            recipientAddresses.add(AppRepository.getInstance().recipientLookup(id).first);
        }
        return recipientAddresses;
    }
    public String getLastMessage() { return lastMessage; }
    public Long getRawTime() { return rawTime; }
    public String getTimestamp() { return DateFormat.format("hh:mm a", rawTime).toString(); }
    public String getSentiment() { return sentiment; }
    public String getThreadId() { return threadId; }
    public boolean getRando() { return rando; }
    public boolean getArchived() { return archived; }
    public Long getMessageCount() { return messageCount; }
    public boolean getIsGroup() { return recipientIds.size() > 1; }

    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
}
