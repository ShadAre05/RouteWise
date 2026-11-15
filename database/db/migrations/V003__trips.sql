-- V003: trip history (E6). Saved trips keep a snapshot of the vehicle and of the price date,
-- so that history does not change when the user edits a vehicle or prices are updated (US-04, US-16).
-- The refuel plan is a normal child table (3NF, no JSON).

CREATE TABLE trips (
  id                  uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             uuid          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  vehicle_id          uuid          REFERENCES vehicles(id) ON DELETE SET NULL,
  title               varchar(120)  NOT NULL,
  origin_label        varchar(120)  NOT NULL,     -- text only: coordinates are never stored
  destination_label   varchar(120)  NOT NULL,
  departed_at         timestamptz   NOT NULL,
  variant             route_variant NOT NULL DEFAULT 'fastest',
  distance_km         numeric(7,1)  NOT NULL CHECK (distance_km > 0),
  duration_min        integer       NOT NULL CHECK (duration_min > 0),
  daily_limit_h       numeric(3,1)  CHECK (daily_limit_h BETWEEN 4 AND 10),
  nights              smallint      NOT NULL DEFAULT 0 CHECK (nights >= 0),
  stop_city           varchar(80),
  stop_country        char(2)       REFERENCES countries(code),
  arrival_at          timestamptz,
  destination_country char(2)       REFERENCES countries(code),
  -- vehicle snapshot (deliberate, documented denormalization of history)
  vehicle_name        varchar(60)   NOT NULL,
  fuel_type           fuel_type     NOT NULL,
  consumption_l100    numeric(3,1)  NOT NULL CHECK (consumption_l100 BETWEEN 2.0 AND 30.0),
  tank_l              numeric(4,1)  CHECK (tank_l BETWEEN 20 AND 120),
  start_fuel          fuel_level,
  refuel_saving_eur   numeric(7,2),                -- tip only, not part of total_eur (US-30)
  price_date          date          NOT NULL,      -- oldest bulletin date used in the calculation
  lodging_entered_eur numeric(8,2)  CHECK (lodging_entered_eur > 0),
  total_eur           numeric(9,2)  NOT NULL CHECK (total_eur >= 0),
  created_at          timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT trips_arrival_after_departure CHECK (arrival_at IS NULL OR arrival_at >= departed_at),
  CONSTRAINT trips_nights_need_stop        CHECK (nights = 0 OR daily_limit_h IS NOT NULL),
  CONSTRAINT trips_refuel_needs_tank       CHECK (start_fuel IS NULL OR tank_l IS NOT NULL)
);
CREATE INDEX trips_user_created_idx ON trips (user_id, created_at DESC);   -- history, newest first (US-17)
CREATE INDEX trips_vehicle_idx      ON trips (vehicle_id);                  -- ON DELETE SET NULL lookups

CREATE TABLE trip_cost_lines (
  id           bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  trip_id      uuid         NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
  kind         cost_kind    NOT NULL,
  country_code char(2)      REFERENCES countries(code),
  distance_km  numeric(7,1) CHECK (distance_km >= 0),
  unit_price   numeric(7,3) CHECK (unit_price >= 0),
  amount_eur   numeric(9,2) CHECK (amount_eur >= 0),
  status       line_status  NOT NULL DEFAULT 'ok',
  CONSTRAINT cost_lines_no_data_has_no_amount CHECK ((status = 'no_data') = (amount_eur IS NULL))
);
CREATE INDEX trip_cost_lines_trip_idx ON trip_cost_lines (trip_id);

CREATE TABLE trip_refuel_stops (
  id           bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  trip_id      uuid         NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
  seq          smallint     NOT NULL CHECK (seq > 0),
  country_code char(2)      NOT NULL REFERENCES countries(code),
  km_from_start numeric(7,1) NOT NULL CHECK (km_from_start >= 0),
  liters       numeric(5,1) NOT NULL CHECK (liters > 0),
  price_eur_l  numeric(5,3) NOT NULL CHECK (price_eur_l BETWEEN 0.500 AND 4.000),
  CONSTRAINT trip_refuel_stops_uq UNIQUE (trip_id, seq)
);
