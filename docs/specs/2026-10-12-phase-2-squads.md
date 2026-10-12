# Phase 2: squads

**Goal:** each manager keeps a squad of the eFootball cards they own, admins check those cards, the league sets squad rules, and match reports credit goals and assists to real cards instead of typed names.

## Decisions

| Topic | Decision |
|---|---|
| Card data | Entered by managers: player name, card type, rating (40–110), position. No outside data source and no screenshots (uploads are Phase 3). |
| Catalogue | One per league. A card entered once can be picked from search by other managers. Search only shows cards that have been approved at least once, so typos don't spread. |
| Approval | Every card a manager adds starts **pending**. An owner or admin approves or rejects it (with an optional reason). Owners and admins can approve their own cards, since a league may have only one. |
| Duplicates | Several managers can own the same card. A manager can't hold the same card twice. |
| Squad rules | Per league: max squad size, banned card types, and a rating cap. The owner picks the cap type: none, **max per card**, **squad total**, or **top-11 total** (the 11 highest ratings, positions ignored). |
| Enforcement | Adding a card that would break a rule is refused with the reason. Pending and approved cards both count. |
| Tightened rules | Saving rules that some squads now break is allowed (the owner sees who first). Those squads are flagged: their manager can only remove cards until the squad is legal, and a season can't start while a participant's squad is flagged. |
| Transfer window | Squads change (add or remove a card) only while the window is open. It opens when a season completes and closes when one starts; an owner or admin can open or close it at any time. Approving and rejecting work whether it's open or not. |
| Scorers | Scorers and assists are picked from the **approved** cards of that side's manager. A side with no approved cards keeps typed names, so leagues not using squads still work. Old typed scorers stay as they are. |
| Removing a card | A soft delete: the card leaves the squad but goals already credited to it keep pointing at it. |

## Features

1. **Your squad.** A manager sees their cards with status (pending, approved, rejected with reason), the squad size, the cap usage (e.g. "Top-11 total 1,012 / 1,050"), whether the window is open, and what's wrong if the squad is flagged.
2. **Add a card.** Search the catalogue as you type; pick a result, or enter a new card. The rule check runs on submit.
3. **Remove a card.** While the window is open. Rejected cards can be cleared at any time.
4. **Approvals.** Owner/admin: pending cards grouped by manager, with Approve and Reject.
5. **Squad rules.** Owner: max size, cap type and value, banned card types. Before saving, the page shows which squads the new rules would flag.
6. **Transfer window.** Owner/admin open or close it from the league page.
7. **Squad pages.** Anyone who can see the league can see a member's approved squad.
8. **Scorers from squads.** The score form offers each side's approved cards. The server checks every picked card belongs to that side's manager and is approved.
9. **Season start check.** Starting a season is refused while any chosen player's squad is flagged, naming who has to fix theirs. Starting closes the window; finishing reopens it.

### Card states

```
PENDING ──approve──▶ APPROVED ──remove (window open)──▶ REMOVED
   │
   ├──reject──▶ REJECTED ──clear──▶ REMOVED
   └──remove (window open)──▶ REMOVED
```

## Architecture

New module **roster**. Dependencies run one way: `league` ← `roster` ← `match` ← `standings`.

- **roster:** catalogue cards, squad cards, squad rules and the window. Public API `Squads` for `match`: a member's approved cards, the problems with a set of members' squads, and opening or closing the window. `match` calls these directly (not through events), so a season start and the window closing happen in one transaction.
- **SquadRuleChecker** (plain Java, unit tested): given the rules and a squad's cards, returns the problems and the cap usage. "Can I add this card?" is the same check run on the squad plus the new card, so a flagged squad can't grow.
- Squad changes, approvals and rule changes in a league are serialised with a row lock on its `squad_rules` row, so two adds at once can't both slip under the cap.
- **match:** `report_goal` gains `scorer_card_id` and `assist_card_id`. The typed name columns stay and hold the card's player name as a snapshot.

**Database (`V6`):** `card` (catalogue, unique per league on name ignoring case + type + rating + position), `squad_card` (one live entry per manager and card), `squad_rules` (one row per league, created for existing leagues with the window closed if a season is active), and the two `report_goal` columns.

Card types: Standard, Featured, Highlight, POTW, Epic, Big Time, Show Time, Legend, Trend. Positions: GK, CB, LB, RB, DMF, CMF, LMF, RMF, AMF, LWF, RWF, SS, CF. Both are fixed lists in code.

## API

- `GET /api/leagues/{slug}/roster`: rules, window, your squad with problems and cap usage, flagged squads, and (owner/admin) the pending count
- `GET /api/leagues/{slug}/cards?q=`: search the catalogue (members)
- `POST /api/leagues/{slug}/squad/cards`: add a card, by `cardId` or as a new card
- `DELETE /api/leagues/{slug}/squad/cards/{id}`: remove your card
- `GET /api/leagues/{slug}/squad/approvals`: pending cards (owner/admin)
- `POST /api/leagues/{slug}/squad/cards/{id}/approve` | `reject`: decide a card (owner/admin)
- `PUT /api/leagues/{slug}/squad-rules`: save the rules (owner); `POST …/squad-rules/check` lists who they would flag
- `PUT /api/leagues/{slug}/transfer-window`: open or close (owner/admin)
- `GET /api/public/leagues/{slug}/squads/{userId}`: a member's approved squad

Rule breaks and a closed window return **409** with a readable reason; bad input returns 400; non-members and wrong roles get 403/404 as in Phase 1.

## Screens

- **League page:** a *Your squad* tile (members) and a *Squads* admin tile (owner/admin: pending count, window toggle, links to approvals and rules). Members link to their squad page.
- **Add card** (`/leagues/:slug/squad/add`), **Approvals** (`/leagues/:slug/squad/approvals`), **Squad rules** (`/leagues/:slug/squad/rules`), **Squad** (`/leagues/:slug/squads/:userId`).
- **Fixture page:** scorer and assist pickers per side.

## Not in Phase 2

Screenshot proof on cards (Phase 3, uploads); player stats leaderboards (Phase 3; Phase 2 stores the card links they will need); notifications for approvals; moving cards between clubs; editing a card's details after entry (remove it and add the right one).

## Testing

- Unit: `SquadRuleChecker`: each cap type, banned types, size, flagged squads, top-11 with fewer than 11 cards.
- API: adding (new and from the catalogue, duplicates, rule breaks, closed window), approvals and permissions, rule changes that flag squads, the window opening and closing with seasons, a flagged squad blocking a season start, scorers from squads (wrong side, pending card, typed-name fallback).
- Modulith verification with the new module.
- A browser run-through: two managers build squads, an admin approves, a season starts, a match is reported with picked scorers.
