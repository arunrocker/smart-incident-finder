package com.incident.finder.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "gemini")
public class GeminiApiClient implements AiAnalysisProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiApiClient.class);
    private static final int MAX_DIAGNOSTIC_LENGTH = 1000;

    private final RestClient restClient;
    private final GeminiApiProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiApiClient(
            RestClient.Builder restClientBuilder,
            GeminiApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this(createRestClient(restClientBuilder, properties), properties, objectMapper);
    }

    GeminiApiClient(
            RestClient restClient,
            GeminiApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "Gemini is selected but app.ai.gemini.api-key, model, and base-url must be configured."
            );
        }
    }

    private static RestClient createRestClient(
            RestClient.Builder restClientBuilder,
            GeminiApiProperties properties
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
        return "gemini";
    }

    @Override
    public AiAnalysis analyze(
            String description,
            List<IncidentSearchResult> historicalIncidents
    ) {
        GeminiGenerateContentRequest request = new GeminiGenerateContentRequest(
                new GeminiContent(List.of(new GeminiPart(AiAnalysisProviderSupport.systemInstructions()))),
                List.of(new GeminiContent(List.of(new GeminiPart(
                        AiAnalysisProviderSupport.createPrompt(
                                description,
                                historicalIncidents,
                                objectMapper,
                                providerName()
                        )
                )))),
                new GeminiGenerationConfig("application/json", properties.getMaxOutputTokens())
        );

        GeminiGenerateContentResponse response;
        try {
            LOGGER.info("Calling Gemini generateContent (model={})", properties.getModel());
            response = restClient.post()
                    .uri(endpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-goog-api-key", properties.getApiKey())
                    .body(request)
                    .retrieve()
                    .body(GeminiGenerateContentResponse.class);
        } catch (RestClientResponseException exception) {
            LOGGER.warn(
                    "Gemini returned HTTP error (status={}, response={})",
                    exception.getStatusCode().value(),
                    sanitizeDiagnostic(exception.getResponseBodyAsString())
            );
            throw responseFailure(exception);
        } catch (ResourceAccessException exception) {
            boolean timedOut = hasTimeoutCause(exception);
            Throwable rootCause = rootCause(exception);
            LOGGER.warn(
                    "Gemini connection failed (failureType={}, causeType={}, causeMessage={})",
                    timedOut ? "TIMEOUT" : "API_FAILURE",
                    rootCause.getClass().getSimpleName(),
                    sanitizeDiagnostic(rootCause.getMessage())
            );
            throw new AiProviderException(
                    timedOut
                            ? AiProviderException.FailureType.TIMEOUT
                            : AiProviderException.FailureType.API_FAILURE,
                    timedOut ? "Gemini API request timed out" : "Gemini API connection failed",
                    exception
            );
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "Gemini response processing failed (causeType={}, causeMessage={})",
                    exception.getClass().getSimpleName(),
                    sanitizeDiagnostic(exception.getMessage())
            );
            throw new AiProviderException(
                    AiProviderException.FailureType.INVALID_RESPONSE,
                    "Gemini API returned an unreadable response",
                    exception
            );
        }

        return parseAnalysis(response);
    }

    private String endpoint() {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        return "%s/models/%s:generateContent".formatted(baseUrl, properties.getModel());
    }

    private AiAnalysis parseAnalysis(GeminiGenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw AiAnalysisProviderSupport.invalidResponse(providerName());
        }

        String text = response.candidates().stream()
                .filter(candidate -> candidate != null
                        && candidate.content() != null
                        && candidate.content().parts() != null)
                .flatMap(candidate -> candidate.content().parts().stream())
                .filter(part -> part != null && part.text() != null)
                .map(GeminiPart::text)
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
                "Gemini API request failed with HTTP status " + status,
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

    private Throwable rootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private String sanitizeDiagnostic(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }

        String sanitized = value;
        String apiKey = properties.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            sanitized = sanitized.replace(apiKey, "[REDACTED]");
        }
        sanitized = sanitized.replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        if (sanitized.length() > MAX_DIAGNOSTIC_LENGTH) {
            return sanitized.substring(0, MAX_DIAGNOSTIC_LENGTH) + "...";
        }
        return sanitized;
    }

    private record GeminiGenerateContentRequest(
            GeminiContent systemInstruction,
            List<GeminiContent> contents,
            GeminiGenerationConfig generationConfig
    ) {
    }

    private record GeminiContent(List<GeminiPart> parts) {
    }

    private record GeminiPart(String text) {
    }

    private record GeminiGenerationConfig(String responseMimeType, int maxOutputTokens) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiGenerateContentResponse(List<GeminiCandidate> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiCandidate(GeminiContent content) {
    }
}
