-- V004: business logic inside the database: helper functions, triggers, procedures.
-- Every SECURITY DEFINER function pins search_path (protection against search_path hijacking).

-- ---------- helpers ----------------------------------------------------------------
CREATE FUNCTION current_app_user() RETURNS uuid
LANGUAGE sql STABLE AS
$$ SELECT nullif(current_setting('app.current_user_id', true), '')::uuid $$;
COMMENT ON FUNCTION current_app_user() IS
  'Id of the signed-in user. The backend runs SET LOCAL app.current_user_id = ... at the start of every request; row-level security uses it.';

CREATE FUNCTION season_for(d date) RETURNS season
LANGUAGE sql IMMUTABLE AS
$$ SELECT CASE WHEN extract(month FROM d) IN (11,12,1,2,3) THEN 'winter'::season ELSE 'summer'::season END $$;
COMMENT ON FUNCTION season_for(date) IS 'US-26: winter = 1 Nov - 31 Mar, otherwise summer.';

CREATE FUNCTION mask_email(p_email text) RETURNS text
LANGUAGE sql IMMUTABLE AS
$$ SELECT CASE WHEN p_email IS NULL THEN NULL
               ELSE left(split_part(p_email,'@',1),1) || '***@' || split_part(p_email,'@',2) END $$;
COMMENT ON FUNCTION mask_email(text) IS 'Personal-data masking: anna.k@example.com -> a***@example.com.';

-- ---------- trigger functions -------------------------------------------------------
CREATE FUNCTION trg_set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS
$$ BEGIN NEW.updated_at := now(); RETURN NEW; END $$;

CREATE TRIGGER vehicles_updated      BEFORE UPDATE ON vehicles      FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
CREATE TRIGGER toll_tariffs_updated  BEFORE UPDATE ON toll_tariffs  FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
CREATE TRIGGER lodging_rates_updated BEFORE UPDATE ON lodging_rates FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
CREATE TRIGGER speed_profiles_updated BEFORE UPDATE ON speed_profiles FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- Exactly one default vehicle per user: the first vehicle becomes default,
-- marking another one as default unmarks the previous one (US-03).
CREATE FUNCTION trg_vehicle_default() RETURNS trigger
LANGUAGE plpgsql AS
$$
BEGIN
  IF TG_OP = 'INSERT' AND NOT EXISTS (SELECT 1 FROM vehicles WHERE user_id = NEW.user_id) THEN
    NEW.is_default := true;
  END IF;
  IF NEW.is_default THEN
    UPDATE vehicles SET is_default = false
     WHERE user_id = NEW.user_id AND id <> NEW.id AND is_default;
  END IF;
  RETURN NEW;
END
$$;
CREATE TRIGGER vehicles_default BEFORE INSERT OR UPDATE OF is_default ON vehicles
  FOR EACH ROW EXECUTE FUNCTION trg_vehicle_default();

-- When the default vehicle is deleted, the oldest remaining one becomes default.
CREATE FUNCTION trg_vehicle_after_delete() RETURNS trigger
LANGUAGE plpgsql AS
$$
BEGIN
  IF OLD.is_default THEN
    UPDATE vehicles SET is_default = true
     WHERE id = (SELECT id FROM vehicles WHERE user_id = OLD.user_id ORDER BY created_at, id LIMIT 1);
  END IF;
  RETURN NULL;
END
$$;
CREATE TRIGGER vehicles_after_delete AFTER DELETE ON vehicles
  FOR EACH ROW EXECUTE FUNCTION trg_vehicle_after_delete();

-- Password change (US-19): stamp the time and revoke all refresh tokens.
CREATE FUNCTION trg_password_changed() RETURNS trigger
LANGUAGE plpgsql AS
$$
BEGIN
  NEW.password_changed_at := now();
  NEW.failed_login_count  := 0;
  NEW.locked_until        := NULL;
  UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = NEW.id AND revoked_at IS NULL;
  RETURN NEW;
END
$$;
CREATE TRIGGER users_password_changed BEFORE UPDATE OF password_hash ON users
  FOR EACH ROW WHEN (OLD.password_hash IS DISTINCT FROM NEW.password_hash)
  EXECUTE FUNCTION trg_password_changed();

