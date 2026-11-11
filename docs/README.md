# Smart Incident Resolution Finder Documentation

This documentation is based on the checked-in Angular frontend and Spring
Boot backend. It describes current behavior and calls out known gaps rather
than treating possible improvements as implemented features.

## Recommended reading order

1. [Project architecture](PROJECT_ARCHITECTURE.md) — system boundaries and
   component/package relationships.
2. [End-to-end code flow](END_TO_END_CODE_FLOW.md) — standard and smart
   requests, from click through response rendering.
3. [Backend code walkthrough](BACKEND_CODE_WALKTHROUGH.md) — important Java
   classes and their call relationships.
4. [Angular frontend walkthrough](ANGULAR_FRONTEND_WALKTHROUGH.md) — UI
   state, service, models, and rendering.
5. [TF-IDF and cosine similarity](TFIDF_AND_COSINE_SIMILARITY.md) — the
   historical retrieval algorithm and its implementation details.
6. [Claude AI integration](CLAUDE_AI_INTEGRATION.md) — configuration,
   request construction, response parsing, and fallback.
7. [API documentation](API_DOCUMENTATION.md) — endpoint contracts and
   request examples.
8. [Exception and failure handling](EXCEPTION_AND_FAILURE_HANDLING.md) —
   validation, upstream failures, and current limitations.
9. [Testing guide](TESTING_GUIDE.md) — test suites, coverage intent, and run
   commands.
10. [Setup and deployment](SETUP_AND_DEPLOYMENT.md) — local environment and
    build/start commands.
11. [Interview preparation](INTERVIEW_PREPARATION.md) — concise,
    implementation-grounded explanations and follow-up questions.

## All documents

- [PROJECT_ARCHITECTURE.md](PROJECT_ARCHITECTURE.md)
- [END_TO_END_CODE_FLOW.md](END_TO_END_CODE_FLOW.md)
- [BACKEND_CODE_WALKTHROUGH.md](BACKEND_CODE_WALKTHROUGH.md)
- [TFIDF_AND_COSINE_SIMILARITY.md](TFIDF_AND_COSINE_SIMILARITY.md)
- [CLAUDE_AI_INTEGRATION.md](CLAUDE_AI_INTEGRATION.md)
- [API_DOCUMENTATION.md](API_DOCUMENTATION.md)
- [EXCEPTION_AND_FAILURE_HANDLING.md](EXCEPTION_AND_FAILURE_HANDLING.md)
- [ANGULAR_FRONTEND_WALKTHROUGH.md](ANGULAR_FRONTEND_WALKTHROUGH.md)
- [TESTING_GUIDE.md](TESTING_GUIDE.md)
- [SETUP_AND_DEPLOYMENT.md](SETUP_AND_DEPLOYMENT.md)
- [INTERVIEW_PREPARATION.md](INTERVIEW_PREPARATION.md)

## Implementation gaps and cautions

- Claude API key/model are `xxxxx` placeholders. Without externally supplied
  valid values, smart search returns historical results without `aiAnalysis`.
- The existing checked-in API sample/help material documents standard search
  only and includes stale dataset details; this docs set describes both
  routes and the current dataset.
- The backend has no CORS policy; local browser access relies on the Angular
  development proxy. Production routing/CORS must be configured separately.
- There is no database, Docker setup, auth, public metrics endpoint, Claude
  retry/circuit breaker, or purpose-built not-found response in the inspected
  implementation.
- Angular's 15-second request timeout is shorter than the backend Claude
  timeout of 30 seconds.
- The frontend production build was successful during implementation but
  emitted a component CSS budget warning (above 4 KB warning and below 8 KB
  error limit).

See the linked walkthroughs for details and source paths.
