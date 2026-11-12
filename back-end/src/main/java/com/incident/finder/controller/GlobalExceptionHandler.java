package com.incident.finder.controller;

import com.incident.finder.model.ApiErrorResponse;
import com.incident.finder.service.AiProviderException;
import com.incident.finder.service.InvalidIncidentSearchException;
import com.incident.finder.service.SearchRequestMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_JSON_MESSAGE =
            "Request body must be valid JSON with a non-empty description.";
    private static final String INTERNAL_ERROR_MESSAGE =
            "An unexpected server error occurred.";
    private static final String AI_UNAVAILABLE_MESSAGE =
            "AI analysis is temporarily unavailable. Please try again later.";

    private final SearchRequestMetrics requestMetrics;

    public GlobalExceptionHandler(SearchRequestMetrics requestMetrics) {
        this.requestMetrics = requestMetrics;
    }

    @ExceptionHandler(InvalidIncidentSearchException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidSearch(
            InvalidIncidentSearchException exception
    ) {
        requestMetrics.recordInvalidRequest();
        LOGGER.warn("Rejected incident search request: {}", exception.getMessage());
        return ResponseEntity.badRequest().body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        requestMetrics.recordInvalidRequest();
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request.");
        LOGGER.warn("Rejected incident search request due to validation failure: {}", message);
        return ResponseEntity.badRequest().body(new ApiErrorResponse(message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception
    ) {
        requestMetrics.recordInvalidRequest();
        LOGGER.warn("Rejected incident search request due to method validation failure");
        return ResponseEntity.badRequest().body(new ApiErrorResponse("Invalid request."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(
            HttpMessageNotReadableException exception
    ) {
        requestMetrics.recordInvalidRequest();
        if (hasRequestSizeCause(exception)) {
            LOGGER.warn("Rejected incident search request because the request body exceeded the size limit");
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new ApiErrorResponse("Request body exceeds the maximum permitted size."));
        }
        LOGGER.warn("Rejected incident search request because its JSON body could not be read");
        return ResponseEntity.badRequest().body(new ApiErrorResponse(INVALID_JSON_MESSAGE));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception
    ) {
        requestMetrics.recordInvalidRequest();
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ApiErrorResponse("Content-Type must be application/json."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMethod(
            HttpRequestMethodNotSupportedException exception
    ) {
        requestMetrics.recordInvalidRequest();
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiErrorResponse("HTTP method is not supported for this endpoint."));
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleAiProviderFailure(
            AiProviderException exception
    ) {
        LOGGER.warn("AI provider unavailable (failureType={})", exception.getFailureType());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse(AI_UNAVAILABLE_MESSAGE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        requestMetrics.recordUnexpectedError();
        LOGGER.error("Unexpected failure while processing an incident search", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(INTERNAL_ERROR_MESSAGE));
    }

    private boolean hasRequestSizeCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof RequestSizeLimitFilter.RequestBodyTooLargeException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