-- History is immutable: only the title may be edited after saving (US-16, US-17).
-- The one allowed side effect is vehicle_id -> NULL, which the foreign key sets when the vehicle is deleted (US-04).
CREATE FUNCTION trg_trip_immutable() RETURNS trigger
LANGUAGE plpgsql AS
$$
BEGIN
  IF (to_jsonb(NEW) - 'title' - 'vehicle_id') IS DISTINCT FROM (to_jsonb(OLD) - 'title' - 'vehicle_id')
     OR (NEW.vehicle_id IS DISTINCT FROM OLD.vehicle_id AND NEW.vehicle_id IS NOT NULL) THEN
    RAISE EXCEPTION 'saved trip % is read-only except its title', OLD.id
      USING ERRCODE = 'integrity_constraint_violation';
  END IF;
  RETURN NEW;
END
$$;
CREATE TRIGGER trips_immutable BEFORE UPDATE ON trips
  FOR EACH ROW EXECUTE FUNCTION trg_trip_immutable();

-- total_eur must equal the sum of the visible cost lines (US-11). Checked at COMMIT,
-- so a trip and its lines can be inserted in any order inside one transaction.
CREATE FUNCTION trg_check_trip_total() RETURNS trigger
LANGUAGE plpgsql AS
$$
DECLARE
  v_trip uuid;
  v_total numeric;
  v_sum numeric;
BEGIN
  IF TG_TABLE_NAME = 'trips' THEN
    v_trip := NEW.id;
  ELSIF TG_OP = 'DELETE' THEN
    v_trip := OLD.trip_id;
  ELSE
    v_trip := NEW.trip_id;
  END IF;
  SELECT total_eur INTO v_total FROM trips WHERE id = v_trip;
  IF NOT FOUND THEN RETURN NULL; END IF;               -- the trip was deleted: nothing to check
  SELECT coalesce(sum(amount_eur), 0) INTO v_sum FROM trip_cost_lines WHERE trip_id = v_trip;
  IF v_total <> v_sum THEN
    RAISE EXCEPTION 'trip %: total_eur % differs from the sum of cost lines %', v_trip, v_total, v_sum
      USING ERRCODE = 'check_violation';
  END IF;
  RETURN NULL;
END
$$;
CREATE CONSTRAINT TRIGGER trips_total_check AFTER INSERT ON trips
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION trg_check_trip_total();
CREATE CONSTRAINT TRIGGER cost_lines_total_check AFTER INSERT OR UPDATE OR DELETE ON trip_cost_lines
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION trg_check_trip_total();

-- A trip's vehicle must belong to the same user.
CREATE FUNCTION trg_trip_vehicle_owner() RETURNS trigger
LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.vehicle_id IS NOT NULL AND NOT EXISTS
     (SELECT 1 FROM vehicles WHERE id = NEW.vehicle_id AND user_id = NEW.user_id) THEN
    RAISE EXCEPTION 'vehicle % does not belong to user %', NEW.vehicle_id, NEW.user_id
      USING ERRCODE = 'foreign_key_violation';
  END IF;
  RETURN NEW;
END
$$;
CREATE TRIGGER trips_vehicle_owner BEFORE INSERT ON trips
  FOR EACH ROW EXECUTE FUNCTION trg_trip_vehicle_owner();

-- ---------- authentication functions (the only way to touch password_hash) ----------
-- bcrypt through pgcrypto, cost 12. Plain passwords are never stored or logged.
CREATE FUNCTION register_user(p_email text, p_password text, p_privacy_accepted boolean)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = rw, public AS
$$
DECLARE v_id uuid;
BEGIN
  IF NOT p_privacy_accepted THEN
    RAISE EXCEPTION 'privacy notice must be accepted' USING ERRCODE = 'check_violation';
  END IF;
  IF length(p_password) < 8 THEN
    RAISE EXCEPTION 'password must have at least 8 characters' USING ERRCODE = 'check_violation';
  END IF;
  INSERT INTO users (email, password_hash, privacy_accepted_at)
  VALUES (lower(btrim(p_email)), crypt(p_password, gen_salt('bf', 12)), now())
  RETURNING id INTO v_id;
  RETURN v_id;
