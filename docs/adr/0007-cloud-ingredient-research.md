# 0007. Cloud ingredient research with a shared cache

## Status
Accepted (2026-10)

## Context
After the dictionary and the Open Food Facts taxonomy (ADR 0006), some ingredients are still unrecognized: regional names, rare additives, OCR'd brand-specific terms. On-device models (Gemini Nano, Apple Foundation Models) can't browse the web and only run on recent phones. The user wanted an LLM that can *search the web* for these ingredients, using only free, production-grade services.

## Decision
- **Supabase Edge Function `research-ingredients`** (Deno) researches each ingredient in two steps:
  1. **Wikipedia retrieval:** a keyless search of the MediaWiki Action API in the label's language and the other supported language, returning intro excerpts.
  2. **Gemini 3.5 Flash-Lite** (free tier) answers in JSON mode from the excerpts plus its general knowledge, and reports which excerpts it used. Those become the cited sources.
  - The API key is a Supabase secret and never ships in the apps.
  - **Google Search grounding, capped:** the free tier no longer includes it. `gemini-2.5-flash` (which had 500 free grounded requests per day) is closed to new API keys, and the 3.x models' grounding is paid-tier only (5,000 free search queries/month across all Gemini 3 models, then $14 per 1,000). With a billing-enabled key and `GEMINI_GOOGLE_SEARCH=true`, each request also offers Gemini the Google Search tool, next to the Wikipedia excerpts, but only within the function's own quota (`reserve_search_queries()`): **4,500 queries per calendar month and 150 per day**. Gemini decides whether and how often to search, so each request reserves 3 queries and settles the real count from `groundingMetadata.webSearchQueries`. Once the quota is used up, requests go without the search tool, so searches are never billed.
  - Gemini can't be forced to search: in tests, Flash-Lite searched for about 1 ingredient in 5 (for example lysozyme and E471) and answered well-known ones from the excerpts and its own knowledge, even when told to always search. Gemini 3.8 Flash searched only slightly more often and took 12–23 s per ingredient. Offering both sources keeps answers fast and gives them sources either way. The `model` column records which were used (`+wikipedia`, `+wikipedia+google-search`). Tokens are still billed at paid-tier rates (fractions of a cent per ingredient).
- **Shared cache** `public.ingredient_knowledge`:
  - Each ingredient is researched **once for all users**, with its sources (grounding metadata) and an English and a Spanish reason.
  - RLS: anyone can read active rows; only the function (service role) writes.
- **Limits:** `reserve_research_call()` enforces 25 calls per user per day and a global 450 per day, comfortably inside free-tier rate limits. Over the limit, names are returned as `deferred` and retried on a later scan. Nothing fails.
- **Identity:** the app creates a silent **anonymous session** (Supabase Auth), so limits and reports are per user without asking anyone to sign up.
- **Prompt-injection and cache-poisoning defenses:**
  - Only short, label-like names are accepted (≤ 60 characters, ≤ 6 words, letters, digits and basic punctuation).
  - The system prompt treats the name strictly as data.
  - **Each ingredient is researched in its own request**, and a crafted "ingredient" is cached under its own key only, so it can't influence the answer for another ingredient.
- **Reports:** users can report a researched verdict as wrong (`ingredient_reports`, one per user). Three reports mark it `disputed`, which hides it from everyone.
- **App behavior** (`RefineWithWebResearchUseCase`):
  - The offline verdict appears immediately. Research runs in the background, only for **unrecognized** ingredients (at most 5), and the screen updates.
  - Ingredients the dictionaries already judged non-vegan or doubtful are researched **only for an explanation**: the reason and sources appear under the ingredient (with an AI note and a report button) when the research agrees it isn't vegan. Their status and the verdict never change, and a disagreeing answer ("vegan" for gelatin) isn't shown.
  - A researched "non_vegan" → NON_VEGAN. Every unrecognized ingredient researched as vegan → VEGAN, with source `WEB_RESEARCH`, an AI warning, per-ingredient reasons and sources, and a report button.
  - Results are cached locally for 30 days (Room v3).
- **Optional:** a build without Supabase settings (forks, CI) simply has no online research (`DisabledIngredientResearchRepository`).

## Alternatives considered
- **On-device only:** private and offline, but no web access, recent phones only, and weaker on regional ingredients. It stays planned as the offline fallback.
- **Uncapped Google Search grounding:** the best evidence, but every query beyond the free allowance is billed, and a burst of new ingredients could run up a bill. It's opt-in and capped instead.
- **One request for a batch of ingredients:** cheaper on quota, but one injected name could influence the others in the batch.

## Consequences
- Each unknown ingredient is researched once for everyone, so the free tier goes a long way.
- Answers are AI-generated and can be wrong. The UI always labels them, shows sources, and lets users report them. Disputed answers disappear until reviewed.
- Only ingredient names are sent (to Wikipedia and Gemini); this is disclosed in PRIVACY.md. On the free tier Google may use requests to improve its products; the project's key is on the paid tier, where it doesn't.
- Misspelled or very regional names may find no Wikipedia article. The model then answers from general knowledge and shows no sources, so users can see that difference.
