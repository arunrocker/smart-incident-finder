package com.incident.finder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.finder.engine.TfidfSimilarityEngine;
import com.incident.finder.model.Incident;
import com.incident.finder.model.IncidentMatch;
import com.incident.finder.model.IncidentSearchResponse;
import com.incident.finder.model.IncidentSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTests {

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource resource;

    @Mock
    private TfidfSimilarityEngine similarityEngine;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IncidentSearchProperties searchProperties = new IncidentSearchProperties();

    private IncidentService incidentService;

    @BeforeEach
    void setUp() throws Exception {
        incidentService = new IncidentService(
                objectMapper, resourceLoader, similarityEngine, searchProperties
        );
        when(resourceLoader.getResource("classpath:incidents.json")).thenReturn(resource);
        useDataset(dataset());
        incidentService.loadIncidents();
    }

    @Test
    void searchesSuccessfullyAndReturnsAtMostThreeMatches() {
        List<IncidentMatch> matches = List.of(
                match(1, 0.91),
                match(2, 0.80),
                match(3, 0.72),
                match(4, 0.50)
        );
        when(similarityEngine.findTopMatches(eq("database timeout"), anyList()))
                .thenReturn(matches);

        IncidentSearchResponse response = incidentService.searchIncidents("database timeout");

        assertEquals("Similar incidents found", response.message());
        assertEquals(3, response.results().size());
        assertEquals(91.0, response.results().get(0).similarityPercentage());
    }

    @Test
    void returnsNoMatchWhenEveryScoreIsBelowTheConfiguredThreshold() {
        when(similarityEngine.findTopMatches(eq("unrelated"), anyList()))
                .thenReturn(List.of(match(1, 0.09)));

        IncidentSearchResponse response = incidentService.searchIncidents("unrelated");

        assertEquals("No sufficiently similar resolved incident found.", response.message());
        assertTrue(response.results().isEmpty());
    }

    @Test
    void returnsAnImmutableCopyOfHistoricalIncidents() {
        List<Incident> returnedIncidents = incidentService.getAllIncidents();
        assertThrows(UnsupportedOperationException.class, returnedIncidents::clear);
        returnedIncidents.get(0).setTitle("changed");

        assertEquals("Incident title 1", incidentService.getAllIncidents().get(0).getTitle());
    }

    @Test
    void failsFastForAnEmptyHistoricalDataset() throws Exception {
        useDataset(List.of());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, incidentService::loadIncidents
        );

        assertTrue(exception.getMessage().contains("Failed to load historical incidents"));
    }

    @Test
    void failsFastForMalformedJson() throws Exception {
        useJson("{ invalid json");

        assertThrows(IllegalStateException.class, incidentService::loadIncidents);
    }

    @Test
    void failsFastWhenHistoricalFieldsAreMissing() throws Exception {
        List<Incident> incidents = dataset();
        incidents.get(0).setResolution(" ");
        useDataset(incidents);

        assertThrows(IllegalStateException.class, incidentService::loadIncidents);
    }

    @Test
    void failsFastForDuplicateHistoricalIds() throws Exception {
        List<Incident> incidents = dataset();
        incidents.get(1).setId(incidents.get(0).getId());
        useDataset(incidents);

        assertThrows(IllegalStateException.class, incidentService::loadIncidents);
    }

    @Test
    void failsFastWhenTheHistoricalResourceCannotBeOpened() throws Exception {
        doThrow(new FileNotFoundException("not found"))
                .when(resource).getInputStream();

        assertThrows(IllegalStateException.class, incidentService::loadIncidents);
    }

    @Test
    void propagatesSimilarityEngineFailures() {
        when(similarityEngine.findTopMatches(eq("database timeout"), anyList()))
                .thenThrow(new IllegalStateException("engine failure"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> incidentService.searchIncidents("database timeout")
        );

        assertEquals("engine failure", exception.getMessage());
    }

    @Test
    void rejectsNonFiniteSimilarityScores() {
        when(similarityEngine.findTopMatches(eq("database timeout"), anyList()))
                .thenReturn(List.of(match(1, Double.NaN)));

        assertThrows(
                IllegalStateException.class,
                () -> incidentService.searchIncidents("database timeout")
        );
    }

    @Test
    void ignoresUnknownVocabularyAndDoesNotProduceInvalidPercentages() {
        when(similarityEngine.findTopMatches(eq("unknown words"), anyList()))
                .thenReturn(List.of());

        IncidentSearchResponse response = incidentService.searchIncidents("unknown words");

        assertFalse(response.results().stream()
                .anyMatch(result -> !Double.isFinite(result.similarityPercentage())));
        assertTrue(response.results().isEmpty());
    }

    private IncidentMatch match(int id, double similarity) {
        return new IncidentMatch(
                new Incident(id, "Incident " + id, "Description " + id, "Resolution " + id),
                similarity
        );
    }

    private List<Incident> dataset() {
        List<Incident> incidents = new ArrayList<>();
        for (int id = 1; id <= 12; id++) {
            incidents.add(new Incident(
                    id,
                    "Incident title " + id,
                    "Incident description " + id,
                    "Incident resolution " + id
            ));
        }
        return incidents;
    }

    private void useDataset(List<Incident> incidents) throws Exception {
        useJson(objectMapper.writeValueAsString(incidents));
    }

    private void useJson(String json) throws Exception {
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        when(resource.getInputStream()).thenAnswer(
                invocation -> new ByteArrayInputStream(content)
        );
    }
}
