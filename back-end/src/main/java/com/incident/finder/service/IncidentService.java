package com.incident.finder.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.finder.engine.TfidfSimilarityEngine;
import com.incident.finder.model.Incident;
import com.incident.finder.model.IncidentMatch;
import com.incident.finder.model.IncidentSearchResponse;
import com.incident.finder.model.IncidentSearchResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class IncidentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentService.class);
    private static final int MINIMUM_INCIDENT_COUNT = 10;
    private static final int MAXIMUM_INCIDENT_COUNT = 100;
    private static final int MAXIMUM_RESULTS = 3;
    private static final String MATCHES_FOUND_MESSAGE = "Similar incidents found";
    private static final String NO_MATCH_MESSAGE = "No sufficiently similar resolved incident found.";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final TfidfSimilarityEngine similarityEngine;
    private final IncidentSearchProperties searchProperties;

    private volatile List<Incident> incidents = List.of();

    public IncidentService(
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            TfidfSimilarityEngine similarityEngine,
            IncidentSearchProperties searchProperties
    ) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.similarityEngine = similarityEngine;
        this.searchProperties = searchProperties;
    }

    @PostConstruct
    void loadIncidents() {
        try {
            Resource resource = resourceLoader.getResource("classpath:incidents.json");
            try (InputStream inputStream = resource.getInputStream()) {
                List<Incident> loadedIncidents = objectMapper.readValue(
                        inputStream,
                        new TypeReference<List<Incident>>() {
                        }
                );
                validateDataset(loadedIncidents);
                incidents = List.copyOf(loadedIncidents);
            }
            LOGGER.info("Loaded {} validated historical incidents", incidents.size());
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to load a valid historical incident dataset", exception);
            throw new IllegalStateException(
                    "Failed to load historical incidents: " + exception.getMessage(),
                    exception
            );
        }
    }

    public List<Incident> getAllIncidents() {
        return incidents.stream()
                .map(incident -> new Incident(
                        incident.getId(),
                        incident.getTitle(),
                        incident.getDescription(),
                        incident.getResolution()
                ))
                .toList();
    }

    public IncidentSearchResponse searchIncidents(String description) {
        if (description == null || description.isBlank()) {
            throw new InvalidIncidentSearchException("description must not be blank");
        }
        if (description.length() > searchProperties.getMaximumDescriptionLength()) {
            throw new InvalidIncidentSearchException(
                    "description must not exceed "
                            + searchProperties.getMaximumDescriptionLength()
                            + " characters"
            );
        }

        List<IncidentMatch> matches = similarityEngine.findTopMatches(description, incidents);
        for (IncidentMatch match : matches) {
            if (match == null || match.incident() == null
                    || !Double.isFinite(match.similarity())
                    || match.similarity() < 0
                    || match.similarity() > 1) {
                throw new IllegalStateException("Similarity engine returned an invalid result");
            }
        }

        List<IncidentSearchResult> results = matches.stream()
                .filter(match -> match.similarity() * 100
                        >= searchProperties.getMinimumSimilarityPercentage())
                .map(this::toSearchResult)
                .limit(MAXIMUM_RESULTS)
                .toList();

        LOGGER.info("Incident search completed (descriptionLength={}, results={})",
                description.length(), results.size());
        return new IncidentSearchResponse(
                results.isEmpty() ? NO_MATCH_MESSAGE : MATCHES_FOUND_MESSAGE,
                results
        );
    }

    private void validateDataset(List<Incident> loadedIncidents) {
        if (loadedIncidents == null
                || loadedIncidents.size() < MINIMUM_INCIDENT_COUNT
                || loadedIncidents.size() > MAXIMUM_INCIDENT_COUNT) {
            throw new IllegalStateException(
                    "incidents.json must contain between 10 and 100 incidents"
            );
        }

        Set<Integer> ids = new HashSet<>();
        for (int index = 0; index < loadedIncidents.size(); index++) {
            Incident incident = loadedIncidents.get(index);
            if (incident == null
                    || incident.getId() <= 0
                    || isBlank(incident.getTitle())
                    || isBlank(incident.getDescription())
                    || isBlank(incident.getResolution())) {
                throw new IllegalStateException(
                        "Historical incident at index " + index
                                + " is missing a valid id, title, description, or resolution"
                );
            }
            if (!ids.add(incident.getId())) {
                throw new IllegalStateException(
                        "Historical incident IDs must be unique; duplicate id: "
                                + incident.getId()
                );
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private IncidentSearchResult toSearchResult(IncidentMatch match) {
        double similarity = match.similarity();
        double percentage = BigDecimal.valueOf(similarity * 100)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
        return new IncidentSearchResult(
                match.incident().getId(),
                match.incident().getTitle(),
                percentage,
                match.incident().getResolution()
        );
    }
}
