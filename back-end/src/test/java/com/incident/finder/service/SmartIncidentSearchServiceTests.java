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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartIncidentSearchServiceTests {

    @Mock
    private IncidentService incidentService;

    @Mock
    private AiAnalysisProvider aiAnalysisProvider;

    @Test
    void enrichesServerGeneratedHistoricalResultsWithSelectedProviderAnalysis() {
        String description = "Database requests are slow";
        List<IncidentSearchResult> historicalResults = List.of(
                new IncidentSearchResult(7, "Slow database", 88.4, "Added an index")
        );
        IncidentSearchResponse historicalResponse =
                new IncidentSearchResponse("Similar incidents found", historicalResults);
        AiAnalysis analysis = analysis();

        when(incidentService.searchIncidents(description)).thenReturn(historicalResponse);
        when(aiAnalysisProvider.analyze(description, historicalResults)).thenReturn(analysis);

        IncidentSearchResponse response =
                new SmartIncidentSearchService(incidentService, aiAnalysisProvider).search(description);

        assertEquals(historicalResults, response.results());
        assertEquals(7, response.results().get(0).incidentId());
        assertEquals("Slow database", response.results().get(0).title());
        assertEquals(88.4, response.results().get(0).similarityPercentage());
        assertEquals("Added an index", response.results().get(0).resolution());
        assertSame(analysis, response.aiAnalysis());
        verify(aiAnalysisProvider).analyze(description, historicalResults);
    }

    @Test
    void propagatesProviderFailureInsteadOfPretendingAnalysisSucceeded() {
        String description = "Database requests are slow";
        IncidentSearchResponse historicalResponse = new IncidentSearchResponse(
                "Similar incidents found",
                List.of(new IncidentSearchResult(7, "Slow database", 88.4, "Added an index"))
        );
        when(incidentService.searchIncidents(description)).thenReturn(historicalResponse);
        when(aiAnalysisProvider.analyze(description, historicalResponse.results()))
                .thenThrow(new AiProviderException(
                        AiProviderException.FailureType.RATE_LIMITED,
                        "Provider rate limit reached"
                ));

        assertThrows(
                AiProviderException.class,
                () -> new SmartIncidentSearchService(incidentService, aiAnalysisProvider)
                        .search(description)
        );
    }

    @Test
    void requestsAnalysisEvenWhenThereAreNoHistoricalMatches() {
        String description = "unmatched incident vocabulary";
        IncidentSearchResponse noMatches = new IncidentSearchResponse(
                "No sufficiently similar resolved incident found.",
                List.of()
        );
        when(incidentService.searchIncidents(description)).thenReturn(noMatches);
        when(aiAnalysisProvider.analyze(description, List.of())).thenReturn(analysis());

        IncidentSearchResponse response =
                new SmartIncidentSearchService(incidentService, aiAnalysisProvider).search(description);

        assertEquals("No sufficiently similar resolved incident found.", response.message());
        assertEquals(List.of(), response.results());
        assertEquals(analysis(), response.aiAnalysis());
        verify(aiAnalysisProvider).analyze(description, List.of());
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
