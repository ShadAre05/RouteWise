-- Reference seed 02: speed profiles, lodging rates, road charges.
-- !! DEMO VALUES. They make the application work out of the box; they are NOT verified data.
-- !! The administrator must check each row against its official source and update 'source'/'checked_on'
-- !! before production use (the 'source' column says so on every row). Idempotent (ON CONFLICT).

INSERT INTO speed_profiles (country_code, season, avg_speed_kmh, motorway_limit_kmh, limit_note, source, checked_on) VALUES
 ('LT','summer', 88, 130, NULL,                           'DEMO seed - not verified', current_date),
 ('LT','winter', 80, 110, NULL,                           'DEMO seed - not verified', current_date),
 ('LV','summer', 82, 110, NULL,                           'DEMO seed - not verified', current_date),
 ('LV','winter', 76, 110, NULL,                           'DEMO seed - not verified', current_date),
 ('EE','summer', 84, 110, NULL,                           'DEMO seed - not verified', current_date),
 ('EE','winter', 76,  90, 'lower limit in winter',        'DEMO seed - not verified', current_date),
 ('PL','summer', 90, 140, NULL,                           'DEMO seed - not verified', current_date),
 ('PL','winter', 84, 140, NULL,                           'DEMO seed - not verified', current_date),
 ('DE','summer', 98, NULL,'no general limit, advised 130','DEMO seed - not verified', current_date),
 ('DE','winter', 92, NULL,'no general limit, advised 130','DEMO seed - not verified', current_date),
 ('DK','summer', 92, 130, NULL,                           'DEMO seed - not verified', current_date),
 ('DK','winter', 86, 130, NULL,                           'DEMO seed - not verified', current_date),
 ('CZ','summer', 90, 130, NULL,                           'DEMO seed - not verified', current_date),
 ('CZ','winter', 84, 130, NULL,                           'DEMO seed - not verified', current_date),
 ('SK','summer', 88, 130, NULL,                           'DEMO seed - not verified', current_date),
 ('SK','winter', 82, 130, NULL,                           'DEMO seed - not verified', current_date)
ON CONFLICT (country_code, season) DO NOTHING;

INSERT INTO lodging_rates (country_code, class, nightly_eur, checked_on)
SELECT c.code, l.class, round(b.base * l.factor, 2), current_date
  FROM (VALUES ('LT',55),('LV',58),('EE',65),('PL',52),('DE',90),('DK',110),('CZ',60),('SK',55)) AS b(code, base)
  JOIN countries c ON c.code = b.code
 CROSS JOIN (VALUES ('economy'::lodging_class, 0.7), ('standard', 1.0), ('comfort', 1.8)) AS l(class, factor)
ON CONFLICT (country_code, class) DO NOTHING;            -- DEMO values, verify before production

INSERT INTO toll_tariffs (country_code, kind, name, rate_eur, unit, checked_on) VALUES
 ('CZ','vignette','Motorway vignette, 10 days (demo)', 12.000, 'per_trip', current_date),
 ('SK','vignette','Motorway vignette, 10 days (demo)', 12.000, 'per_trip', current_date),
 ('PL','toll',    'Paid motorway section (demo)',      0.100,  'per_km',   current_date)
ON CONFLICT (country_code, kind, name) DO NOTHING;       -- no row => the app shows "no data" for that country
