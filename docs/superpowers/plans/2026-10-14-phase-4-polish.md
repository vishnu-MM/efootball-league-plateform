# Phase 4 Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** League lifecycle (settings, transfer, leave, delete/restore/purge, past seasons), profile and account (name, verified email, deletion), email notifications with per-type preferences, and site-admin moderation.

**Architecture:** Extends the existing Spring Modulith modules (`league`, `identity`, `match`, `standings`, `stats`, `notify`, `discord`, `files`) and adds two: `email` (listens to a new `notify` event, sends through Resend) and `moderation` (reports and actions, calling `league` and `identity`). Cross-module needs that would reverse a dependency use the existing patterns: a guard interface (`MembershipRemovalGuard` style) or a synchronous event.

**Tech Stack:** Spring Boot 4.1, Java 21, JPA, Flyway, Spring Modulith, PostgreSQL 17, Testcontainers; React 19, Vite, TypeScript, TanStack Query.

**Spec:** `docs/specs/2026-10-14-phase-4-polish.md`

## Global Constraints

- Migrations are new files `V12__…` to `V15__…` in `backend/src/main/resources/db/migration`; never edit V1–V11.
- Errors: 400 bad input, 403/404 permissions (a hidden or deleted league is **404** to anyone who can't see it), 409 state, 429 limits. Messages are plain sentences shown to users (`ResponseStatusException(status, "…")`).
- League name 1–80 characters (create keeps its existing 3–80); display name 1–50 (`AppUser.DISPLAY_NAME_MAX`).
- Delete grace is **7 days**; verification links valid **24 hours**, single use; resend at most **once a minute**; reports: one open per reporter+target, **10 per user per day**, details ≤ **500** characters.
- Default-on email types: `RESULT_REPORTED`, `DISPUTE_OPENED`, `CARD_SUBMITTED`, `OWNERSHIP_TRANSFERRED`, `LEAGUE_DELETED`. Everything else off.
- Config: `RESEND_API_KEY` (unset → fake sender), `MAIL_FROM`, `APP_SECRET` (required outside the `dev` profile), `SITE_ADMIN_EMAILS`, existing `APP_PUBLIC_URL`.
- Verify, unsubscribe and resubscribe are `POST` endpoints; the frontend page posts the token.
- Backend tests: `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Import(TestcontainersConfiguration.class)` with `ApiSupport`, like `MembershipApiTests`. Docker must be running.
- Frontend checks: `cd frontend && npm run build && npm run lint`.
- After each task: restart the app (backend on **8081** locally, not 8080) and tell the user what changed.
- Commit messages: imperative sentence like the existing history ("Add …"), ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **A hidden or deleted league reached through another module's endpoint** (fixture page, roster, stats, log, uploads, invite preview) must 404 for outsiders and members alike — they all resolve the league through `LeagueService.detail/access`, so the check lives there. Test in Task 2 hits the fixture and log endpoints.
2. **Ownership transfer must update both `league.owner_id` and the two membership roles**, so the old owner can leave afterwards and the new owner passes every "owner only" check. Test in Task 1.
3. **A verification link opened signed out, or in another account's browser**, still verifies the account the token belongs to. Test in Task 6.
4. **An address that gets verified on another account between request and click** returns 409, not a 500 from the unique constraint. Test in Task 6.
5. **A deleted or suspended user's existing session** stops working on the next request (401), and a deleted player in an active season shows as "Deleted user" with the table unchanged. Tests in Tasks 7 and 10.

---

### Task 1: League settings, ownership transfer, leave

**Files:**
- Modify: `backend/src/main/java/com/leagueapp/league/League.java` (add `rename`, `changeVisibility`, `transferTo(UUID)`)
- Modify: `league/LeagueService.java`, `league/MembershipService.java`, `league/LeagueController.java`, `league/MembershipController.java`
- Create: `league/LeagueEvents.java`
- Modify: `notify/Notifier.java`, `activity/ActivityRecorder.java`
- Test: `backend/src/test/java/com/leagueapp/league/LeagueSettingsApiTests.java`

**Interfaces:**
- Produces: `public final class LeagueEvents { public record OwnershipTransferred(UUID leagueId, UUID actorId, UUID newOwnerId) {} }` — Task 2 adds `LeagueDeleted` and `LeaguePurging` to the same class.
- Produces: `MembershipService.leave(String slug, UUID userId)`, `MembershipService.transferOwnership(String slug, UUID actorId, UUID newOwnerId)`, `LeagueService.update(String slug, UUID actorId, String name, League.Visibility visibility)`.

- [ ] **Step 1: Write the failing tests** in `LeagueSettingsApiTests`:
  - `ownerRenamesAndChangesVisibility`: `PUT /api/leagues/{slug}` `{"name":"Renamed Cup","visibility":"PUBLIC"}` → 200; `GET /api/public/leagues/{slug}` has `name == "Renamed Cup"`, same slug; an admin gets 403; blank name → 400.
  - `ownerTransfersOwnership`: owner transfers to a member → 204; member's `myRole == "OWNER"`, old owner's `myRole == "ADMIN"`; new owner can `PUT /api/leagues/{slug}` (200); old owner then `POST …/leave` → 204. Transfer to a non-member → 404; by an admin → 403.
  - `memberLeaves`: manager `POST /api/leagues/{slug}/leave` → 204, then `GET /api/public/leagues/{slug}` (private league) → 404. Owner leaving → 409 with message containing "transfer". Non-member → 404.
  - `leavingIsBlockedInTheActiveSeason`: start a season with the member (copy setup from `SeasonApiTests`), leave → 409.
  - `transferIsLoggedAndNotified`: after transfer, `GET /api/public/leagues/{slug}/log` first entry type `OWNERSHIP_TRANSFERRED`; awaitility: new owner's `GET /api/notifications` has an item with type `OWNERSHIP_TRANSFERRED`.

- [ ] **Step 2: Run them to see them fail**
  Run: `cd backend && ./mvnw -q test -Dtest=LeagueSettingsApiTests`
  Expected: FAIL (404/405 on the new endpoints).

- [ ] **Step 3: Implement**
  - `PUT /api/leagues/{slug}` body `UpdateLeagueRequest(@NotBlank @Size(max = 80) String name, @NotNull League.Visibility visibility)` → `LeagueSummary`; owner only (403 "Only the owner can change league settings").
  - `POST /api/leagues/{slug}/transfer-ownership` `{userId}` → 204: in one transaction set `league.ownerId`, new member's role `OWNER`, old owner's role `ADMIN`; publish `OwnershipTransferred`.
  - `POST /api/leagues/{slug}/leave` → 204: owner → 409 "Transfer ownership or delete the league before leaving"; run `removalGuards` (409 with the guard's reason); delete club and membership like `remove`.
  - `Notifier`: on `OwnershipTransferred` send type `OWNERSHIP_TRANSFERRED` to `newOwnerId`, text "You're now the owner of {league name}.", link `l.home()`.
  - `ActivityRecorder`: log `OWNERSHIP_TRANSFERRED` with summary "{actor} handed the league to {new owner}" (follow the existing synchronous `@EventListener` pattern).

- [ ] **Step 4: Run the tests to see them pass**
  Run: `./mvnw -q test -Dtest=LeagueSettingsApiTests,MembershipApiTests,ModularityTests` → PASS.

- [ ] **Step 5: Commit** — "Add league settings, ownership transfer and leaving a league".

---

### Task 2: Delete, restore, purge and the visibility rule

**Files:**
- Create: `db/migration/V12__league_deleted_and_hidden.sql` — `ALTER TABLE league ADD COLUMN deleted_at TIMESTAMPTZ, ADD COLUMN hidden_at TIMESTAMPTZ;`
- Create: `league/LeaguePurger.java` (scheduled job)
- Modify: `league/League.java`, `league/LeagueService.java`, `league/LeagueRepository.java`, `league/InviteService.java`, `league/LeagueController.java`
- Modify: `files/Uploads.java` (listen to `LeaguePurging`), `files/UploadRepository.java`
- Modify: `notify/Notifier.java`
- Test: `league/LeagueDeletionApiTests.java`

**Interfaces:**
- Produces: `LeagueEvents.LeagueDeleted(UUID leagueId, UUID actorId)`, `LeagueEvents.LeaguePurging(UUID leagueId)` (published synchronously; listeners run inside the purge transaction).
- Produces: `public boolean LeagueService.isHidden(UUID leagueId)`, `public void LeagueService.hide(UUID leagueId)`, `public void LeagueService.unhide(UUID leagueId)` — used by Task 10. `hide`/`unhide` throw 404 for an unknown or purged league.
- Produces: one private rule in `LeagueService.detail`: a league with `deletedAt` or `hiddenAt` set is visible only to its owner (Task 10 adds site admins). Everyone else gets `notFound()`.
- `LeagueDetailResponse` and `MyLeagueResponse` gain `OffsetDateTime deletedAt`, `OffsetDateTime restoreUntil` (deletedAt + 7 days, null when not deleted), `boolean hidden`.

- [ ] **Step 1: Write the failing tests** in `LeagueDeletionApiTests`:
  - `ownerDeletesAndRestores`: `DELETE /api/leagues/{slug}` `{"confirmName":"wrong"}` → 400; with the exact name → 204. Member: `GET /api/public/leagues/{slug}` → 404; public list doesn't contain the slug; invite preview `GET /api/public/invites/{code}` → 404; `GET /api/leagues/mine` for the member still lists it with `deletedAt` set. Owner: league detail 200 with `restoreUntil` non-null; `POST …/restore` → 204; member sees it again.
  - `otherModulesHideADeletedLeague`: after deleting a public league with a season, `GET /api/public/leagues/{slug}/fixtures/{id}` and `GET /api/public/leagues/{slug}/log` → 404 for a signed-out viewer.
  - `restoreAfterSevenDaysIsRefused`: set `deleted_at = now() - interval '8 days'` with `JdbcTemplate`, restore → 409.
  - `purgeRemovesTheLeagueAndItsUploads`: upload a card proof (copy from `UploadsApiTests`, needs its SeaweedFS container setup), backdate `deleted_at` 8 days, call `LeaguePurger.purgeExpired()`; `select count(*) from league where id = ?` is 0 and the object is gone from the bucket (`ObjectStore`/S3 `headObject` throws `NoSuchKeyException`).
  - `membersAreNotifiedOfDeletion`: awaitility; a member's notifications contain type `LEAGUE_DELETED`; the owner's don't.

- [ ] **Step 2: Run them to see them fail**
  Run: `./mvnw -q test -Dtest=LeagueDeletionApiTests` → FAIL.

- [ ] **Step 3: Implement**
  - `DELETE /api/leagues/{slug}` body `{confirmName}` (must equal the name exactly, else 400 "Type the league name to confirm") → sets `deletedAt`, revokes nothing (invites are blocked by the rule), publishes `LeagueDeleted`. Already deleted → 409.
  - `POST /api/leagues/{slug}/restore` → owner only; 409 "This league can no longer be restored" after 7 days.
  - `publicLeagues()` excludes deleted and hidden; `InviteService.activeInviteLeague` treats a deleted or hidden league as not found.
  - `LeaguePurger.purgeExpired()`: `@Scheduled(cron = "0 0 4 * * *")` plus `@EventListener(ApplicationReadyEvent.class)`; for each league with `deleted_at < now() - 7 days`, in its own transaction: publish `LeaguePurging`, then `leagues.delete` (all child tables cascade).
  - `Uploads`: `@EventListener void on(LeaguePurging e)` collects the league's storage keys and deletes them from the store after commit (`TransactionSynchronization`); a failed delete is only logged (`ObjectStore.delete` already does that).
  - `Notifier`: on `LeagueDeleted` send `LEAGUE_DELETED` to all members except the actor, text "{owner} deleted {league name}. It will be gone for good in 7 days.", link `/`.
  - Enable scheduling is already on (the uploads job uses `@Scheduled`); check and reuse.

- [ ] **Step 4: Run the tests to see them pass**
  Run: `./mvnw -q test -Dtest=LeagueDeletionApiTests,LeagueApiTests,InviteApiTests,ModularityTests` → PASS.

- [ ] **Step 5: Commit** — "Add league deletion with a 7-day restore window".

---

### Task 3: Past seasons

**Files:**
- Modify: `match/MatchRepositories.java`, `match/SeasonService.java`, `match/MatchController.java`, `match/MatchResults.java`
- Modify: `standings/StandingsController.java`, `stats/StatsController.java`
- Test: `match/PastSeasonsApiTests.java`

**Interfaces:**
- Produces: `MatchResults.season(UUID leagueId, UUID seasonId)` and `MatchResults.seasonCredits(UUID leagueId, UUID seasonId)` returning the same `Optional<SeasonResults>` / `Optional<SeasonCredits>` as the `latest…` methods; a `null` seasonId means latest. A season id from another league → empty (the controllers turn that into 404 "Season not found").
- Produces: `SeasonService.current(String slug, UUID viewerId, UUID seasonId)`.

- [ ] **Step 1: Write the failing tests:**
  - `listsSeasonsNewestFirst`: play season 1 to completion (forfeit every fixture as admin, as in `CorrectionsApiTests`), start season 2; `GET /api/public/leagues/{slug}/seasons` → 2 items, `[0].name` is season 2's name, `[1].status == "COMPLETED"`.
  - `opensAPastSeason`: `GET …/season?season={id1}` returns season 1's fixtures; `…/standings?season={id1}` shows season 1's table; `…/stats?season={id1}` has `season.id == id1`. Without the parameter they return season 2.
  - `seasonFromAnotherLeagueIs404`: `…/standings?season={otherLeagueSeasonId}` → 404.

- [ ] **Step 2: Run them to see them fail** — `./mvnw -q test -Dtest=PastSeasonsApiTests` → FAIL.

- [ ] **Step 3: Implement** `GET /api/public/leagues/{slug}/seasons` → `[{id, name, status, startedAt, completedAt}]` ordered by `number` desc; add `@RequestParam(required = false) UUID season` to the three endpoints and pass it through. Past seasons are read-only already (actions are computed from the season status).

- [ ] **Step 4: Run** `./mvnw -q test -Dtest=PastSeasonsApiTests,SeasonApiTests,StatsApiTests` → PASS.

- [ ] **Step 5: Commit** — "Add browsing past seasons".

---

### Task 4: League settings and seasons in the app

**Files:**
- Create: `frontend/src/pages/LeagueSettingsPage.tsx` (tabs General / Discord / Danger zone; the Discord tab renders the body of today's `DiscordSettingsPage`, extracted to `features/discord/DiscordSettings.tsx`)
- Create: `frontend/src/features/league/useLeagueSettings.ts`, `frontend/src/features/match/SeasonPicker.tsx`
- Modify: `frontend/src/main.tsx` (route `leagues/:slug/settings` inside `RequireAuth`; `leagues/:slug/settings/discord` redirects to `leagues/:slug/settings?tab=discord`), `pages/LeaguePage.tsx`, `pages/StatsPage.tsx`, `pages/HomePage.tsx` / `features/league/LeagueList.tsx`, `features/league/useLeagues.ts`, `features/match/useSeason.ts`, `features/stats/useStats.ts`
- Delete: `frontend/src/pages/DiscordSettingsPage.tsx`

**Interfaces:**
- Consumes: the endpoints from Tasks 1–3.
- Produces: `useLeagueActions(slug)` → `{ update, transfer, leave, remove, restore }` mutations; `SeasonPicker({ slug, value, onChange })`; season-aware query keys `['leagues', slug, 'season', seasonId ?? 'latest']` (same for standings/stats) so live updates still invalidate by `['leagues', slug]` prefix — check how `useLiveUpdates` invalidates and keep its prefix.

- [ ] **Step 1: Build the screens**
  - Settings page: General form (name, visibility, Save); Danger zone: *Transfer ownership* (member picker + `ConfirmDialog`), *Delete league* (`FormDialog` that enables Delete only when the typed text equals the name).
  - League page: owner sees *Settings* (replaces the Discord link); non-owners see *Leave league* (`ConfirmDialog`; show the 409 message as a toast); a banner "Deleted — restore until {date}" with *Restore* for the owner when `deletedAt` is set.
  - My leagues list: deleted leagues shown muted with "Deleted"; the owner's row links to the league (for restore).
  - `SeasonPicker` (a `<select>`, newest first, label "Season") on the league page and stats page; selection kept in the `?season=` URL parameter.
- [ ] **Step 2: Verify** `cd frontend && npm run build && npm run lint` → no errors.
- [ ] **Step 3: Click through** (restart app): rename, transfer and back, leave as a second dev user, delete and restore, switch seasons. Use the `run` skill to drive it.
- [ ] **Step 4: Commit** — "Add league settings, leaving, deletion and season picker screens".

---

### Task 5: Email module foundation

**Files:**
- Create: `backend/src/main/java/com/leagueapp/email/EmailMessage.java`, `email/EmailSender.java`, `email/ResendEmailSender.java`, `email/FakeEmailSender.java`, `email/EmailConfiguration.java`, `email/DevMailController.java`
- Modify: `application.yml` (`app.mail.resend-api-key: ${RESEND_API_KEY:}`, `app.mail.from: ${MAIL_FROM:League Platform <noreply@localhost>}`, `app.mail.resend-base-url: https://api.resend.com`)
- Test: `email/ResendEmailSenderTests.java`, `email/DevMailApiTests.java`

**Interfaces:**
- Produces: `record EmailMessage(String to, String subject, String text, String html)`; `interface EmailSender { void send(EmailMessage message); }` — throws `EmailDeliveryException` (unchecked, in `email`) for retryable failures (5xx, timeout, connection error); logs and returns for 4xx.
- Produces: `FakeEmailSender.sent()` → last 50 messages, newest first.
- `EmailConfiguration` picks `ResendEmailSender` when `app.mail.resend-api-key` is non-blank, else `FakeEmailSender`.

- [ ] **Step 1: Write the failing tests**
  - `ResendEmailSenderTests` with a JDK `HttpServer` stub (pattern from `DiscordApiTests`): 200 → request was `POST /emails` with `Authorization: Bearer test-key` and JSON fields `from`, `to` (array), `subject`, `text`, `html`; 422 → no exception; 500 → `EmailDeliveryException`.
  - `DevMailApiTests`: inject `EmailSender`, send a message, `GET /dev/mail` (dev profile) → 200 and body contains the subject.
- [ ] **Step 2: Run** `./mvnw -q test -Dtest=ResendEmailSenderTests,DevMailApiTests` → FAIL.
- [ ] **Step 3: Implement** `ResendEmailSender` with `RestClient` over JDK `HttpClient` (10 s timeout, like `DiscordClient`). `GET /dev/mail` (`@Profile("dev")`, permitted in `SecurityConfig` like `/api/dev/**`) returns a plain HTML list of the fake sender's messages (to, subject, time, text with links clickable).
- [ ] **Step 4: Run** the two test classes plus `ModularityTests` → PASS.
- [ ] **Step 5: Commit** — "Add email sending through Resend with a dev mailbox".

---

### Task 6: Profile and email verification

**Files:**
- Create: `db/migration/V13__account_status_and_email_tokens.sql`:
  - `app_user` adds `email_verified_at TIMESTAMPTZ`, `pending_email VARCHAR(255)`, `suspended_at TIMESTAMPTZ`, `deleted_at TIMESTAMPTZ`; `UPDATE app_user SET email_verified_at = created_at WHERE email IS NOT NULL;` (only verified provider emails were ever stored).
  - `email_token (token_hash CHAR(64) PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE, email VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), expires_at TIMESTAMPTZ NOT NULL, used_at TIMESTAMPTZ)`, index on `user_id`.
- Create: `identity/EmailToken.java`, `identity/EmailVerification.java` (service), `identity/ProfileController.java`, `identity/IdentityEvents.java`
- Modify: `identity/AppUser.java`, `identity/IdentityService.java`, `identity/IdentityRepositories.java`, `identity/AuthController.java`
- Create: `email/VerificationMailer.java`
- Test: `identity/ProfileApiTests.java`

**Interfaces:**
- Produces: `IdentityEvents.EmailVerificationRequested(UUID userId, String email, String token)` (raw token; only its SHA-256 hex is stored). `VerificationMailer` listens with `@ApplicationModuleListener` and sends subject "Confirm your email", link `{APP_PUBLIC_URL}/verify-email?token={token}`.
- Produces: `IdentityService.verifiedEmail(UUID userId)` → `Optional<String>`: the email only if verified and the user is neither suspended nor deleted. Used by Tasks 8 and 10.
- `GET /api/me` → `UserSummary(id, displayName, email, boolean emailVerified, String pendingEmail)`; Task 10 adds `boolean siteAdmin`.

- [ ] **Step 1: Write the failing tests** in `ProfileApiTests`:
  - `changesDisplayName`: `PUT /api/me` `{"displayName":"New Name"}` → 200; `/api/me` shows it; `""` and 51 characters → 400.
  - `verifiesAnEmail`: `POST /api/me/email` `{"email":"a@example.com"}` → 204; `/api/me` `pendingEmail == "a@example.com"`, `emailVerified == false`; read the token from `FakeEmailSender.sent()` (awaitility, extract `token=` from the text); `POST /api/public/email/verify` `{"token":…}` **without a session** → 204; `/api/me` `email == "a@example.com"`, `emailVerified == true`, `pendingEmail == null`. Reusing the token → 400 "This link has already been used or has expired".
  - `expiredTokenIsRefused`: backdate `expires_at` with `JdbcTemplate` → 400.
  - `resendIsLimitedToOncePerMinute`: request, then `POST /api/me/email/resend` → 429.
  - `addressVerifiedElsewhereIsRefused`: user A and B both request `same@example.com`; A verifies; B's `POST /api/me/email` with that address → 409; B's earlier token verify → 409 "That email is already used by another account".
  - `badEmailIs400`: `"not-an-email"` → 400.
- [ ] **Step 2: Run** `./mvnw -q test -Dtest=ProfileApiTests` → FAIL.
- [ ] **Step 3: Implement** `PUT /api/me`, `POST /api/me/email` (`@Email @Size(max = 255)`, lower-cased and stripped; 409 if verified on another account), `POST /api/me/email/resend` (429 if the user's newest token is under 60 s old; 409 if no pending email), `POST /api/public/email/verify`. Tokens: 32 random bytes, base64url; 24 h expiry; verification sets `email`, `email_verified_at`, clears `pending_email`, marks the token used. Catch `DataIntegrityViolationException` on the unique `email` as 409.
- [ ] **Step 4: Run** `./mvnw -q test -Dtest=ProfileApiTests,ModularityTests` and the full suite `./mvnw -q test` → PASS.
- [ ] **Step 5: Commit** — "Add profile editing and email verification".

---

### Task 7: Account deletion and the account-status filter

**Files:**
- Create: `identity/AccountDeletionGuard.java`, `identity/AccountStatusFilter.java`
- Create: `league/LeagueOwnershipDeletionGuard.java`
- Modify: `identity/IdentityService.java`, `identity/ProfileController.java`, `identity/SecurityConfig.java`, `identity/OAuthLoginSuccessHandler.java`, `identity/DevLoginController.java`, `identity/IdentityEvents.java`, `league/MembershipService.java`
- Test: `identity/AccountDeletionApiTests.java`

**Interfaces:**
- Produces: `public interface AccountDeletionGuard { Optional<String> blockReason(UUID userId); }`; `league`'s implementation blocks when the user owns any league (deleted ones included) with "You own leagues. Hand them over or delete them first." The 409 body also lists them: `GET /api/leagues/owned` → `[{slug, name}]` (new, in `LeagueController`) — the frontend calls it rather than parsing the error.
- Produces: `IdentityEvents.AccountDeleted(UUID userId)` published synchronously; `MembershipService` `@EventListener` deletes the user's clubs and memberships (no removal guards: deletion wins).
- Produces: `IdentityService.suspend(UUID userId)`, `unsuspend(UUID userId)` (Task 10 calls them) and `AccountStatusFilter`: for a request with a session `appUserId` whose user has `deleted_at` or `suspended_at`, invalidate the session and answer 401 with detail "This account is suspended." / "Sign in required". Register after the security context filter.

- [ ] **Step 1: Write the failing tests:**
  - `ownerCannotDeleteTheirAccount`: owner `DELETE /api/me` `{"confirm":"DELETE"}` → 409; wrong confirm text → 400.
  - `deletionAnonymizes`: a member playing in another owner's active season with a verified email deletes their account → 204; their old session's `GET /api/me` → 401; the league's standings still have the same number of rows and the member's row `teamName == "Deleted user"`; `/api/leagues/{slug}/members` no longer lists them; `app_user.email` is null and `user_identity` has no rows for them. Signing in again with the same dev name creates a **new** user (different id).
  - `suspendedSessionIsEnded`: call `IdentityService.suspend(id)` directly; the user's next `GET /api/me` → 401; `POST /api/dev/login` with their name → 403 "This account is suspended."; after `unsuspend`, login works.
- [ ] **Step 2: Run** `./mvnw -q test -Dtest=AccountDeletionApiTests` → FAIL.
- [ ] **Step 3: Implement** `DELETE /api/me` (guards → 409; anonymize: `display_name = 'Deleted user'`, `email`/`pending_email` null, `deleted_at = now()`, delete identities and email tokens, publish `AccountDeleted`, invalidate the current session). Dev login and the OAuth handler refuse suspended users (OAuth: redirect to `/signin?error=suspended`).
- [ ] **Step 4: Run** `./mvnw -q test` (full suite: the filter touches every request) → PASS.
- [ ] **Step 5: Commit** — "Add account deletion and end sessions of suspended or deleted accounts".

---

### Task 8: Email notifications and preferences

**Files:**
- Create: `db/migration/V14__email_preferences.sql` — `email_preference (user_id UUID REFERENCES app_user(id) ON DELETE CASCADE, type VARCHAR(40), enabled BOOLEAN NOT NULL, PRIMARY KEY (user_id, type))`
- Create: `notify/NotifyEvents.java`
- Modify: `notify/Notifier.java`
- Create: `email/EmailType.java`, `email/EmailPreference.java`, `email/EmailPreferenceRepository.java`, `email/EmailPreferences.java` (service), `email/UnsubscribeTokens.java`, `email/NotificationMailer.java`, `email/NotificationEmails.java` (content), `email/EmailPreferenceController.java`
- Modify: `application.yml` (`app.secret: ${APP_SECRET:dev-only-secret-change-me}`; fail startup outside `dev` when it equals that default)
- Test: `email/UnsubscribeTokensTests.java`, `email/NotificationEmailsTests.java`, `email/EmailNotificationApiTests.java`

**Interfaces:**
- Produces: `NotifyEvents.NotificationCreated(UUID notificationId, UUID userId, UUID leagueId, String leagueName, String type, String text, String link)`, published by `League.send` for each saved row.
- `enum EmailType(String group, boolean defaultOn)` with exactly the notification types: matches — `RESULT_REPORTED`(on), `RESULT_CONFIRMED`, `DISPUTE_OPENED`(on), `DISPUTE_RESOLVED`, `RESULT_OVERRIDDEN`, `FORFEIT`, `POINTS_DEDUCTED`, `DEDUCTION_REVOKED`; squads — `CARD_SUBMITTED`(on), `CARD_APPROVED`, `CARD_REJECTED`, `WINDOW_OPENED`, `WINDOW_CLOSED`; seasons — `SEASON_STARTED`, `SEASON_COMPLETED`; league — `OWNERSHIP_TRANSFERRED`(on), `LEAGUE_DELETED`(on). Unknown types are never emailed.
- `UnsubscribeTokens.create(UUID userId, EmailType type)` → `base64url(userId:type).base64url(HMAC-SHA256)`; `parse(String token)` → `Optional<Subscription(UUID userId, EmailType type)>`, empty when tampered.
- `NotificationEmails.build(NotificationCreated n, String to, String unsubscribeUrl)` → `EmailMessage`; subject `"{text cut to 100 chars} · {leagueName}"`.

- [ ] **Step 1: Write the failing tests:**
  - `UnsubscribeTokensTests`: round trip; flipping one character of the signature → empty; changing the type part → empty.
  - `NotificationEmailsTests`: 150-character text → subject starts with the first 100 characters + "…"; `text` and `html` contain `{publicUrl}{link}`, "Email settings" (`{publicUrl}/account`) and the unsubscribe URL; HTML escapes `<script>` in the text.
  - `EmailNotificationApiTests` (awaitility on `FakeEmailSender.sent()`):
    - verified opponent gets an email when a score is reported; an unverified one gets none.
    - after `PUT /api/me/email-preferences` `[{"type":"RESULT_REPORTED","enabled":false}]` → no email; `RESULT_CONFIRMED` off by default → none.
    - `GET /api/me/email-preferences` returns all 17 types with `group` and defaults.
    - `POST /api/public/email/unsubscribe` with the token from the email (no session) → 204 and the preference is off; `…/resubscribe` → on; tampered token → 400.
    - a suspended user (`IdentityService.suspend`) gets no email.
- [ ] **Step 2: Run** the three classes → FAIL.
- [ ] **Step 3: Implement** `NotificationMailer` `@ApplicationModuleListener void on(NotificationCreated n)`: `EmailType` known → `identity.verifiedEmail(userId)` present → preference enabled → `sender.send(...)`. Do not catch `EmailDeliveryException` (the publication stays incomplete and is republished on restart). Preferences endpoints: `GET`/`PUT /api/me/email-preferences` (PUT takes a partial list).
- [ ] **Step 4: Run** `./mvnw -q test` → PASS.
- [ ] **Step 5: Commit** — "Add email notifications with per-type preferences and unsubscribe links".

---

### Task 9: Account screens

**Files:**
- Modify: `frontend/src/pages/AccountPage.tsx`, `features/auth/useAuth.ts` (new `Me` fields)
- Create: `features/account/useProfile.ts`, `features/account/EmailPreferencesTile.tsx`, `features/account/DeleteAccountTile.tsx`, `pages/VerifyEmailPage.tsx`, `pages/UnsubscribePage.tsx`
- Modify: `main.tsx` (public routes `verify-email`, `unsubscribe`), `pages/SignInPage.tsx` (show "This account is suspended." for `?error=suspended`)

- [ ] **Step 1: Build the screens**
  - Account page tiles: *Profile* (display name form); *Email* (address with "Verified" / "Waiting for you to confirm — Resend" / "No email", change form); *Email notifications* (switches grouped by `group`, disabled with "Verify your email first" when not verified); *Delete account* (if `GET /api/leagues/owned` is non-empty, list them with links to their settings and disable the button; otherwise a dialog that requires typing `DELETE`, then go to `/`).
  - `VerifyEmailPage` and `UnsubscribePage` post the `token` from the URL once on mount and show the result; Unsubscribe offers *Undo* (resubscribe) and names the type.
- [ ] **Step 2: Verify** `npm run build && npm run lint`.
- [ ] **Step 3: Click through** with the dev mailbox at `/dev/mail` (through the Vite proxy — add `/dev` to the proxy list in `vite.config.ts` if needed): verify an email, report a score from a second user, see the email, unsubscribe from it.
- [ ] **Step 4: Commit** — "Add profile, email and account deletion screens".

---

### Task 10: Moderation backend and site admins

**Files:**
- Create: `db/migration/V15__moderation.sql` — `report` and `moderation_action` exactly as in the spec's Database section; indexes `report(target_type, target_id, status)`, `report(reporter_id, created_at)`; a partial unique index `report(reporter_id, target_type, target_id) WHERE status = 'OPEN'`.
- Create: `identity/SiteAdmins.java`
- Modify: `identity/SecurityConfig.java` (`/api/admin/**` requires `SiteAdmins.isSiteAdmin(currentUserId)`), `identity/AuthController.java` (`siteAdmin` on `/api/me`), `league/LeagueService.java` (site admins pass the hidden/deleted rule), `notify/Notifier.java` and `discord/DiscordPoster.java` (skip when `leagueService.isHidden(leagueId)`)
- Create: `moderation/Report.java`, `moderation/ModerationAction.java`, `moderation/ModerationRepositories.java`, `moderation/ModerationService.java`, `moderation/ReportController.java`, `moderation/ModerationController.java`
- Modify: `application.yml` (`app.site-admin-emails: ${SITE_ADMIN_EMAILS:}`)
- Test: `identity/SiteAdminsTests.java`, `moderation/ModerationApiTests.java`

**Interfaces:**
- Produces: `public boolean SiteAdmins.isSiteAdmin(UUID userId)` — reads the user's **verified** email each call, compares case-insensitively with the trimmed comma-separated list.
- Consumes: `LeagueService.hide/unhide/isHidden` (Task 2), `IdentityService.suspend/unsuspend` (Task 7).
- Report target ids: a league's `id` (from `GET /api/public/leagues/{slug}`) or a user's `id`.

- [ ] **Step 1: Write the failing tests**
  - `SiteAdminsTests`: listed + verified → true; listed but unverified (only pending) → false; `" A@Example.com "` in the list matches `a@example.com`.
  - `ModerationApiTests` (set `app.site-admin-emails=mod@example.com` with `@DynamicPropertySource`; give the moderator a verified email by setting `email`/`email_verified_at` via `JdbcTemplate`):
    - `reportsAreLimited`: reporting the same league twice → 409; 11th report in a day → 429; unknown target → 404; details of 501 characters → 400.
    - `onlySiteAdminsModerate`: `GET /api/admin/reports` → 403 for a normal user, 200 for the moderator with the report grouped by target (`[0].count`, `[0].reasons`).
    - `hidingALeague`: `POST /api/admin/leagues/{id}/hide` `{"note":"spam"}` → 204; open reports on it are `ACTIONED`; members and outsiders get 404 on the league and its fixture page; owner gets 200 with `hidden == true`; moderator gets 200; Discover excludes it; scoring a match in it creates no notification; `unhide` restores everything. Both actions appear in `GET /api/admin/moderation-log`.
    - `suspendingAUser`: suspend → the user's next request 401; `GET /api/admin/restricted` lists them; unsuspend → they can log in.
    - `dismissClosesReports`: `POST /api/admin/reports/dismiss` → reports `DISMISSED`, listed under `?status=CLOSED`.
    - `meShowsSiteAdmin`: `/api/me` `siteAdmin` true for the moderator, false otherwise.
- [ ] **Step 2: Run** `./mvnw -q test -Dtest=SiteAdminsTests,ModerationApiTests` → FAIL.
- [ ] **Step 3: Implement** the endpoints from the spec's API section. Every action writes a `moderation_action` row; hide/suspend/dismiss close all open reports on the target (`ACTIONED` or `DISMISSED`, `closed_by`, `closed_at`). Daily limit counts the reporter's reports with `created_at > now() - 1 day`.
- [ ] **Step 4: Run** `./mvnw -q test` → PASS.
- [ ] **Step 5: Commit** — "Add reports, site admins and moderation actions".

---

### Task 11: Moderation screens

**Files:**
- Create: `frontend/src/features/moderation/useModeration.ts`, `features/moderation/ReportButton.tsx`, `pages/ModerationPage.tsx`
- Modify: `main.tsx` (route `moderation` inside `RequireAuth`), `components/layout/UserMenu.tsx` (*Moderation* item when `me.siteAdmin`), `pages/LeaguePage.tsx` (Report button; "Hidden by moderators" banner when `hidden`), `pages/SquadPage.tsx` (Report on another member's squad)

- [ ] **Step 1: Build the screens**
  - `ReportButton({ targetType, targetId, label })`: flag icon, `FormDialog` with reason (spam, abuse, cheating, other) and details (max 500); success toast "Thanks, a moderator will look at it."; shows 409/429 messages as toasts. Hidden when signed out or reporting yourself/your own league.
  - Moderation page tabs: *Open* (grouped reports with Dismiss / Hide league / Suspend user, each asking for a note), *Closed*, *Hidden & suspended* (Unhide / Unsuspend), *Log*.
- [ ] **Step 2: Verify** `npm run build && npm run lint`.
- [ ] **Step 3: Click through** with `SITE_ADMIN_EMAILS` set to a verified dev user's email: report a league as another user, hide it, check the owner's banner, unhide; suspend a second browser's user and unsuspend.
- [ ] **Step 4: Commit** — "Add report button and moderation screens".

---

### Task 12: Docs and end-to-end check

**Files:**
- Modify: `README.md` (feature line; new endpoints grouped like the existing lists; a "Email" section with `RESEND_API_KEY`, `MAIL_FROM`, `/dev/mail`; `APP_SECRET`, `SITE_ADMIN_EMAILS`), `.env.example`, `docker-compose.yml` (pass the four new variables through to the backend)

- [ ] **Step 1: Update the docs and compose file.**
- [ ] **Step 2: Full checks** `cd backend && ./mvnw test` and `cd frontend && npm run build && npm run lint` → all pass.
- [ ] **Step 3: Browser run-through** from the spec's Testing section, in one go, on the restarted app.
- [ ] **Step 4: Commit** — "Document Phase 4 endpoints and configuration".
