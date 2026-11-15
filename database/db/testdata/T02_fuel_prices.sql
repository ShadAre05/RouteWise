-- Test data 02: 12 weekly bulletins per country and fuel. SYNTHETIC numbers (not real EC Oil Bulletin prices).
-- Edge cases: EE petrol has no row for the latest week (older price_date); one failed import is logged.
INSERT INTO fuel_prices (country_code, fuel_type, price_eur_l, bulletin_date)
SELECT b.code, f.fuel, round(b.base + f.delta + 0.004 * (((w * 7 + b.idx * 3) % 5) - 2), 3),
       date '2026-11-09' - 7 * w
  FROM (VALUES ('LT',1,1.550),('LV',2,1.570),('EE',3,1.580),('PL',4,1.400),
               ('DE',5,1.720),('DK',6,1.850),('CZ',7,1.480),('SK',8,1.600)) AS b(code, idx, base)
 CROSS JOIN (VALUES ('petrol95'::fuel_type, 0.000), ('diesel', -0.040)) AS f(fuel, delta)
 CROSS JOIN generate_series(0, 11) AS w
 WHERE NOT (b.code = 'EE' AND f.fuel = 'petrol95' AND w = 0)
ON CONFLICT (country_code, fuel_type, bulletin_date) DO NOTHING;

INSERT INTO fuel_import_log (status, bulletin_date, message)
SELECT 'ok', date '2026-11-09', 'synthetic test import'
 WHERE NOT EXISTS (SELECT 1 FROM fuel_import_log);
INSERT INTO fuel_import_log (status, bulletin_date, message)
SELECT 'failed', NULL, 'edge case: source unavailable, previous prices kept'
 WHERE (SELECT count(*) FROM fuel_import_log) = 1;

REFRESH MATERIALIZED VIEW mv_latest_fuel_prices;
