package com.incident.finder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.incident.finder.model.AiAnalysis;
import com.incident.finder.model.IncidentSearchResult;
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

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "claude")
public class ClaudeApiClient implements AiAnalysisProvider {

    private final RestClient restClient;
    private final ClaudeApiProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public ClaudeApiClient(
            RestClient.Builder restClientBuilder,
            ClaudeApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this(createRestClient(restClientBuilder, properties), properties, objectMapper);
    }

    ClaudeApiClient(
            RestClient restClient,
            ClaudeApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "Claude is selected but app.ai.claude.api-key, model, and url must be configured."
            );
        }
    }

    private static RestClient createRestClient(
            RestClient.Builder restClientBuilder,
            ClaudeApiProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout());
        requestFactory.setReadTimeout(properties.getTimeout());
        return restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String providerName() {
        return "claude";
    }

    @Override
    public AiAnalysis analyze(
            String description,
            List<IncidentSearchResult> historicalIncidents
    ) {
        if (!properties.isConfigured()) {
            throw new ClaudeApiException(
                    ClaudeApiException.FailureType.NOT_CONFIGURED,
                    "Claude API credentials or model are not configured"
            );
        }

        ClaudeMessagesRequest request = new ClaudeMessagesRequest(
                properties.getModel(),
                properties.getMaxTokens(),
                AiAnalysisProviderSupport.systemInstructions(),
                List.of(new ClaudeMessage(
                        "user",
                        AiAnalysisProviderSupport.createPrompt(
                                description,
                                historicalIncidents,
                                objectMapper,
                                providerName()
                        )
                ))
        );

        ClaudeMessagesResponse response;
        try {
            response = restClient.post()
                    .uri(properties.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-api-key", properties.getApiKey())
                    .header("anthropic-version", properties.getApiVersion())
                    .body(request)
                    .retrieve()
                    .body(ClaudeMessagesResponse.class);
        } catch (RestClientResponseException exception) {
            throw responseFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new ClaudeApiException(
                    hasTimeoutCause(exception)
                            ? ClaudeApiException.FailureType.TIMEOUT
                            : ClaudeApiException.FailureType.API_FAILURE,
                    hasTimeoutCause(exception)
                            ? "Claude API request timed out"
                            : "Claude API connection failed",
                    exception
            );
        } catch (RestClientException exception) {
            throw new ClaudeApiException(
                    ClaudeApiException.FailureType.INVALID_RESPONSE,
                    "Claude API returned an unreadable response",
                    exception
            );
        }

        return parseAnalysis(response);
    }

    private AiAnalysis parseAnalysis(ClaudeMessagesResponse response) {
        if (response == null || response.content() == null || response.content().isEmpty()) {
            throw invalidResponse();
        }

        String text = response.content().stream()
                .filter(content -> content != null && content.text() != null)
                .map(ClaudeContent::text)
                .findFirst()
                .orElseThrow(this::invalidResponse)
                .trim();

        return AiAnalysisProviderSupport.parseAnalysis(text, objectMapper, providerName());
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

    private ClaudeApiException responseFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        ClaudeApiException.FailureType failureType = switch (status) {
            case 401, 403 -> ClaudeApiException.FailureType.AUTHENTICATION;
            case 429 -> ClaudeApiException.FailureType.RATE_LIMITED;
            case 408, 504 -> ClaudeApiException.FailureType.TIMEOUT;
            default -> ClaudeApiException.FailureType.API_FAILURE;
        };
        return new ClaudeApiException(
                failureType,
                "Claude API request failed with HTTP status " + status,
                exception
        );
    }

    private ClaudeApiException invalidResponse() {
        AiProviderException failure = AiAnalysisProviderSupport.invalidResponse(providerName());
        return new ClaudeApiException(failure.getFailureType(), failure.getMessage());
    }

    private record ClaudeMessagesRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<ClaudeMessage> messages
    ) {
    }

    private record ClaudeMessage(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ClaudeMessagesResponse(List<ClaudeContent> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ClaudeContent(String type, String text) {
    }
}
