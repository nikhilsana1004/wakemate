package com.wakemate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wakemate.model.AlarmRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Parses natural language alarm requests using LLMs.
 *
 * Strategy (in order):
 *   1. Try Anthropic Claude (claude-sonnet-4-6)
 *   2. If Claude fails, try OpenAI GPT-4o
 *   3. If both fail, return empty so the bot falls back to step-by-step flow
 *
 * Both APIs are prompted to return structured JSON only.
 * Java 25 virtual threads handle the HTTP calls efficiently.
 *
 * Example input:
 *   "wake me up at 7:30 tomorrow, tell John at john@gmail.com if I don't get up, it's for my job interview"
 *
 * Example JSON output:
 *   {
 *     "alarmTime": "2025-09-15T07:30:00",
 *     "contactEmail": "john@gmail.com",
 *     "contactName": "John",
 *     "label": "Job interview"
 *   }
 */
@Service
public class AlarmParserService {

    private static final Logger log = LoggerFactory.getLogger(AlarmParserService.class);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final HttpClient    httpClient;
    private final ObjectMapper  objectMapper;
    private final String        claudeApiKey;
    private final String        openAiApiKey;

    public AlarmParserService(
            @Value("${anthropic.api-key:}") String claudeApiKey,
            @Value("${openai.api-key:}")    String openAiApiKey) {
        this.httpClient   = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.claudeApiKey = claudeApiKey;
        this.openAiApiKey = openAiApiKey;
    }

    /**
     * Attempt to parse a free-text message into an AlarmRequest.
     * Returns empty if both LLMs fail or the message is not alarm-related.
     */
    public Optional<AlarmRequest> parse(String userMessage) {
        String now = LocalDateTime.now().format(ISO);

        // Try Claude first
        if (!claudeApiKey.isBlank()) {
            try {
                Optional<AlarmRequest> result = callClaude(userMessage, now);
                if (result.isPresent()) {
                    log.info("AlarmParserService: parsed via Claude");
                    return result;
                }
            } catch (Exception e) {
                log.warn("Claude parse failed, trying OpenAI: {}", e.getMessage());
            }
        }

        // Fall back to OpenAI
        if (!openAiApiKey.isBlank()) {
            try {
                Optional<AlarmRequest> result = callOpenAi(userMessage, now);
                if (result.isPresent()) {
                    log.info("AlarmParserService: parsed via OpenAI");
                    return result;
                }
            } catch (Exception e) {
                log.warn("OpenAI parse also failed: {}", e.getMessage());
            }
        }

        log.info("AlarmParserService: both LLMs failed, returning empty");
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Claude
    // -------------------------------------------------------------------------
    private Optional<AlarmRequest> callClaude(String userMessage, String now) throws Exception {
        String prompt = buildPrompt(userMessage, now);

        String body = """
                {
                  "model": "claude-sonnet-4-6",
                  "max_tokens": 300,
                  "messages": [{ "role": "user", "content": %s }]
                }
                """.formatted(jsonString(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", claudeApiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("Claude returned HTTP {}", response.statusCode());
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("content").get(0).path("text").asText();
        return parseJson(text);
    }

    // -------------------------------------------------------------------------
    // OpenAI
    // -------------------------------------------------------------------------
    private Optional<AlarmRequest> callOpenAi(String userMessage, String now) throws Exception {
        String prompt = buildPrompt(userMessage, now);

        String body = """
                {
                  "model": "gpt-4o",
                  "max_tokens": 300,
                  "messages": [{ "role": "user", "content": %s }]
                }
                """.formatted(jsonString(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openAiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("OpenAI returned HTTP {}", response.statusCode());
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("choices").get(0).path("message").path("content").asText();
        return parseJson(text);
    }

    // -------------------------------------------------------------------------
    // Shared prompt and JSON parsing
    // -------------------------------------------------------------------------
    private String buildPrompt(String userMessage, String now) {
        return "Current date and time: " + now + "\n\n"
                + "The user wants to set an alarm. Extract the details from their message.\n"
                + "Return ONLY a JSON object with these fields:\n"
                + "  alarmTime    - ISO 8601 datetime (e.g. 2025-09-15T07:30:00)\n"
                + "  contactEmail - email address to notify if alarm is missed\n"
                + "  contactName  - first name of the contact\n"
                + "  label        - short description of what the alarm is for\n\n"
                + "If any field cannot be determined, use null.\n"
                + "If the message is not about setting an alarm, return: {\"notAlarm\": true}\n"
                + "Return JSON only. No explanation, no markdown.\n\n"
                + "User message: " + userMessage;
    }

    private Optional<AlarmRequest> parseJson(String raw) {
        try {
            // Strip markdown code fences if the LLM added them despite instructions
            String cleaned = raw.strip()
                    .replaceAll("(?i)^```json\\s*", "")
                    .replaceAll("(?i)^```\\s*", "")
                    .replaceAll("```$", "")
                    .strip();

            JsonNode node = objectMapper.readTree(cleaned);

            // LLM says this is not an alarm message
            if (node.has("notAlarm") && node.get("notAlarm").asBoolean()) {
                return Optional.empty();
            }

            String alarmTimeStr  = nullSafe(node, "alarmTime");
            String contactEmail  = nullSafe(node, "contactEmail");
            String contactName   = nullSafe(node, "contactName");
            String label         = nullSafe(node, "label");

            // Must have at least a time and email to be useful
            if (alarmTimeStr == null || contactEmail == null) {
                return Optional.empty();
            }

            LocalDateTime alarmTime = LocalDateTime.parse(alarmTimeStr, ISO);

            // AlarmRequest Record validates itself in its compact constructor
            return Optional.of(new AlarmRequest(alarmTime, contactEmail, contactName, label));

        } catch (Exception e) {
            log.warn("Failed to parse LLM JSON response: {} | raw: {}", e.getMessage(), raw);
            return Optional.empty();
        }
    }

    private String nullSafe(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private String jsonString(String text) throws Exception {
        return objectMapper.writeValueAsString(text);
    }
}