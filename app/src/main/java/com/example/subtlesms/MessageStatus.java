package com.example.subtlesms;

public enum MessageStatus {
    SENT_PENDING("Pending"),                // sent, pending/not delivered (50% opacity)
    MANUAL_SENT_DELIVERED("Delivered"),     // manual sent & delivered (solid border)
    AUTO_SENT_DELIVERED("Auto Delivered"),  // automated sent & delivered (dashed border)
    MANUAL_SENT_FAILED("Failed"),           // manual sent failed (50% opacity, red solid border)
    AUTO_SENT_FAILED("Auto Failed"),        // auto sent failed (50% opacity, red dashed border)
    I_RECEIVED("Received");                 // incoming message

    private final String label;

    MessageStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
