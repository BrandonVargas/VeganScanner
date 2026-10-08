-- Caps Google Search grounding queries so research stays inside Gemini's free search allowance
-- (5,000 queries per month on the paid tier, then billed per query). Over the cap, research falls back to
-- Wikipedia excerpts. See docs/adr/0007-cloud-ingredient-research.md.

create table public.search_usage (
  day date primary key default current_date,
  queries integer not null default 0 check (queries >= 0)
);
alter table public.search_usage enable row level security;
-- No policies: only the service role can read or write it.

-- Atomically reserves [p_reserve] queries for one grounded request. Gemini decides how many searches a request
-- runs, so the caller reserves a pessimistic amount and settles the actual count afterwards. Returns false when
-- the reservation would exceed the daily or the calendar-month cap.
create function public.reserve_search_queries(p_reserve integer, p_daily_limit integer, p_monthly_limit integer)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  daily_used integer;
  monthly_used integer;
begin
  insert into public.search_usage (day, queries) values (current_date, 0)
    on conflict (day) do nothing;
  -- Serializes reservations, so concurrent requests can't both pass the check.
  select queries into daily_used from public.search_usage where day = current_date for update;
  select coalesce(sum(queries), 0) into monthly_used from public.search_usage
    where day >= date_trunc('month', current_date)::date;

  if daily_used + p_reserve > p_daily_limit or monthly_used + p_reserve > p_monthly_limit then
    return false;
  end if;

  update public.search_usage set queries = queries + p_reserve where day = current_date;
  return true;
end;
$$;

-- Replaces a reservation with the number of queries Gemini actually ran (may be more or fewer).
create function public.settle_search_queries(p_reserved integer, p_used integer)
returns void
language sql
security definer
set search_path = ''
as $$
  insert into public.search_usage (day, queries) values (current_date, greatest(p_used, 0))
    on conflict (day) do update
    set queries = greatest(public.search_usage.queries - p_reserved + p_used, 0);
$$;

revoke execute on function public.reserve_search_queries(integer, integer, integer) from public, anon, authenticated;
revoke execute on function public.settle_search_queries(integer, integer) from public, anon, authenticated;
grant execute on function public.reserve_search_queries(integer, integer, integer) to service_role;
grant execute on function public.settle_search_queries(integer, integer) to service_role;
