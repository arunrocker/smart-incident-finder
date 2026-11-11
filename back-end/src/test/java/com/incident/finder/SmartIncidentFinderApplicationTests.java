package com.incident.finder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.finder.model.Incident;
import com.incident.finder.model.IncidentSearchResponse;
import com.incident.finder.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class SmartIncidentFinderApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private IncidentService incidentService;

	@Test
	void contextLoads() {
		assertEquals(95, incidentService.getAllIncidents().size());
	}

	@Test
	void searchesTheLoadedHistoricalDatasetThroughTheApi() throws Exception {
		String responseBody = mockMvc.perform(post("/api/incidents/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description":"Database queries are slow"}
								"""))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		IncidentSearchResponse response =
				objectMapper.readValue(responseBody, IncidentSearchResponse.class);
		List<Incident> historicalIncidents = incidentService.getAllIncidents();

		assertFalse(response.results().isEmpty());
		assertTrue(response.results().size() <= 3);
		response.results().forEach(result -> {
			Incident incident = historicalIncidents.stream()
					.filter(historical -> historical.getId() == result.incidentId())
					.findFirst()
					.orElse(null);
			assertNotNull(incident);
			assertTrue(Double.isFinite(result.similarityPercentage()));
			assertTrue(result.similarityPercentage() >= 0);
			assertTrue(result.similarityPercentage() <= 100);
			assertEquals(incident.getTitle(), result.title());
			assertEquals(incident.getResolution(), result.resolution());
		});
	}

	@Test
	void returnsAnEmptyResultForAQueryOutsideTheDatasetVocabulary() throws Exception {
		mockMvc.perform(post("/api/incidents/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description":"qzxwkjh vbnmty"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results").isEmpty());
	}

	@Test
	void smartSearchKeepsHistoricalResultsWhenClaudeIsNotConfigured() throws Exception {
		mockMvc.perform(post("/api/incidents/smart-search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description":"Database queries are slow"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(3)))
				.andExpect(jsonPath("$.results[0].incidentId").exists())
				.andExpect(jsonPath("$.results[0].title").exists())
				.andExpect(jsonPath("$.results[0].similarityPercentage").exists())
				.andExpect(jsonPath("$.results[0].resolution").exists())
				.andExpect(jsonPath("$.aiAnalysis").doesNotExist());
	}
}
