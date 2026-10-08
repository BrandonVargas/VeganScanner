# Backend (Supabase)

Used for online features. The app works fully without it: forks and CI builds simply have no web research.

| Path | What |
|---|---|
| `migrations/` | Database schema, Row Level Security and functions |
| `tests/` | pgTAP tests for RLS and rate limits |
| `functions/research-ingredients/` | Edge Function: web research of unknown ingredients (Gemini with capped Google Search grounding, or Wikipedia excerpts), shared cache. See [ADR 0007](../docs/adr/0007-cloud-ingredient-research.md) |

## Setup for your own project

```bash
supabase login
supabase link --project-ref <your-project-ref>
supabase db push --linked
supabase secrets set GEMINI_API_KEY=<key from aistudio.google.com>
supabase functions deploy research-ingredients --use-api
```

Optional secrets: `GEMINI_MODEL` (default `gemini-3.5-flash-lite`) and `GEMINI_GOOGLE_SEARCH=true` to ground answers in Google Search. That needs a billing-enabled Gemini key; searches are capped at 4,500 per month and 150 per day (inside Google's 5,000 free monthly queries) and fall back to Wikipedia beyond that. Tokens are billed at paid-tier rates, so also set a budget alert on the Google Cloud billing account.

Then enable **Authentication → Sign In / Providers → Allow anonymous sign-ins** in the dashboard. Copy `local.properties.example` and `iosApp/Configuration/Secrets.xcconfig.example` to their git-ignored names and fill in your project host and **publishable** key.

## Tests

```bash
cd supabase/functions/research-ingredients && deno test --allow-env   # Edge Function (no network)
supabase db query --linked -f supabase/tests/ingredient_research_test.sql   # pgTAP, no Docker needed
supabase db advisors --linked                                                # Supabase security/performance lints
```
