package com.wakemate.service;

import com.wakemate.model.Alarm;
import com.wakemate.model.Alarm.AlarmStatus;
import com.wakemate.model.AlarmRequest;
import com.wakemate.repository.AlarmRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AlarmService {

    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);

    private final AlarmRepository     repo;
    private final NotificationService notificationService;
    private final long                gracePeriodMinutes;

    public AlarmService(
            AlarmRepository repo,
            NotificationService notificationService,
            @Value("${wakemate.grace-period-minutes:5}") long gracePeriodMinutes) {
        this.repo                = repo;
        this.notificationService = notificationService;
        this.gracePeriodMinutes  = gracePeriodMinutes;
    }

    /**
     * Create a new alarm. Cancels any existing pending alarm for this user first.
     * AlarmRequest is a Java 25 Record - validated at construction time.
     */
    public Alarm createAlarm(Long chatId, AlarmRequest request) {
        repo.findFirstByChatIdAndStatusOrderByAlarmTimeAsc(chatId, AlarmStatus.PENDING)
            .ifPresent(a -> { a.setStatus(AlarmStatus.CANCELLED); repo.save(a); });

        Alarm alarm = new Alarm();
        alarm.setChatId(chatId);
        alarm.setAlarmTime(request.alarmTime());
        alarm.setContactEmail(request.contactEmail());
        alarm.setContactName(request.contactName());
        alarm.setLabel(request.label());
        alarm.setStatus(AlarmStatus.PENDING);

        Alarm saved = repo.save(alarm);
        log.info("Alarm {} created for chat {} at {}", saved.getId(), chatId, request.alarmTime());
        return saved;
    }

    /**
     * User confirms they are awake.
     *
     * Java 25 Pattern Matching Switch - exhaustive over AlarmStatus.
     * Adding a new status without handling it here causes a compile error.
     */
    public String acknowledgeAlarm(Long chatId) {
        Optional<Alarm> opt = repo
                .findFirstByChatIdAndStatusOrderByAlarmTimeAsc(chatId, AlarmStatus.FIRED)
                .or(() -> repo.findFirstByChatIdAndStatusOrderByAlarmTimeAsc(chatId, AlarmStatus.PENDING));

        if (opt.isEmpty()) {
            return "No active alarm found. Use /setalarm to set one.";
        }

        Alarm alarm = opt.get();

        return switch (alarm.getStatus()) {
            case PENDING -> {
                alarm.setStatus(AlarmStatus.ACKNOWLEDGED);
                alarm.setAcknowledgedAt(LocalDateTime.now());
                repo.save(alarm);
                yield "Alarm cancelled early - all good!";
            }
            case FIRED -> {
                alarm.setStatus(AlarmStatus.ACKNOWLEDGED);
                alarm.setAcknowledgedAt(LocalDateTime.now());
                repo.save(alarm);
                yield "Great, you are awake! Alarm '" + alarm.getLabel() + "' dismissed.";
            }
            case ACKNOWLEDGED -> "Already acknowledged!";
            case MISSED       -> "This alarm was already marked missed and your contact was notified.";
            case CANCELLED    -> "This alarm was already cancelled.";
        };
    }

    public String cancelAlarm(Long chatId) {
        return repo.findFirstByChatIdAndStatusOrderByAlarmTimeAsc(chatId, AlarmStatus.PENDING)
                .map(a -> {
                    a.setStatus(AlarmStatus.CANCELLED);
                    repo.save(a);
                    return "Alarm '" + a.getLabel() + "' cancelled.";
                })
                .orElse("No pending alarm to cancel.");
    }

    @Transactional(readOnly = true)
    public List<Alarm> getAlarmsForChat(Long chatId) {
        return repo.findByChatIdOrderByAlarmTimeDesc(chatId);
    }

    /** Called by scheduler - transitions PENDING to FIRED */
    public void fireReadyAlarms() {
        repo.findPendingToFire().forEach(a -> {
            a.setStatus(AlarmStatus.FIRED);
            repo.save(a);
            log.info("Alarm {} fired for chat {}", a.getId(), a.getChatId());
        });
    }

    /** Called by scheduler - transitions FIRED to MISSED and sends email */
    public void notifyMissedAlarms() {
        repo.findFiredPastGracePeriod(gracePeriodMinutes).forEach(a -> {
            a.setStatus(AlarmStatus.MISSED);
            a.setNotifiedAt(LocalDateTime.now());
            repo.save(a);
            notificationService.notifyContact(a);
            log.warn("Alarm {} missed - contact {} notified", a.getId(), a.getContactEmail());
        });
    }
}