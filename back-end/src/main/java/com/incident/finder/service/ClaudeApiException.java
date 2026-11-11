package com.incident.finder.service;

public class ClaudeApiException extends RuntimeException {

    public enum FailureType {
        NOT_CONFIGURED,
        AUTHENTICATION,
        RATE_LIMITED,
        TIMEOUT,
        INVALID_RESPONSE,
        API_FAILURE
    }

    private final FailureType failureType;

    public ClaudeApiException(FailureType failureType, String message) {
        super(message);
        this.failureType = failureType;
    }

    public ClaudeApiException(FailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public FailureType getFailureType() {
        return failureType;
    }
}
