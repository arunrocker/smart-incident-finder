package com.incident.finder.service;

import com.incident.finder.model.AiAnalysis;
import com.incident.finder.model.IncidentSearchResult;

import java.util.List;

public interface AiAnalysisProvider {

    String providerName();

    AiAnalysis analyze(String description, List<IncidentSearchResult> historicalIncidents);
}
