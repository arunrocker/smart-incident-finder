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

## Claude API failures

`ClaudeApiClient` wraps upstream issues in `ClaudeApiException` with a
`FailureType`. It classifies:

- 401/403 as authentication failure;
- 429 as rate limited;
- 408/504 and socket timeouts as timeout;
- malformed, unreadable, incomplete, or structurally invalid model output as
  invalid response;
- other HTTP or connection issues as API failure; and
- blank or `xxxxx` API key/model/url as not configured.

The configured 30-second connect/read timeout is set on the request factory.
`SmartIncidentSearchService.search()` catches the typed exception, logs its
category, and returns the already-generated historical response without
`aiAnalysis`. It does not retry, back off, or call Claude for the standard
endpoint. This is the core isolation boundary that keeps normal search
available when the external AI provider is down.

The public smart-search response does not currently indicate *why* analysis
is missing. The frontend can show an AI-unavailable notice but cannot
differentiate authentication from rate limiting from that payload.

## Logging and counters

The code uses Spring's SLF4J logging:

- accepted searches log description length;
- the search service logs result count;
- invalid and oversized requests log rejection reasons;
- unexpected errors log the full server-side exception;
- smart-search Claude failures log the failure category.

The log statements do not intentionally include the description text or
Claude API key.

`SearchRequestMetrics` records atomic in-memory counts for total requests,
successful searches, no-match searches, invalid requests, and unexpected
errors. The class exposes `snapshot()` for Java callers, but no HTTP endpoint
or Actuator integration exposes it. Claude fallback is recorded as a
successful request because the application returns a successful historic
response.

## Practical scenarios

| Scenario | What happens |
|---|---|
| User submits whitespace | `@NotBlank` returns HTTP 400; Angular normally catches this before sending. |
| Description is 5001 chars | Controller/service reject with HTTP 400. |
| Body exceeds 32768 bytes | Filter returns HTTP 413. |
| `incidents.json` is malformed | Startup fails; the server does not run with an invalid/partial corpus. |
| Search engine throws unexpectedly | Generic handler returns HTTP 500; internal details are only logged. |
| Claude key is still `xxxxx` | Claude client does not make an outbound request; smart search returns historic results only. |
| Claude responds 401, 429, 503, or times out | Typed failure is caught by smart service; historical response is returned without AI. A 503 is classified as generic `API_FAILURE`, not a special enum category. |
| Claude returns malformed JSON | Client classifies it as `INVALID_RESPONSE`; the smart service falls back to history. |
| Standard Search is clicked while Claude is unavailable | Claude is never called; normal TF-IDF behavior is unaffected. |
| Browser times out before a slow smart response | Angular's 15-second RxJS timeout may fire before backend's 30-second timeout; browser shows an error while the server-side Claude call may still finish. |

## What the application does not implement

There is no retry/circuit-breaker policy, persistent metrics, alerting,
public Claude-status field, special 503 response, custom 404 body, or request
correlation ID in the inspected code. Those are possible future improvements,
not current behavior.
