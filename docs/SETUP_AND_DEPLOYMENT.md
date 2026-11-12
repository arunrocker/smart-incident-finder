# Setup and Deployment

## Repository layout

The GitHub repository keeps both applications together:

```text
smart-incident-finder\
├── back-end\
├── front-end\
└── docs\
```

## Required software

- Java 21 JDK (the backend sets `java.version` to 21).
- Node.js supported by the installed Angular CLI and npm 11. The frontend
  manifest pins the package manager metadata to npm 11.20.0.
- PowerShell on Windows, or equivalent shell commands on another OS.
- Internet access for the first dependency download, unless dependencies are
  already cached.

Maven is provided through the backend Maven Wrapper, so a separate Maven
installation is not needed.

## Install dependencies

Backend dependencies are resolved by the wrapper. Frontend dependencies:

```powershell
Set-Location .\front-end
npm ci
```

`npm ci` uses the committed lock file to install the declared dependency set.

## Start the backend

From a PowerShell terminal:

```powershell
Set-Location .\back-end
.\mvnw.cmd spring-boot:run
```

The API listens on port **8080** by default. It loads the historical incident
corpus from `src/main/resources/incidents.json` during application startup.
An invalid or missing dataset prevents startup.

## Start the Angular application

In a second terminal:

```powershell
Set-Location .\front-end
npm start
```

The Angular development server listens on **http://localhost:4200**. Its
development configuration loads `proxy.conf.json`, forwarding `/api` requests
to `http://localhost:8080`. Use the browser at
`http://localhost:4200`; the frontend calls relative `/api/...` URLs.

The proxy is a development-server feature. It is not a production reverse
proxy. A deployed frontend needs its host/reverse proxy configured to route
`/api` to the backend or needs an appropriate API base URL configuration.
The backend does not currently configure CORS. A direct browser call from a
different origin therefore requires deployment routing or a deliberate CORS
configuration.

## Select Claude or Gemini

`back-end/src/main/resources/application.yaml` selects the backend AI
integration:

```yaml
app:
  ai:
    provider: gemini # set to claude to select Claude instead
```

Change only `app.ai.provider` to `claude` or `gemini`, then restart the
backend. Both clients implement the same `AiAnalysisProvider` interface.
Spring creates only the selected conditional client; Angular continues using
the same smart-search endpoint and response DTO.

API credentials are not stored in YAML. Supply the two keys independently
through environment variables:

```powershell
$env:CLAUDE_API_KEY = '<your Claude API key>'
$env:GEMINI_API_KEY = '<your Gemini API key>'
Set-Location .\back-end
.\mvnw.cmd spring-boot:run
```

Only the selected provider's key is required; the inactive provider's key
may remain unset. Startup fails clearly when the provider is unsupported or
the selected API key and required settings are missing. The YAML holds model,
endpoint, timeout, and output-token settings. Claude uses Anthropic Messages;
Gemini uses `generateContent` with JSON response mode. Both map to the same
`AiAnalysis` response model. Runtime provider failures still fall back to
historical results without AI analysis.

## Deploy on Render (free tier)

The root `render.yaml` and `Dockerfile` deploy the Angular production build and
Spring Boot API together as one Docker web service. This keeps the frontend's
`/api` requests same-origin and avoids a separate CORS configuration.

1. Push the repository to GitHub and sign in to Render.
2. In Render, create a **Blueprint** and select this GitHub repository. Render
   reads `render.yaml` and builds the free web service.
3. Set `GEMINI_API_KEY` to a valid Gemini API key when prompted (or in the
   service's Environment settings). Keep the key in Render, not in source.
4. Wait for the deployment to finish, then open the service's `onrender.com`
   URL.

The application serves the Angular UI at `/` and its API at `/api`. Render's
free web services may spin down after inactivity, so the first request after a
quiet period can take longer. Free-tier limits and availability can change;
the Gemini API is a separate service and may have its own usage limits or
charges.

`application.properties` sets the 10.0% minimum similarity, 5000-character
description limit, and 32768-byte request limit. External Spring property
overrides can change these values.

## Build and test

Backend:

```powershell
Set-Location .\back-end
.\mvnw.cmd clean test
.\mvnw.cmd package
```

Frontend:

```powershell
Set-Location .\front-end
npm test
npm run build
```

The frontend's production output is generated under its Angular build output
directory (normally `dist/frontend/browser` with the current builder).

## Ports and environment-specific configuration

| Service | Default |
|---|---|
| Spring Boot API | `http://localhost:8080` |
| Angular dev server | `http://localhost:4200` |
| Angular local API proxy | `/api` → `http://localhost:8080` |

No database, Docker Compose configuration, Kubernetes manifests, or
authentication setup is present. The incidents are loaded from a JSON
classpath resource. Do not treat the dev proxy or placeholder Claude
configuration as a production deployment configuration.
