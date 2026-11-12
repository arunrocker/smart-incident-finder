package com.incident.finder.service;

import com.incident.finder.model.AiAnalysis;
import com.incident.finder.model.IncidentSearchResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SmartIncidentSearchService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SmartIncidentSearchService.class);

    private final IncidentService incidentService;
    private final AiAnalysisProvider aiAnalysisProvider;

    public SmartIncidentSearchService(
            IncidentService incidentService,
            AiAnalysisProvider aiAnalysisProvider
    ) {
        this.incidentService = incidentService;
        this.aiAnalysisProvider = aiAnalysisProvider;
    }

    @PostConstruct
    void logSelectedProvider() {
        LOGGER.info("AI analysis provider selected: {}", aiAnalysisProvider.providerName());
    }

    public IncidentSearchResponse search(String description) {
        IncidentSearchResponse historicalResponse =
                incidentService.searchIncidents(description);

        try {
            LOGGER.info("Requesting {} incident analysis", aiAnalysisProvider.providerName());
            AiAnalysis analysis = aiAnalysisProvider.analyze(
                    description,
                    historicalResponse.results()
            );
            return new IncidentSearchResponse(
                    historicalResponse.message(),
                    historicalResponse.results(),
                    analysis
            );
        } catch (AiProviderException exception) {
            LOGGER.warn(
                    "{} analysis unavailable (failureType={}); returning TF-IDF results only",
                    aiAnalysisProvider.providerName(),
                    exception.getFailureType()
            );
            return historicalResponse;
        }
    }
}
