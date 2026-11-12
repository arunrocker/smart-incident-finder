package com.incident.finder.controller;

import com.incident.finder.model.IncidentSearchResponse;
import com.incident.finder.model.IncidentSearchResult;
import com.incident.finder.model.AiAnalysis;
import com.incident.finder.service.IncidentSearchProperties;
import com.incident.finder.service.IncidentService;
import com.incident.finder.service.SearchRequestMetrics;
import com.incident.finder.service.SmartIncidentSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentSearchController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, IncidentSearchControllerTests.TestBeans.class})
class IncidentSearchControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IncidentService incidentService;

    @MockBean
    private SmartIncidentSearchService smartIncidentSearchService;

    @MockBean
    private SearchRequestMetrics requestMetrics;

    @Test
    void returnsMatchesForAValidSearchRequest() throws Exception {
        when(smartIncidentSearchService.search("Database API is slow"))
                .thenReturn(new IncidentSearchResponse(
                        "Similar incidents found",
                        List.of(new IncidentSearchResult(
                                1,
                                "Application API Response Slow",
                                85.6,
                                "Optimized SQL queries"
                        ))
                ));

        mockMvc.perform(post("/api/incidents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Database API is slow"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Similar incidents found"))
                .andExpect(jsonPath("$.results[0].incidentId").value(1))
                .andExpect(jsonPath("$.results[0].similarityPercentage").value(85.6))
                .andExpect(jsonPath("$.results[0].resolution").value("Optimized SQL queries"))
                .andExpect(jsonPath("$.aiAnalysis").doesNotExist());

        verify(smartIncidentSearchService).search("Database API is slow");
    }

    @Test
    void tfidfOnlyRouteSkipsAiAnalysis() throws Exception {
        when(incidentService.searchIncidents("Database API is slow"))
                .thenReturn(new IncidentSearchResponse(
                        "Similar incidents found",
                        List.of(new IncidentSearchResult(
                                1,
                                "Application API Response Slow",
                                85.6,
                                "Optimized SQL queries"
                        ))
                ));

        mockMvc.perform(post("/api/incidents/tfidf-search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Database API is slow\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].incidentId").value(1))
                .andExpect(jsonPath("$.aiAnalysis").doesNotExist());

        verify(incidentService).searchIncidents("Database API is slow");
    }

    @Test
    void smartSearchReturnsHistoricalMatchesAndAiAnalysis() throws Exception {
        AiAnalysis analysis = new AiAnalysis(
                List.of("A slow database query"),
                "The query may be waiting on an unindexed table scan.",
                List.of("Inspect slow query logs", "Review the query execution plan"),
                List.of("Add monitoring for query latency"),
                List.of("Load test the affected endpoint")
        );
        when(smartIncidentSearchService.search("Database API is slow"))
                .thenReturn(new IncidentSearchResponse(
                        "Similar incidents found",
                        List.of(new IncidentSearchResult(
                                1,
                                "Application API Response Slow",
                                85.6,
                                "Optimized SQL queries"
                        )),
                        analysis
                ));

        mockMvc.perform(post("/api/incidents/smart-search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Database API is slow\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].incidentId").value(1))
                .andExpect(jsonPath("$.results[0].title")
                        .value("Application API Response Slow"))
                .andExpect(jsonPath("$.aiAnalysis.possibleRootCauses[0]")
                        .value("A slow database query"))
                .andExpect(jsonPath("$.aiAnalysis.explanation")
                        .value("The query may be waiting on an unindexed table scan."))
                .andExpect(jsonPath("$.aiAnalysis.troubleshootingSteps.length()").value(2))
                .andExpect(jsonPath("$.aiAnalysis.preventiveMeasures.length()").value(1))
                .andExpect(jsonPath("$.aiAnalysis.additionalRecommendations.length()").value(1));

        verify(smartIncidentSearchService).search("Database API is slow");
    }

    @Test
    void smartSearchRejectsDescriptionsExceedingTheConfiguredLength() throws Exception {
        String description = "x".repeat(5001);

        mockMvc.perform(post("/api/incidents/smart-search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + description + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "description must not exceed 5000 characters"
                ));
    }

    @Test
    void returnsSuccessfulNoMatchResponse() throws Exception {
        when(smartIncidentSearchService.search("unrelated"))
                .thenReturn(new IncidentSearchResponse(
                        "No sufficiently similar resolved incident found.",
                        List.of()
                ));

        mockMvc.perform(post("/api/incidents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"unrelated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isEmpty())
                .andExpect(jsonPath("$.message")
                        .value("No sufficiently similar resolved incident found."));
    }

    @Test
    void rejectsMissingOrBlankDescriptions() throws Exception {
        mockMvc.perform(post("/api/incidents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("description must not be blank"));

        mockMvc.perform(post("/api/incidents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("description must not be blank"));
    }

    @Test
    void rejectsMalformedJsonWithAConsistentErrorResponse() throws Exception {
        mockMvc.perform(post("/api/incidents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Request body must be valid JSON with a non-empty description."
                ));
    }

    @Test
    void rejectsAnEmptyBodyAndUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/api/incidents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Request body must be valid JSON with a non-empty description."
                ));

        mockMvc.perform(post("/api/incidents/search")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"description\":\"test\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message")
                        .value("Content-Type must be application/json."));
    }

    @Test
    void rejectsDescriptionsExceedingTheConfiguredLength() throws Exception {
        String description = "x".repeat(5001);

        mockMvc.perform(post("/api/incidents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + description + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "description must not exceed 5000 characters"
                ));
    }

    @Test
    void rejectsOversizedRequestBodies() throws Exception {
        String description = "x".repeat(33000);

        mockMvc.perform(post("/api/incidents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + description + "\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value(
                        "Request body exceeds the maximum permitted size."
                ));
    }

    @Test
    void hidesUnexpectedServiceFailures() throws Exception {
        when(smartIncidentSearchService.search("failure"))
                .thenThrow(new IllegalStateException("internal implementation detail"));

        mockMvc.perform(post("/api/incidents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"failure\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("An unexpected server error occurred."))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @TestConfiguration
    @EnableConfigurationProperties(IncidentSearchProperties.class)
    static class TestBeans {

        @Bean
        SearchRequestMetrics searchRequestMetrics() {
            return new SearchRequestMetrics();
        }
    }
}
