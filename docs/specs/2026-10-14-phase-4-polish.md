# Phase 4: polish

**Goal:** owners can run a league for its whole life (rename it, hand it over, delete it, look back at old seasons); members can leave; everyone controls their own account (name, verified email, deletion) and can get email for the notifications they care about; and site admins can act on reports of abusive leagues and users.

Phase 5 (launch) covers production deployment, legal pages, the landing page, rate limits beyond the ones below, error pages and backups.

## Decisions

| Topic | Decision |
|---|---|
| League settings | The owner edits the name (1–80 characters) and visibility. The slug never changes, so links keep working. |
| Ownership transfer | The owner picks another member; the new owner becomes OWNER and the old owner becomes ADMIN. Immediate (no acceptance step), with a confirm dialog. The new owner is notified. |
| Leave league | Any member except the owner. Blocked while they play in the active season (the same `MembershipRemovalGuard` rule as removal). The owner must transfer or delete first. |
| Delete league | Owner only, confirmed by typing the league name. Soft delete: `deleted_at` is set, members are notified, and the owner can restore it for **7 days**. Then a scheduled job purges it for good, including its screenshots in the bucket. |
| Past seasons | Every season of a league can be opened: fixtures, table and leaderboards. Read-only, like a completed season today. |
| Display name | Editable by the user, 1–50 characters (the existing limit). Past log entries keep the name as it was. |
| Email address | Added or changed in the profile and **verified by a link** (valid 24 hours, single use). Nothing is emailed to an unverified address. Emails from Google sign-in count as verified; Discord's only when Discord says `verified: true`. An address verified on another account is refused. |
| Delete account | Confirmed by typing `DELETE`. Refused while the user owns any league (including one in its 7-day grace). The account is **anonymized**: display name becomes "Deleted user", email and sign-in identities are removed, memberships end, sessions are dropped. Fixtures, results and log entries stay, so other leagues' history stays correct. |
| Email sending | **Resend** HTTP API. With no `RESEND_API_KEY` (dev, tests) a fake sender keeps the last 50 emails in memory, shown at `/dev/mail` (dev profile only). |
| Email triggers | One email per in-app notification, sent straight away, when the user has a verified email and that type switched on. |
| Email preferences | One switch per notification type. **On by default:** score reported, dispute opened, card submitted, ownership transferred, league deleted. Everything else is off by default. Every email has a one-click unsubscribe link for its type. |
| Site admins | `SITE_ADMIN_EMAILS` (comma-separated). A user is a site admin when their **verified** email is in the list; checked on every request, so changing the list takes effect without signing in again. |
| Reports | Any signed-in user can report a league or a user: reason (spam, abuse, cheating, other) plus details (up to 500 characters). One open report per reporter and target; at most 10 reports per user per day. |
| Moderation actions | Dismiss a report, **hide a league**, or **suspend a user**, each with a note. Hide and suspend can be undone. Every action is recorded in a moderation log that only site admins can read. |
| Hidden league | Gone from Discover, its pages and invite links for everyone but its owner and site admins. The owner sees "Hidden by moderators". Members aren't notified. Discord posts and emails for it stop. |
| Suspended user | Their sessions are deleted and sign-in shows "This account is suspended." No emails. Their data and memberships stay. Leagues they own are not hidden automatically. |

## Features

### 1. League settings, lifecycle and past seasons

1. **League settings page** (`/leagues/:slug/settings`, owner) with tabs: *General* (name, visibility, Save), *Discord* (the existing page moves here), *Danger zone* (Transfer ownership, Delete league).
2. **Transfer ownership:** pick a member from a list, confirm. Logged in the league log.
3. **Leave league:** a button on the league page for non-owners, with a confirm. Shows the block reason when they're in the active season.
4. **Delete league:** type the name to confirm. The league disappears from Discover and its invite link stops working. Members see it as deleted in *My leagues* until the purge; only the owner sees *Restore* (with the date it will be gone).
5. **Past seasons:** a season picker on the league page and the stats page (newest first; the active season is the default).

### 2. Profile and account

1. **Account page** (`/account`, existing) gains: display name, email with its state (*verified*, *awaiting verification: resend*, *none*), the email preferences list (feature 3), and *Delete account*.
2. **Verify email:** the link opens `/verify-email?token=…`, which confirms and shows the result. A resend is allowed once a minute.
3. **Delete account:** blocked with a list of the leagues they own (each linking to its settings) until there are none.

### 3. Email notifications

