# Showing in-game players and ratings

There is **no official or public API** for eFootball player cards and ratings, and the workarounds that exist aren't safe to rely on. The recommendation is to let players enter their own cards. The architecture doc already planned for this.

## What exists

| Option | What you get | Problems |
|---|---|---|
| **Konami official API** | Doesn't exist. | — |
| **Fan databases** (pesdb.net, efhub, pesmaster, apps like [eFootBase](https://mwm.ai/apps/efootbase/6759371167) and [eFootbox](https://mwm.ai/apps/efootbox-formerly-pesdata/1451203120)) | Every card with ratings and stats. | No API: you'd have to scrape them, which their terms usually forbid. The data is Konami's, taken from the game. Scrapers break whenever the site changes. A public, possibly paid app built on it is a real legal risk, including a takedown that kills the feature overnight. |
| **Real-football data APIs** (API-Football, Sportmonks, etc.) | Licensed real player names, clubs and real-life stats. | **No eFootball ratings or card types.** Paid at scale. Player photos have their own licensing rules. |
| **Reading the game itself** (memory or files) | Exact data. | Against Konami's terms and impossible on mobile. Not an option. |

Messi's eFootball rating isn't one number anyway. There are several cards (base, Epic, Big Time, Show Time, and so on), boosted versions, and players level them up. What matters in a league is the card **this manager** actually owns, and only the manager knows that.

## Recommended: player-entered cards with a shared catalogue

1. **A manager adds a card to their squad:** player name, card type, overall rating and position, plus an optional screenshot as proof.
2. **Entries become a league-wide catalogue.** Once someone enters "Lionel Messi · Epic · 98 · RWF", the next manager can pick it from search instead of retyping it.
3. **League admins approve cards,** as the architecture doc planned, so a manager can't claim a 103-rated card they don't have.
4. **Later:** reading the card from the screenshot automatically, as an optional shortcut. This is the original "OCR mode".

This needs no third-party dependency or licence, carries no takedown risk, and is always as current as the game. It also fits the "no Konami branding, card data comes from users" rule in the architecture doc. Showing real player names as text is fine; Konami's card artwork and player photos are what to avoid.

**Decision (11 Oct 2026):** player cards will be entered by users. No external player-data source for now.

## API-Football (considered, not used for now)

[API-Football](https://www.api-football.com/) is a real-world football data API: players (name, age, nationality, club, position, photo), real season stats, per-match performance ratings, and 1,200+ leagues. It has a free plan (100 requests/day), with paid plans at $19, $29 and $39 a month.

- **It has no eFootball data:** no card ratings, card types, boosts or in-game positions. Its "rating" is a real match performance score (e.g. 7.4).
- **Possible later use:** name autocomplete in the card form, so everyone picks "Lionel Messi · Inter Miami · Argentina" instead of typing variants. The user still enters card type and rating.
- **Cautions:** check its terms before relying on it. Player photos likely come without usage rights, and storing their data in our database may be restricted. It would also be a monthly bill and an outside dependency.
- **Decision:** not now. Revisit only if inconsistent player names become a real problem.

## Open question: what are the ratings for?

The answer changes what to build first:

- **Squad rules**, such as a rating cap per squad or banned card types: the ratings must be trusted, so admin approval comes first.
- **Display only**, such as squad pages and "who's on whose team": start simpler, with manager entries and no approval.
- **Both**: build display first and add approval when squad rules come in.

## Sources

- [eFootBase: DB & Community](https://mwm.ai/apps/efootbase/6759371167)
- [eFootbox (formerly PESData)](https://mwm.ai/apps/efootbox-formerly-pesdata/1451203120)
