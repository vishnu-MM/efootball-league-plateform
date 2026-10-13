# Phase 3: trust and engagement

**Goal:** admins can correct results in the open, with a log the whole league can read; players see who scores, assists and wins MVP; everyone hears about what happens in their league (in the app, live, and in Discord); and disputes and cards can carry screenshot proof.

## Decisions

| Topic | Decision |
|---|---|
| Who corrects results | Owner and admins (the people who already resolve disputes). Every correction needs a reason (up to 280 characters). |
| Override | Only on a **confirmed** fixture of the **active** season. New score, scorers and MVP. Earlier reports stay in the history. A completed season is closed: 409. |
| Forfeit | On any fixture of the active season, final or not. One side forfeits: the admin picks a **fixed 3–0** or enters a score. Both sides forfeit: a **0–0 draw**. Forfeits have no scorers or MVP. |
| Point deduction | 1–99 points from one participant in the active season, with a reason. It can be revoked (marked, never deleted). The table subtracts live deductions. |
| League log | Admin actions and season start/finish, newest first. Readable by anyone who can see the league (public leagues: public). Append-only. |
| MVP | Optional, picked by whoever enters a score (report, dispute, resolution, override), from either side's approved cards, or typed when that side has no approved cards. The opponent confirms or disputes it with the score. |
| Leaderboards | Goals, assists and MVPs for the active (or latest) season, counted from final results only. Squad cards are grouped by squad card; typed names by manager + name (ignoring case). |
| Notifications | An inbox per user, for things that concern them (see the table below). Read/unread; no email (Phase 4). |
| Live updates | One Server-Sent Events stream per signed-in tab. The server pushes small "this changed" events and the app refetches. Single backend instance (in-memory connection registry). |
| Discord | Owner pastes a Discord channel webhook URL and ticks which event types to post (all on by default); "Send test message". Delivery happens after commit, never inside a score report. A failed post is recorded and shown, not retried. Posts never ping anyone. |
| Storage | S3-compatible API. SeaweedFS in docker-compose for development (MinIO stopped publishing images in 2025); any S3/R2/B2 bucket in production via environment variables. |
| Screenshots | PNG, JPEG or WebP, up to 5 MB, checked by their first bytes. Dispute evidence: up to 3 per report or dispute. Card proof: one per card; the owner can **require** it for new cards (off by default). |

## Features

### 1. Overrides, forfeits, deductions and the league log

1. **Override.** On a confirmed fixture, an owner or admin enters the correct score, scorers and MVP plus a reason. The fixture page shows "Overridden by an admin" and the reason; the report history keeps every earlier version.
2. **Forfeit.** An owner or admin picks *home forfeits*, *away forfeits* or *both forfeit*, then (one side) *3–0* or *enter score*, plus a reason. The fixture becomes final and shows "Forfeit". If it was the last open fixture, the season completes as usual.
3. **Deduct points.** From the standings tile an owner or admin deducts points from a participant with a reason. The table shows a *Ded.* column whenever the season has deductions, and lists them under the table with *Revoke* (reason required).
4. **League log.** A *League log* tile (last 5) and page (`/leagues/:slug/log`, paged, filter "admin actions only"). Each entry: when, who, what, reason, and a link to the fixture or member.

Logged: dispute resolved, result overridden, forfeit, points deducted, deduction revoked, card approved/rejected, squad rules changed, transfer window opened/closed by an admin, season started, season completed.

### 2. MVP and leaderboards

1. **MVP picker** on the score form (report, dispute, resolve, override): one card from either side's approved squad, or a typed name for a side without one. Confirming accepts the reporter's MVP.
2. **Leaderboards tile** on the league page: top 5 scorers / assists / MVPs (tabs). **Stats page** (`/leagues/:slug/stats`): top 20 of each, with team, card type and rating where the entry is a card.

### 3. Notifications and live updates

| Event | Who is notified |
|---|---|
| Score reported | the opponent ("confirm or dispute") |
| Result confirmed | the reporter |
| Dispute opened | the reporter, and owner/admins |
| Dispute resolved, result overridden, forfeit | both players |
| Points deducted / deduction revoked | that manager |
| Card submitted | owner/admins |
| Card approved / rejected | the card's manager |
| Season started / completed | its participants |
| Transfer window opened / closed by an admin | all members |

The actor is never notified of their own action. The window opening or closing on its own (a season starting or ending) isn't notified: the season notification covers it.

