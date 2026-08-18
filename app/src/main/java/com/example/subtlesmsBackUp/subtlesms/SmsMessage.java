package com.example.subtlesmsBackUp.subtlesms;

public class SmsMessage {
    private final String id;
    private final String body;
    private final long timestamp;
    private final boolean isSent;
    private boolean isAutomated;
    private MessageStatus status;
    private String senderName; // SENDER ADDRESS OR NAME
    private boolean isGroup;
    private String mediaUrl; // Local content URI string
    private String mediaType;

    public SmsMessage(String id, String body, long timestamp, boolean isSent, boolean isAutomated, int systemStatus, String senderName, boolean isGroup, String mediaUrl, String mediaType) {
        this.id = id;
        this.body = body;
        this.timestamp = timestamp;
        this.isSent = isSent;
        this.isAutomated = isAutomated;
        this.status = determineStatus(isSent, isAutomated, systemStatus);
        this.senderName = senderName;
        this.isGroup = isGroup;
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
    }

    private MessageStatus determineStatus(boolean isSent, boolean isAutomated, int systemStatus) {
        if (!isSent) {
            // 6. Incoming message to me
            return MessageStatus.I_RECEIVED;
        }

        // Outgoing status evaluation based on Android systemStatus
        if (systemStatus == 0) { // STATUS_COMPLETE (Delivered by carrier)
            return isAutomated ? MessageStatus.AUTO_SENT_DELIVERED : MessageStatus.MANUAL_SENT_DELIVERED;
        } else if (systemStatus >= 64) { // STATUS_FAILED / Error code
            return isAutomated ? MessageStatus.AUTO_SENT_FAILED : MessageStatus.MANUAL_SENT_FAILED;
        } else {
            // 1. Message sent, pending delivery (systemStatus -1 or 32)
            return MessageStatus.SENT_PENDING;
        }
    }

    public String getId() { return id; }
    public String getBody() { return body; }
    public long getTimestamp() { return timestamp; }
    public boolean isSent() { return isSent; }
    public boolean isAutomated() { return isAutomated; }
    public MessageStatus getStatus() { return status; }
    public String getSenderName() { return senderName; }
    public boolean isGroup() { return isGroup; }
    public String getMediaUrl() { return mediaUrl; }
    public String getMediaType() { return mediaType; }
    public boolean hasMedia() { return mediaUrl != null && !mediaUrl.isEmpty(); }

    public void setAutomated(boolean automated) {
        this.isAutomated = automated;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }
}