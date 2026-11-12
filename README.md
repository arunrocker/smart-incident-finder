# Smart Incident Resolution Finder

Full-stack incident search application with an Angular frontend and a Spring
Boot backend.

## Repository layout

- [`front-end/`](front-end/) — Angular application.
- [`back-end/`](back-end/) — Java 21 / Spring Boot REST API.
- [`docs/`](docs/) — architecture, code-flow, API, algorithm, testing, and
  setup documentation.

## Run locally

Start the backend in one terminal:

```powershell
Set-Location .\back-end
.\mvnw.cmd spring-boot:run
```

Start the frontend in another terminal:

```powershell
Set-Location .\front-end
npm ci
npm start
```

Open http://localhost:4200. The Angular development proxy forwards `/api`
requests to the backend at `http://localhost:8080`.

## Choose the Smart Search AI provider

Set `app.ai.provider` in `back-end/src/main/resources/application.yaml` to
`claude` or `gemini`. The selected provider's API key is supplied separately
through `CLAUDE_API_KEY` or `GEMINI_API_KEY`. For example, in PowerShell:

```powershell
$env:CLAUDE_API_KEY = '<your Claude API key>'
$env:GEMINI_API_KEY = '<your Gemini API key>'
```

Only the selected provider client is created. The application fails during
startup if the provider name is unsupported or the selected provider's key is
missing. Changing provider requires only configuration and an application
restart; Angular and the REST response DTO are provider-neutral. See
[`docs/SETUP_AND_DEPLOYMENT.md`](docs/SETUP_AND_DEPLOYMENT.md) for details.

See [`docs/README.md`](docs/README.md) for the documentation index and
recommended reading order.

## Deploy on Render

This repository includes a Render Blueprint for a free, single-service Docker
deployment. See [`docs/SETUP_AND_DEPLOYMENT.md`](docs/SETUP_AND_DEPLOYMENT.md)
for setup steps and free-tier limitations.
