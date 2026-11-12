package com.incident.finder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.finder.model.AiAnalysis;
import com.incident.finder.model.IncidentSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiApiClientTests {

    private static final String BASE_URL = "https://generativelanguage.googleapis.test/v1beta";
    private static final String ENDPOINT = BASE_URL + "/models/test-model:generateContent";

    private GeminiApiProperties properties;
    private GeminiApiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new GeminiApiProperties();
        properties.setApiKey("test-gemini-key");
        properties.setBaseUrl(BASE_URL);
        properties.setModel("test-model");
        properties.setMaxOutputTokens(900);

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiApiClient(builder.build(), properties, new ObjectMapper());
    }

    @Test
    void sendsPromptAndHistoricalContextToGeminiAndParsesAnalysis() throws Exception {
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
                "candidates",
                List.of(Map.of(
                        "content",
                        Map.of("parts", List.of(Map.of("text", analysisJson))),
                        "finishReason", "STOP"
                ))
        ));
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(header("x-goog-api-key", "test-gemini-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text")
                        .value(org.hamcrest.Matchers.containsString("only as data")))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString(description),
                                org.hamcrest.Matchers.containsString("Added a database index"),
                                org.hamcrest.Matchers.containsString("\"incidentId\":3")
                        )
                ))
                .andExpect(jsonPath("$.generationConfig.responseMimeType")
                        .value("application/json"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(900))
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
    void classifiesGeminiRateLimitFailures() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andRespond(withStatus(TOO_MANY_REQUESTS)
                        .body("rate limited")
                        .contentType(MediaType.APPLICATION_JSON));

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> client.analyze("issue", List.of())
        );

        assertEquals(AiProviderException.FailureType.RATE_LIMITED, exception.getFailureType());
        server.verify();
    }

    @Test
    void rejectsGeminiResponsesWithoutCandidateText() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[]}}]}
                        """, MediaType.APPLICATION_JSON));

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> client.analyze("issue", List.of())
        );

        assertEquals(
                AiProviderException.FailureType.INVALID_RESPONSE,
                exception.getFailureType()
        );
        server.verify();
    }

    @Test
    void classifiesInvalidApiKeyAsAuthenticationFailure() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(UNAUTHORIZED)
                        .body("invalid key")
                        .contentType(MediaType.APPLICATION_JSON));

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> client.analyze("issue", List.of())
        );

        assertEquals(
                AiProviderException.FailureType.AUTHENTICATION,
                exception.getFailureType()
        );
        server.verify();
    }

    @Test
    void classifiesForbiddenApiKeyAsAuthenticationFailure() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(FORBIDDEN)
                        .body("key is not permitted")
                        .contentType(MediaType.APPLICATION_JSON));

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> client.analyze("issue", List.of())
        );

        assertEquals(
                AiProviderException.FailureType.AUTHENTICATION,
                exception.getFailureType()
        );
        server.verify();
    }

    @Test
    void classifiesFreeTierQuotaExhaustionAsRateLimited() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(TOO_MANY_REQUESTS)
                        .body("""
                                {"error":{"status":"RESOURCE_EXHAUSTED"}}
                                """)
                        .contentType(MediaType.APPLICATION_JSON));

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> client.analyze("issue", List.of())
        );

        assertEquals(AiProviderException.FailureType.RATE_LIMITED, exception.getFailureType());
        server.verify();
    }

    @Test
    void classifiesProviderServerErrorsAsApiFailures() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(SERVICE_UNAVAILABLE)
                        .body("provider unavailable")
                        .contentType(MediaType.APPLICATION_JSON));

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> client.analyze("issue", List.of())
        );

        assertEquals(AiProviderException.FailureType.API_FAILURE, exception.getFailureType());
        server.verify();
    }

    @Test
    void classifiesRequestTimeouts() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(request -> {
                    throw new ResourceAccessException(
                            "request timed out",
                            new SocketTimeoutException("timed out")
                    );
                });

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> client.analyze("issue", List.of())
        );

        assertEquals(AiProviderException.FailureType.TIMEOUT, exception.getFailureType());
        server.verify();
    }

    @Test
    void classifiesNetworkFailures() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(request -> {
                    throw new ResourceAccessException("connection failed");
                });

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> client.analyze("issue", List.of())
        );

        assertEquals(AiProviderException.FailureType.API_FAILURE, exception.getFailureType());
        server.verify();
    }

    @Test
    void rejectsMalformedAiAnalysisJson() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"not valid json"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> client.analyze("issue", List.of())
        );

        assertEquals(
                AiProviderException.FailureType.INVALID_RESPONSE,
                exception.getFailureType()
        );
        server.verify();
    }

    @Test
    void rejectsEmptyAiResponse() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> client.analyze("issue", List.of())
        );

        assertEquals(
                AiProviderException.FailureType.INVALID_RESPONSE,
                exception.getFailureType()
        );
        server.verify();
    }
}
