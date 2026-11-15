-- Automated database tests. Run by scripts/test.sh as a superuser (needed for SET ROLE).
-- Everything runs in one transaction that is rolled back at the end: the data is left untouched.
-- Each check prints "ok: ..."; a failed check raises an exception and the script stops with exit code 1.
\set ON_ERROR_STOP on
\pset tuples_only on
SET search_path = rw, public;
BEGIN;

CREATE SCHEMA rw_test;
GRANT USAGE ON SCHEMA rw_test TO PUBLIC;
CREATE FUNCTION rw_test.check(p_ok boolean, p_msg text) RETURNS void LANGUAGE plpgsql AS
$$ BEGIN
  IF p_ok IS NOT TRUE THEN RAISE EXCEPTION 'FAILED: %', p_msg; END IF;
  RAISE NOTICE 'ok: %', p_msg;
END $$;
-- runs a statement and requires it to fail with the given SQLSTATE
CREATE FUNCTION rw_test.expect_error(p_sql text, p_state text, p_msg text) RETURNS void LANGUAGE plpgsql AS
$$ BEGIN
  BEGIN
    EXECUTE p_sql;
  EXCEPTION WHEN OTHERS THEN
    IF SQLSTATE = p_state THEN RAISE NOTICE 'ok: % (rejected, %)', p_msg, SQLSTATE; RETURN; END IF;
    RAISE EXCEPTION 'FAILED: % - expected %, got % (%)', p_msg, p_state, SQLSTATE, SQLERRM;
  END;
  RAISE EXCEPTION 'FAILED: % - statement was accepted', p_msg;
END $$;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA rw_test TO PUBLIC;

-- =============================================================== 1. structure
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['users','refresh_tokens','vehicles','countries','fuel_prices','fuel_import_log',
                           'toll_tariffs','lodging_rates','speed_profiles','trips','trip_cost_lines','trip_refuel_stops'] LOOP
    PERFORM rw_test.check(EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conrelid = ('rw.'||t)::regclass AND c.contype = 'p'),
                          'table ' || t || ' has a primary key');
  END LOOP;
  PERFORM rw_test.check((SELECT count(*) FROM pg_type ty JOIN pg_namespace n ON n.oid = ty.typnamespace
                          WHERE n.nspname = 'rw' AND ty.typtype = 'e') = 11, '11 enumerations exist');
  PERFORM rw_test.check(NOT EXISTS (SELECT 1 FROM information_schema.columns
                          WHERE table_schema = 'rw' AND data_type IN ('json','jsonb')), 'no JSON columns (3NF, no unjustified JSON)');
  PERFORM rw_test.check(NOT EXISTS (SELECT 1 FROM information_schema.columns
                          WHERE table_schema = 'rw' AND column_name LIKE '%eur%' AND data_type NOT IN ('numeric')), 'money columns are numeric');
END $$;

-- =============================================================== 2. constraints
SELECT rw_test.expect_error($$INSERT INTO users(email,password_hash,privacy_accepted_at) VALUES ('Bad Mail','$2a$12$x',now())$$,
  '23514', 'e-mail format and lower case are enforced');
SELECT rw_test.expect_error($$INSERT INTO users(email,password_hash,privacy_accepted_at) VALUES ('a@b.co','5f4dcc3b5aa765d61d8327deb882cf99',now())$$,
  '23514', 'an MD5 value or plain text cannot be stored as password_hash');
SELECT rw_test.expect_error($$INSERT INTO users(email,password_hash,privacy_accepted_at) VALUES ('anna.k@example.com','$2a$12$abcdefghijklmnopqrstuv',now())$$,
  '23505', 'duplicate e-mail rejected (UNIQUE)');
SELECT rw_test.expect_error($$INSERT INTO vehicles(user_id,name,fuel_type,consumption_l100) VALUES ('11111111-1111-1111-1111-111111111111','x','diesel',1.0)$$,
  '23514', 'consumption below 2.0 rejected');
