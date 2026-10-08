-- Shared cache of ingredients researched on the web by the `research-ingredients` Edge Function.
-- Every device benefits from a lookup done once. See docs/adr/0007-cloud-ingredient-research.md.

create table public.ingredient_knowledge (
  -- Folded name (lowercase, no accents, single spaces), same folding as the app's TextFolding.
  normalized_name text primary key check (char_length(normalized_name) between 1 and 80),
  display_name text not null check (char_length(display_name) between 1 and 80),
  language text not null check (language in ('en', 'es')),
  status text not null check (status in ('vegan', 'non_vegan', 'maybe', 'unknown')),
  reason_en text check (char_length(reason_en) <= 400),
  reason_es text check (char_length(reason_es) <= 400),
  sources jsonb not null default '[]'::jsonb check (jsonb_typeof(sources) = 'array'),
  model text not null,
  researched_at timestamptz not null default now(),
  report_count integer not null default 0,
  state text not null default 'active' check (state in ('active', 'disputed'))
);

comment on table public.ingredient_knowledge is
  'Web-researched ingredient verdicts (AI + Google Search). Written only by the research-ingredients Edge Function.';

alter table public.ingredient_knowledge enable row level security;

create policy "Anyone can read active knowledge"
  on public.ingredient_knowledge for select
  to anon, authenticated
  using (state = 'active');
-- No insert/update/delete policies: only the Edge Function (service role) writes.

-- Rate limiting: research calls per user per day, and a global daily budget under the Gemini free tier.
create table public.research_usage (
  user_id uuid not null references auth.users (id) on delete cascade,
  day date not null default current_date,
  count integer not null default 0,
  primary key (user_id, day)
);
alter table public.research_usage enable row level security;

create table public.research_budget (
  day date primary key default current_date,
  count integer not null default 0
);
alter table public.research_budget enable row level security;
-- No policies on either table: only the service role can read or write them.

-- Atomically reserves one research call. Returns false when the user or the global budget is exhausted.
create function public.reserve_research_call(p_user uuid, p_user_limit integer, p_global_limit integer)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  user_used integer;
  global_used integer;
begin
  insert into public.research_usage (user_id, day, count) values (p_user, current_date, 0)
    on conflict (user_id, day) do nothing;
  insert into public.research_budget (day, count) values (current_date, 0)
    on conflict (day) do nothing;

  select count into user_used from public.research_usage
    where user_id = p_user and day = current_date for update;
  select count into global_used from public.research_budget
    where day = current_date for update;

  if user_used >= p_user_limit or global_used >= p_global_limit then
    return false;
  end if;

  update public.research_usage set count = count + 1 where user_id = p_user and day = current_date;
  update public.research_budget set count = count + 1 where day = current_date;
  return true;
end;
$$;

revoke execute on function public.reserve_research_call(uuid, integer, integer) from public, anon, authenticated;
grant execute on function public.reserve_research_call(uuid, integer, integer) to service_role;

-- Users (including anonymous sessions) can report a researched verdict as wrong. Three reports dispute it,
-- which hides it from everyone until it is re-researched or reviewed.
create table public.ingredient_reports (
  normalized_name text not null references public.ingredient_knowledge (normalized_name) on delete cascade,
  reporter_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  reason text check (char_length(reason) <= 500),
  created_at timestamptz not null default now(),
  primary key (normalized_name, reporter_id)
);

alter table public.ingredient_reports enable row level security;

create policy "Users can report as themselves"
  on public.ingredient_reports for insert
  to authenticated
  with check (reporter_id = (select auth.uid()));
-- No select policy: reports are private.

create function public.count_ingredient_report()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.ingredient_knowledge
    set report_count = report_count + 1,
        state = case when report_count + 1 >= 3 then 'disputed' else state end
    where normalized_name = new.normalized_name;
  return new;
end;
$$;

revoke execute on function public.count_ingredient_report() from public, anon, authenticated;

create trigger on_ingredient_report
  after insert on public.ingredient_reports
  for each row execute function public.count_ingredient_report();
