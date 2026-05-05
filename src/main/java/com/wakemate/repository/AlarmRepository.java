package com.wakemate.repository;

import com.wakemate.model.Alarm;
import com.wakemate.model.Alarm.AlarmStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlarmRepository extends JpaRepository<Alarm, Long> {

    List<Alarm> findByStatusAndAlarmTimeBefore(AlarmStatus status, LocalDateTime cutoff);

    List<Alarm> findByChatIdOrderByAlarmTimeDesc(Long chatId);

    Optional<Alarm> findFirstByChatIdAndStatusOrderByAlarmTimeAsc(Long chatId, AlarmStatus status);

    default List<Alarm> findPendingToFire() {
        return findByStatusAndAlarmTimeBefore(AlarmStatus.PENDING, LocalDateTime.now());
    }

    default List<Alarm> findFiredPastGracePeriod(long gracePeriodMinutes) {
        return findByStatusAndAlarmTimeBefore(
                AlarmStatus.FIRED,
                LocalDateTime.now().minusMinutes(gracePeriodMinutes));
    }
}