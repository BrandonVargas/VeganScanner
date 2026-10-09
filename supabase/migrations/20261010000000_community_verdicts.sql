-- Product verdicts shared by the community: when AI research (online or on a phone) concludes that a product Open
-- Food Facts couldn't decide is vegan, the verdict is stored per barcode so other users get it instantly, with a
-- warning and a report option. See docs/adr/0009-community-verdicts.md.

create table public.community_verdicts (
  barcode text primary key check (barcode ~ '^[0-9]{8,14}$'),
  -- Only "vegan" is written today; the column allows the others for later.
  status text not null check (status in ('vegan', 'non_vegan', 'maybe')),
  source text not null check (source in ('web_research', 'on_device_ai')),
  -- The ingredient list the verdict was made from (from Open Food Facts or a user's label scan).
  ingredients_text text not null check (char_length(ingredients_text) between 3 and 3000),
  -- Hash of the folded ingredient text: clients ignore the verdict when the product's ingredients changed.
  ingredients_hash text not null check (ingredients_hash ~ '^[0-9a-f]{16}$'),
  -- AI-researched ingredients: [{"name", "status", "reason"}], shown with the verdict.
  researched jsonb not null default '[]'::jsonb check (jsonb_typeof(researched) = 'array' and pg_column_size(researched) < 16000),
  created_by uuid default auth.uid() references auth.users (id) on delete set null,
  state text not null default 'active' check (state in ('active', 'disputed', 'retracted')),
  report_count integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.community_verdicts enable row level security;

create policy "Anyone can read active community verdicts"
  on public.community_verdicts for select
  to anon, authenticated
  using (state = 'active');
-- No insert/update/delete policies: clients write through submit_community_verdict() only.

-- Submissions per user per day (rate limit).
create table public.community_submissions (
  user_id uuid not null references auth.users (id) on delete cascade,
  day date not null default current_date,
  count integer not null default 0,
  primary key (user_id, day)
);
alter table public.community_submissions enable row level security;
-- No policies: only security-definer functions touch it.

-- Shares a verdict. An active verdict is replaced only by one made from a different ingredient list (reformulation,
-- better label scan), which starts over without reports. Disputed or retracted verdicts are left to moderators: the
-- hash comes from the client, so a resubmission must not be able to revive them. Returns whether it was stored.
create function public.submit_community_verdict(
  p_barcode text,
  p_status text,
  p_source text,
  p_ingredients_text text,
  p_ingredients_hash text,
  p_researched jsonb
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
  used integer;
  existing public.community_verdicts;
begin
  if uid is null then
    raise exception 'Sign-in required' using errcode = '42501';
  end if;
  if p_status <> 'vegan' then
    return false; -- Only vegan verdicts are shared for now.
  end if;

  insert into public.community_submissions (user_id, day, count) values (uid, current_date, 0)
    on conflict (user_id, day) do nothing;
  select count into used from public.community_submissions where user_id = uid and day = current_date for update;
  if used >= 30 then
    return false;
  end if;

  select * into existing from public.community_verdicts where barcode = p_barcode for update;
  if found and (existing.state <> 'active' or existing.ingredients_hash = p_ingredients_hash) then
    return false;
  end if;

  update public.community_submissions set count = count + 1 where user_id = uid and day = current_date;
  insert into public.community_verdicts
    (barcode, status, source, ingredients_text, ingredients_hash, researched, created_by)
    values (p_barcode, p_status, p_source, p_ingredients_text, p_ingredients_hash, coalesce(p_researched, '[]'::jsonb), uid)
  on conflict (barcode) do update set
    status = excluded.status,
    source = excluded.source,
    ingredients_text = excluded.ingredients_text,
    ingredients_hash = excluded.ingredients_hash,
    researched = excluded.researched,
    created_by = excluded.created_by,
    state = 'active',
    report_count = 0,
    updated_at = now();
  delete from public.verdict_reports where barcode = p_barcode; -- reports were about the replaced verdict
  return true;
end;
$$;

-- Users report a verdict as wrong, once each. Three reports dispute it, which hides it from everyone.
create table public.verdict_reports (
  barcode text not null references public.community_verdicts (barcode) on delete cascade,
  reporter_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  reason text check (char_length(reason) <= 500),
  created_at timestamptz not null default now(),
  primary key (barcode, reporter_id)
);
create index verdict_reports_reporter_idx on public.verdict_reports (reporter_id);

alter table public.verdict_reports enable row level security;

create policy "Users can report as themselves"
  on public.verdict_reports for insert
  to authenticated
  with check (reporter_id = (select auth.uid()));
-- No select policy: reports are private.

create function public.count_verdict_report()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.community_verdicts
    set report_count = report_count + 1,
        state = case when state = 'active' and report_count + 1 >= 3 then 'disputed' else state end,
        updated_at = now()
    where barcode = new.barcode;
  return new;
end;
$$;

create trigger on_verdict_report
  after insert on public.verdict_reports
  for each row execute function public.count_verdict_report();

revoke execute on function public.count_verdict_report() from public, anon, authenticated;
revoke execute on function public.submit_community_verdict(text, text, text, text, text, jsonb) from public, anon;
grant execute on function public.submit_community_verdict(text, text, text, text, text, jsonb) to authenticated;