SELECT rw_test.expect_error($$INSERT INTO vehicles(user_id,name,fuel_type,consumption_l100,tank_l) VALUES ('11111111-1111-1111-1111-111111111111','x','diesel',6.0,500)$$,
  '23514', 'tank above 120 l rejected');
SELECT rw_test.expect_error($$INSERT INTO vehicles(user_id,name,fuel_type,consumption_l100) VALUES (gen_random_uuid(),'x','diesel',6.0)$$,
  '23503', 'vehicle of a non-existing user rejected (FK)');
SELECT rw_test.expect_error($$INSERT INTO fuel_prices(country_code,fuel_type,price_eur_l,bulletin_date) VALUES ('LT','diesel',-1,'2030-01-01')$$,
  '23514', 'negative fuel price rejected');
SELECT rw_test.expect_error($$INSERT INTO fuel_prices(country_code,fuel_type,price_eur_l,bulletin_date) VALUES ('LT','diesel',1.5,'2026-11-09')$$,
  '23505', 'second price for the same country/fuel/week rejected');
SELECT rw_test.expect_error($$INSERT INTO fuel_prices(country_code,fuel_type,price_eur_l,bulletin_date) VALUES ('XX','diesel',1.5,'2030-01-01')$$,
  '23503', 'price for an unknown country rejected (FK)');
SELECT rw_test.expect_error($$UPDATE speed_profiles SET avg_speed_kmh = 120, motorway_limit_kmh = 100 WHERE country_code='LT' AND season='summer'$$,
  '23514', 'average speed above the motorway limit rejected');
SELECT rw_test.expect_error($$INSERT INTO fuel_import_log(status) VALUES ('ok')$$,
  '23514', 'successful import must have a bulletin date');
SELECT rw_test.expect_error($$INSERT INTO trips(user_id,title,origin_label,destination_label,departed_at,distance_km,duration_min,
   vehicle_name,fuel_type,consumption_l100,price_date,total_eur,arrival_at)
   VALUES ('11111111-1111-1111-1111-111111111111','t','A','B','2026-01-02 10:00+00',10,10,'v','diesel',5,'2026-01-01',0,'2026-01-02 09:00+00')$$,
  '23514', 'arrival before departure rejected');
SELECT rw_test.expect_error($$INSERT INTO trips(user_id,vehicle_id,title,origin_label,destination_label,departed_at,distance_km,duration_min,
   vehicle_name,fuel_type,consumption_l100,price_date,total_eur)
   VALUES ('11111111-1111-1111-1111-111111111111','b0000000-0000-0000-0000-000000000001','t','A','B',now(),10,10,'v','diesel',5,'2026-01-01',0)$$,
  '23503', 'trip with a vehicle of another user rejected (trigger)');

-- total_eur must equal the sum of the lines (deferred constraint trigger, checked here with SET CONSTRAINTS)
DO $$
BEGIN
  BEGIN
    INSERT INTO trips(id,user_id,title,origin_label,destination_label,departed_at,distance_km,duration_min,vehicle_name,fuel_type,consumption_l100,price_date,total_eur)
      VALUES ('d0000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','t','A','B',now(),10,10,'v','diesel',5,'2026-01-01',10.00);
    INSERT INTO trip_cost_lines(trip_id,kind,amount_eur) VALUES ('d0000000-0000-0000-0000-000000000001','fuel',5.00);
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'FAILED: wrong trip total was accepted';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'ok: trip total different from the sum of cost lines rejected';
  END;
  INSERT INTO trips(id,user_id,title,origin_label,destination_label,departed_at,distance_km,duration_min,vehicle_name,fuel_type,consumption_l100,price_date,total_eur)
    VALUES ('d0000000-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','t','A','B',now(),10,10,'v','diesel',5,'2026-01-01',5.00);
  INSERT INTO trip_cost_lines(trip_id,kind,amount_eur) VALUES ('d0000000-0000-0000-0000-000000000002','fuel',5.00);
  SET CONSTRAINTS ALL IMMEDIATE;
  RAISE NOTICE 'ok: trip with a matching total accepted';
  SET CONSTRAINTS ALL DEFERRED;
  DELETE FROM trips WHERE id = 'd0000000-0000-0000-0000-000000000002';
