package com.wakemate.bot;

import com.wakemate.bot.ConversationState.State;
import com.wakemate.model.Alarm;
import com.wakemate.model.AlarmRequest;
import com.wakemate.service.AlarmParserService;
import com.wakemate.service.AlarmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Main Telegram bot - the user's only interface with WakeMate.
 *
 * Conversation flow:
 *   /setalarm -> time -> contact email -> contact name -> label -> confirmed
 *
 * Java 25 features:
 *   - Sealed interface + pattern matching switch for conversation state
 *   - Records as DTOs (AlarmRequest)
 *   - Virtual threads via Spring Boot 4 config
 */
@Component
public class WakeMateBot implements SpringLongPollingBot, LongPollingUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(WakeMateBot.class);
    private static final DateTimeFormatter TIME_FMT    = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final String             botToken;
    private final TelegramClient     telegramClient;
    private final AlarmService       alarmService;
    private final AlarmParserService alarmParserService;
    private final ConversationState  state;

    public WakeMateBot(
            @Value("${telegram.bot.token}") String botToken,
            AlarmService alarmService,
            AlarmParserService alarmParserService) {
        this.botToken            = botToken;
        this.telegramClient      = new OkHttpTelegramClient(botToken);
        this.alarmService        = alarmService;
        this.alarmParserService  = alarmParserService;
        this.state               = new ConversationState();
    }

    @Override public String getBotToken()                           { return botToken; }
    @Override public LongPollingUpdateConsumer getUpdatesConsumer() { return this; }

    @Override
    public void consume(List<Update> updates) {
        updates.forEach(this::handleUpdate);
    }

    private void handleUpdate(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;
        Long   chatId    = update.getMessage().getChatId();
        String text      = update.getMessage().getText().trim();
        String firstName = update.getMessage().getFrom().getFirstName();

        if (text.startsWith("/")) {
            handleCommand(chatId, text, firstName);
            return;
        }
        handleConversationStep(chatId, text);
    }

    private void handleCommand(Long chatId, String command, String firstName) {
        String response = switch (command.split(" ")[0].toLowerCase()) {
            case "/start" ->
                "Hey " + firstName + ", welcome to WakeMate!\n\n"
                + "If you do not dismiss your alarm in time, I will email someone you choose.\n\n"
                + "/setalarm - set a new alarm\n"
                + "/awake    - dismiss your current alarm\n"
                + "/cancel   - cancel your pending alarm\n"
                + "/myalarms - see your alarm history";
            case "/setalarm" -> {
                state.set(chatId, new State.AwaitingTime());
                yield "Let's set your alarm!\n\n"
                    + "What time should it go off?\n"
                    + "Send the time in 24h format: HH:mm\n"
                    + "Example: 07:30";
            }
            case "/awake"    -> alarmService.acknowledgeAlarm(chatId);
            case "/cancel"   -> alarmService.cancelAlarm(chatId);
            case "/myalarms" -> buildAlarmList(chatId);
            default          -> "Unknown command. Try /setalarm or /help";
        };
        send(chatId, response);
    }

    /**
     * Java 25 Pattern Matching Switch on Sealed Interface.
     * Each State record carries exactly the data collected so far.
     * If a new State is added without a matching case here, the build fails.
     */
    private void handleConversationStep(Long chatId, String text) {
        switch (state.get(chatId)) {

            case State.Idle() -> {
                // Try to parse as a natural language alarm request via LLM
                send(chatId, "Let me understand that...");
                alarmParserService.parse(text).ifPresentOrElse(
                    request -> {
                        try {
                            Alarm alarm = alarmService.createAlarm(chatId, request);
                            send(chatId, "Got it! Alarm set from your message:\n\n"
                                    + "Time:    " + alarm.getAlarmTime().format(DISPLAY_FMT) + "\n"
                                    + "Label:   " + alarm.getLabel() + "\n"
                                    + "Contact: " + alarm.getContactName()
                                    + " (" + alarm.getContactEmail() + ")\n\n"
                                    + "Send /awake when you are up. "
                                    + "If you don't respond within 5 minutes, "
                                    + "I will email " + alarm.getContactName() + ".");
                        } catch (IllegalArgumentException e) {
                            send(chatId, "I understood your message but hit an issue: "
                                    + e.getMessage()
                                    + "\n\nTry /setalarm for the step-by-step flow.");
                        }
                    },
                    () -> send(chatId, "I could not understand that as an alarm request.\n\n"
                            + "Try something like:\n"
                            + "  \"wake me at 7:30, tell John at john@gmail.com, it's for my flight\"\n\n"
                            + "Or use /setalarm for the step-by-step setup.")
                );
            }

            case State.AwaitingTime() -> {
                try {
                    LocalTime time = LocalTime.parse(text, TIME_FMT);
                    LocalDateTime dt = LocalDate.now().atTime(time);
                    if (dt.isBefore(LocalDateTime.now())) dt = dt.plusDays(1);

                    state.set(chatId, new State.AwaitingContactEmail(dt.toString()));
                    send(chatId, "Got it - alarm at " + text + "\n\n"
                            + "What is the email of the person I should notify if you do not wake up?");
                } catch (DateTimeParseException e) {
                    send(chatId, "Could not parse that time. Please use HH:mm format, e.g. 07:30");
                }
            }

            case State.AwaitingContactEmail(String alarmTime) -> {
                if (!text.contains("@")) {
                    send(chatId, "That does not look like a valid email. Please try again:");
                    return;
                }
                state.set(chatId, new State.AwaitingContactName(alarmTime, text));
                send(chatId, "Great! What is your contact's name?");
            }

            case State.AwaitingContactName(String alarmTime, String email) -> {
                state.set(chatId, new State.AwaitingLabel(alarmTime, email, text));
                send(chatId, "Last step - give this alarm a label.\n"
                        + "Example: Morning standup, Flight, Gym\n"
                        + "Or send 'skip' to use the default.");
            }

            case State.AwaitingLabel(String alarmTime, String email, String contactName) -> {
                String label = text.equalsIgnoreCase("skip") ? "Wake up!" : text;
                try {
                    AlarmRequest request = new AlarmRequest(
                            LocalDateTime.parse(alarmTime), email, contactName, label);
                    Alarm alarm = alarmService.createAlarm(chatId, request);
                    state.reset(chatId);

                    send(chatId, "Alarm set!\n\n"
                            + "Time:    " + alarm.getAlarmTime().format(DISPLAY_FMT) + "\n"
                            + "Label:   " + alarm.getLabel() + "\n"
                            + "Contact: " + alarm.getContactName()
                            + " (" + alarm.getContactEmail() + ")\n\n"
                            + "Send /awake when you are up.\n"
                            + "If you do not respond within 5 minutes, "
                            + "I will email " + alarm.getContactName() + ".");
                } catch (IllegalArgumentException e) {
                    state.reset(chatId);
                    send(chatId, "Could not create alarm: " + e.getMessage()
                            + "\nUse /setalarm to try again.");
                }
            }
        }
    }

    private String buildAlarmList(Long chatId) {
        List<Alarm> alarms = alarmService.getAlarmsForChat(chatId);
        if (alarms.isEmpty()) return "No alarms yet. Use /setalarm!";

        StringBuilder sb = new StringBuilder("Your recent alarms:\n\n");
        alarms.stream().limit(5).forEach(a -> {
            String status = switch (a.getStatus()) {
                case PENDING      -> "[PENDING]";
                case FIRED        -> "[FIRED]";
                case ACKNOWLEDGED -> "[DONE]";
                case MISSED       -> "[MISSED]";
                case CANCELLED    -> "[CANCELLED]";
            };
            sb.append(status)
              .append(" ")
              .append(a.getLabel())
              .append(" - ")
              .append(a.getAlarmTime().format(TIME_FMT))
              .append("\n");
        });
        return sb.toString();
    }

    private void send(Long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            log.error("Failed to send message to {}: {}", chatId, e.getMessage());
        }
    }
}