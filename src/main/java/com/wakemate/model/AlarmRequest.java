package com.wakemate.model;

import java.time.LocalDateTime;

/**
 * Java 25 Record - immutable DTO with zero boilerplate.
 * The compact constructor runs validation automatically at construction time.
 */
public record AlarmRequest(
        LocalDateTime alarmTime,
        String contactEmail,
        String contactName,
        String label
) {
    public AlarmRequest {
        if (alarmTime == null || alarmTime.isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Alarm time must be in the future");
        if (contactEmail == null || !contactEmail.contains("@"))
            throw new IllegalArgumentException("Invalid contact email");
        if (label == null || label.isBlank())
            label = "Wake up!";

        contactEmail = contactEmail.trim().toLowerCase();
        contactName  = contactName != null ? contactName.trim() : "Your contact";
        label        = label.trim();
    }
}