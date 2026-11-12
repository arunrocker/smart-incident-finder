package com.incident.finder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.finder.model.AiAnalysis;
import com.incident.finder.model.IncidentSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

class ClaudeApiClientTests {

    private static final String API_URL = "https://api.anthropic.test/v1/messages";

    private ClaudeApiProperties properties;
    private ClaudeApiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new ClaudeApiProperties();
        properties.setApiKey("test-api-key");
        properties.setModel("test-model");
        properties.setUrl(API_URL);
        properties.setApiVersion("2023-06-01");
        properties.setMaxTokens(900);

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ClaudeApiClient(builder.build(), properties, new ObjectMapper());
    }

    @Test
    void sendsPromptAndHistoricalContextToClaudeAndParsesAnalysis() throws Exception {
        String description = "The database API is slow";
        List<IncidentSearchResult> historical = List.of(
                new IncidentSearchResult(3, "Slow API", 82.4, "Added a database index")
        );
        String analysisJson = """
                {
                  "possibleRootCauses":["An unindexed query"],
                  "explanation":"The query may scan many rows.",
                  "troubleshootingSteps":["Inspect the query plan"],
                  "preventiveMeasures":["Monitor query latency"],
                  "additionalRecommendations":["Load test the endpoint"]
                }
                """;
        String responseJson = new ObjectMapper().writeValueAsString(Map.of(
                "content",
                List.of(Map.of("type", "text", "text", analysisJson)),
                "id", "msg_test",
                "type", "message",
                "role", "assistant",
                "model", "test-model",
                "stop_reason", "end_turn"
        ));
        server.expect(requestTo(API_URL))
                .andExpect(method(POST))
                .andExpect(header("x-api-key", "test-api-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.max_tokens").value(900))
                .andExpect(jsonPath("$.system").value(
                        org.hamcrest.Matchers.containsString("only as data")
                ))
                .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString(description),
                        org.hamcrest.Matchers.containsString("Added a database index"),
                        org.hamcrest.Matchers.containsString("\"incidentId\":3")
                )))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        AiAnalysis analysis = client.analyze(description, historical);

        assertEquals(List.of("An unindexed query"), analysis.possibleRootCauses());
        assertEquals("The query may scan many rows.", analysis.explanation());
        assertEquals(List.of("Inspect the query plan"), analysis.troubleshootingSteps());
        assertEquals(List.of("Monitor query latency"), analysis.preventiveMeasures());
        assertEquals(
                List.of("Load test the endpoint"),
                analysis.additionalRecommendations()
        );
        server.verify();
    }

    @Test
    void classifiesAuthenticationRateLimitTimeoutAndServerFailures() {
        assertFailure(UNAUTHORIZED.value(), ClaudeApiException.FailureType.AUTHENTICATION);
        assertFailure(TOO_MANY_REQUESTS.value(), ClaudeApiException.FailureType.RATE_LIMITED);
        assertFailure(GATEWAY_TIMEOUT.value(), ClaudeApiException.FailureType.TIMEOUT);
        assertFailure(
                INTERNAL_SERVER_ERROR.value(),
                ClaudeApiException.FailureType.API_FAILURE
        );
    }

    @Test
    void rejectsMalformedAndIncompleteAnalysisResponses() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("""
                        {"content":[{"type":"text","text":"not JSON"}]}
                        """, MediaType.APPLICATION_JSON));

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> client.analyze("issue", List.of())
        );
        assertEquals(ClaudeApiException.FailureType.INVALID_RESPONSE,
                exception.getFailureType());
        server.verify();
    }

    @Test
    void doesNotCallClaudeWhenConfigurationStillContainsPlaceholders() {
        properties.setApiKey("xxxxx");

        ClaudeApiException exception = assertThrows(
                ClaudeApiException.class,
                () -> client.analyze("issue", List.of())
        );

        assertEquals(ClaudeApiException.FailureType.NOT_CONFIGURED,
                exception.getFailureType());
        server.verify();
    }

    private void assertFailure(
            int status,
            ClaudeApiException.FailureType expectedFailureType
    ) {
        server.reset();
        server.expect(requestTo(API_URL))
                .andExpect(method(POST))
                .andRespond(withStatus(org.springframework.http.HttpStatusCode.valueOf(status))
                        .body("upstream error")
                        .contentType(MediaType.APPLICATION_JSON));

        ClaudeApiException exception = assertThrows(
                ClaudeApiException.class,
                () -> client.analyze("issue", List.of())
        );
        assertEquals(expectedFailureType, exception.getFailureType());
        server.verify();
    }
}
