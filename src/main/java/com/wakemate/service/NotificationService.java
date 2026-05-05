package com.wakemate.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.wakemate.model.Alarm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Sends email via SendGrid when a user misses their alarm.
 *
 * Java 25 + Spring Boot 4:
 * With spring.threads.virtual.enabled=true, this I/O-heavy HTTP call
 * to SendGrid runs on a virtual thread automatically.
 * No thread pool tuning needed.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SendGrid sendGrid;
    private final String fromEmail;

    public NotificationService(
            @Value("${sendgrid.api-key}") String apiKey,
            @Value("${sendgrid.from-email}") String fromEmail) {
        this.sendGrid  = new SendGrid(apiKey);
        this.fromEmail = fromEmail;
    }

    public void notifyContact(Alarm alarm) {
        String subject = "Alarm missed: " + alarm.getLabel()
                + " - User #" + alarm.getChatId() + " has not woken up!";

        String body = "Hi " + alarm.getContactName() + ",\n\n"
                + "This is an automated message from WakeMate.\n\n"
                + "User #" + alarm.getChatId() + " set an alarm for "
                + alarm.getAlarmTime() + "\n"
                + "with the note: \"" + alarm.getLabel() + "\"\n\n"
                + "They have not confirmed waking up yet.\n"
                + "You might want to give them a call!\n\n"
                + "-- WakeMate Bot";

        sendEmail(alarm.getContactEmail(), subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        Mail mail = new Mail(
                new Email(fromEmail),
                subject,
                new Email(to),
                new Content("text/plain", body)
        );

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");

        try {
            request.setBody(mail.build());
            Response response = sendGrid.api(request);
            if (response.getStatusCode() >= 400) {
                log.error("SendGrid error {}: {}", response.getStatusCode(), response.getBody());
            } else {
                log.info("Email sent to {} (status {})", to, response.getStatusCode());
            }
        } catch (IOException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}