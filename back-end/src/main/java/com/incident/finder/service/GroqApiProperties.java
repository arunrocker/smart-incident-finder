package com.incident.finder.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.ai.groq")
public class GroqApiProperties {

    private String apiKey;
    private String baseUrl = "https://api.groq.com/openai/v1";
    private String model = "openai/gpt-oss-120b";
    private Duration timeout = Duration.ofSeconds(30);
    private int maxCompletionTokens = 1200;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(int maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public boolean isConfigured() {
        return isSet(apiKey) && isSet(baseUrl) && isSet(model);
    }

    private boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
