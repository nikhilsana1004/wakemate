package com.wakemate.scheduler;

import com.wakemate.service.AlarmService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the alarm lifecycle on a fixed schedule.
 *
 * Java 25 + Spring Boot 4: with spring.threads.virtual.enabled=true,
 * scheduled tasks run on virtual threads automatically.
 * DB queries and HTTP calls never block a platform thread.
 */
@Component
public class AlarmScheduler {

    private final AlarmService alarmService;

    public AlarmScheduler(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    /** Every 30 seconds: move PENDING alarms whose time has passed to FIRED */
    @Scheduled(fixedDelayString = "${wakemate.check-interval-ms:30000}")
    public void fireReadyAlarms() {
        alarmService.fireReadyAlarms();
    }

    /** Every 60 seconds: notify contacts for FIRED alarms past the grace period */
    @Scheduled(fixedDelayString = "${wakemate.check-interval-ms:60000}", initialDelay = 15000)
    public void notifyMissedAlarms() {
        alarmService.notifyMissedAlarms();
    }
}