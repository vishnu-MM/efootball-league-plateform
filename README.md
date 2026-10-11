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
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Frontend (http://localhost:5173) — proxies /api to the backend
cd frontend
npm install
npm run dev
```

## Sign-in

The backend uses session cookies (stored in Postgres via Spring Session) and CSRF protection
(the frontend echoes the `XSRF-TOKEN` cookie as the `X-XSRF-TOKEN` header).

| Profile   | What it enables                                                      |
|-----------|----------------------------------------------------------------------|
| `dev`     | Name-only test login (`POST /api/dev/login`). **Never in production.** |
| `google`  | "Continue with Google" — needs `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` |
| `discord` | "Continue with Discord" — needs `DISCORD_CLIENT_ID` / `DISCORD_CLIENT_SECRET` |

Docker Compose uses `dev` by default. To add a real provider, copy `.env.example` to `.env`, fill in the
credentials and set e.g. `SPRING_PROFILES_ACTIVE=dev,google`. Register these redirect URIs with the provider:

- Docker: `http://localhost:3000/login/oauth2/code/{google|discord}`
- Local dev server: `http://localhost:5173/login/oauth2/code/{google|discord}`

Running the backend locally with the dev login: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

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
- `GET /api/public/auth/providers` — sign-in options to show
- `GET /api/me` — signed-in user (401 when signed out)
- `POST /api/auth/logout` — sign out
- `GET /api/public/leagues` — public leagues
- `GET /api/public/leagues/{slug}` — league details (private leagues: members only)
- `POST /api/leagues` — create a league (you become OWNER)
- `GET /api/leagues/mine` — leagues you belong to
- `GET /actuator/health` — health check
