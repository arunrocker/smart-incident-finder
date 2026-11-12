# Interview Preparation

These sample answers describe the current implementation. If an interviewer
asks about a missing production capability, identify it honestly as a
potential improvement rather than claiming it already exists.

## Two-minute project introduction

> I built a Smart Incident Resolution Finder with an Angular standalone
> frontend and a Java 21 Spring Boot backend. An operator enters an incident
> description and can run either standard search or smart search. Standard
> search preprocesses the text, creates TF-IDF vectors against a JSON corpus
> of resolved incidents, ranks candidates using cosine similarity, applies a
> configurable minimum score, and returns up to three results. Each result
> includes its ID, title, score, and the resolution stored in the corpus.
>
> Smart search reuses that same historical search first, then sends the user
> report and selected historic context to Claude for possible causes,
> explanation, troubleshooting, prevention, and recommendations. Claude does
> not generate the historic IDs or resolutions; those stay in the Java
> response. If Claude is unavailable, the backend still returns the historic
> results without AI analysis. The frontend shows the two sources separately.
> I tested the backend with JUnit 5 and Mockito and the Angular UI with
> Vitest and Angular's HTTP testing utilities.

## Architecture explanation

**Question: How is the application structured?**

> The Angular component owns the user interaction and view state. Its service
> calls the REST API through HttpClient. On the backend, a Spring MVC
> controller validates requests and delegates to services. IncidentService
> owns the historic corpus and calls the TF-IDF engine. SmartIncidentSearchService
> composes that historic result with ClaudeApiClient output. A centralized
> controller advice maps failures to HTTP responses.

**Question: Why keep historic results separate from AI guidance?**

> The historic resolution is evidence from the application's dataset, while
> Claude's analysis is generated guidance. They have different reliability
> and provenance, so the API contract and UI preserve that distinction. Java
> creates and retains the historic result DTOs; Claude's DTO only contains
> analysis fields.

## Standard Search

**Question: What happens on a standard search?**

> Angular validates the description and posts JSON to
> `/api/incidents/search`. The controller validates the request, the service
> searches the loaded incident list, and the TF-IDF engine ranks it. The
> service returns up to three candidates over the configured 10% threshold.
> The UI renders their titles, similarity percentages, and stored
> resolutions. Claude is not involved.

**Question: Why use a similarity threshold?**

> Ranking alone always produces a best candidate, even if it is unrelated.
> The minimum threshold allows the backend to return an explicit no-match
> result when none of the candidates is sufficiently similar.

## TF-IDF and cosine similarity

**Question: What does TF-IDF contribute?**

> Term frequency gives weight to terms that occur in a document. Inverse
> document frequency reduces the weight of terms common across many incidents.
> Multiplying them creates a vector representation where more informative
> shared terms affect the comparison more strongly.

**Question: How do you compare a query with an incident?**

> The engine uses cosine similarity between the query vector and each
> incident vector. The cosine normalizes the dot product by both vector
> lengths, so it measures the direction of the vectors rather than their raw
> magnitude. The score is converted to a percentage, rounded to one decimal
> place, then thresholded and ranked.

**Question: What are the preprocessing limitations?**

> The current engine lowercases, splits on non-ASCII-alphanumeric separators,
> and does not implement stop-word removal, stemming, or semantic embeddings.
> Unknown query terms have no corresponding vocabulary dimensions. If the
> query vector is zero, it returns no results. A future version could evaluate
> language-aware tokenization or embeddings, but that would change the
> algorithm and require new tests.

## Smart Search and AI providers

**Question: How does the AI integration work?**

> The backend first obtains the normal historic results. It sends the report
> and up to three Java-ranked result DTOs to Groq, with an instruction to
> return a specific JSON analysis shape. RestClient sends the request using
> the configured API key. Jackson parses and validates the five analysis
> fields. Claude remains an alternative provider.

**Question: What if the AI provider is down or returns invalid data?**

> The client categorizes the failure and the centralized exception handler
> returns HTTP 503 with a generic message. Standard Search never calls AI, so
> provider outages do not block TF-IDF search. There is no retry or circuit
> breaker currently.

**Question: How do you prevent hallucinations?**

> The prompt tells the model not to invent historic facts, but a prompt is not a
> hard guarantee. The stronger boundary is in code: the AI response schema
> has only analysis fields. The final IDs, scores, titles, and resolutions
> remain the Java-generated historic results.

**Question: Where are API credentials stored?**

> The checked-in YAML contains placeholders only. Spring configuration
> binding allows deployment properties or environment variables to override
> the key and model. A production deployment should use a secret manager or
> protected environment injection and never commit credentials.

## Spring Boot and API design

**Question: Why use DTOs?**

> Request and response DTOs define a stable JSON contract and keep transport
> concerns separate from the dataset and search implementation. The optional
> `aiAnalysis` property extends the response without replacing the existing
> historic result fields.

**Question: How is input validation handled?**

> The request DTO uses Jakarta validation for blank descriptions. The
> controller and service enforce the maximum description size. A filter
> enforces the request-body byte limit before controller processing.

**Question: How are errors returned?**

> A `RestControllerAdvice` maps validation and parsing problems to client
> errors and unexpected exceptions to a generic 500 response. The body uses
> an error DTO rather than exposing internal exception details.

## Angular

**Question: How do the buttons share the same input but call different APIs?**

> Both buttons invoke the same component search flow with a mode parameter.
> The component uses the same description signal and delegates to one of two
> service methods. The service shares the request and response-validation
> pipeline but selects the appropriate relative URL.

**Question: How do you prevent stale or duplicate requests?**

> While a request is active, both buttons are disabled, and the component
> returns early if loading is already true. Starting a new search clears old
> results and errors. `finalize()` resets loading for success and failure.

**Question: How does local cross-origin development work?**

> The Angular development server proxies `/api` to port 8080, so the browser
> uses the frontend origin. The backend does not configure CORS; production
> needs routing or an explicit CORS policy.

## Testing

**Question: How do you test AI without spending credits?**

> The AI clients are exercised through mock HTTP responses and service
> collaborators. The tests cover request/response parsing and failure
> categories without sending a real provider request.

**Question: What do the Angular tests cover?**

> The component tests provide `HttpTestingController` responses and verify
> endpoint selection, successful and empty results, validation, loading,
> duplicate prevention, errors, AI-unavailable states, and clearing state. They don't
> start a real backend, so they are not end-to-end network tests.

## Trade-offs and realistic follow-ups

- **Why a JSON dataset rather than a database?** It keeps the small reference
  corpus simple to load and package. A database becomes useful for frequent
  updates, larger datasets, and operational management.
- **Why no semantic embeddings?** TF-IDF is explainable and local; embeddings
  could improve paraphrase matching but add model/runtime or service costs.
- **Why synchronous Claude calls?** The UI is request/response-oriented and
  the initial implementation is simple. A longer-term design could use
  asynchronous jobs and polling for long AI analysis.
- **What would you improve for production?** Add health and metrics exposure,
  persistent observability, retries with bounded policy/circuit breaking,
  response correlation IDs, explicit 404 handling, deployment CORS/routing,
  secret-manager integration, and load/timeout testing.
- **What is an important current timeout trade-off?** Angular times out after
  15 seconds while the backend Claude timeout is 30 seconds. The browser may
  show a timeout while server-side work is still in progress; the values
  should be coordinated based on measured latency.
- **Is there a production container setup?** No Docker or orchestration
  configuration is included in the inspected project.
