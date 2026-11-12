# End-to-End Code Flow

This document follows the actual Angular event handlers through the Spring
controller and back to the rendered page. The projects are siblings:
backend paths begin at `smart-incident-finder/`; frontend paths begin at
`smart-incident-finder-angular/frontend/`.

## Shared frontend setup

`src/main.ts` calls `bootstrapApplication(App, appConfig)`.
`src/app/app.config.ts` provides browser error listeners, zone-based change
detection, `HttpClient`, and the router. `src/app/app.routes.ts` maps the empty
path to `IncidentSearchComponent`. The component is standalone and uses
`src/app/components/incident-search/incident-search.component.html`.

The description is stored in the component's `description` signal. Both
buttons call the same `search(mode)` method. It trims the description,
validates non-empty input and the 5000-character maximum, clears old state,
records the mode, and sets `loading` before calling the service.

## Standard Search

1. The user clicks **Standard Search**. The component template calls
   `search('standard')`.
2. `IncidentSearchComponent.search()` selects `IncidentService.search()`.
3. `IncidentService.search()` posts `{ "description": "..." }` to
   `/api/incidents/search`. `environment.apiBaseUrl` is `/api`.
4. In development, `proxy.conf.json` forwards that same-origin path to
   `http://localhost:8080`.
5. Backend `IncidentSearchController.search()` validates the request with
   `@Valid`, checks the configured maximum description length, records a
   valid request, and invokes `IncidentService.searchIncidents(description)`.
6. `IncidentService.searchIncidents()` validates input, invokes
   `TfidfSimilarityEngine.findTopMatches(description, incidents)`, applies
   the configured 10.0% minimum threshold, converts matches to API results,
   and returns up to three.
7. The controller records a successful/no-match search and returns HTTP 200
   with `message` and `results`.
8. The service validates the JSON response shape. The component stores the
   message and at most three results, marks `hasSearched`, and `finalize`
   resets the loading signal.
9. The template renders verified historical result cards or the no-match
   state. An error is rendered instead if the HTTP request fails.

```mermaid
sequenceDiagram
  actor User
  participant UI as IncidentSearchComponent
  participant HTTP as IncidentService
  participant Proxy as Angular /api proxy
  participant Controller as IncidentSearchController
  participant Search as backend IncidentService
  participant Engine as TfidfSimilarityEngine
  User->>UI: Click Standard Search
  UI->>UI: search("standard"), validate, set loading
  UI->>HTTP: search(description)
  HTTP->>Proxy: POST /api/incidents/search
  Proxy->>Controller: POST /api/incidents/search
  Controller->>Search: searchIncidents(description)
  Search->>Engine: findTopMatches(description, incidents)
  Engine-->>Search: ranked IncidentMatch list
  Search-->>Controller: IncidentSearchResponse(message, results)
  Controller-->>Proxy: HTTP 200 JSON
  Proxy-->>HTTP: JSON response
  HTTP-->>UI: validated response
  UI->>UI: set results; finalize loading=false
  UI-->>User: Historical cards or no-match state
```

## Smart Search

The frontend stages are the same through request validation. The differences
begin at the service selection:

1. The **Smart Search** button calls `search('smart')`.
2. `IncidentSearchComponent.search()` selects
   `IncidentService.smartSearch()`.
3. The frontend posts the same request shape to `/api/incidents/smart-search`.
4. `IncidentSearchController.smartSearch()` uses the same bean-validation
   and description-length rules as standard search, then calls
   `SmartIncidentSearchService.search(description)`.
5. `SmartIncidentSearchService.search()` first calls
   `IncidentService.searchIncidents(description)`. This is the sole source of
   the historical IDs, titles, similarity percentages, and resolutions.
6. It passes the description and those Java-produced result DTOs to
   `ClaudeApiClient.analyze()`. The client sends an Anthropic Messages API
   request containing a system instruction and a user prompt with the
   description and JSON historical context.
7. If Claude returns usable analysis JSON, the client maps it to `AiAnalysis`.
   The smart service combines that object with the unmodified historic
   message/results. If a handled Claude API failure occurs, it logs the
   failure type and returns the historic response with no `aiAnalysis`.
8. The frontend renders the historical result cards exactly as for standard
   search. When `aiAnalysis` exists, a separate AI-generated guidance section
   renders root causes, explanation, troubleshooting steps, preventive
   measures, and additional recommendations.
9. If the smart endpoint returns historical results without `aiAnalysis`,
   Angular shows the analysis-unavailable notice while retaining the
   historical cards. Loading is reset with `finalize`.

```mermaid
sequenceDiagram
  actor User
  participant UI as IncidentSearchComponent
  participant HTTP as IncidentService
  participant Proxy as Angular /api proxy
  participant Controller as IncidentSearchController
  participant Smart as SmartIncidentSearchService
  participant Search as backend IncidentService
  participant Engine as TfidfSimilarityEngine
  participant Claude as ClaudeApiClient
  participant API as Claude Messages API
  User->>UI: Click Smart Search
  UI->>UI: search("smart"), validate, set loading
  UI->>HTTP: smartSearch(description)
  HTTP->>Proxy: POST /api/incidents/smart-search
  Proxy->>Controller: POST /api/incidents/smart-search
  Controller->>Smart: search(description)
  Smart->>Search: searchIncidents(description)
  Search->>Engine: findTopMatches(description, incidents)
  Engine-->>Search: top ranked matches
  Search-->>Smart: authoritative historic response
  Smart->>Claude: analyze(description, historic results)
  Claude->>API: POST Messages API (system + user context)
  API-->>Claude: text containing analysis JSON
  Claude-->>Smart: AiAnalysis (or typed failure)
  alt Claude analysis valid
    Smart-->>Controller: same historic results + aiAnalysis
  else Claude unavailable or invalid
    Smart-->>Controller: historic results only
  end
  Controller-->>HTTP: HTTP 200 JSON
  HTTP-->>UI: validated response
  UI->>UI: set results/analysis; finalize loading=false
  UI-->>User: Historical cards and optional separate AI guidance
```

## Loading, validation, and errors

`loading` disables both search buttons and Clear, preventing overlapping
requests. Search validation is performed client-side for user feedback and
again on the backend (`@NotBlank` plus the 5000-character setting). The HTTP service has a 90-second RxJS timeout and maps common statuses to
user-facing messages. The backend uses `GlobalExceptionHandler` for malformed
JSON, validation failures, unsupported methods/content types, AI provider
failures, and unexpected exceptions. A provider failure returns HTTP 503 for
Smart Search; Standard Search does not call the provider.

## HTTP payload shapes

Both Angular service methods send the same request:

```json
{ "description": "Application API requests are slow..." }
```

Standard response:

```json
{
  "message": "Similar incidents found",
  "results": [
    {
      "incidentId": 1,
      "title": "Application API Response Slow",
      "similarityPercentage": 91.9,
      "resolution": "Analyzed slow SQL queries..."
    }
  ]
}
```

Smart search adds an optional field:

```json
{
  "message": "Similar incidents found",
  "results": [],
  "aiAnalysis": {
    "possibleRootCauses": ["Example cause"],
    "explanation": "Example technical explanation.",
    "troubleshootingSteps": ["Example check"],
    "preventiveMeasures": ["Example measure"],
    "additionalRecommendations": ["Example recommendation"]
  }
}
```

Values above illustrate the contract; actual historical data comes from
`incidents.json`, while analysis is generated at runtime. Without a configured
AI key, Standard Search still works and Smart Search returns HTTP 503.
