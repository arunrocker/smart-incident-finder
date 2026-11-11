package com.incident.finder.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record IncidentSearchResponse(
        String message,
        List<IncidentSearchResult> results,
        @JsonInclude(JsonInclude.Include.NON_NULL) AiAnalysis aiAnalysis
) {
    public IncidentSearchResponse(String message, List<IncidentSearchResult> results) {
        this(message, results, null);
    }

    public IncidentSearchResponse {
        results = List.copyOf(results);
    }
}
