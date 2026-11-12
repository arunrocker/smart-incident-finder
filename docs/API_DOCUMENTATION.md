# API Documentation

Base URL for local development: `http://localhost:8080`. Search routes are
JSON POST operations under `/api/incidents`. The Angular development server
forwards `/api` to port 8080.

## Shared request

Both endpoints accept the same request DTO:

```json
{
  "description": "Application APIs are taking too much time to respond due to slow database queries."
}
```

`description` must be present, nonblank, and at most 5000 Java characters.
The complete HTTP request body is limited to 32768 bytes by default. The
content type must be `application/json`.

## `POST /api/incidents/search`

Standard TF-IDF search. It does not call any AI provider and stays available
when the AI provider is unavailable or has no configured key.

**Success (200), matching results:**

```json
{
  "message": "Similar incidents found",
  "results": [
    {
      "incidentId": 1,
      "title": "Application API Response Slow",
      "similarityPercentage": 91.9,
      "resolution": "Analyzed slow SQL queries, added appropriate database indexes and optimized frequently executed queries."
    }
  ]
}
```

The API returns up to three results that meet the configured minimum
similarity percentage (default 10.0%). Exact scores depend on the current
corpus and query.

**Success (200), no match:**

```json
{
  "message": "No sufficiently similar resolved incident found.",
  "results": []
}
```

The no-match case is not an HTTP error.
`POST /api/incidents/tfidf-search` is retained as an alias for this endpoint.

## `POST /api/incidents/smart-search`

Runs the same Java TF-IDF historical search and asks the selected AI provider
to analyze the description with up to the three Java-ranked historical
matches as context. Historical results (including IDs, titles, similarity
percentages, and resolutions) always come from `incidents.json`; AI output
cannot replace or add historical records. `aiAnalysis` contains possible
causes, explanation, troubleshooting steps, preventive measures, and
additional recommendations.

The default provider is Groq using `openai/gpt-oss-120b` and strict JSON Schema
output. Free-tier quotas are account/model-specific and may change; check your
GroqCloud rate limits.

**Success (200), analysis available:**

```json
{
  "message": "Similar incidents found",
  "results": [
    {
      "incidentId": 1,
      "title": "Application API Response Slow",
      "similarityPercentage": 91.9,
      "resolution": "Analyzed slow SQL queries and optimized frequently executed queries."
    }
  ],
  "aiAnalysis": {
    "possibleRootCauses": ["A query may not use an appropriate index"],
    "explanation": "The symptoms are consistent with database query latency.",
    "troubleshootingSteps": ["Inspect query plans"],
    "preventiveMeasures": ["Monitor query latency"],
    "additionalRecommendations": ["Test with representative load"]
  }
}
```

A successful response includes `aiAnalysis`. Causes are hypotheses unless
supported by the supplied evidence; recommendations are AI-generated, not
verified incident history. A successful no-match historic search can still
include AI analysis.

## Error status codes

All structured application errors use `{ "message": "..." }`.

| HTTP status | Cause | Example response |
|---|---|---|
| 400 | Missing/blank description, description over 5000 characters, malformed JSON | `{"message":"description must not be blank"}` |
| 413 | Request body exceeds configured byte limit | `{"message":"Request body exceeds the maximum permitted size."}` |
| 415 | Content type is not JSON | `{"message":"Content-Type must be application/json."}` |
| 405 | HTTP method is not supported on a mapped route | `{"message":"HTTP method is not supported for this endpoint."}` |
| 503 | Selected AI provider is unconfigured or unavailable, rejects the key, rate limits the request, times out, or returns invalid/empty analysis | `{"message":"AI analysis is temporarily unavailable. Please try again later."}` |
| 500 | Unexpected internal error, e.g. search engine/dataset runtime failure | `{"message":"An unexpected server error occurred."}` |

Provider errors are logged by failure category and returned as a generic 503
message; upstream response bodies, API keys, and provider diagnostics are not
included in the public response. Smart Search does not pretend AI succeeded
or silently fall back. Standard Search does not call the provider.

An unmapped URL is not explicitly given a 404 handler in
`GlobalExceptionHandler`; Spring's routing/resource exception may flow to its
generic exception handler. Do not rely on a custom 404 JSON body from this
code as written.

## Postman

For either endpoint:

1. Select **POST**.
2. Set URL to `http://localhost:8080/api/incidents/search` or
   `http://localhost:8080/api/incidents/smart-search`.
3. Under **Headers**, set `Content-Type` to `application/json`.
4. Under **Body → raw → JSON**, paste:

```json
{
  "description": "Application APIs are taking too much time to respond due to slow database queries."
}
```

5. Send the request and inspect the HTTP status and JSON body. Smart Search
   returns HTTP 503 if it cannot produce valid AI analysis; Standard Search
   continues to work independently.

## PowerShell testing examples

```powershell
$body = @{
  description = "Application APIs are taking too much time to respond due to slow database queries."
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://localhost:8080/api/incidents/search" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body

Invoke-RestMethod `
  -Uri "http://localhost:8080/api/incidents/smart-search" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body
```

Blank-input example:

```powershell
$body = @{ description = "   " } | ConvertTo-Json
Invoke-WebRequest -Uri "http://localhost:8080/api/incidents/search" `
  -Method Post -ContentType "application/json" -Body $body
```

## Endpoint inventory and non-endpoints

The inspected controller implements only the two POST routes above. There is
no search-history GET endpoint, CRUD API, health/metrics API, authentication
endpoint, or pagination API in the current controller source.
