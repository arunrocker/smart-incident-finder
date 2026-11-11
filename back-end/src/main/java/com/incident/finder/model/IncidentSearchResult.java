package com.incident.finder.model;

public record IncidentSearchResult(
        int incidentId,
        String title,
        double similarityPercentage,
        String resolution
) {
}
