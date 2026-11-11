package com.incident.finder.engine;

import com.incident.finder.model.Incident;
import com.incident.finder.model.IncidentMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TfidfSimilarityEngineTests {

    private final TfidfSimilarityEngine engine = new TfidfSimilarityEngine();

    @Test
    void givesIdenticalTextMaximumSimilarity() {
        Incident incident = incident(1, "Database timeout", "", "");

        List<IncidentMatch> matches =
                engine.findTopMatches("DATABASE---timeout", List.of(incident));

        assertEquals(1, matches.size());
        assertEquals(1.0, matches.get(0).similarity(), 1.0e-12);
    }

    @Test
    void ranksRelatedIncidentsAndLimitsResultsToThree() {
        List<Incident> incidents = List.of(
                incident(1, "Database query timeout", "Slow SQL queries", ""),
                incident(2, "Database connection issue", "Database connections exhausted", ""),
                incident(3, "Database API error", "Database request failed", ""),
                incident(4, "Database server unavailable", "Database service is offline", ""),
                incident(5, "File upload failure", "Upload size limit", "")
        );

        List<IncidentMatch> matches =
                engine.findTopMatches("database query is slow", incidents);

        assertEquals(3, matches.size());
        assertEquals(1, matches.get(0).incident().getId());
        assertTrue(matches.get(0).similarity() > matches.get(1).similarity());
    }

    @Test
    void returnsNoMatchesForUnrelatedOrUnknownVocabulary() {
        List<Incident> incidents = List.of(
                incident(1, "Database timeout", "Slow query", "")
        );

        assertTrue(engine.findTopMatches("unrelated vocabulary", incidents).isEmpty());
        assertTrue(engine.findTopMatches("unknownword", incidents).isEmpty());
    }

    @Test
    void returnsNoMatchesForEmptyInputAndZeroVectors() {
        List<Incident> emptyDocument = List.of(incident(1, "", "", ""));

        assertTrue(engine.findTopMatches(null, emptyDocument).isEmpty());
        assertTrue(engine.findTopMatches("   ", emptyDocument).isEmpty());
        assertTrue(engine.findTopMatches("!!!", emptyDocument).isEmpty());
        assertTrue(engine.findTopMatches("database", List.of()).isEmpty());
        assertTrue(engine.findTopMatches("database", emptyDocument).isEmpty());
    }

    @Test
    void handlesDuplicateWordsAndAlwaysReturnsFiniteBoundedScores() {
        List<Incident> incidents = List.of(
                incident(1, "database database timeout", "database query timeout", ""),
                incident(2, "database timeout", "slow query", "")
        );

        List<IncidentMatch> matches =
                engine.findTopMatches("database database query timeout", incidents);

        assertFalse(matches.isEmpty());
        for (IncidentMatch match : matches) {
            assertTrue(Double.isFinite(match.similarity()));
            assertTrue(match.similarity() >= 0.0);
            assertTrue(match.similarity() <= 1.0);
        }
    }

    @Test
    void ordersEqualScoresByIncidentId() {
        List<IncidentMatch> matches = engine.findTopMatches(
                "database timeout",
                List.of(
                        incident(7, "database timeout", "", ""),
                        incident(2, "database timeout", "", "")
                )
        );

        assertEquals(List.of(2, 7),
                matches.stream().map(match -> match.incident().getId()).toList());
    }

    private Incident incident(int id, String title, String description, String resolution) {
        return new Incident(id, title, description, resolution);
    }
}
