package com.incident.finder.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class SearchRequestMetrics {

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong successfulSearches = new AtomicLong();
    private final AtomicLong noMatchSearches = new AtomicLong();
    private final AtomicLong invalidRequests = new AtomicLong();
    private final AtomicLong unexpectedErrors = new AtomicLong();

    public void recordValidRequest() {
        totalRequests.incrementAndGet();
    }

    public void recordSuccessfulSearch(boolean noMatches) {
        successfulSearches.incrementAndGet();
        if (noMatches) {
            noMatchSearches.incrementAndGet();
        }
    }

    public void recordInvalidRequest() {
        totalRequests.incrementAndGet();
        invalidRequests.incrementAndGet();
    }

    public void recordUnexpectedError() {
        unexpectedErrors.incrementAndGet();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                totalRequests.get(),
                successfulSearches.get(),
                noMatchSearches.get(),
                invalidRequests.get(),
                unexpectedErrors.get()
        );
    }

    public record Snapshot(
            long totalRequests,
            long successfulSearches,
            long noMatchSearches,
            long invalidRequests,
            long unexpectedErrors
    ) {
    }
}