END $$;

-- =============================================================== 3. passwords and login
DO $$
DECLARE v_id uuid; v_hash text; i int;
BEGIN
  v_id := register_user('  New.Person@Example.COM ', 'correct horse 42', true);
  SELECT password_hash INTO v_hash FROM users WHERE id = v_id;
  PERFORM rw_test.check(v_hash LIKE '$2a$12$%' AND v_hash <> 'correct horse 42', 'password stored only as a bcrypt hash (cost 12)');
  PERFORM rw_test.check((SELECT email FROM users WHERE id = v_id) = 'new.person@example.com', 'e-mail normalised to lower case');
  PERFORM rw_test.check(authenticate('new.person@example.com', 'correct horse 42') = v_id, 'login with the right password works');
  PERFORM rw_test.check(authenticate('new.person@example.com', 'wrong') IS NULL, 'login with a wrong password fails');
  PERFORM rw_test.check(authenticate('nobody@example.com', 'x') IS NULL, 'login of an unknown user fails');
  FOR i IN 1..4 LOOP PERFORM authenticate('new.person@example.com', 'wrong'); END LOOP;   -- 5 failures in total
  PERFORM rw_test.check((SELECT locked_until > now() FROM users WHERE id = v_id), '5 failed logins lock the account (US-02)');
  PERFORM rw_test.check(authenticate('new.person@example.com', 'correct horse 42') IS NULL, 'locked account rejects even the right password');
  UPDATE users SET locked_until = now() - interval '1 second' WHERE id = v_id;
  PERFORM rw_test.check(authenticate('new.person@example.com', 'correct horse 42') = v_id, 'account works again after the lock expires');

  -- change password revokes refresh tokens (US-19)
  INSERT INTO refresh_tokens(user_id, token_hash, expires_at) VALUES (v_id, 'tok-hash-1', now() + interval '5 days');
  PERFORM set_config('app.current_user_id', v_id::text, true);
  PERFORM rw_test.check(change_password(v_id, 'wrong', 'another pass 1') = false, 'change_password with a wrong old password is refused');
  PERFORM rw_test.check(change_password(v_id, 'correct horse 42', 'another pass 1') = true, 'change_password succeeds');
  PERFORM rw_test.check((SELECT revoked_at IS NOT NULL FROM refresh_tokens WHERE token_hash = 'tok-hash-1'), 'password change revoked refresh tokens');
  PERFORM rw_test.check(authenticate('new.person@example.com', 'another pass 1') = v_id, 'new password works');
  PERFORM set_config('app.current_user_id', '', true);
END $$;
SELECT rw_test.expect_error($$SELECT register_user('x@y.zz','short',true)$$, '23514', 'password shorter than 8 characters rejected');
SELECT rw_test.expect_error($$SELECT register_user('x@y.zz','long enough pw',false)$$, '23514', 'registration without accepting the privacy notice rejected');
SELECT rw_test.expect_error($$SELECT change_password('11111111-1111-1111-1111-111111111111','a','bbbbbbbbb')$$, '42501', 'changing the password of another user rejected');

