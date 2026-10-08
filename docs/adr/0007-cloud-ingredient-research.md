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
  - **Why not Google Search grounding:** the free tier no longer includes it. `gemini-2.5-flash` (which had 500 free grounded requests per day) is closed to new API keys, and the 3.x models' grounding is paid-tier only (5,000 free searches/month, then $14 per 1,000). It stays available as a switch: set `GEMINI_GOOGLE_SEARCH=true` (and optionally `GEMINI_MODEL`) once the key has billing enabled.
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
  - It never touches ingredients the dictionaries already judged.
  - A researched "non_vegan" → NON_VEGAN. Every unrecognized ingredient researched as vegan → VEGAN, with source `WEB_RESEARCH`, an AI warning, per-ingredient reasons and sources, and a report button.
  - Results are cached locally for 30 days (Room v3).
- **Optional:** a build without Supabase settings (forks, CI) simply has no online research (`DisabledIngredientResearchRepository`).

## Alternatives considered
- **On-device only:** private and offline, but no web access, recent phones only, and weaker on regional ingredients. It stays planned as the offline fallback.
- **Google Search grounding by default:** the best evidence, but it needs billing, so it's opt-in.
- **One request for a batch of ingredients:** cheaper on quota, but one injected name could influence the others in the batch.

## Consequences
- Each unknown ingredient is researched once for everyone, so the free tier goes a long way.
- Answers are AI-generated and can be wrong. The UI always labels them, shows sources, and lets users report them. Disputed answers disappear until reviewed.
- Google may use free-tier requests to improve its products. Only ingredient names are sent (to Wikipedia and Gemini); this is disclosed in PRIVACY.md.
- Misspelled or very regional names may find no Wikipedia article. The model then answers from general knowledge and shows no sources, so users can see that difference.
