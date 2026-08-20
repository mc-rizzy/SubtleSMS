package com.example.subtlesms;

import android.net.Uri;
import android.util.Log;

public class SmsMessage {
    private final long id;
    private final long threadId;
    private final String address;
    private String body;
    private final String name;
    private long date;
    private final long dateSent;
    private boolean read;
    private int status;
    private boolean hasMedia;
    private String mediaType;
    private final boolean isAutomated;
    private int simpleStatus;
    private final int subId;
    private Uri mediaContent;

    public static final int RECEIVED_MESSAGE = 1;
    public static final int SENT_MESSAGE = 2;
    public static final int DRAFT_MESSAGE = 3;
    public static final int QUEUED_SEND = 4;
    public static final int FAILED_SEND = 5;
    public static final int RETRYING_SEND = 6;


    public static final int SIMPLE_RECEIVED = -1;
    public static final int SIMPLE_DELIVERED = 0;
    public static final int SIMPLE_PENDING = 32;
    public static final int SIMPLE_FAILED = 64;



    public SmsMessage(long id, long threadIdVal, String address, String body, String name,
                      long date, long dateSent, boolean read, boolean isMedia, boolean isAutomated, int statusMode1, int statusMode2, int subId) {
        this.id = id;
        this.threadId = threadIdVal;
        this.address = address;
        this.body = body;
        this.name = name;
        this.date = date;
        this.dateSent = dateSent;
        this.read = read;
        this.hasMedia = isMedia;
        this.isAutomated = isAutomated;
        this.simpleStatus = statusMode1; // -1 received messages, 0 (Complete/Delivered), 32 (Pending), 64 (Failed)
        this.status = statusMode2;
//        Values: Maps to Telephony.Sms.MESSAGE_TYPE_* constants:
//        1 = MESSAGE_TYPE_INBOX (Received message)
//        2 = MESSAGE_TYPE_SENT (Sent message)
//        3 = MESSAGE_TYPE_DRAFT (Saved draft)
//        4 = MESSAGE_TYPE_OUTBOX (Queued to send)
//        5 = MESSAGE_TYPE_FAILED (Failed to send)
//        6 = MESSAGE_TYPE_QUEUED (Pending retry)
        this.subId = subId;
    }

    public void setBody(String newBody){
        this.body+=newBody;
    }
    public long getId() {return this.id;}
    public void setMedia(Uri media){
        this.hasMedia=true;
        this.mediaContent = media;
    }
    public void setMediaType(String media){
        this.mediaType=media;
    }
    public boolean isSent(){
        return status == SENT_MESSAGE || status == QUEUED_SEND || status == FAILED_SEND || status == RETRYING_SEND;
    }
    public String getBody(){
        return this.body;
    }
    public Long getTimestamp(){
        return (dateSent != 0) ? dateSent : date;
    }
    public void setTimestamp(long dateDelivered) {this.date = dateDelivered;}
    public boolean getIsAutomated(){
        return isAutomated;
    }
    public long getThreadId(){
        return threadId;
    }
    public String getName(){
        return name;
    }
    public int getStatus(){
        return status;
    }
    public void setStatus(int status) {this.status = status;}
    public String getMediaType(){
        return mediaType;
    }
    public boolean hasMedia(){
        return hasMedia;
    }
    public Uri getMediaUri(){ return mediaContent; }

    private MessageStatus determineStatus(boolean isSent, boolean isAutomated, int systemStatus) {
        if (!isSent) {
            return MessageStatus.I_RECEIVED;
        }
        if (systemStatus == 0) { // STATUS_COMPLETE (Delivered by carrier)
            return isAutomated ? MessageStatus.AUTO_SENT_DELIVERED : MessageStatus.MANUAL_SENT_DELIVERED;
        } else if (systemStatus >= 64) { // STATUS_FAILED / Error code
            return isAutomated ? MessageStatus.AUTO_SENT_FAILED : MessageStatus.MANUAL_SENT_FAILED;
        } else {
            return MessageStatus.SENT_PENDING;
        }
    }
}