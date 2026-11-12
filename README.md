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

## Smart Search AI provider

Groq is selected by default and uses `openai/gpt-oss-120b` for structured
incident analysis. Set `GROQ_API_KEY` in the environment. Claude and Gemini
remain available by changing `app.ai.provider` and supplying their respective
environment-variable keys.

```powershell
$env:GROQ_API_KEY = '<your Groq API key>'
```

If AI is unavailable or unconfigured, only Smart Search returns HTTP 503;
Standard Search remains available. Changing the provider requires an
application restart; Angular and the REST response DTO are provider-neutral. See
[`docs/SETUP_AND_DEPLOYMENT.md`](docs/SETUP_AND_DEPLOYMENT.md) for details.

See [`docs/README.md`](docs/README.md) for the documentation index and
recommended reading order.

## Deploy on Render

This repository includes a Render Blueprint for a free, single-service Docker
deployment. See [`docs/SETUP_AND_DEPLOYMENT.md`](docs/SETUP_AND_DEPLOYMENT.md)
for setup steps and free-tier limitations.
