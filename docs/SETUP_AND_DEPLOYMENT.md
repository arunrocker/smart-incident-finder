# Setup and Deployment

## Project layout

The applications are sibling folders, not a single Maven/Angular monorepo:

```text
C:\Users\gbala\
├── smart-incident-finder\                 # Spring Boot backend
└── smart-incident-finder-angular\
    ├── frontend\                           # Angular application
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
Set-Location C:\Users\gbala\smart-incident-finder-angular\frontend
npm ci
```

`npm ci` uses the committed lock file to install the declared dependency set.

## Start the backend

From a PowerShell terminal:

```powershell
Set-Location C:\Users\gbala\smart-incident-finder
.\mvnw.cmd spring-boot:run
```

The API listens on port **8080** by default. It loads the historical incident
corpus from `src/main/resources/incidents.json` during application startup.
An invalid or missing dataset prevents startup.

## Start the Angular application

In a second terminal:

```powershell
Set-Location C:\Users\gbala\smart-incident-finder-angular\frontend
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

## Claude configuration

`src/main/resources/application.yaml` uses `xxxxx` placeholders for the
Claude API key and model. Keep real credentials out of source control. Spring
Boot property binding permits external overrides. For example:

```powershell
$env:CLAUDE_API_APIKEY = '<your Anthropic API key>'
$env:CLAUDE_API_MODEL = '<model available to your account>'
Set-Location C:\Users\gbala\smart-incident-finder
.\mvnw.cmd spring-boot:run
```

Other bound keys include URL, API version, timeout, and max tokens. The
defaults are in `application.yaml`: Anthropic Messages URL,
`2023-06-01`, 30 seconds, and 1200 tokens. With placeholders unchanged,
standard search works and smart search returns historic results without
`aiAnalysis`; no fake analysis is substituted.

`application.properties` sets the 10.0% minimum similarity, 5000-character
description limit, and 32768-byte request limit. External Spring property
overrides can change these values.

## Build and test

Backend:

```powershell
Set-Location C:\Users\gbala\smart-incident-finder
.\mvnw.cmd clean test
.\mvnw.cmd package
```

Frontend:

```powershell
Set-Location C:\Users\gbala\smart-incident-finder-angular\frontend
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

No database, Dockerfile/Compose configuration, Kubernetes manifests,
authentication setup, or production hosting recipe is present in the
inspected project. The incidents are loaded from a JSON classpath resource.
Do not treat the dev proxy or placeholder Claude configuration as a
production deployment configuration.
