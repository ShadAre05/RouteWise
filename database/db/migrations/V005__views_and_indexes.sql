-- V005: views, materialized view, masking views.

-- Latest price per country and fuel (US-09). Materialized: read on every calculation, changes once a week.
CREATE MATERIALIZED VIEW mv_latest_fuel_prices AS
SELECT DISTINCT ON (country_code, fuel_type)
       country_code, fuel_type, price_eur_l, bulletin_date
  FROM fuel_prices
 ORDER BY country_code, fuel_type, bulletin_date DESC;
CREATE UNIQUE INDEX mv_latest_fuel_prices_uq ON mv_latest_fuel_prices (country_code, fuel_type);

-- Price trend: up to the last 12 bulletins per country and fuel (US-31).
CREATE VIEW v_fuel_price_trend AS
SELECT country_code, fuel_type, bulletin_date, price_eur_l
  FROM (SELECT p.*, row_number() OVER (PARTITION BY country_code, fuel_type ORDER BY bulletin_date DESC) AS rn
          FROM fuel_prices p) t
 WHERE rn <= 12;

-- Speed profile of the current season (US-26).
CREATE VIEW v_current_speed_profiles AS
SELECT s.country_code, s.season, s.avg_speed_kmh, s.motorway_limit_kmh, s.limit_note, s.checked_on
  FROM speed_profiles s
 WHERE s.season = season_for(current_date);

-- History list for the signed-in user (row-level security applies through security_invoker).
CREATE VIEW v_trip_history WITH (security_invoker = true) AS
SELECT t.id, t.title, t.origin_label, t.destination_label, t.departed_at, t.variant,
       t.distance_km, t.duration_min, t.nights, t.total_eur, t.price_date, t.created_at,
       t.arrival_at, local_arrival(t.arrival_at, t.destination_country) AS arrival_local,
       t.vehicle_name, t.vehicle_id IS NULL AS vehicle_deleted
  FROM trips t;

-- Cost breakdown per trip as one row (fuel / road / lodging), for the detail screen.
CREATE VIEW v_trip_cost_summary WITH (security_invoker = true) AS
SELECT l.trip_id,
       sum(l.amount_eur) FILTER (WHERE l.kind = 'fuel')    AS fuel_eur,
       sum(l.amount_eur) FILTER (WHERE l.kind = 'road')    AS road_eur,
       sum(l.amount_eur) FILTER (WHERE l.kind = 'lodging') AS lodging_eur,
       bool_or(l.status = 'no_data')                       AS has_missing_data
  FROM trip_cost_lines l
 GROUP BY l.trip_id;

-- ---------- personal-data masking (for reporting and support, role rw_reporting) --------
CREATE VIEW v_users_masked AS
SELECT left(encode(digest(id::text, 'sha256'), 'hex'), 12) AS user_pseudonym,   -- SHA-256 of a random UUID
       mask_email(email)                                   AS email_masked,
       role,
       created_at::date                                    AS registered_on,
       (locked_until IS NOT NULL AND locked_until > now()) AS is_locked
  FROM users;

CREATE VIEW v_trips_anonymized AS
SELECT left(encode(digest(user_id::text, 'sha256'), 'hex'), 12) AS user_pseudonym,
       date_trunc('month', departed_at)::date                   AS departure_month,   -- no exact time
       destination_country, variant, distance_km, duration_min, nights,
       fuel_type, total_eur
  FROM trips;                                                  -- titles, labels and vehicle names are left out
