# Smart Incident Resolution Finder

Angular 22 frontend for searching the Spring Boot incident-resolution API.

## Run locally

Start the backend from its project directory in one terminal:

```powershell
cd C:\Users\gbala\smart-incident-finder
.\mvnw.cmd spring-boot:run
```

Start the frontend from its project directory in a second terminal:

```powershell
cd C:\Users\gbala\smart-incident-finder-angular\frontend
npm start
```

Open [http://localhost:4200](http://localhost:4200). During local development,
the Angular dev server proxies `/api` requests to `http://localhost:8080`, so
the browser and Spring Boot API communicate through the same origin. The
backend must be running for incident searches to return recommendations.

## API

The frontend posts `{ "description": "..." }` to
`/api/incidents/search` and displays the backend response. Request/response
interfaces and runtime response validation are in `src/app/models` and
`src/app/services`.

## Build and tests

```powershell
npm run build
npm test -- --watch=false
```
