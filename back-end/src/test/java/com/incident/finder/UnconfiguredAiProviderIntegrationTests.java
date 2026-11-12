package com.incident.finder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.ai.provider=groq",
        "app.ai.groq.api-key="
})
class UnconfiguredAiProviderIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void standardSearchWorksWithoutAiCredentials() throws Exception {
        mockMvc.perform(post("/api/incidents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Database queries are slow"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.aiAnalysis").doesNotExist());
    }

    @Test
    void smartSearchReturnsServiceUnavailableWithoutAiCredentials() throws Exception {
        mockMvc.perform(post("/api/incidents/smart-search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Database queries are slow"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "AI analysis is temporarily unavailable. Please try again later."
                ));
    }
}
