-- Covering index for the reporter foreign key (account deletion cascades through it). Flagged by db advisors.
create index ingredient_reports_reporter_id_idx on public.ingredient_reports (reporter_id);

comment on table public.research_usage is 'Per-user daily research calls. Service role only (RLS on, no policies by design).';
comment on table public.research_budget is 'Global daily research budget. Service role only (RLS on, no policies by design).';
