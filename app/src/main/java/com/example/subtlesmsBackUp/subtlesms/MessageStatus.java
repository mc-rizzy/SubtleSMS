package com.example.subtlesmsBackUp.subtlesms;

public enum MessageStatus {
    SENT_PENDING("Pending"),                          // 1. Sent, pending/not delivered (50% opacity)
    MANUAL_SENT_DELIVERED("Delivered"),             // 2. Manual sent & delivered (solid white border)
    AUTO_SENT_DELIVERED("Auto Delivered"),          // 3. Automated sent & delivered (dashed border)
    MANUAL_SENT_FAILED("Failed"),                   // 4. Manual sent failed (50% opacity, red solid border)
    AUTO_SENT_FAILED("Auto Failed"),                // 5. Auto sent failed (50% opacity, red dashed border)
    I_RECEIVED("Received");                         // 6. Incoming message (no border, slight opaque bg)

    private final String label;

    MessageStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}