# League Platform

Custom-league manager for mobile football communities: leagues, rosters, fixtures, score reporting with
opponent confirmation, standings, admin overrides with a league log, leaderboards, notifications (in-app, live and
Discord) and screenshot proof.

| Part     | Stack                                                                 |
|----------|-----------------------------------------------------------------------|
| Frontend | React 19, Vite, TypeScript, TanStack Query, vite-plugin-pwa           |
| Backend  | Spring Boot 4.1, Java 21, Spring Security, JPA, Flyway, Spring Modulith |
| Database | PostgreSQL 17 (Docker)                                                |
| Storage  | Any S3-compatible bucket for screenshots (SeaweedFS in Docker)        |

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
# 1. Database and screenshot storage only
docker compose up -d db storage

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

Members, clubs and invites:

- `GET /api/leagues/{slug}/members` — members with roles and club names (members only)
- `PUT /api/leagues/{slug}/members/{userId}/role` — make someone an admin or manager (owner only)
- `DELETE /api/leagues/{slug}/members/{userId}` — remove a member (owner only; not while they're in the active season)
- `PUT /api/leagues/{slug}/club` — set your club name in this league
- `GET` / `POST /api/leagues/{slug}/invite`, `POST /api/leagues/{slug}/invite/rotate` — the invite link (owner/admin)
- `GET /api/public/invites/{code}`, `POST /api/invites/{code}/accept` — preview and join

Seasons, matches and the table:

- `POST /api/leagues/{slug}/seasons` — start a season; creates every fixture (owner/admin)
- `GET /api/public/leagues/{slug}/season` — active (or latest) season with fixtures and your available actions
- `GET /api/public/leagues/{slug}/fixtures/{id}` — one match with its score history
- `POST /api/leagues/{slug}/fixtures/{id}/report` | `confirm` | `dispute` | `resolve` — move a match through reporting
- `GET /api/public/leagues/{slug}/standings` — the table, recalculated from final results

Squads (cards, approvals, rules and the transfer window):

- `GET /api/leagues/{slug}/roster` — rules, window, your squad and flagged squads (members)
- `GET /api/leagues/{slug}/cards?q=` — search the league's approved card catalogue
- `POST /api/leagues/{slug}/squad/cards`, `DELETE …/squad/cards/{id}` — add (pending) or remove your card
- `GET /api/leagues/{slug}/squad/approvals`, `POST …/squad/cards/{id}/approve` | `reject` — approve cards (owner/admin)
- `PUT /api/leagues/{slug}/squad-rules`, `POST …/squad-rules/check` — set the rules, or preview who they flag (owner)
- `PUT /api/leagues/{slug}/transfer-window` — open or close the window (owner/admin)
- `GET /api/public/leagues/{slug}/squads/{userId}` — a member's approved squad

Corrections, the league log and leaderboards:

- `POST /api/leagues/{slug}/fixtures/{id}/override` | `forfeit` — correct a result, with a reason (owner/admin, active season)
- `POST /api/leagues/{slug}/deductions`, `POST …/deductions/{id}/revoke` — take points or give them back (owner/admin)
- `GET /api/public/leagues/{slug}/log?before=&adminOnly=` — the league log, newest first
- `GET /api/public/leagues/{slug}/stats` — top scorers, assists and MVPs of the active (or latest) season

Notifications, live updates and Discord:

- `GET /api/notifications?before=`, `GET /api/notifications/unread-count`, `POST /api/notifications/read` — your inbox
- `GET /api/stream` — Server-Sent Events: `notification` (unread count) and `league` (something in a league changed)
- `GET` / `PUT` / `DELETE /api/leagues/{slug}/discord`, `POST …/discord/test` — the league's Discord webhook (owner)

Screenshots:

- `POST /api/leagues/{slug}/uploads` — upload a PNG/JPEG/WebP (max 5 MB, multipart `file` + `purpose`), then pass its id
  as `proofUploadId` when adding a card or in `evidenceUploadIds` when reporting or disputing a score
- `GET /api/leagues/{slug}/squad/cards/{id}/proof`, `GET /api/leagues/{slug}/fixtures/{id}/evidence/{uploadId}` — view one

- `GET /actuator/health` — health check
