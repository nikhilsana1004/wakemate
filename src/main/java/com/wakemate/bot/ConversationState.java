package com.wakemate.bot;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-user Telegram conversation state for multi-step alarm setup.
 *
 * Java 25 Sealed Interface - every possible bot state is declared here.
 * Pattern matching switch on State won't compile if any case is missing.
 * Each state Record carries only the data collected so far.
 *
 * LinkedIn talking point:
 *   "I modeled the bot conversation flow with sealed interfaces.
 *    The compiler guarantees I handle every possible user state.
 *    If I add a new step and forget to handle it, the build fails."
 */
public class ConversationState {

    public sealed interface State
            permits State.Idle,
                    State.AwaitingTime,
                    State.AwaitingContactEmail,
                    State.AwaitingContactName,
                    State.AwaitingLabel {

        record Idle()                                                            implements State {}
        record AwaitingTime()                                                    implements State {}
        record AwaitingContactEmail(String alarmTime)                            implements State {}
        record AwaitingContactName(String alarmTime, String contactEmail)        implements State {}
        record AwaitingLabel(String alarmTime, String contactEmail, String name) implements State {}
    }

    private final ConcurrentHashMap<Long, State> store = new ConcurrentHashMap<>();

    public State get(Long chatId)              { return store.getOrDefault(chatId, new State.Idle()); }
    public void  set(Long chatId, State state) { store.put(chatId, state); }
    public void  reset(Long chatId)            { store.put(chatId, new State.Idle()); }
}