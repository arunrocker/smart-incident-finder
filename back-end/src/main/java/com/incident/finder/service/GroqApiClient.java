package com.incident.finder.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.finder.model.AiAnalysis;
import com.incident.finder.model.IncidentSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "groq")
public class GroqApiClient implements AiAnalysisProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroqApiClient.class);
    private static final Map<String, Object> ANALYSIS_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "possibleRootCauses", stringArraySchema(),
                    "explanation", Map.of("type", "string"),
                    "troubleshootingSteps", stringArraySchema(),
                    "preventiveMeasures", stringArraySchema(),
                    "additionalRecommendations", stringArraySchema()
            ),
            "required", List.of(
                    "possibleRootCauses",
                    "explanation",
                    "troubleshootingSteps",
                    "preventiveMeasures",
                    "additionalRecommendations"
            ),
            "additionalProperties", false
    );

    private final RestClient restClient;
    private final GroqApiProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public GroqApiClient(
            RestClient.Builder restClientBuilder,
            GroqApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this(createRestClient(restClientBuilder, properties), properties, objectMapper);
    }

    GroqApiClient(
            RestClient restClient,
            GroqApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    private static RestClient createRestClient(
            RestClient.Builder restClientBuilder,
            GroqApiProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout());
        requestFactory.setReadTimeout(properties.getTimeout());
        return restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    private static Map<String, Object> stringArraySchema() {
        return Map.of(
                "type", "array",
                "items", Map.of("type", "string")
        );
    }

    @Override
    public String providerName() {
        return "groq";
    }

    @Override
    public AiAnalysis analyze(
            String description,
            List<IncidentSearchResult> historicalIncidents
    ) {
        if (!properties.isConfigured()) {
            throw new AiProviderException(
                    AiProviderException.FailureType.NOT_CONFIGURED,
                    "Groq API credentials or model are not configured"
            );
        }

        GroqChatRequest request = new GroqChatRequest(
                properties.getModel(),
                List.of(
                        new GroqMessage("system", AiAnalysisProviderSupport.systemInstructions()),
                        new GroqMessage(
                                "user",
                                AiAnalysisProviderSupport.createPrompt(
                                        description,
                                        historicalIncidents,
                                        objectMapper,
                                        providerName()
                                )
                        )
                ),
                properties.getMaxCompletionTokens(),
                new GroqResponseFormat(
                        "json_schema",
                        new GroqJsonSchema("incident_analysis", true, ANALYSIS_SCHEMA)
                )
        );

        GroqChatResponse response;
        try {
            LOGGER.info("Calling Groq chat completions (model={})", properties.getModel());
            response = restClient.post()
                    .uri(endpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(request)
                    .retrieve()
                    .body(GroqChatResponse.class);
        } catch (RestClientResponseException exception) {
            LOGGER.warn("Groq returned HTTP error (status={})", exception.getStatusCode().value());
            throw responseFailure(exception);
        } catch (ResourceAccessException exception) {
            boolean timedOut = hasTimeoutCause(exception);
            LOGGER.warn(
                    "Groq connection failed (failureType={})",
                    timedOut ? "TIMEOUT" : "API_FAILURE"
            );
            throw new AiProviderException(
                    timedOut
                            ? AiProviderException.FailureType.TIMEOUT
                            : AiProviderException.FailureType.API_FAILURE,
                    timedOut ? "Groq API request timed out" : "Groq API connection failed",
                    exception
            );
        } catch (RestClientException exception) {
            LOGGER.warn("Groq response could not be read");
            throw new AiProviderException(
                    AiProviderException.FailureType.INVALID_RESPONSE,
                    "Groq API returned an unreadable response",
                    exception
            );
        }

        return parseAnalysis(response);
    }

    private String endpoint() {
        return "%s/chat/completions".formatted(properties.getBaseUrl().replaceAll("/+$", ""));
    }

    private AiAnalysis parseAnalysis(GroqChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw AiAnalysisProviderSupport.invalidResponse(providerName());
        }

        String text = response.choices().stream()
                .filter(choice -> choice != null && choice.message() != null)
                .map(choice -> choice.message().content())
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .orElseThrow(() -> AiAnalysisProviderSupport.invalidResponse(providerName()));

        return AiAnalysisProviderSupport.parseAnalysis(text, objectMapper, providerName());
    }

    private AiProviderException responseFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        AiProviderException.FailureType failureType = switch (status) {
            case 401, 403 -> AiProviderException.FailureType.AUTHENTICATION;
            case 429 -> AiProviderException.FailureType.RATE_LIMITED;
            case 408, 504 -> AiProviderException.FailureType.TIMEOUT;
            default -> AiProviderException.FailureType.API_FAILURE;
        };
        return new AiProviderException(
                failureType,
                "Groq API request failed with HTTP status " + status,
                exception
        );
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private record GroqChatRequest(
            String model,
            List<GroqMessage> messages,
            @JsonProperty("max_completion_tokens") int maxCompletionTokens,
            @JsonProperty("response_format") GroqResponseFormat responseFormat
    ) {
    }

    private record GroqMessage(String role, String content) {
    }

    private record GroqResponseFormat(
            String type,
            @JsonProperty("json_schema") GroqJsonSchema jsonSchema
    ) {
    }

    private record GroqJsonSchema(
            String name,
            boolean strict,
            Map<String, Object> schema
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GroqChatResponse(List<GroqChoice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GroqChoice(GroqResponseMessage message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GroqResponseMessage(String content) {
    }
}