-- =============================================================== 4. vehicle triggers
DO $$
DECLARE u uuid; a uuid; b uuid; c uuid;
BEGIN
  u := register_user('car.owner@example.com', 'long enough pw', true);
  INSERT INTO vehicles(user_id,name,fuel_type,consumption_l100) VALUES (u,'first','diesel',5.0) RETURNING id INTO a;
  PERFORM rw_test.check((SELECT is_default FROM vehicles WHERE id = a), 'the first vehicle becomes default automatically');
  INSERT INTO vehicles(user_id,name,fuel_type,consumption_l100) VALUES (u,'second','diesel',6.0) RETURNING id INTO b;
  PERFORM rw_test.check(NOT (SELECT is_default FROM vehicles WHERE id = b), 'the second vehicle is not default');
  UPDATE vehicles SET is_default = true WHERE id = b;
  PERFORM rw_test.check((SELECT count(*) FROM vehicles WHERE user_id = u AND is_default) = 1
                        AND (SELECT is_default FROM vehicles WHERE id = b), 'marking a default unmarks the previous one');
  INSERT INTO vehicles(user_id,name,fuel_type,consumption_l100,is_default) VALUES (u,'third','diesel',7.0,true) RETURNING id INTO c;
  PERFORM rw_test.check((SELECT count(*) FROM vehicles WHERE user_id = u AND is_default) = 1, 'inserting with is_default keeps exactly one default');
  DELETE FROM vehicles WHERE id = c;
  PERFORM rw_test.check((SELECT count(*) FROM vehicles WHERE user_id = u AND is_default) = 1, 'deleting the default vehicle promotes another one');
  UPDATE vehicles SET created_at = now() - interval '1 day' WHERE id = a;   -- now() is fixed inside a transaction
  UPDATE vehicles SET name = 'renamed' WHERE id = a;
  PERFORM rw_test.check((SELECT updated_at > created_at FROM vehicles WHERE id = a), 'updated_at is set by trigger');
END $$;

-- =============================================================== 5. history is protected
SELECT rw_test.expect_error($$UPDATE trips SET total_eur = 1 WHERE id = 'c0000000-0000-0000-0000-000000000001'$$,
  '23000', 'saved trip cannot be changed (only the title)');
DO $$
BEGIN
  UPDATE trips SET title = 'Renamed trip' WHERE id = 'c0000000-0000-0000-0000-000000000001';
  PERFORM rw_test.check((SELECT title FROM trips WHERE id = 'c0000000-0000-0000-0000-000000000001') = 'Renamed trip', 'the title of a saved trip can be edited');
  DELETE FROM vehicles WHERE id = 'a0000000-0000-0000-0000-000000000002';
  PERFORM rw_test.check((SELECT vehicle_id IS NULL AND vehicle_name = 'Old Golf (no tank data)' FROM trips WHERE id = 'c0000000-0000-0000-0000-000000000001'),
                        'deleting a vehicle keeps its trips with the vehicle snapshot (US-04)');
END $$;

-- =============================================================== 6. roles and row-level security
DO $$
DECLARE n int; r record;
BEGIN
  PERFORM rw_test.check(NOT (SELECT rolsuper OR rolcreaterole OR rolcreatedb FROM pg_roles WHERE rolname = 'rw_app'), 'rw_app is not a superuser');
  PERFORM rw_test.check(NOT (SELECT rolsuper FROM pg_roles WHERE rolname = 'rw_owner'), 'rw_owner is not a superuser');
  PERFORM rw_test.check((SELECT count(*) FROM pg_roles WHERE rolname IN ('app_read','app_write','rw_admin')) = 3, 'roles app_read, app_write, rw_admin exist');

  SET LOCAL ROLE rw_app;
  PERFORM set_config('app.current_user_id', '11111111-1111-1111-1111-111111111111', true);
  SELECT count(*) INTO n FROM vehicles;   PERFORM rw_test.check(n = 1, 'rw_app (Anna) sees only her own vehicles (RLS)');
  SELECT count(*) INTO n FROM trips;      PERFORM rw_test.check(n = 2, 'rw_app (Anna) sees only her own trips (RLS)');
  SELECT count(*) INTO n FROM trip_cost_lines; PERFORM rw_test.check(n = 9, 'cost lines of other users are invisible (RLS)');
  SELECT count(*) INTO n FROM v_trip_history;  PERFORM rw_test.check(n = 2, 'view v_trip_history respects RLS (security_invoker)');
  PERFORM rw_test.check((SELECT arrival_local FROM v_trip_history WHERE id='c0000000-0000-0000-0000-000000000002') = timestamp '2026-10-12 16:00',
                        'arrival is shown in destination local time (CZ, UTC+2 in October)');
  SELECT count(*) INTO n FROM countries;  PERFORM rw_test.check(n = 8, 'rw_app can read reference data');
  PERFORM rw_test.check((SELECT price_eur_l FROM mv_latest_fuel_prices WHERE country_code='LT' AND fuel_type='diesel') > 0, 'rw_app can read latest fuel prices');
  PERFORM set_config('app.current_user_id', '', true);
  SELECT count(*) INTO n FROM vehicles;   PERFORM rw_test.check(n = 0, 'without a user context nothing is visible');
  RESET ROLE;
