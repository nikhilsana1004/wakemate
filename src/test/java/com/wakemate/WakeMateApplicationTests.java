package com.wakemate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test - verifies the Spring context loads correctly.
 * Run with: mvn test
 */
@SpringBootTest
@TestPropertySource(properties = {
    "telegram.bot.token=fake-token-for-tests",
    "telegram.bot.username=fake_bot",
    "sendgrid.api-key=fake-sendgrid-key",
    "anthropic.api-key=",
    "openai.api-key="
})
class WakeMateApplicationTests {

    @Test
    void contextLoads() {
        // If the Spring context starts without errors, this test passes.
        // That means all beans are wired correctly.
    }
}