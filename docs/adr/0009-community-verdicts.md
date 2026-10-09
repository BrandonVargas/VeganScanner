# 0009. Community verdicts

## Status
Accepted (2026-10)

## Context
When Open Food Facts can't decide on a product, the app needs research to reach a verdict, and sometimes a label scan first. If the product isn't in Open Food Facts at all, every user has to scan the label again. The user asked for shared knowledge: when AI marks such a product as vegan, everyone should benefit. It must come with a warning that the verdict came from AI, and a way to report it.

## Decision
- **Table `community_verdicts`**, one row per barcode. It holds the status, how the verdict was concluded (`web_research` or `on_device_ai`), the ingredient list it was made from, a hash of that list, and the AI reasons for each researched ingredient.
  - RLS: anyone can read active rows.
  - Clients can't write the table directly.
- **Writing** goes through `submit_community_verdict()`, a security-definer function:
  - Only signed-in users (the app's silent anonymous session), at most 30 submissions a day.
  - Only `vegan` verdicts for now.
  - An active verdict is replaced only by one made from a *different* ingredient list (a reformulation, or a better label scan). The replacement starts over with no reports.
  - Disputed or retracted verdicts are left to moderators. The hash comes from the client, so a resubmission can't be allowed to revive them.
- **Reports:** `verdict_reports`, one per user per barcode. Three reports mark the verdict `disputed`, which hides it from everyone.
- **App** (`CommunityVerdictsUseCase`):
  - **Lookup**, before research, only when the offline verdict isn't conclusive (a conclusive Open Food Facts answer always wins). The verdict is used when its ingredient hash matches the product's ingredients, or when this phone has none.
  - **Missing products:** a barcode Open Food Facts doesn't have also gets the shared ingredient list.
  - **Sharing:** after research makes a product VEGAN, the app shares the verdict with its ingredients and reasons.
  - **On screen:** the source is `COMMUNITY` ("Shared by the community (AI estimate)"). A card warns that another person's scan was checked by AI and has a "Report as not vegan" button. Shared ingredients are labeled "From another person's label scan".
- **Ingredient hash:** FNV-1a 64-bit over the folded words, so case, accents and punctuation don't matter but a changed word does. A test pins the value, because stored verdicts depend on it.

## Alternatives considered
- **Writing to Open Food Facts:** the right long-term home for ingredient lists, but it needs an OFF account per user and editorial review. It's a later "contribute to Open Food Facts" feature.
- **Sharing only web-research verdicts:** on-device estimates are weaker, but with the warning and reports they still help phones without a model. The source is stored in case the policy changes.
- **Server-side hashing:** stops a client from faking a hash, but means reimplementing the app's text folding in SQL. Moderation and dispute rules cover that risk instead.

## Consequences
- A product only needs to be researched (and label-scanned) once for everyone.
- Anyone with the app could submit a wrong verdict. That's limited by the rate limits, the AI warning, reports and disputes, and moderators can retract verdicts in the Supabase dashboard.
- Shared label text is product data, not personal data. PRIVACY.md says it's shared.