1. **Preferences** on the Account page: one switch per notification type, grouped (matches, squads, seasons, league). Shown disabled with "Verify your email first" when there's no verified email.
2. **Email content:** subject = the notification text (shortened to 100 characters) with the league name; body = the text, a button to the linked page, and a footer with *Email settings* and *Unsubscribe from these emails*. Plain-text and HTML versions.
3. **Unsubscribe** (`/unsubscribe?token=…`) works without signing in and switches off that one type. The page offers *Undo*.

New notification types this phase: `OWNERSHIP_TRANSFERRED` (new owner), `LEAGUE_DELETED` (all members except the owner).

### 4. Moderation

1. **Report** button (flag icon) on a league page and on a member's squad page, opening a small form (reason, details). After sending: "Thanks, a moderator will look at it."
2. **Moderation page** (`/moderation`, site admins; linked from the account menu only for them): open reports grouped by target, newest first, with the target's name, report count and reasons. Actions per target: *Dismiss*, *Hide league* / *Suspend user*, each with a note. Tabs: *Open*, *Closed*, *Hidden & suspended* (with *Unhide* / *Unsuspend*), *Log*.
3. Acting on a target closes all of its open reports.

## Architecture

Modules and their one-way dependencies (new: `email`, `moderation`):

```
identity ← league ← files ← roster ← match ← standings
                                   ↖      ↖  ↖
                                     activity  stats
          notify, discord (listen to league, roster and match events)
          email (listens to notify) · moderation (uses league and identity)
```

- **identity** owns profile changes, email verification, suspension and anonymization. It can't depend on `league`, so it declares `AccountDeletionGuard` (like `MembershipRemovalGuard`; `league` implements it: "you own leagues") and publishes `AccountDeleted` synchronously (`@EventListener`, same transaction) so `league` ends the memberships. Sign-in (OAuth success handler and dev login) refuses suspended and deleted users. A filter checks `suspended_at` on each authenticated request and ends the session, so a suspension takes effect at once. `SiteAdmins` (in `identity`) answers "is this user a site admin" from the verified email and `SITE_ADMIN_EMAILS`.
- **league** owns `deleted_at` and `hidden_at`. One `LeagueVisibility` check (used everywhere a league is looked up by slug or listed) hides deleted and hidden leagues from everyone but their owner and site admins. Other modules already look leagues up through `LeagueService`, so they inherit this. The purge job (daily, plus once at startup for overdue leagues) publishes `LeaguePurging` synchronously so `files` collects the league's storage keys, then deletes the league row (all league tables already `ON DELETE CASCADE`); `files` deletes the bucket objects after commit. A failed object delete is logged and left for the existing orphan cleanup.
- **match / standings / stats** take an optional season id; without one they behave as today (active or latest).
- **notify:** after saving each notification, `Notifier` publishes `NotificationCreated(notificationId, userId, leagueId, type, text, link)`. It also listens to the new `league` events (`OwnershipTransferred`, `LeagueDeleted`). It skips leagues that are hidden. Discord does the same.
- **email** listens to `NotificationCreated` after commit (`@ApplicationModuleListener`, through the `event_publication` outbox). It sends only when the user has a verified email, isn't suspended or deleted, and has the type switched on. `EmailSender` has two implementations: `ResendEmailSender` (Java `HttpClient`, `POST https://api.resend.com/emails`) and `FakeEmailSender`. A 4xx from Resend is logged and dropped; a 5xx or timeout throws, so the publication stays incomplete and is retried on restart. Unsubscribe tokens are HMAC-SHA256 over `userId:type` with `APP_SECRET`; they don't expire.
- **moderation** stores reports and actions, and calls `LeagueService.hide/unhide` and `IdentityService.suspend/unsuspend`. Endpoints under `/api/admin/**` require the `SITE_ADMIN` authority, which a request gets from `SiteAdmins`.

**Database:**
- `V12`: `league.deleted_at`, `league.hidden_at`.
- `V13`: `app_user.email_verified_at`, `pending_email`, `suspended_at`, `deleted_at`; `email_token` (token hash, user, email, expires at, used at).
- `V14`: `email_preference` (user, type, enabled; PK user + type).
- `V15`: `report` (id, reporter, target type `LEAGUE`|`USER`, target id, reason, details, status `OPEN`|`DISMISSED`|`ACTIONED`, created at, closed at, closed by); `moderation_action` (id, actor, action `DISMISS`|`HIDE_LEAGUE`|`UNHIDE_LEAGUE`|`SUSPEND_USER`|`UNSUSPEND_USER`, target type, target id, note, created at).

## API

