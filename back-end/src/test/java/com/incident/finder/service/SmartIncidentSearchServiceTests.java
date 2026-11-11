package com.incident.finder.service;

import com.incident.finder.model.AiAnalysis;
import com.incident.finder.model.IncidentSearchResponse;
import com.incident.finder.model.IncidentSearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartIncidentSearchServiceTests {

    @Mock
    private IncidentService incidentService;

    @Mock
    private ClaudeApiClient claudeApiClient;

    @Test
    void enrichesServerGeneratedHistoricalResultsWithClaudeAnalysis() {
        String description = "Database requests are slow";
        List<IncidentSearchResult> historicalResults = List.of(
                new IncidentSearchResult(7, "Slow database", 88.4, "Added an index")
        );
        IncidentSearchResponse historicalResponse =
                new IncidentSearchResponse("Similar incidents found", historicalResults);
        AiAnalysis analysis = analysis();

        when(incidentService.searchIncidents(description)).thenReturn(historicalResponse);
        when(claudeApiClient.analyze(description, historicalResults)).thenReturn(analysis);

        IncidentSearchResponse response =
                new SmartIncidentSearchService(incidentService, claudeApiClient).search(description);

        assertEquals(historicalResults, response.results());
        assertEquals(7, response.results().get(0).incidentId());
        assertEquals("Slow database", response.results().get(0).title());
        assertEquals(88.4, response.results().get(0).similarityPercentage());
        assertEquals("Added an index", response.results().get(0).resolution());
        assertSame(analysis, response.aiAnalysis());
        verify(claudeApiClient).analyze(description, historicalResults);
    }

    @Test
    void preservesHistoricalResultsAndOmitsAnalysisWhenClaudeFails() {
        String description = "Database requests are slow";
        IncidentSearchResponse historicalResponse = new IncidentSearchResponse(
                "Similar incidents found",
                List.of(new IncidentSearchResult(7, "Slow database", 88.4, "Added an index"))
        );
        when(incidentService.searchIncidents(description)).thenReturn(historicalResponse);
        when(claudeApiClient.analyze(description, historicalResponse.results()))
                .thenThrow(new ClaudeApiException(
                        ClaudeApiException.FailureType.RATE_LIMITED,
                        "Claude rate limit reached"
                ));

        IncidentSearchResponse response =
                new SmartIncidentSearchService(incidentService, claudeApiClient).search(description);

        assertSame(historicalResponse, response);
        assertNull(response.aiAnalysis());
        assertEquals(historicalResponse.results(), response.results());
    }

    @Test
    void requestsAnalysisEvenWhenThereAreNoHistoricalMatches() {
        String description = "unmatched incident vocabulary";
        IncidentSearchResponse noMatches = new IncidentSearchResponse(
                "No sufficiently similar resolved incident found.",
                List.of()
        );
        when(incidentService.searchIncidents(description)).thenReturn(noMatches);
        when(claudeApiClient.analyze(description, List.of())).thenReturn(analysis());

        IncidentSearchResponse response =
                new SmartIncidentSearchService(incidentService, claudeApiClient).search(description);

        assertEquals("No sufficiently similar resolved incident found.", response.message());
        assertEquals(List.of(), response.results());
        assertEquals(analysis(), response.aiAnalysis());
        verify(claudeApiClient).analyze(description, List.of());
    }

    private AiAnalysis analysis() {
        return new AiAnalysis(
                List.of("A database query may be missing an index"),
                "The query may be scanning more rows than expected.",
                List.of("Inspect database query plans"),
                List.of("Monitor query latency"),
                List.of("Load test the affected endpoint")
        );
    }
}
