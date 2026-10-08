# 0002. Supabase for the marketplace and community verdicts

## Status
Accepted (2026-10). Implementation planned for phases 4–5.

## Context
The marketplace (listings, photos, accounts, "near me" search) and the shared community-verdict cache need a backend. Constraints: free tier, production-grade, enterprise-familiar, works from KMP, and needs no custom server to operate.

## Decision
Use Supabase: Postgres with PostGIS, Row Level Security, Auth (Apple, Google, anonymous), and Storage. Access it through the official KMP client `supabase-kt`.

- Every table has RLS enabled. Writes that need validation or rate limiting go through `security definer` RPCs.
- Seller locations are rounded to about 1 km. WhatsApp numbers are visible only to authenticated users.
- Schema, policies and pgTAP tests live in `supabase/` and are applied with the Supabase CLI.

## Alternatives considered
- **Firebase:** KMP support comes only from a community SDK, and since late 2024 Cloud Storage requires the paid Blaze plan.
- **Custom backend:** more to build, host and secure, for no product benefit.

## Consequences
- Plain SQL with relational data and geo queries.
- The anon key ships in the apps, so security depends entirely on RLS, and RLS is covered by tests.
- Free projects pause after 7 days of inactivity. A scheduled GitHub Action keeps the project awake.