END $$;
SELECT rw_test.expect_error($$SET LOCAL ROLE rw_app; SELECT password_hash FROM rw.users$$, '42501', 'rw_app cannot read password_hash');
SELECT rw_test.expect_error($$SET LOCAL ROLE rw_app; SELECT set_config('app.current_user_id','11111111-1111-1111-1111-111111111111',true);
   INSERT INTO rw.vehicles(user_id,name,fuel_type,consumption_l100) VALUES ('22222222-2222-2222-2222-222222222222','x','diesel',5)$$,
  '42501', 'rw_app cannot insert a vehicle for another user (RLS WITH CHECK)');
SELECT rw_test.expect_error($$SET LOCAL ROLE rw_app; UPDATE rw.trips SET total_eur = 0$$, '42501', 'rw_app cannot update trip amounts (column privilege)');
SELECT rw_test.expect_error($$SET LOCAL ROLE rw_app; INSERT INTO rw.countries VALUES ('XX','x','Europe/Vilnius', NULL)$$, '42501', 'rw_app cannot change reference data');
SELECT rw_test.expect_error($$SET LOCAL ROLE rw_app; DROP TABLE rw.trips$$, '42501', 'rw_app cannot drop tables');
SELECT rw_test.expect_error($$SET LOCAL ROLE rw_app; CREATE TABLE rw.hack(x int)$$, '42501', 'rw_app cannot create objects');
SELECT rw_test.expect_error($$SET LOCAL ROLE rw_app; TRUNCATE rw.trips$$, '42501', 'rw_app cannot truncate');
SELECT rw_test.expect_error($$SET LOCAL ROLE rw_reporting; SELECT * FROM rw.users$$, '42501', 'rw_reporting cannot read the users table');
SELECT rw_test.expect_error($$SET LOCAL ROLE rw_reporting; SELECT * FROM rw.trips$$, '42501', 'rw_reporting cannot read raw trips');
SELECT rw_test.expect_error($$SET LOCAL ROLE rw_reporting; INSERT INTO rw.fuel_prices(country_code,fuel_type,price_eur_l,bulletin_date) VALUES ('LT','diesel',1.5,'2031-01-01')$$,
  '42501', 'rw_reporting is read-only');
SELECT rw_test.expect_error($$SET LOCAL ROLE rw_admin_user; SELECT password_hash FROM rw.users$$, '42501', 'administrator cannot read password hashes either');
DO $$
DECLARE n int;
BEGIN
  SET LOCAL ROLE rw_admin_user;
  UPDATE toll_tariffs SET rate_eur = 13.000 WHERE country_code = 'CZ';
  PERFORM rw_test.check(FOUND, 'administrator can update tariffs');
  CALL import_fuel_price('LT', 'diesel', 1.499, date '2026-11-16');
  CALL finish_fuel_import('ok', date '2026-11-16', 'test');
  PERFORM rw_test.check((SELECT bulletin_date FROM mv_latest_fuel_prices WHERE country_code='LT' AND fuel_type='diesel') = date '2026-11-16',
                        'import procedure adds a price and refreshes the materialized view');
  CALL import_fuel_price('LT', 'diesel', 1.999, date '2026-11-16');            -- duplicate week is ignored
  PERFORM rw_test.check((SELECT price_eur_l FROM fuel_prices WHERE country_code='LT' AND fuel_type='diesel' AND bulletin_date = date '2026-11-16') = 1.499,
                        'a repeated import of the same week does not overwrite the price');
  CALL finish_fuel_import('failed', NULL, 'source unavailable');
  PERFORM rw_test.check((SELECT bulletin_date FROM mv_latest_fuel_prices WHERE country_code='LT' AND fuel_type='diesel') = date '2026-11-16',
                        'a failed import keeps the old prices (US-21)');
  RESET ROLE;
