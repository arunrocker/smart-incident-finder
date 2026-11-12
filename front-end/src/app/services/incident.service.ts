import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, TimeoutError, catchError, map, throwError, timeout } from 'rxjs';
import {
  IncidentSearchRequest,
  IncidentSearchResponse,
} from '../models/incident.model';
import { environment } from '../../environments/environment';

const SEARCH_TIMEOUT_MS = 90_000;

export class IncidentSearchError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'IncidentSearchError';
  }
}

@Injectable({ providedIn: 'root' })
export class IncidentService {
  private readonly http = inject(HttpClient);
  private readonly searchUrl = `${environment.apiBaseUrl}/incidents/search`;

  search(description: string): Observable<IncidentSearchResponse> {
    return this.searchAt(`${environment.apiBaseUrl}/incidents/search`, description);
  }

  smartSearch(description: string): Observable<IncidentSearchResponse> {
    return this.searchAt(`${environment.apiBaseUrl}/incidents/smart-search`, description);
  }

  private searchAt(url: string, description: string): Observable<IncidentSearchResponse> {
    const request: IncidentSearchRequest = { description };

    return this.http.post<unknown>(url, request).pipe(
      timeout({ first: SEARCH_TIMEOUT_MS }),
      map((response) => this.validateResponse(response)),
      catchError((error: unknown) => throwError(() => this.toSearchError(error))),
    );
  }

  private validateResponse(response: unknown): IncidentSearchResponse {
    if (response === null || response === undefined) {
      throw new IncidentSearchError('The server returned an empty response. Please try again.');
    }

    if (!isIncidentSearchResponse(response)) {
      throw new IncidentSearchError(
        'The server returned an unexpected response. Please try again.',
      );
    }

    return response;
  }

  private toSearchError(error: unknown): IncidentSearchError {
    if (error instanceof IncidentSearchError) {
      return error;
    }

    if (error instanceof TimeoutError) {
      return new IncidentSearchError('The search request timed out. Please try again.');
    }

    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 0:
          return new IncidentSearchError(
            'Unable to reach the incident service. Check that the backend is running and try again.',
          );
        case 400:
          return new IncidentSearchError(
            getApiErrorMessage(error.error) ?? 'The incident description is invalid.',
          );
        case 404:
          return new IncidentSearchError(
            'The incident search endpoint was not found. Check the backend configuration.',
          );
        case 413:
          return new IncidentSearchError(
            getApiErrorMessage(error.error) ?? 'The request is too large to process.',
          );
        case 500:
          if (isProxyConnectionError(error.error)) {
            return backendUnavailableError();
          }
          return new IncidentSearchError(
            'The server encountered an error while searching. Please try again.',
          );
        case 503:
          return new IncidentSearchError(
            getApiErrorMessage(error.error) ??
              'The service is temporarily unavailable. Please try again later.',
          );
        case 502:
        case 504:
          return backendUnavailableError();
        default:
          return new IncidentSearchError(
            getApiErrorMessage(error.error) ?? 'The search request failed. Please try again.',
          );
      }
    }

    return new IncidentSearchError('An unexpected error occurred. Please try again.');
  }
}

function backendUnavailableError(): IncidentSearchError {
  return new IncidentSearchError(
    'Unable to reach the incident service. Check that the backend is running and try again.',
  );
}

function isProxyConnectionError(value: unknown): boolean {
  return typeof value === 'string' && /ECONNREFUSED|proxy/i.test(value);
}

function isIncidentSearchResponse(value: unknown): value is IncidentSearchResponse {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const response = value as Record<string, unknown>;
  const isValidResponse =
    typeof response['message'] === 'string' &&
    Array.isArray(response['results']) &&
    response['results'].every(isIncidentSearchResult);

  if (!isValidResponse || response['aiAnalysis'] === undefined) {
    return isValidResponse;
  }

  return isAiAnalysis(response['aiAnalysis']);
}

function isAiAnalysis(value: unknown): boolean {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const analysis = value as Record<string, unknown>;
  return (
    isStringArray(analysis['possibleRootCauses']) &&
    typeof analysis['explanation'] === 'string' &&
    isStringArray(analysis['troubleshootingSteps']) &&
    isStringArray(analysis['preventiveMeasures']) &&
    isStringArray(analysis['additionalRecommendations'])
  );
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string');
}

function isIncidentSearchResult(value: unknown): boolean {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const result = value as Record<string, unknown>;
  return (
    typeof result['incidentId'] === 'number' &&
    Number.isInteger(result['incidentId']) &&
    typeof result['title'] === 'string' &&
    typeof result['similarityPercentage'] === 'number' &&
    Number.isFinite(result['similarityPercentage']) &&
    result['similarityPercentage'] >= 0 &&
    result['similarityPercentage'] <= 100 &&
    typeof result['resolution'] === 'string'
  );
}

function getApiErrorMessage(value: unknown): string | null {
  if (
    typeof value === 'object' &&
    value !== null &&
    'message' in value &&
    typeof value.message === 'string'
  ) {
    return value.message;
  }

  return null;
}
