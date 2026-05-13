package com.project.skin_me.integration;

import com.google.genai.Client;
import com.project.skin_me.SkinMeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Loads the full application context with profile {@code test} (H2, local dirs).
 * Use for smoke checks; prefer {@code unit/} tests with mocks for TDD cycles.
 */
@SpringBootTest(classes = SkinMeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class SkinMeApplicationContextTest {

    /** Production disables the real bean when {@code google.api.enabled=false}; tests still wire {@link GeminiService}. */
    @MockitoBean
    private Client geminiClient;

    @Test
    void contextLoads() {
        // success if the Spring context starts without errors
    }
}
