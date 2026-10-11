# League Platform

Custom-league manager for mobile football communities: leagues, rosters, fixtures, score reporting with
opponent confirmation, standings and admin overrides with an audit log.

| Part     | Stack                                                                 |
|----------|-----------------------------------------------------------------------|
| Frontend | React 19, Vite, TypeScript, TanStack Query, vite-plugin-pwa           |
| Backend  | Spring Boot 4.1, Java 21, Spring Security, JPA, Flyway, Spring Modulith |
| Database | PostgreSQL 17 (Docker)                                                |

## Prerequisites

- Docker Desktop (running)
- Java 21 (`JAVA_HOME` must point to it)
- Node 22 (`nvm use 22`; see `.nvmrc`)

## Run everything in Docker

```bash
docker compose up --build
```

Open http://localhost:3000. The API is also exposed on http://localhost:8080.

## Develop locally (hot reload)

```bash
# 1. Database only
docker compose up -d db

# 2. Backend (http://localhost:8080) — Flyway creates the tables on startup
cd backend
./mvnw spring-boot:run

# 3. Frontend (http://localhost:5173) — proxies /api to the backend
cd frontend
npm install
npm run dev
```

## Tests

```bash
cd backend && ./mvnw test      # needs Docker running (Testcontainers starts a throwaway Postgres)
cd frontend && npm run build && npm run lint
```

## Project layout

```
backend/   Spring Boot app, one package per module (league, shared, …)
frontend/  React PWA, one folder per feature under src/features
docker-compose.yml
```

Database changes go in `backend/src/main/resources/db/migration` as new Flyway files (`V2__...sql`);
never edit a migration that has already run.

## Useful endpoints

- `GET /api/public/status` — app version and database time
- `GET /api/public/leagues` — public leagues
- `GET /actuator/health` — health check