- `PUT /api/leagues/{slug}` `{name, visibility}` (owner)
- `POST /api/leagues/{slug}/transfer-ownership` `{userId}` (owner)
- `POST /api/leagues/{slug}/leave` (members except the owner)
- `DELETE /api/leagues/{slug}` `{confirmName}`; `POST /api/leagues/{slug}/restore` (owner, within 7 days)
- `GET /api/public/leagues/{slug}/seasons` → `[{id, name, status, startedAt, completedAt}]`
- `GET /api/public/leagues/{slug}/season`, `/standings`, `/stats` accept `?season={id}`
- `PUT /api/me` `{displayName}`
- `POST /api/me/email` `{email}` (sends the link); `POST /api/me/email/resend`; `POST /api/public/email/verify` `{token}`
- `GET` / `PUT /api/me/email-preferences` → `[{type, group, enabled}]`
- `POST /api/public/email/unsubscribe` `{token}`; `POST /api/public/email/resubscribe` `{token}`
- `DELETE /api/me` `{confirm: "DELETE"}`
- `GET /api/me` gains `emailVerified`, `pendingEmail`, `siteAdmin`
- `POST /api/reports` `{targetType, targetId, reason, details}`
- `GET /api/admin/reports?status=OPEN|CLOSED` (grouped by target), `POST /api/admin/reports/dismiss` `{targetType, targetId, note}`
- `POST /api/admin/leagues/{id}/hide` | `unhide`, `POST /api/admin/users/{id}/suspend` | `unsuspend` `{note}`
- `GET /api/admin/moderation-log?before=`; `GET /api/admin/restricted` (hidden leagues and suspended users)
- `GET /dev/mail` (dev profile only)

Verify, unsubscribe and resubscribe are `POST` so that link scanners in mail clients can't trigger them by fetching the link; the frontend page posts the token.

Errors follow earlier phases: 400 bad input, 403/404 for permissions (a hidden or deleted league is a 404 to anyone who can't see it), 409 for state (owner leaving, account owns leagues, restore after 7 days, email taken), 429 for the report and resend limits.

## Screens

- **New pages:** League settings (General / Discord / Danger zone), Verify email, Unsubscribe, Moderation.
- **League page:** season picker, *Leave league*, *Report*, banners for "Deleted, restore until …" and "Hidden by moderators", a *Settings* link for the owner (replaces the Discord link).
- **Stats page:** season picker.
- **Squad page:** *Report* on another member's squad.
- **Account page:** display name, email and verification state, email preferences, delete account.
- **Account menu:** *Moderation* for site admins.

## Configuration

`RESEND_API_KEY` (unset = fake sender), `MAIL_FROM` (e.g. `League Platform <noreply@yourdomain>`; must be a domain verified in Resend), `APP_SECRET` (HMAC key for unsubscribe tokens; required outside dev), `SITE_ADMIN_EMAILS`. `APP_PUBLIC_URL` (already present) builds the links in emails.

## Not in Phase 4

Daily digests; email for site-admin reports; appeals; moderator notes visible to the user; deleting leagues or accounts from the moderation screen; changing league slugs; season deletion or editing a completed season; exporting your data; Phase 5 items (production deployment, legal pages, landing page, general rate limiting, error pages, backups).

## Build order

Each step ships with its tests and leaves the app runnable: (1) league settings, ownership transfer, leave, delete/restore/purge, past seasons; (2) profile, email verification, account deletion; (3) email sending and preferences; (4) moderation and site admins.

## Testing

- Unit: unsubscribe token signing (tampered, wrong type), email content building, `SiteAdmins` matching (case, verified only), the default-on preference set.
- API: settings and transfer permissions; leave blocked in the active season and for the owner; delete → restore → purge (with a SeaweedFS Testcontainer, objects removed); hidden/deleted league is 404 to members and outsiders, visible to owner and site admin, gone from Discover and invites; past seasons on season/standings/stats; display name and email verification (expired, reused, taken); account deletion refused while owning, anonymized after (standings unchanged); the email listener sends or skips by verification, preference, suspension and hidden league; Resend client against a local HTTP stub (2xx, 4xx dropped, 5xx retried); reports (duplicate, daily limit), moderation actions and their permissions; a suspended user's next request is signed out and sign-in is refused.
- Modulith verification with `email` and `moderation`.
- A browser run-through: rename and transfer a league; leave; delete and restore; open a past season; verify an email through `/dev/mail`; get a score-reported email and unsubscribe from it; report a league, hide it as a site admin, unhide it; suspend and unsuspend a user in a second browser.
