-- V002: reference data (E4, E5, E7): countries, fuel prices, tariffs, lodging, speed profiles.

CREATE TABLE countries (
  code     char(2)     PRIMARY KEY CHECK (code = upper(code)),
  name     varchar(40) NOT NULL UNIQUE,
  timezone varchar(40) NOT NULL CHECK (timezone ~ '^[A-Za-z]+/[A-Za-z_]+$'),   -- IANA zone for US-29
  geom     geometry(MultiPolygon, 4326) NOT NULL                                -- borders, km per country (US-07)
);
CREATE INDEX countries_geom_gix ON countries USING gist (geom);

CREATE TABLE fuel_prices (
  id            bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  country_code  char(2)       NOT NULL REFERENCES countries(code),
  fuel_type     fuel_type     NOT NULL,
  price_eur_l   numeric(5,3)  NOT NULL CHECK (price_eur_l BETWEEN 0.500 AND 4.000),   -- sanity range
  bulletin_date date          NOT NULL,
  imported_at   timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT fuel_prices_uq UNIQUE (country_code, fuel_type, bulletin_date)           -- history is kept (US-31)
);

CREATE TABLE fuel_import_log (
  id            bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  started_at    timestamptz   NOT NULL DEFAULT now(),
  status        import_status NOT NULL,
  bulletin_date date,
  message       text,
  CONSTRAINT fuel_import_ok_has_date CHECK (status <> 'ok' OR bulletin_date IS NOT NULL)
);

CREATE TABLE toll_tariffs (
  id           integer      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  country_code char(2)      NOT NULL REFERENCES countries(code),
  kind         tariff_kind  NOT NULL,
  name         varchar(80)  NOT NULL,
  rate_eur     numeric(7,3) NOT NULL CHECK (rate_eur >= 0),
  unit         tariff_unit  NOT NULL,
  checked_on   date         NOT NULL,
  updated_by   uuid         REFERENCES users(id) ON DELETE SET NULL,
  updated_at   timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT toll_tariffs_uq UNIQUE (country_code, kind, name)
);
CREATE INDEX toll_tariffs_country_idx ON toll_tariffs (country_code, kind);

CREATE TABLE lodging_rates (
  id           integer       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  country_code char(2)       NOT NULL REFERENCES countries(code),
  class        lodging_class NOT NULL,
  nightly_eur  numeric(7,2)  NOT NULL CHECK (nightly_eur > 0),
  checked_on   date          NOT NULL,
  updated_by   uuid          REFERENCES users(id) ON DELETE SET NULL,
  updated_at   timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT lodging_rates_uq UNIQUE (country_code, class)
);

CREATE TABLE speed_profiles (
  id                 integer      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  country_code       char(2)      NOT NULL REFERENCES countries(code),
  season             season       NOT NULL,
  avg_speed_kmh      numeric(5,1) NOT NULL CHECK (avg_speed_kmh BETWEEN 30 AND 130),
  motorway_limit_kmh smallint     CHECK (motorway_limit_kmh BETWEEN 50 AND 160),   -- NULL = no general limit
  limit_note         varchar(80),
  source             varchar(160) NOT NULL,
  checked_on         date         NOT NULL,
  updated_by         uuid         REFERENCES users(id) ON DELETE SET NULL,
  updated_at         timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT speed_profiles_uq    UNIQUE (country_code, season),
  CONSTRAINT speed_profiles_avg_le_limit CHECK (motorway_limit_kmh IS NULL OR avg_speed_kmh <= motorway_limit_kmh)
);
