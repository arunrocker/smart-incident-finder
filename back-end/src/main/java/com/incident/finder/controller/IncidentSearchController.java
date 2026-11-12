package com.incident.finder.controller;

import com.incident.finder.model.IncidentSearchRequest;
import com.incident.finder.model.IncidentSearchResponse;
import com.incident.finder.service.InvalidIncidentSearchException;
import com.incident.finder.service.IncidentSearchProperties;
import com.incident.finder.service.IncidentService;
import com.incident.finder.service.SearchRequestMetrics;
import com.incident.finder.service.SmartIncidentSearchService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentSearchController {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(IncidentSearchController.class);

    private final IncidentService incidentService;
    private final SmartIncidentSearchService smartIncidentSearchService;
    private final IncidentSearchProperties searchProperties;
    private final SearchRequestMetrics requestMetrics;

    public IncidentSearchController(
            IncidentService incidentService,
            SmartIncidentSearchService smartIncidentSearchService,
            IncidentSearchProperties searchProperties,
            SearchRequestMetrics requestMetrics
    ) {
        this.incidentService = incidentService;
        this.smartIncidentSearchService = smartIncidentSearchService;
        this.searchProperties = searchProperties;
        this.requestMetrics = requestMetrics;
    }

    @PostMapping(path = {"/search", "/tfidf-search"}, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IncidentSearchResponse> search(
            @Valid @RequestBody IncidentSearchRequest request
    ) {
        String description = request.description();
        if (description.length() > searchProperties.getMaximumDescriptionLength()) {
            throw new InvalidIncidentSearchException(
                    "description must not exceed "
                            + searchProperties.getMaximumDescriptionLength()
                            + " characters"
            );
        }

        requestMetrics.recordValidRequest();
        LOGGER.info("Incident search request received (descriptionLength={}, mode=standard)",
                description.length());
        IncidentSearchResponse response = incidentService.searchIncidents(description);
        requestMetrics.recordSuccessfulSearch(response.results().isEmpty());
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/smart-search", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IncidentSearchResponse> smartSearch(
            @Valid @RequestBody IncidentSearchRequest request
    ) {
        String description = request.description();
        if (description.length() > searchProperties.getMaximumDescriptionLength()) {
            throw new InvalidIncidentSearchException(
                    "description must not exceed "
                            + searchProperties.getMaximumDescriptionLength()
                            + " characters"
            );
        }

        requestMetrics.recordValidRequest();
        LOGGER.info("Incident search request received (descriptionLength={}, mode=smart)",
                description.length());
        IncidentSearchResponse response = smartIncidentSearchService.search(description);
        requestMetrics.recordSuccessfulSearch(response.results().isEmpty());
        return ResponseEntity.ok(response);
    }
}
