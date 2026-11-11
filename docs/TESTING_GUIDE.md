# Testing Guide

The backend tests use JUnit 5 and Mockito through Spring Boot's
`spring-boot-starter-test`. The frontend uses Angular's unit-test builder with
Vitest and JSDOM. Claude network requests are mocked in tests; running the
test suites does not require Anthropic credentials or use API credits.

## Run the suites

Backend, from `C:\Users\gbala\smart-incident-finder`:

```powershell
.\mvnw.cmd clean test
```

Frontend, from `C:\Users\gbala\smart-incident-finder-angular\frontend`:

```powershell
npm test
```

The frontend test command is `ng test`. For a frontend production compile,
run `npm run build`.

## Backend test classes

| Test file | What it verifies |
|---|---|
| `src/test/java/com/incident/finder/SmartIncidentFinderApplicationTests.java` | Application context/startup and top-level search wiring. |
| `src/test/java/com/incident/finder/controller/IncidentSearchControllerTests.java` | MVC request handling, both route responses, validation and HTTP error shapes. Controller dependencies are isolated using Spring test mocks. |
| `src/test/java/com/incident/finder/engine/TfidfSimilarityEngineTests.java` | Tokenization/vector similarity, ranking, thresholding, limits, and edge cases such as unrelated vocabulary. |
| `src/test/java/com/incident/finder/service/IncidentServiceTests.java` | Dataset-backed historical search behavior and service validation/result selection. |
| `src/test/java/com/incident/finder/service/ClaudeApiClientTests.java` | Claude request construction, response parsing, configuration checks, invalid responses and upstream error classification without live HTTP. |
| `src/test/java/com/incident/finder/service/SmartIncidentSearchServiceTests.java` | Standard historic results are preserved, AI analysis is added on success, and Claude failure falls back to history. |

`@SpringBootTest` is used for application context coverage; MVC behavior is
tested as a controller slice. Service and engine tests isolate their target
logic. The suite does not require a live Claude service. The test sources
should be consulted for the specific examples and assertions; test method
names can evolve independently of this guide.

### Why Claude is mocked

Claude tests use a mock HTTP request factory / mocked client response (per
the test setup) rather than calling Anthropic. This makes them repeatable,
fast, deterministic, and free of API charges. They can test headers, payload
shape, parser behavior, timeout/error mapping, and fallback without requiring
credentials.

## Frontend test class

`frontend/src/app/components/incident-search/incident-search.component.spec.ts`
configures `provideHttpClient()` and `provideHttpClientTesting()` and uses
`HttpTestingController` to assert requests and provide controlled responses.
It covers:

- empty input validation without an HTTP request;
- standard search endpoint and rendering up to three historic results;
- smart endpoint and separation of historic resolutions from AI suggestions;
- AI-unavailable fallback display;
- no-match display;
- HTTP 400 and HTTP 500 messages;
- proxy/network unavailable errors;
- loading state and prevention of duplicate searches;
- Clear behavior, including AI state;
- empty or malformed successful responses without fake recommendations.

`frontend/src/app/app.spec.ts` verifies the root application can be created.

These are component/service interaction tests against Angular's HTTP testing
backend. They do not start a Spring Boot process and therefore are not a
live cross-application integration test.

## Important edge cases represented

- Unknown query terms produce no historic matches rather than arbitrary
  recommendations.
- Empty and malformed API payloads are not rendered as success data.
- Claude failure must not discard the already-computed historical matches.
- A standard search must not depend on Claude configuration or availability.
- Only the configured number of historic results should be shown.
- UI loading state must always clear on success and failure.

## Previously verified results

During implementation, `.\mvnw.cmd clean test` completed with **38 tests,
0 failures, 0 errors**. `npm test` completed with **14 tests passing**. The
Angular production build succeeded with a component CSS budget warning; see
the frontend walkthrough and setup notes. These are recorded execution
results, not a promise that tests have been rerun at the time you read this.
