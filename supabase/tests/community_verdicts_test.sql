-- pgTAP tests for community verdicts.
-- Without Docker: supabase db query --linked -f supabase/tests/community_verdicts_test.sql
begin;
create extension if not exists pgtap with schema extensions;
create temp table tap (n serial, line text);
grant all on tap to public;
grant usage on sequence tap_n_seq to public;
insert into tap (line) select plan(12);

insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000a1', 'a@test.local'),
  ('00000000-0000-0000-0000-0000000000b1', 'b@test.local'),
  ('00000000-0000-0000-0000-0000000000c1', 'c@test.local'),
  ('00000000-0000-0000-0000-0000000000d1', 'd@test.local');

-- Anonymous clients can neither submit nor write directly.
set local role anon;
insert into tap (line) select throws_ok($$ select public.submit_community_verdict('7500000000017', 'vegan', 'web_research',
  'agua, sal', '0123456789abcdef', '[]') $$, '42501', null, 'anon cannot submit');
insert into tap (line) select throws_ok($$ insert into public.community_verdicts (barcode, status, source, ingredients_text, ingredients_hash)
  values ('7500000000017', 'vegan', 'web_research', 'agua', '0123456789abcdef') $$, '42501', null, 'anon cannot insert directly');
reset role;

set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}';
insert into tap (line) select throws_ok($$ insert into public.community_verdicts (barcode, status, source, ingredients_text, ingredients_hash)
  values ('7500000000017', 'vegan', 'web_research', 'agua', '0123456789abcdef') $$, '42501', null, 'users cannot insert directly');
insert into tap (line) select results_eq($$ select public.submit_community_verdict('7500000000017', 'vegan', 'web_research',
  'agua, goma gelana, sal', '0123456789abcdef', '[{"name":"goma gelana","status":"vegan"}]') $$,
  $$ values (true) $$, 'a user can share a vegan verdict');
insert into tap (line) select results_eq($$ select public.submit_community_verdict('7500000000017', 'vegan', 'web_research',
  'agua, goma gelana, sal', '0123456789abcdef', '[]') $$, $$ values (false) $$, 'the same ingredients are not shared twice');
insert into tap (line) select results_eq($$ select public.submit_community_verdict('7500000000024', 'maybe', 'web_research',
  'agua, E471', 'fedcba9876543210', '[]') $$, $$ values (false) $$, 'only vegan verdicts are shared');
insert into tap (line) select results_eq($$ select status from public.community_verdicts where barcode = '7500000000017' $$,
  $$ values ('vegan') $$, 'active verdicts are readable');

-- Three reports dispute the verdict, which hides it; a resubmission can't revive it.
insert into public.verdict_reports (barcode, reason) values ('7500000000017', 'Contains milk');
insert into tap (line) select throws_ok($$ insert into public.verdict_reports (barcode) values ('7500000000017') $$,
  '23505', null, 'one report per user');
insert into tap (line) select is_empty($$ select * from public.verdict_reports $$, 'reports are private');
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000b1","role":"authenticated"}';
insert into public.verdict_reports (barcode) values ('7500000000017');
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000c1","role":"authenticated"}';
insert into public.verdict_reports (barcode) values ('7500000000017');
insert into tap (line) select is_empty($$ select * from public.community_verdicts where barcode = '7500000000017' $$,
  'a disputed verdict is hidden');
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000d1","role":"authenticated"}';
insert into tap (line) select results_eq($$ select public.submit_community_verdict('7500000000017', 'vegan', 'web_research',
  'agua, goma gelana, sal.', '1111111111111111', '[]') $$, $$ values (false) $$, 'a disputed verdict cannot be revived');
reset role;

-- New ingredients replace an active verdict and clear its reports.
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}';
select public.submit_community_verdict('7500000000031', 'vegan', 'on_device_ai', 'agua, sal', '2222222222222222', '[]');
insert into public.verdict_reports (barcode) values ('7500000000031');
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000b1","role":"authenticated"}';
select public.submit_community_verdict('7500000000031', 'vegan', 'web_research', 'agua, sal, vinagre', '3333333333333333', '[]');
insert into tap (line) select results_eq($$ select ingredients_text, report_count from public.community_verdicts where barcode = '7500000000031' $$,
  $$ values ('agua, sal, vinagre', 0) $$, 'new ingredients replace the verdict and reset reports');
reset role;

insert into tap (line) select * from finish();
select string_agg(line, E'\n' order by n) as tap from tap;
rollback;
