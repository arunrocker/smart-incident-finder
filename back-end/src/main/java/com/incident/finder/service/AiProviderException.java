package com.incident.finder.service;

public class AiProviderException extends RuntimeException {

    public enum FailureType {
        NOT_CONFIGURED,
        AUTHENTICATION,
        RATE_LIMITED,
        TIMEOUT,
        INVALID_RESPONSE,
        API_FAILURE
    }

    private final FailureType failureType;

    public AiProviderException(FailureType failureType, String message) {
        super(message);
        this.failureType = failureType;
    }

    public AiProviderException(FailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public FailureType getFailureType() {
        return failureType;
    }
}