END $$;

-- =============================================================== 7. views, masking, functions
DO $$
BEGIN
  PERFORM rw_test.check(mask_email('anna.k@example.com') = 'a***@example.com', 'mask_email hides the local part');
  PERFORM rw_test.check(mask_email(NULL) IS NULL, 'mask_email keeps NULL');
  PERFORM rw_test.check(season_for(date '2026-11-01') = 'winter' AND season_for(date '2026-03-31') = 'winter'
                        AND season_for(date '2026-04-01') = 'summer' AND season_for(date '2026-10-31') = 'summer', 'season_for: winter = 1 Nov - 31 Mar');
  PERFORM rw_test.check((SELECT count(*) FROM v_fuel_price_trend WHERE country_code='DE' AND fuel_type='diesel') = 12, 'price trend returns at most 12 bulletins');
  PERFORM rw_test.check((SELECT bulletin_date FROM mv_latest_fuel_prices WHERE country_code='EE' AND fuel_type='petrol95') = date '2026-11-02',
                        'a country without the newest bulletin keeps its latest older price');
  PERFORM rw_test.check((SELECT count(*) FROM v_current_speed_profiles) = 8, 'one speed profile per country for the current season');
  PERFORM rw_test.check((SELECT fuel_eur FROM v_trip_cost_summary WHERE trip_id = 'c0000000-0000-0000-0000-000000000002') = 102.49
                        AND (SELECT lodging_eur FROM v_trip_cost_summary WHERE trip_id = 'c0000000-0000-0000-0000-000000000002') = 70.00, 'cost summary view adds lines by kind');
  PERFORM rw_test.check((SELECT has_missing_data FROM v_trip_cost_summary WHERE trip_id = 'c0000000-0000-0000-0000-000000000004'), 'cost summary flags missing data');
  SET LOCAL ROLE rw_reporting;
  PERFORM rw_test.check((SELECT bool_and(email_masked ~ '^.\*\*\*@') FROM v_users_masked), 'reporting view shows only masked e-mails');
  PERFORM rw_test.check(NOT EXISTS (SELECT 1 FROM v_users_masked WHERE email_masked LIKE '%anna.k%'), 'no full e-mail in the masked view');
  PERFORM rw_test.check((SELECT count(*) FROM v_trips_anonymized) >= 4, 'anonymized trips are readable for reporting');
  RESET ROLE;
  PERFORM rw_test.check(NOT EXISTS (SELECT 1 FROM information_schema.columns
     WHERE table_schema='rw' AND table_name='v_trips_anonymized' AND column_name IN ('user_id','title','origin_label','destination_label','vehicle_name')),
     'anonymized trips contain no user id, titles or place names');
END $$;

-- =============================================================== 8. account deletion (US-20)
DO $$
DECLARE u uuid;
BEGIN
  SET LOCAL ROLE rw_app;
  u := register_user('to.delete@example.com', 'long enough pw', true);
  PERFORM set_config('app.current_user_id', u::text, true);
  INSERT INTO vehicles(user_id,name,fuel_type,consumption_l100) VALUES (u,'tmp','diesel',5);
  PERFORM delete_account(u);
  RESET ROLE;
  PERFORM rw_test.check(NOT EXISTS (SELECT 1 FROM users WHERE id = u) AND NOT EXISTS (SELECT 1 FROM vehicles WHERE user_id = u),
                        'delete_account removes the user and all personal data (cascade)');
END $$;

ROLLBACK;     -- nothing from the tests stays in the database
