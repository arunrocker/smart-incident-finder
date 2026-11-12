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

## Smart Search AI provider

`back-end/src/main/resources/application.yaml` selects Groq and stores only
non-secret provider settings:

```yaml
app:
  ai:
    provider: groq
    groq:
      api-key: ${GROQ_API_KEY:}
      base-url: https://api.groq.com/openai/v1
      model: openai/gpt-oss-120b
      timeout: 30s
      max-completion-tokens: 1200
```

The selected Groq model is GPT-OSS 120B. Groq lists it as a high-capability
reasoning and coding model, and supports strict JSON Schema output for it.
Groq's API uses an OpenAI-compatible chat-completions endpoint. The provider
can also be changed to `gemini` or `claude`.

Create a Groq API key at [GroqCloud API Keys](https://console.groq.com/keys)
and provide it through the environment; never put the actual key in YAML or
source control:

```powershell
$env:GROQ_API_KEY = '<your Groq API key>'
Set-Location .\back-end
.\mvnw.cmd spring-boot:run
```

The environment variable overrides the empty YAML placeholder via Spring
configuration properties. Missing or invalid credentials do not prevent
application startup: Standard Search remains usable, while Smart Search returns
HTTP 503 if AI analysis cannot be completed. The Groq call requests strict JSON
Schema output, and the response is validated before it is returned.

Groq provides free API access with account/model-specific limits, so a paid
subscription is not required to try the free tier. Limits are applied at the
organization level and can change; check your GroqCloud limits dashboard.
Requests beyond available limits can receive HTTP 429. Groq's model/pricing
pages may also show paid usage rates, so review your account's free quota and
billing settings before deployment. See Groq's [API compatibility guide](https://console.groq.com/docs/openai),
[models](https://console.groq.com/docs/models),
[GPT-OSS 120B details](https://console.groq.com/docs/model/openai/gpt-oss-120b),
[structured outputs](https://console.groq.com/docs/structured-outputs),
[rate limits](https://console.groq.com/docs/rate-limits), and
[API keys](https://console.groq.com/keys).

## Deploy on Render (free tier)

The root `render.yaml` and `Dockerfile` deploy the Angular production build and
Spring Boot API together as one Docker web service. This keeps the frontend's
`/api` requests same-origin and avoids a separate CORS configuration.

1. Push the repository to GitHub and sign in to Render.
2. In Render, create a **Blueprint** and select this GitHub repository. Render
   reads `render.yaml` and builds the free web service.
3. Set `GROQ_API_KEY` to your Groq API key when prompted (or in the
   service's Environment settings). Keep the key in Render, not in source.
4. Wait for the deployment to finish, then open the service's `onrender.com`
   URL.

The application serves the Angular UI at `/` and its API at `/api`. Render's
free web services may spin down after inactivity, so the first request after a
quiet period can take longer. Free-tier limits and availability can change;
The Groq API is a separate service. Its free-tier eligibility and rate limits
are governed by Groq, not Render.

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
