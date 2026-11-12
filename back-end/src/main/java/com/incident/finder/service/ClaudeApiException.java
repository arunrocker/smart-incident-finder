package com.incident.finder.service;

public class ClaudeApiException extends AiProviderException {

    public ClaudeApiException(FailureType failureType, String message) {
        super(failureType, message);
    }

    public ClaudeApiException(FailureType failureType, String message, Throwable cause) {
        super(failureType, message, cause);
    }
}
