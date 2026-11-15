-- V001: enumerations, accounts (E1), vehicles (E2).
-- Executed by rw_owner in schema rw (search_path = rw, public). Extensions are created by bootstrap.sql.

CREATE TYPE user_role     AS ENUM ('user', 'admin');
CREATE TYPE fuel_type     AS ENUM ('petrol95', 'diesel');
CREATE TYPE route_variant AS ENUM ('fastest', 'shortest', 'no_tolls');                  -- US-22
CREATE TYPE tariff_kind   AS ENUM ('toll', 'vignette');                                 -- US-10, US-25
CREATE TYPE tariff_unit   AS ENUM ('per_km', 'per_trip');
CREATE TYPE lodging_class AS ENUM ('economy', 'standard', 'comfort');                   -- US-14
CREATE TYPE season        AS ENUM ('summer', 'winter');                                 -- US-26
CREATE TYPE cost_kind     AS ENUM ('fuel', 'road', 'lodging');
CREATE TYPE line_status   AS ENUM ('ok', 'no_data', 'estimate', 'eu_average', 'user_entered');
CREATE TYPE import_status AS ENUM ('ok', 'failed');                                     -- US-21
CREATE TYPE fuel_level    AS ENUM ('full', 'three_quarter', 'half', 'quarter');         -- US-30

CREATE TABLE users (
  id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
  email               varchar(254) NOT NULL,
  password_hash       text         NOT NULL,
  role                user_role    NOT NULL DEFAULT 'user',
  privacy_accepted_at timestamptz  NOT NULL,
  password_changed_at timestamptz,
  failed_login_count  smallint     NOT NULL DEFAULT 0,
  locked_until        timestamptz,
  created_at          timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT users_email_format  CHECK (email = lower(email) AND email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
  -- only slow salted hashes are accepted: bcrypt ($2a/$2b/$2y) or argon2id; MD5/SHA-1/plain text are impossible
  CONSTRAINT users_hash_format   CHECK (password_hash ~ '^\$(2[aby]\$[0-9]{2}\$|argon2id\$)'),
  CONSTRAINT users_failed_nonneg CHECK (failed_login_count >= 0)
);
CREATE UNIQUE INDEX users_email_uq ON users (email);

CREATE TABLE refresh_tokens (
  id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash text        NOT NULL UNIQUE,                  -- only the hash of the token is stored
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT refresh_tokens_expiry CHECK (expires_at > created_at)
);
CREATE INDEX refresh_tokens_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;

CREATE TABLE vehicles (
  id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name             varchar(60)  NOT NULL CHECK (length(btrim(name)) > 0),
  fuel_type        fuel_type    NOT NULL,
  consumption_l100 numeric(3,1) NOT NULL CHECK (consumption_l100 BETWEEN 2.0 AND 30.0),
  tank_l           numeric(4,1)          CHECK (tank_l BETWEEN 20 AND 120),   -- optional; NULL = no refuel plan (US-30)
  is_default       boolean      NOT NULL DEFAULT false,
  created_at       timestamptz  NOT NULL DEFAULT now(),
  updated_at       timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX vehicles_user_idx ON vehicles (user_id);
CREATE UNIQUE INDEX vehicles_one_default_uq ON vehicles (user_id) WHERE is_default;
