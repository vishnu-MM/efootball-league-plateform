# Phase 1: playable seasons

**Goal:** a league owner can run a whole season in the app: pick who plays, generate fixtures, have managers report and confirm scores, settle disputes, and see a live table.

## Decisions

| Topic | Decision |
|---|---|
| Who plays | When creating a season, the owner or an admin picks which members play (at least 2). |
| Format | Chosen per season: **single** round-robin (once) or **home and away** (twice). |
| Unconfirmed results | **Never auto-confirmed.** A result waits for the opponent, or for an owner or admin to confirm it. |
| Timing | Chosen per season: **no dates** (rounds only), or **play-by dates** (start date + days per round gives each round a deadline). Deadlines are shown, not enforced. |
| Points | 3 for a win, 1 for a draw by default; editable when creating the season. |
| Table order | Points, goal difference, goals scored, head-to-head points among the tied teams, then name. |
| Scorers and assists | Optional typed names (squads arrive in Phase 2). A side can't list more scorers than goals. |
| One season at a time | A league has at most one active season. It completes automatically when every fixture is final. |

## Features

1. **Roles.** The owner can make a member an admin or a manager, or remove them. Members in the active season can't be removed. The owner can't be demoted or removed.
2. **Clubs.** Each member can set a club name for the league (unique within the league). Fixtures and the table show the club name, falling back to the member's name.
3. **Create season.** Owner/admin: name (default "Season N"), participants, format, points, timing. Creating it generates every fixture at once.
4. **Fixtures.** Circle-method round-robin. With an odd number of players, one player sits out each round. Home/away alternates; the second half of a home-and-away season mirrors the first. Players are shuffled so the order is random.
5. **Report.** Either player in a fixture enters the score (plus optional scorers and assists). Status becomes *awaiting confirmation*, and the report shows who entered it.
6. **Confirm.** The opponent confirms, or an owner/admin confirms on their behalf. The result is final and counts in the table.
7. **Dispute.** The opponent enters their own score and an optional note instead. The status becomes *disputed*. An owner or admin resolves it by picking either version or entering the correct score. The result is then final and shows "decided by an admin".
8. **Table.** Played, won, drawn, lost, goals for, goals against, goal difference, points. Calculated from final results every time it's read, so it's never out of sync.

### Fixture states

```
SCHEDULED ──report──▶ AWAITING_CONFIRMATION ──opponent or admin confirms──▶ CONFIRMED
                                │
                                └──opponent disputes──▶ DISPUTED ──admin resolves──▶ CONFIRMED (resolved)
```

## Architecture

The modules follow the architecture doc. Dependencies run one way: `league` ← `match` ← `standings`.

- **league:** roles, clubs, and a small public API used by `match`: league lookup with the viewer's role, and member names with club names. Role becomes a public `LeagueRole` enum, with the same JSON values. Removing a member asks any `MembershipRemovalGuard` beans (implemented by `match`), so `league` never depends on `match`.
- **match:** seasons, participants, fixtures, score reports and scorers. `FixtureGenerator` and `ResultRules` (who may do what in which state) are plain Java classes with unit tests.
- **standings:** `StandingsCalculator` (plain Java, unit tested), fed with final results from `match`.

**Database (`V5`):** `club`, `season` (only one active per league, enforced by a partial unique index), `season_participant`, `fixture`, `score_report`, `report_goal`.

## Screens

- **League page:** hero, *Your next matches* (fixtures needing your action first), table, fixtures by round, members (with role actions for the owner), invite, and your club name.
- **Create season** (`/leagues/:slug/seasons/new`): name, participant checklist, format, points, timing.
- **Fixture page** (`/leagues/:slug/fixtures/:id`): the score and status, plus Report, Confirm, Dispute or Resolve, depending on who is viewing.

## Not in Phase 1

**All image uploads move to the final phase, built together on object storage:** league logos, club logos, profile pictures, and dispute/card screenshots. Until then, leagues and clubs use the generated crests and people use initials.

Also deferred: admin overrides of final results, forfeits and point deductions (Phase 3); player stats leaderboards; notifications; screenshot uploads; editing a season after creation; ending a season early.

## Testing

- Unit tests: fixture generation (counts, no repeats, byes, home/away balance, mirroring), the table (points, tie-breakers including head-to-head), and result rules.
- API tests: each role action and each fixture transition, including permission failures.
- A browser run-through of a full mini-season with three players.