1. **Bell** in the app header with the unread count; **Notifications page** (`/notifications`) with *Mark all read*. Opening a notification marks it read and goes to its link.
2. **Live updates.** While the app is open, a new notification bumps the bell straight away, and pages showing the league (season, fixture, standings, roster, log, stats) refresh when something changes in it.

### 4. Discord webhook

1. **Discord settings** (`/leagues/:slug/settings/discord`, owner): webhook URL (must be `https://discord.com/api/webhooks/…` or `discordapp.com`), event checkboxes (results confirmed, disputes opened/resolved, overrides and forfeits, point deductions, seasons started/completed, transfer window), Save, Send test message, Remove.
2. The saved URL is never sent back in full (shown as `…/webhooks/1234…/****`).
3. The page shows the last delivery failure, if any. A 404 from Discord (webhook deleted) switches the webhook off and says so.

### 5. Screenshot uploads

1. **Dispute evidence.** The report and dispute forms take up to 3 screenshots. The fixture page shows them as thumbnails on that report, to the two players and owner/admins.
2. **Card proof.** The add-card form takes a screenshot (required when the league requires it). Approvers see it on the Approvals page; the manager sees it on their own squad.
3. **Squad rules** gains "Require a screenshot for new cards".

### Fixture states (Phase 3)

```
SCHEDULED ──report──▶ AWAITING_CONFIRMATION ──confirm──▶ CONFIRMED ──override──▶ CONFIRMED
    │                        │                              ▲   │
    │                        └──dispute──▶ DISPUTED ─resolve┘   └──forfeit──▶ CONFIRMED (forfeit)
    └──────────── forfeit (from any non-final state) ──────────────────────────▶ CONFIRMED (forfeit)
```

Override and forfeit on a confirmed fixture need the season to be active.

## Architecture

Modules and their one-way dependencies:

```
league ← files ← roster ← match ← standings
                     ↖      ↖  ↖
                       activity  stats
                           notify, discord (listen to roster and match events)
```

