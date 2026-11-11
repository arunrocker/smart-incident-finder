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

The backend's `application.yaml` contains placeholders for the Claude API
key and model. Configure these securely outside source control to enable AI
analysis. Standard search works independently; smart search returns verified
historical matches without AI analysis when Claude is not configured.

See [`docs/README.md`](docs/README.md) for the documentation index and
recommended reading order.
