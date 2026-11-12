package com.incident.finder.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.ai.gemini")
public class GeminiApiProperties {

    private String apiKey;
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String model = "gemini-2.5-flash";
    private Duration timeout = Duration.ofSeconds(30);
    private int maxOutputTokens = 1200;

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

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public boolean isConfigured() {
        return isSet(apiKey) && isSet(baseUrl) && isSet(model);
    }

    private boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
