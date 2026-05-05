# WakeMate - Telegram Alarm Bot

Set an alarm via Telegram. If you don't wake up, WakeMate emails someone you choose.

Built with Java 25 LTS + Spring Boot 4.

---

## What It Does

1. Chat with the bot: /setalarm
2. Give it a time, a contact email, and a label
3. When the alarm fires, the bot messages you: "Are you awake?"
4. Reply /awake to dismiss it
5. If you don't respond within 5 minutes, your contact gets an email

---

## Java 25 Features Used

| Feature                | Where Used                          | Why It Matters                                      |
|------------------------|-------------------------------------|-----------------------------------------------------|
| Virtual Threads        | application.yml                     | Thousands of concurrent alarms, no thread tuning    |
| Records                | AlarmRequest.java                   | Immutable DTOs with built-in validation             |
| Sealed Interfaces      | ConversationState.java              | Compiler-enforced exhaustive conversation states    |
| Pattern Matching Switch| AlarmService.java, WakeMateBot.java | Type-safe, readable alarm lifecycle transitions     |

---

## Project Structure

  src/main/java/com/wakemate/
  |-- WakeMateApplication.java       Entry point
  |-- model/
  |   |-- Alarm.java                 JPA entity with status enum
  |   +-- AlarmRequest.java          Java Record DTO
  |-- repository/
  |   +-- AlarmRepository.java       Spring Data JPA
  |-- service/
  |   |-- AlarmService.java          Core logic + pattern matching switch
  |   +-- NotificationService.java   SendGrid email
  |-- scheduler/
  |   +-- AlarmScheduler.java        Fires and escalates alarms
  +-- bot/
      |-- ConversationState.java     Sealed interface for bot state
      +-- WakeMateBot.java           Telegram long polling bot

---

## Local Setup

Prerequisites:
  - Java 25 JDK (https://adoptium.net)
  - Maven 3.9+
  - Telegram bot token (free, see below)
  - SendGrid account (free tier, 100 emails/day)

Step 1 - Create your Telegram bot:
  1. Open Telegram and search for @BotFather
  2. Send /newbot and follow the prompts
  3. Copy the token you receive

Step 2 - Get a SendGrid API key:
  1. Sign up at https://sendgrid.com (free)
  2. Go to Settings > API Keys > Create API Key
  3. Give it Mail Send permission
  4. Verify your sender email address

Step 3 - Set environment variables (PowerShell):

  $env:TELEGRAM_BOT_TOKEN="your_token"
  $env:TELEGRAM_BOT_USERNAME="your_bot_username"
  $env:SENDGRID_API_KEY="your_key"
  $env:SENDGRID_FROM_EMAIL="you@domain.com"

Step 4 - Run:

  mvn spring-boot:run

---

## Deploy to Railway (Free)

1. Push your code to GitHub
2. Go to https://railway.app > New Project > Deploy from GitHub
3. Select your repo
4. Add the four environment variables in the Railway dashboard
5. Railway detects the Dockerfile and deploys automatically

Your bot is live 24/7 at no cost.

---

## Alarm Lifecycle

  PENDING --(alarm time reached)--> FIRED --(user sends /awake)--> ACKNOWLEDGED
                                      |
                                      +--(grace period expires, no /awake)--> MISSED
                                                                                |
                                                                        Contact emailed

The AlarmScheduler checks every 30 seconds for state transitions.

---

## LinkedIn Post Ideas

- "I missed an alarm and built a Telegram bot that emails someone if I don't wake up."
- "Java 25 sealed interfaces let me model bot conversation state with compiler-enforced exhaustiveness."
- "One line in Spring Boot 4 enables Java 25 virtual threads across the whole app."

---

## Future Ideas

- PostgreSQL instead of H2 for persistent storage on Railway
- SMS fallback via Twilio
- Snooze support: /snooze 10
- Recurring alarms for weekdays
- Telegram inline keyboard buttons instead of typing /awake

---

Built with Java 25 - Spring Boot 4 - Telegram Bot API - SendGrid