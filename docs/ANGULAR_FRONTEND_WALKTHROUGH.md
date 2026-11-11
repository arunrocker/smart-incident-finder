# Angular Frontend Walkthrough

The frontend is an Angular 22 standalone application in
`smart-incident-finder-angular/frontend`. It uses Angular signals, router,
HttpClient, RxJS, Vitest, and plain CSS. Bootstrap and PrimeNG are not
installed or used in the current `package.json`.

## Bootstrap and routing

`src/main.ts` calls `bootstrapApplication(App, appConfig)`.
`src/app/app.config.ts` provides:

- `provideBrowserGlobalErrorListeners()`;
- `provideZoneChangeDetection({ eventCoalescing: true })`;
- `provideHttpClient()`; and
- `provideRouter(routes)`.

`src/app/app.routes.ts` maps `/` to `IncidentSearchComponent` and redirects
unknown routes back to the root. The root `App` shell uses a `RouterOutlet`;
it does not own search state.

## Search component

`src/app/components/incident-search/incident-search.component.ts` is the
standalone page component. It imports `DecimalPipe` for similarity display
and injects `IncidentService`.

Signals used by the page:

| Signal | Purpose |
|---|---|
| `description` | Shared text-area input for both search modes. |
| `validationMessage` | Empty/too-long user input validation. |
| `errorMessage` | Backend/network/timeout errors. |
| `responseMessage` | Backend match/no-match message. |
| `results` | Historical incident DTOs to render. |
| `aiAnalysis` | Optional AI response data for smart mode. |
| `searchMode` | Tracks `standard` or `smart` for loading feedback and conditional AI display. |
| `loading` | Disables both search buttons and Clear; shows loading UI. |
| `hasSearched` | Controls results and no-match states. |

`updateDescription(event)` updates the shared description and clears the
validation message. `search(mode)` trims and validates the input, clears
stale response state, chooses `IncidentService.search()` or
`IncidentService.smartSearch()`, and subscribes. It applies `finalize()` so
loading is reset on either completion or error. `clear()` resets all state.
`similarityWidth()` clamps meter width to 0–100.

The template is
`src/app/components/incident-search/incident-search.component.html`.
It contains one labeled textarea, Standard Search and Smart Search buttons,
Clear, loading/error/no-match states, historic incident cards, and the
conditional AI section. The styles are in
`incident-search.component.css`; shared AI additions are in `src/styles.css`.

## Models and service

`src/app/models/incident.model.ts` defines:

- `IncidentSearchRequest`;
- `IncidentSearchResult`;
- `IncidentSearchResponse`, whose `aiAnalysis` is optional;
- `AiAnalysis`, with the five backend analysis fields; and
- `ApiErrorResponse`.

`src/app/services/incident.service.ts` injects Angular `HttpClient`.
`search(description)` posts to `/api/incidents/search`;
`smartSearch(description)` posts to `/api/incidents/smart-search`.
Both call a private shared `searchAt()` method so timeout, request shape,
response checks, and error mapping remain consistent.

RxJS operators:

- `timeout({ first: 15000 })` bounds the frontend wait;
- `map()` validates that the body has a message, results array, valid result
  fields, and—when present—a well-shaped `aiAnalysis`;
- `catchError()` converts timeouts and `HttpErrorResponse`s to readable
  `IncidentSearchError`s; and
- the component's `finalize()` resets `loading`.

The integration uses `environment.apiBaseUrl` (`/api`) rather than a component
hard-coded backend host. `proxy.conf.json` forwards local development requests
to `http://localhost:8080`.

## Standard Search flow

The button passes `standard` to `search()`. `IncidentService.search()` sends
the shared description. On success, the component stores the message and at
most three historic results. The template renders each result's ID, title,
similarity meter, and resolution, with “Verified Historical Resolutions” and
“Verified Resolution” labels. No AI section is shown for standard mode.

## Smart Search flow

The button passes `smart` to the same `search()` method. The service posts to
the smart-search endpoint. The component stores the same historic results and
the optional `response.aiAnalysis`.

When analysis is present, the template renders a separate
“AI-Generated Guidance” panel with:

- possible root causes;
- technical explanation;
- troubleshooting steps;
- preventive measures; and
- additional recommendations.

The panel warns that suggestions are AI-generated and should be validated.
When the backend returns successful historic results without `aiAnalysis`,
the UI shows “AI analysis unavailable” and retains historic cards.

## Loading, validation, and error states

Both buttons are disabled while `loading()` is true, as is Clear. Only the
active button changes its label to Searching or Analyzing; an overall loading
card explains the operation. Empty text and descriptions over 5000 characters
are rejected client-side. Pressing Ctrl+Enter or Cmd+Enter starts Standard
Search.

The service maps status 0 to backend/network unavailable, 400 to validation,
404 to endpoint not found, 413 to oversized request, 500 to internal error,
502–504 to unavailable, and a 15-second RxJS timeout to a timeout message.
The backend's Claude failures normally do not reach this error handler:
the backend returns historic results successfully and omits optional analysis.

## Frontend tests and current limits

`incident-search.component.spec.ts` uses Angular's
`provideHttpClientTesting()` and `HttpTestingController`; it covers standard
and smart endpoint routing, rendering, loading, clear, no matches, error
responses, and AI-unavailable behavior. `app.spec.ts` covers the root app.
The current production build succeeds, but Angular emits an existing
component-style budget warning: the component CSS is close to the configured
8 KB error budget and above the 4 KB warning budget.
