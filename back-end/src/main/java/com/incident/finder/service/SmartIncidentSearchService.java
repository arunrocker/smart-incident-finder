package com.incident.finder.service;

import com.incident.finder.model.AiAnalysis;
import com.incident.finder.model.IncidentSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SmartIncidentSearchService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SmartIncidentSearchService.class);

    private final IncidentService incidentService;
    private final ClaudeApiClient claudeApiClient;

    public SmartIncidentSearchService(
            IncidentService incidentService,
            ClaudeApiClient claudeApiClient
    ) {
        this.incidentService = incidentService;
        this.claudeApiClient = claudeApiClient;
    }

    public IncidentSearchResponse search(String description) {
        IncidentSearchResponse historicalResponse =
                incidentService.searchIncidents(description);

        try {
            AiAnalysis analysis = claudeApiClient.analyze(
                    description,
                    historicalResponse.results()
            );
            return new IncidentSearchResponse(
                    historicalResponse.message(),
                    historicalResponse.results(),
                    analysis
            );
        } catch (ClaudeApiException exception) {
            LOGGER.warn(
                    "Claude analysis unavailable (failureType={}); returning TF-IDF results only",
                    exception.getFailureType()
            );
            return historicalResponse;
        }
    }
}
