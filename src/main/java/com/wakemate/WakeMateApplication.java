package com.wakemate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * WakeMate - Telegram alarm bot.
 * If you don't dismiss your alarm in time, it emails someone you choose.
 *
 * Java 25 features used:
 *   Virtual Threads         - spring.threads.virtual.enabled=true
 *   Records                 - AlarmRequest DTO
 *   Sealed Interfaces       - ConversationState
 *   Pattern Matching Switch - AlarmService, WakeMateBot
 */
@SpringBootApplication
@EnableScheduling
public class WakeMateApplication {
    public static void main(String[] args) {
        SpringApplication.run(WakeMateApplication.class, args);
    }
}