END
$$;

-- Returns the user id on success, NULL on failure. 5 failures lock the account for 10 minutes (US-02).
CREATE FUNCTION authenticate(p_email text, p_password text)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = rw, public AS
$$
DECLARE u users%ROWTYPE;
BEGIN
  SELECT * INTO u FROM users WHERE email = lower(btrim(p_email)) FOR UPDATE;
  IF NOT FOUND THEN RETURN NULL; END IF;
  IF u.locked_until IS NOT NULL AND u.locked_until > now() THEN RETURN NULL; END IF;
  IF u.password_hash = crypt(p_password, u.password_hash) THEN
    UPDATE users SET failed_login_count = 0, locked_until = NULL WHERE id = u.id;
    RETURN u.id;
  END IF;
  UPDATE users
     SET failed_login_count = CASE WHEN failed_login_count + 1 >= 5 THEN 0 ELSE failed_login_count + 1 END,
         locked_until       = CASE WHEN failed_login_count + 1 >= 5 THEN now() + interval '10 minutes' END
   WHERE id = u.id;
  RETURN NULL;
END
$$;

CREATE FUNCTION change_password(p_user uuid, p_old text, p_new text)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = rw, public AS
$$
DECLARE v_hash text;
BEGIN
  IF p_user IS DISTINCT FROM current_app_user() THEN
    RAISE EXCEPTION 'not allowed' USING ERRCODE = 'insufficient_privilege';
  END IF;
  IF length(p_new) < 8 THEN
    RAISE EXCEPTION 'password must have at least 8 characters' USING ERRCODE = 'check_violation';
  END IF;
  SELECT password_hash INTO v_hash FROM users WHERE id = p_user;
  IF v_hash IS NULL OR v_hash <> crypt(p_old, v_hash) THEN RETURN false; END IF;
  UPDATE users SET password_hash = crypt(p_new, gen_salt('bf', 12)) WHERE id = p_user;   -- trigger revokes tokens
  RETURN true;
END
$$;

-- US-20: delete the account with all personal data (cascade removes vehicles, trips, tokens).
CREATE FUNCTION delete_account(p_user uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = rw, public AS
$$
BEGIN
  IF p_user IS DISTINCT FROM current_app_user() THEN
    RAISE EXCEPTION 'not allowed' USING ERRCODE = 'insufficient_privilege';
  END IF;
  DELETE FROM users WHERE id = p_user;
END
$$;

-- ---------- reference-data procedures ------------------------------------------------
-- Weekly fuel price import (US-09, US-21): one transaction per call; a duplicate week is ignored.
CREATE PROCEDURE import_fuel_price(p_country char, p_fuel fuel_type, p_price numeric, p_bulletin date)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = rw, public AS
$$
BEGIN
  INSERT INTO fuel_prices (country_code, fuel_type, price_eur_l, bulletin_date)
  VALUES (upper(p_country), p_fuel, p_price, p_bulletin)
  ON CONFLICT (country_code, fuel_type, bulletin_date) DO NOTHING;
END
$$;

CREATE PROCEDURE finish_fuel_import(p_status import_status, p_bulletin date, p_message text DEFAULT NULL)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = rw, public AS
$$
BEGIN
  INSERT INTO fuel_import_log (status, bulletin_date, message) VALUES (p_status, p_bulletin, p_message);
  IF p_status = 'ok' THEN
    REFRESH MATERIALIZED VIEW mv_latest_fuel_prices;      -- failed import: old prices stay (US-21)
  END IF;
END
$$;

-- Time-zone aware arrival (US-29): shows the arrival in the destination country's local time.
CREATE FUNCTION local_arrival(p_arrival timestamptz, p_country char) RETURNS timestamp
LANGUAGE sql STABLE AS
$$ SELECT p_arrival AT TIME ZONE (SELECT timezone FROM countries WHERE code = p_country) $$;
