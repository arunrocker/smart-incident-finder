export interface IncidentSearchRequest {
  description: string;
}

export interface IncidentSearchResult {
  incidentId: number;
  title: string;
  similarityPercentage: number;
  resolution: string;
}

export interface IncidentSearchResponse {
  message: string;
  results: IncidentSearchResult[];
  aiAnalysis?: AiAnalysis;
}

export interface AiAnalysis {
  possibleRootCauses: string[];
  explanation: string;
  troubleshootingSteps: string[];
  preventiveMeasures: string[];
  additionalRecommendations: string[];
}

export interface ApiErrorResponse {
  message: string;
}
