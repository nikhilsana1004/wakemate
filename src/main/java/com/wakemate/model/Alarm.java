package com.wakemate.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "alarms")
public class Alarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private LocalDateTime alarmTime;
    private String contactEmail;
    private String contactName;
    private String label;

    @Enumerated(EnumType.STRING)
    private AlarmStatus status;

    private LocalDateTime acknowledgedAt;
    private LocalDateTime notifiedAt;

    // Java 25: pattern matching switch on this enum enforces exhaustive handling
    public enum AlarmStatus {
        PENDING, FIRED, ACKNOWLEDGED, MISSED, CANCELLED
    }

    public Alarm() {}

    public Long getId()                            { return id; }
    public Long getChatId()                        { return chatId; }
    public void setChatId(Long v)                  { this.chatId = v; }
    public LocalDateTime getAlarmTime()            { return alarmTime; }
    public void setAlarmTime(LocalDateTime v)      { this.alarmTime = v; }
    public String getContactEmail()                { return contactEmail; }
    public void setContactEmail(String v)          { this.contactEmail = v; }
    public String getContactName()                 { return contactName; }
    public void setContactName(String v)           { this.contactName = v; }
    public String getLabel()                       { return label; }
    public void setLabel(String v)                 { this.label = v; }
    public AlarmStatus getStatus()                 { return status; }
    public void setStatus(AlarmStatus v)           { this.status = v; }
    public LocalDateTime getAcknowledgedAt()       { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime v) { this.acknowledgedAt = v; }
    public LocalDateTime getNotifiedAt()           { return notifiedAt; }
    public void setNotifiedAt(LocalDateTime v)     { this.notifiedAt = v; }
}