-- pgTAP tests for the ingredient research tables and functions.
-- Without Docker: supabase db query --linked -f supabase/tests/ingredient_research_test.sql
-- With Docker:    supabase test db --local
-- Every TAP line is collected in a temp table and returned at the end, so runners that only show the last
-- result set (like the Management API) still see the full output.
begin;
create extension if not exists pgtap with schema extensions;
create temp table tap (n serial, line text);
grant all on tap to public;
grant usage on sequence tap_n_seq to public;
insert into tap (line) select plan(11);

-- Fixtures (as the service role / migration owner).
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-00000000000a', 'a@test.local'),
  ('00000000-0000-0000-0000-00000000000b', 'b@test.local'),
  ('00000000-0000-0000-0000-00000000000c', 'c@test.local');
insert into public.ingredient_knowledge (normalized_name, display_name, language, status, model)
values ('xantolina', 'Xantolina', 'es', 'vegan', 'test');

-- Anonymous clients can read active knowledge but not write it.
set local role anon;
insert into tap (line) select results_eq($$ select status from public.ingredient_knowledge where normalized_name = 'xantolina' $$,
  $$ values ('vegan') $$, 'anon can read active knowledge');
insert into tap (line) select throws_ok($$ insert into public.ingredient_knowledge (normalized_name, display_name, language, status, model)
  values ('evil', 'Evil', 'es', 'vegan', 'x') $$, '42501', null, 'anon cannot insert knowledge');
insert into tap (line) select is_empty($$ select * from public.research_budget $$, 'anon cannot see the research budget');
insert into tap (line) select throws_ok($$ select public.reserve_research_call('00000000-0000-0000-0000-00000000000a', 5, 5) $$,
  '42501', null, 'anon cannot reserve research calls');
reset role;

-- Authenticated users can report as themselves only, once per ingredient.
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}';
insert into tap (line) select lives_ok($$ insert into public.ingredient_reports (normalized_name, reason) values ('xantolina', 'It contains milk') $$,
  'a user can report');
insert into tap (line) select throws_ok($$ insert into public.ingredient_reports (normalized_name) values ('xantolina') $$,
  '23505', null, 'a user can report an ingredient only once');
insert into tap (line) select throws_ok($$ insert into public.ingredient_reports (normalized_name, reporter_id)
  values ('xantolina', '00000000-0000-0000-0000-00000000000b') $$, '42501', null, 'a user cannot report as someone else');
insert into tap (line) select is_empty($$ select * from public.ingredient_reports $$, 'reports are not readable');
reset role;

-- Three reports dispute the verdict, which hides it from clients.
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}';
insert into public.ingredient_reports (normalized_name) values ('xantolina');
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-00000000000c","role":"authenticated"}';
insert into public.ingredient_reports (normalized_name) values ('xantolina');
insert into tap (line) select is_empty($$ select * from public.ingredient_knowledge where normalized_name = 'xantolina' $$,
  'a disputed verdict is hidden');
reset role;

-- The service role reserves calls up to the per-user and global limits.
insert into tap (line) select results_eq($$ select public.reserve_research_call('00000000-0000-0000-0000-00000000000a', 2, 3)
  union all select public.reserve_research_call('00000000-0000-0000-0000-00000000000a', 2, 3)
  union all select public.reserve_research_call('00000000-0000-0000-0000-00000000000a', 2, 3) $$,
  $$ values (true), (true), (false) $$, 'per-user limit is enforced');
insert into tap (line) select results_eq($$ select public.reserve_research_call('00000000-0000-0000-0000-00000000000b', 5, 3)
  union all select public.reserve_research_call('00000000-0000-0000-0000-00000000000b', 5, 3) $$,
  $$ values (true), (false) $$, 'global limit is enforced');

insert into tap (line) select * from finish();
select string_agg(line, E'\n' order by n) as tap from tap;
rollback;
