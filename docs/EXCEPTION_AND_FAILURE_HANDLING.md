# Exception and Failure Handling

## Request-side validation

`IncidentSearchRequest.description` has Jakarta `@NotBlank`. Spring MVC
performs that validation in both controller methods because they accept
`@Valid @RequestBody`. The controller additionally checks the configured
5000-character maximum before calling a service. `IncidentService` repeats
blank and length checks so the domain service remains safe when invoked
outside the HTTP controller.

The shared custom `InvalidIncidentSearchException` is translated to HTTP 400
by `GlobalExceptionHandler.handleInvalidSearch`. Bean-validation failures
also return 400 with a field validation message.

## Request-size limit

`RequestSizeLimitFilter` runs before controller binding. It compares a known
content length against `incident.search.maximum-request-size-bytes` (32768 by
default), and wraps the stream to count bytes when the length is unknown.
Known overflow is written directly as HTTP 413 JSON. A streamed overflow
throws `RequestBodyTooLargeException`; the exception advice searches its
cause chain when Spring reports an unreadable request.

The 5000-character description limit and 32768-byte JSON body limit are
separate checks: JSON encoding overhead and Unicode can make the byte count
differ from character count.

## Dataset loading

`IncidentService.loadIncidents()` reads `classpath:incidents.json` once at
startup. It validates a flat list of 10–100 records, positive unique IDs,
and nonblank title, description, and resolution. A missing file, malformed
JSON, duplicate ID, invalid field, or count outside the accepted range is
logged and rethrown as `IllegalStateException`. This intentionally fails
application startup instead of silently serving partial data.

The current dataset is a flat list of 95 records. Dataset failures are
startup failures, not ordinary per-request fallbacks.

## Centralized backend handling

`GlobalExceptionHandler` uses `@RestControllerAdvice` and returns
`ApiErrorResponse(message)`:

- malformed JSON → HTTP 400 with a consistent message;
- request DTO validation or custom description validation → HTTP 400;
- request-size exception → HTTP 413;
- unsupported content type → HTTP 415;
- unsupported method on a mapped path → HTTP 405;
- unexpected application exception → HTTP 500 and a generic public message.

The generic handler logs the exception and records the unexpected-error
counter. Internal exception text is not returned in the API body.

There is a noteworthy gap: no dedicated not-found handler exists. An
unmapped path's Spring exception may reach the generic handler, which can
return 500 rather than a purpose-built 404 response.

## AI provider failures

The selected AI client wraps upstream issues in `AiProviderException` with a
`FailureType`. Groq, Gemini, and Claude clients classify:

- 401/403 as authentication failure;
- 429 as rate limited;
- 408/504 and socket timeouts as timeout;
- malformed, unreadable, incomplete, or structurally invalid model output as
  invalid response;
- other HTTP or connection issues as API failure; and
- blank or `xxxxx` API key/model/url as not configured.

The configured 30-second connect/read timeout is set on the request factory.
`GlobalExceptionHandler` maps typed failures to HTTP 503 with a generic
message. It logs only the failure category, not provider response details or
credentials. Smart Search does not retry, back off, or return an
AI-less success-shaped response.

Missing AI credentials do not prevent application startup. Standard Search
continues to use TF-IDF without calling the provider, even if the AI service
is unavailable or unconfigured.

## Logging and counters

The code uses Spring's SLF4J logging:

- accepted searches log description length;
- the search service logs result count;
- invalid and oversized requests log rejection reasons;
- unexpected errors log the full server-side exception;
- AI provider failures log only the failure category.

The log statements do not intentionally include the description text or API
key.

`SearchRequestMetrics` records atomic in-memory counts for total requests,
successful searches, no-match searches, invalid requests, and unexpected
errors. The class exposes `snapshot()` for Java callers, but no HTTP endpoint
or Actuator integration exposes it.

## Practical scenarios

| Scenario | What happens |
|---|---|
| User submits whitespace | `@NotBlank` returns HTTP 400; Angular normally catches this before sending. |
| Description is 5001 chars | Controller/service reject with HTTP 400. |
| Body exceeds 32768 bytes | Filter returns HTTP 413. |
| `incidents.json` is malformed | Startup fails; the server does not run with an invalid/partial corpus. |
| Search engine throws unexpectedly | Generic handler returns HTTP 500; internal details are only logged. |
| Selected AI key is missing or invalid | Application startup and Standard Search remain available; Smart Search returns HTTP 503. |
| Provider responds 401/403, 429, 503, or times out | Smart Search returns HTTP 503 with a generic message; the public response does not expose provider details. |
| Provider returns malformed or empty JSON | The response is classified as invalid and Smart Search returns HTTP 503. |
| Standard Search is clicked while the AI provider is unavailable | No provider request is made; TF-IDF search continues independently. |
| Smart Search takes longer than usual | Angular allows up to 90 seconds; the selected AI request timeout is 30 seconds. |

## What the application does not implement

There is no retry/circuit-breaker policy, persistent metrics, alerting,
provider-specific public error detail, custom 404 body, or request
correlation ID. Those are possible future improvements, not current behavior.
