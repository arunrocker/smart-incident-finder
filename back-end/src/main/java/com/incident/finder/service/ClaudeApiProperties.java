package com.incident.finder.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "claude.api")
public class ClaudeApiProperties {

    private String apiKey = "xxxxx";
    private String url = "https://api.anthropic.com/v1/messages";
    private String model = "xxxxx";
    private String apiVersion = "2023-06-01";
    private Duration timeout = Duration.ofSeconds(30);
    private int maxTokens = 1200;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public boolean isConfigured() {
        return isSet(apiKey) && isSet(model) && isSet(url);
    }

    private boolean isSet(String value) {
        return value != null && !value.isBlank() && !"xxxxx".equalsIgnoreCase(value.trim());
    }
}
