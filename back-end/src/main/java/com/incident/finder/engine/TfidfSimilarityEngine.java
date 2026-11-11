package com.incident.finder.engine;

import com.incident.finder.model.Incident;
import com.incident.finder.model.IncidentMatch;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TfidfSimilarityEngine {

    private static final int MAX_MATCHES = 3;

    public List<IncidentMatch> findTopMatches(String query, List<Incident> incidents) {
        if (query == null || incidents == null || incidents.isEmpty()) {
            return List.of();
        }

        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        List<List<String>> documents = incidents.stream()
                .map(this::documentTerms)
                .toList();
        Map<String, Double> inverseDocumentFrequencies = calculateIdf(documents);
        Map<String, Double> queryVector = createVector(queryTerms, inverseDocumentFrequencies);
        double queryNorm = vectorNorm(queryVector);
        if (queryNorm == 0) {
            return List.of();
        }

        List<IncidentMatch> matches = new ArrayList<>();
        for (int i = 0; i < incidents.size(); i++) {
            Map<String, Double> documentVector =
                    createVector(documents.get(i), inverseDocumentFrequencies);
            double similarity = cosineSimilarity(
                    queryVector, queryNorm, documentVector, vectorNorm(documentVector)
            );
            if (similarity > 0) {
                matches.add(new IncidentMatch(incidents.get(i), similarity));
            }
        }

        return matches.stream()
                .sorted(Comparator.comparingDouble(IncidentMatch::similarity).reversed()
                        .thenComparingInt(match -> match.incident().getId()))
                .limit(MAX_MATCHES)
                .toList();
    }

    private List<String> documentTerms(Incident incident) {
        return tokenize(valueOrEmpty(incident.getTitle()) + " "
                + valueOrEmpty(incident.getDescription()));
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> !token.isEmpty())
                .toList();
    }

    private Map<String, Double> calculateIdf(List<List<String>> documents) {
        Map<String, Integer> documentFrequencies = new HashMap<>();
        for (List<String> document : documents) {
            document.stream().distinct().forEach(term ->
                    documentFrequencies.merge(term, 1, Integer::sum));
        }

        Map<String, Double> inverseDocumentFrequencies = new HashMap<>();
        int documentCount = documents.size();
        documentFrequencies.forEach((term, frequency) ->
                inverseDocumentFrequencies.put(
                        term,
                        Math.log((double) (documentCount + 1) / (frequency + 1)) + 1
                ));
        return inverseDocumentFrequencies;
    }

    private Map<String, Double> createVector(
            List<String> terms,
            Map<String, Double> inverseDocumentFrequencies
    ) {
        if (terms.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> termFrequencies = new HashMap<>();
        terms.forEach(term -> termFrequencies.merge(term, 1, Integer::sum));

        Map<String, Double> vector = new HashMap<>();
        termFrequencies.forEach((term, frequency) -> {
            Double idf = inverseDocumentFrequencies.get(term);
            if (idf != null) {
                vector.put(term, (double) frequency / terms.size() * idf);
            }
        });
        return vector;
    }

    private double cosineSimilarity(
            Map<String, Double> first,
            double firstNorm,
            Map<String, Double> second,
            double secondNorm
    ) {
        if (firstNorm == 0 || secondNorm == 0) {
            return 0;
        }

        double dotProduct = 0;
        for (Map.Entry<String, Double> entry : first.entrySet()) {
            dotProduct += entry.getValue() * second.getOrDefault(entry.getKey(), 0.0);
        }

        double similarity = dotProduct / (firstNorm * secondNorm);
        if (!Double.isFinite(similarity)) {
            throw new IllegalStateException("TF-IDF similarity calculation produced a non-finite value");
        }
        return Math.max(0.0, Math.min(1.0, similarity));
    }

    private double vectorNorm(Map<String, Double> vector) {
        return Math.sqrt(vector.values().stream().mapToDouble(weight -> weight * weight).sum());
    }
}
