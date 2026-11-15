-- V006: access control. Least privilege; the application never runs as superuser and never owns objects.
--   rw_owner (owner, runs migrations)  >  rw_admin (reference data)  >  app_write (user data)  >  app_read (reads)
-- Everything below is repeated for new objects through ALTER DEFAULT PRIVILEGES (see the end of the file).

REVOKE ALL ON ALL TABLES    IN SCHEMA rw FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA rw FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA rw FROM PUBLIC;

-- ---------- row-level security: a user sees only their own rows ----------------------
ALTER TABLE users          ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE vehicles       ENABLE ROW LEVEL SECURITY;
ALTER TABLE trips          ENABLE ROW LEVEL SECURITY;
ALTER TABLE trip_cost_lines   ENABLE ROW LEVEL SECURITY;
ALTER TABLE trip_refuel_stops ENABLE ROW LEVEL SECURITY;

CREATE POLICY users_self     ON users          FOR SELECT TO app_write USING (id = current_app_user());
CREATE POLICY tokens_self    ON refresh_tokens FOR ALL    TO app_write USING (user_id = current_app_user()) WITH CHECK (user_id = current_app_user());
CREATE POLICY vehicles_self  ON vehicles       FOR ALL    TO app_write USING (user_id = current_app_user()) WITH CHECK (user_id = current_app_user());
CREATE POLICY trips_self     ON trips          FOR ALL    TO app_write USING (user_id = current_app_user()) WITH CHECK (user_id = current_app_user());
CREATE POLICY cost_lines_self ON trip_cost_lines FOR ALL  TO app_write
  USING      (EXISTS (SELECT 1 FROM trips t WHERE t.id = trip_id AND t.user_id = current_app_user()))
  WITH CHECK (EXISTS (SELECT 1 FROM trips t WHERE t.id = trip_id AND t.user_id = current_app_user()));
CREATE POLICY refuel_stops_self ON trip_refuel_stops FOR ALL TO app_write
  USING      (EXISTS (SELECT 1 FROM trips t WHERE t.id = trip_id AND t.user_id = current_app_user()))
  WITH CHECK (EXISTS (SELECT 1 FROM trips t WHERE t.id = trip_id AND t.user_id = current_app_user()));

-- ---------- app_read: reference data and read-only views ------------------------------
GRANT SELECT ON countries, fuel_prices, toll_tariffs, lodging_rates, speed_profiles,
                mv_latest_fuel_prices, v_fuel_price_trend, v_current_speed_profiles TO app_read;
GRANT EXECUTE ON FUNCTION season_for(date), local_arrival(timestamptz, char), mask_email(text) TO app_read;

-- ---------- app_write: the user's own data ---------------------------------------------
-- users: only non-secret columns are readable; password_hash is reachable solely through the SECURITY DEFINER functions.
GRANT SELECT (id, email, role, privacy_accepted_at, password_changed_at, created_at) ON users TO app_write;
GRANT SELECT, INSERT, UPDATE, DELETE ON refresh_tokens TO app_write;
GRANT SELECT, INSERT, UPDATE, DELETE ON vehicles       TO app_write;
GRANT SELECT, INSERT, DELETE         ON trips          TO app_write;
GRANT UPDATE (title)                 ON trips          TO app_write;        -- history is read-only except the title
GRANT SELECT, INSERT, DELETE         ON trip_cost_lines, trip_refuel_stops TO app_write;
GRANT SELECT ON v_trip_history, v_trip_cost_summary TO app_write;
GRANT EXECUTE ON FUNCTION current_app_user(), register_user(text, text, boolean), authenticate(text, text),
                          change_password(uuid, text, text), delete_account(uuid) TO app_write;

-- ---------- rw_admin: reference data maintenance and the weekly import ----------------
GRANT INSERT, UPDATE, DELETE ON toll_tariffs, lodging_rates, speed_profiles TO rw_admin;
GRANT UPDATE (name, timezone, geom) ON countries TO rw_admin;
GRANT INSERT ON fuel_prices, fuel_import_log TO rw_admin;
GRANT SELECT ON fuel_import_log TO rw_admin;
GRANT EXECUTE ON PROCEDURE import_fuel_price(char, fuel_type, numeric, date),
                           finish_fuel_import(import_status, date, text) TO rw_admin;

-- ---------- reporting: masked / anonymized views only ----------------------------------
GRANT SELECT ON v_users_masked, v_trips_anonymized TO rw_reporting;

-- ---------- sequences and future objects -------------------------------------------------
GRANT USAGE ON ALL SEQUENCES IN SCHEMA rw TO app_write, rw_admin;
-- objects created by later migrations are closed by default; grants are listed in each migration
ALTER DEFAULT PRIVILEGES FOR ROLE rw_owner IN SCHEMA rw REVOKE ALL ON TABLES    FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE rw_owner IN SCHEMA rw REVOKE ALL ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE rw_owner IN SCHEMA rw GRANT USAGE ON SEQUENCES TO app_write, rw_admin;