- **Events.** `match` and `roster` publish domain events (public records in their base package) from the methods that change state: `ResultReported`, `ResultConfirmed`, `DisputeOpened`, `DisputeResolved`, `ResultOverridden`, `ForfeitAwarded`, `PointsDeducted`, `DeductionRevoked`, `SeasonStarted`, `SeasonCompleted`, `CardSubmitted`, `CardDecided`, `SquadRulesChanged`, `TransferWindowChanged`. Each carries the league id, actor and the ids involved.
- **activity** listens synchronously (`@EventListener`, inside the publisher's transaction), so a log entry can't exist without its action or vice versa. It stores a summary written at the time (names as they were then) plus links.
- **notify** listens after commit (`@ApplicationModuleListener`, through Modulith's `event_publication` outbox, republished on restart). It writes inbox rows, pushes SSE events, and posts to Discord. A failing webhook never affects the action.
- **stats** reads final results with goals and MVPs from `match`'s `MatchResults` and card details from `roster`'s `Squads`, and computes leaderboards on read (like standings). `LeaderboardCalculator` is plain Java.
- **files** stores uploads in S3 (AWS SDK v2, path-style for MinIO). The backend receives the multipart upload, checks it, and streams it to the bucket; images are read back through the backend (so the bucket never needs to be public or reachable from the browser). `roster` and `match` keep upload ids and ask `files` to attach them (an upload can be attached once, by its uploader, in the same league). Unattached uploads older than 24 hours are deleted by a scheduled job.
- **match:** `ResultRules` gains `OVERRIDE` and `FORFEIT`. `score_report.kind` gains `OVERRIDE` and `FORFEIT`; `fixture` gains `forfeit` (null, `HOME`, `AWAY`, `BOTH`: who forfeited). Point deductions live in `match` (they belong to a season). `MatchResults` exposes deductions and goal/MVP details for `standings` and `stats`.
- **SSE:** `GET /api/stream` returns an `SseEmitter` (ends after 30 minutes and the browser reconnects; heartbeat comment every 25 s). The registry maps user → emitters. Events: `notification` (`{unread}`) and `league` (`{slug}`), sent to connected members of that league; the app refetches every query about that league. nginx already has `proxy_buffering off` for `/api/`.
- **discord** is its own module (listens after commit like notify). Requests only go to the configured Discord base URL; only the webhook path is taken from the saved URL.

**Database:**
- `V7`: `score_report.kind` adds `OVERRIDE`, `FORFEIT`; `fixture.forfeit`; `point_adjustment` (season, user, points, reason, created by/at, revoked by/at/reason); `league_activity` (league, created at, actor, type, summary, reason, fixture id, subject user id).
- `V8`: `score_report.mvp_side`, `mvp_name`, `mvp_card_id`.
- `V9`: `notification` (user, league, type, text, link, created at, read at).
- `V10`: `league_webhook` (league PK, url, event types array, enabled, last error, last error at).
- `V11`: `upload` (id, league, uploaded by, purpose, content type, size, storage key, attached, created at); `report_evidence` (report, upload); `squad_card.proof_upload_id`; `squad_rules.require_card_proof`.

## API

- `POST /api/leagues/{slug}/fixtures/{id}/override` `{homeGoals, awayGoals, goals, mvp, reason}` (owner/admin)
- `POST /api/leagues/{slug}/fixtures/{id}/forfeit` `{forfeiting: HOME|AWAY|BOTH, homeGoals?, awayGoals?, reason}`; no score for one side means 3–0 (owner/admin)
- `GET /api/public/leagues/{slug}/standings` now includes `deducted` per row and a `deductions` list
- `POST /api/leagues/{slug}/deductions` `{userId, points, reason}`; `POST /api/leagues/{slug}/deductions/{id}/revoke` `{reason}` (owner/admin, active season)
- `GET /api/public/leagues/{slug}/log?before=&adminOnly=`: 30 per page
- `GET /api/public/leagues/{slug}/stats`: `{season, scorers, assists, mvps}`
- `GET /api/notifications?before=`, `GET /api/notifications/unread-count`, `POST /api/notifications/read` `{ids}` (empty = all)
- `GET /api/stream`: SSE (signed in)
- `GET` / `PUT` / `DELETE /api/leagues/{slug}/discord`, `POST /api/leagues/{slug}/discord/test` (owner)
- `POST /api/leagues/{slug}/uploads` (multipart `file`, `purpose`: `CARD_PROOF` | `DISPUTE_EVIDENCE`) → `{id}`
- `GET /api/leagues/{slug}/squad/cards/{id}/proof` (the card's manager, owner/admins) and `GET /api/leagues/{slug}/fixtures/{id}/evidence/{uploadId}` (the two players, owner/admins) stream the image
- Score bodies gain `mvp` `{side, cardId?, name?}` and `evidenceUploadIds` (report and dispute); add-card gains `proofUploadId`; squad rules gain `requireCardProof`

Errors follow Phases 1–2: 400 bad input (missing reason, wrong file type, too big), 403/404 for permissions, 409 for state (season closed, result not final for override, proof required).

## Screens

- **Fixture page:** *Admin* menu (Override, Forfeit) for owner/admins; "Overridden" / "Forfeit" badges with the reason; MVP picker; evidence uploads and thumbnails.
- **League page:** *League log* tile, *Leaderboards* tile; standings tile gains *Ded.* and deductions (owner/admin: *Deduct points*, *Revoke*); owner sees a *Discord* link.
- **New pages:** League log, Stats, Notifications, Discord settings.
- **App header:** notification bell.
- **Add card / Approvals / Squad rules:** proof upload, proof thumbnails, the require-proof toggle.

## Configuration

`STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` (defaults point at the docker-compose SeaweedFS, whose dev credentials are in `docker/storage-s3.json`). The backend creates the bucket on startup if it is missing. `APP_PUBLIC_URL` is the address used in Discord links.

## Not in Phase 3

Email notifications and notification preferences (Phase 4); multi-instance live updates (would need Postgres LISTEN/NOTIFY); leaderboards across seasons or all time; editing or deleting log entries; Discord bot commands; video uploads; overriding results in completed seasons.

## Build order

Each step ships with its tests and leaves the app runnable: (1) overrides, forfeits, deductions, log; (2) MVP and leaderboards; (3) notifications and SSE; (4) Discord; (5) uploads.

## Testing

- Unit: `ResultRules` (override/forfeit by state and role), `StandingsCalculator` with deductions and forfeits, `LeaderboardCalculator` (cards vs typed names, ties, forfeits excluded), Discord message formatting, image type sniffing.
- API: override/forfeit/deduction permissions and 409s, season completing through a forfeit, log entries and visibility, MVP validation, stats, notification recipients and read state, SSE stream delivers an event, Discord settings validation and delivery (local HTTP stub), uploads with a SeaweedFS Testcontainer (type, visibility, attach once, require proof).
- Modulith verification with the new modules.
- A browser run-through: dispute with screenshots → resolve → override → forfeit → deduction; log, leaderboards, bell and live refresh across two browsers; Discord test message.
