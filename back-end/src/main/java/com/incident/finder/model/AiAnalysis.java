package com.incident.finder.model;

import java.util.List;

public record AiAnalysis(
        List<String> possibleRootCauses,
        String explanation,
        List<String> troubleshootingSteps,
        List<String> preventiveMeasures,
        List<String> additionalRecommendations
) {
    public AiAnalysis {
        possibleRootCauses = List.copyOf(possibleRootCauses);
        troubleshootingSteps = List.copyOf(troubleshootingSteps);
        preventiveMeasures = List.copyOf(preventiveMeasures);
        additionalRecommendations = List.copyOf(additionalRecommendations);
    }
}
