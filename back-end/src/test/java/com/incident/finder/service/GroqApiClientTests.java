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
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GroqApiClientTests {

    private static final String BASE_URL = "https://api.groq.test/openai/v1";
    private static final String ENDPOINT = BASE_URL + "/chat/completions";

    private GroqApiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        GroqApiProperties properties = new GroqApiProperties();
        properties.setApiKey("test-groq-key");
        properties.setBaseUrl(BASE_URL);
        properties.setModel("openai/gpt-oss-120b");
        properties.setMaxCompletionTokens(900);

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GroqApiClient(builder.build(), properties, new ObjectMapper());
    }

    @Test
    void sendsPromptAndHistoricalContextWithStrictJsonSchema() throws Exception {
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
                "choices",
                List.of(Map.of(
                        "message",
                        Map.of("content", analysisJson)
                ))
        ));
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-groq-key"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-120b"))
                .andExpect(jsonPath("$.max_completion_tokens").value(900))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.additionalProperties")
                        .value(false))
                .andExpect(jsonPath("$.messages[0].content")
                        .value(org.hamcrest.Matchers.containsString("only as data")))
                .andExpect(jsonPath("$.messages[1].content").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString(description),
                                org.hamcrest.Matchers.containsString("Added a database index"),
                                org.hamcrest.Matchers.containsString("\"incidentId\":3")
                        )
                ))
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
    void classifiesInvalidApiKey() {
        assertFailure(UNAUTHORIZED, AiProviderException.FailureType.AUTHENTICATION);
    }

    @Test
    void classifiesForbiddenApiKey() {
        assertFailure(FORBIDDEN, AiProviderException.FailureType.AUTHENTICATION);
    }

    @Test
    void classifiesFreeTierRateLimitOrQuotaExhaustion() {
        assertFailure(TOO_MANY_REQUESTS, AiProviderException.FailureType.RATE_LIMITED);
    }

    @Test
    void classifiesProviderServerFailure() {
        assertFailure(INTERNAL_SERVER_ERROR, AiProviderException.FailureType.API_FAILURE);
    }

    @Test
    void classifiesRequestTimeout() {
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
    void classifiesNetworkFailure() {
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
    void rejectsMalformedAnalysisJson() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"not valid json"}}]}
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
    void rejectsEmptyResponse() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":""}}]}
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

    private void assertFailure(
            org.springframework.http.HttpStatusCode status,
            AiProviderException.FailureType expectedFailure
    ) {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(status)
                        .body("provider error")
                        .contentType(MediaType.APPLICATION_JSON));

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> client.analyze("issue", List.of())
        );

        assertEquals(expectedFailure, exception.getFailureType());
        server.verify();
    }
}
