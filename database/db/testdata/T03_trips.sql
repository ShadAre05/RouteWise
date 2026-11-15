-- Test data 03: saved trips with cost lines and refuel stops. SYNTHETIC; totals equal the sum of lines
-- (checked at COMMIT by the deferred trigger trips_total_check). Idempotent.
DO $$
DECLARE anna constant uuid := '11111111-1111-1111-1111-111111111111';
        boris constant uuid := '22222222-2222-2222-2222-222222222222';
BEGIN
  IF EXISTS (SELECT 1 FROM trips WHERE id = 'c0000000-0000-0000-0000-000000000001') THEN RETURN; END IF;

  -- 1) Vilnius -> Warsaw, same day, no tolls known for LT ("no data" line), no refuel plan (no tank)
  INSERT INTO trips (id, user_id, vehicle_id, title, origin_label, destination_label, departed_at, variant,
                     distance_km, duration_min, daily_limit_h, nights, arrival_at, destination_country,
                     vehicle_name, fuel_type, consumption_l100, price_date, total_eur)
  VALUES ('c0000000-0000-0000-0000-000000000001', anna, 'a0000000-0000-0000-0000-000000000002',
          'Vilnius - Warsaw', 'Vilnius', 'Warsaw', '2026-11-01 07:00+02', 'fastest',
          450.0, 330, 8.0, 0, '2026-11-01 12:30+02', 'PL',
          'Old Golf (no tank data)', 'petrol95', 8.5, '2026-11-02', 83.72);
  INSERT INTO trip_cost_lines (trip_id, kind, country_code, distance_km, unit_price, amount_eur, status) VALUES
   ('c0000000-0000-0000-0000-000000000001','fuel','LT',170.0,1.550,22.40,'ok'),
   ('c0000000-0000-0000-0000-000000000001','fuel','PL',280.0,1.400,33.32,'ok'),
   ('c0000000-0000-0000-0000-000000000001','road','LT',NULL,NULL,NULL,'no_data'),
   ('c0000000-0000-0000-0000-000000000001','road','PL',280.0,0.100,28.00,'estimate');

  -- 2) Vilnius -> Prague, 2 nights, user-entered lodging, vignette, refuel plan with 2 stops
  INSERT INTO trips (id, user_id, vehicle_id, title, origin_label, destination_label, departed_at, variant,
                     distance_km, duration_min, daily_limit_h, nights, stop_city, stop_country, arrival_at,
                     destination_country, vehicle_name, fuel_type, consumption_l100, tank_l, start_fuel,
                     refuel_saving_eur, price_date, lodging_entered_eur, total_eur)
  VALUES ('c0000000-0000-0000-0000-000000000002', anna, 'a0000000-0000-0000-0000-000000000001',
          'Vilnius - Prague', 'Vilnius', 'Prague', '2026-10-10 09:00+03', 'fastest',
          1020.0, 750, 8.0, 2, 'Lodz', 'PL', '2026-10-12 16:00+02',
          'CZ', 'Skoda Octavia', 'petrol95', 7.0, 50.0, 'half',
          2.15, '2026-10-05', 70.00, 184.49);
  INSERT INTO trip_cost_lines (trip_id, kind, country_code, distance_km, unit_price, amount_eur, status) VALUES
   ('c0000000-0000-0000-0000-000000000002','fuel','LT',150.0,1.550,16.28,'ok'),
   ('c0000000-0000-0000-0000-000000000002','fuel','PL',700.0,1.400,68.60,'ok'),
   ('c0000000-0000-0000-0000-000000000002','fuel','CZ',170.0,1.480,17.61,'ok'),
   ('c0000000-0000-0000-0000-000000000002','road','CZ',NULL,12.000,12.00,'ok'),
   ('c0000000-0000-0000-0000-000000000002','lodging','PL',NULL,NULL,70.00,'user_entered');
  INSERT INTO trip_refuel_stops (trip_id, seq, country_code, km_from_start, liters, price_eur_l) VALUES
   ('c0000000-0000-0000-0000-000000000002',1,'PL',420.0,30.0,1.400),
   ('c0000000-0000-0000-0000-000000000002',2,'PL',900.0,28.0,1.400);

  -- 3) Copenhagen -> Berlin, vehicle later deleted (vehicle_id NULL), EU-average price line
  INSERT INTO trips (id, user_id, vehicle_id, title, origin_label, destination_label, departed_at, variant,
                     distance_km, duration_min, nights, arrival_at, destination_country,
                     vehicle_name, fuel_type, consumption_l100, price_date, total_eur)
  VALUES ('c0000000-0000-0000-0000-000000000003', boris, NULL,
          'Copenhagen - Berlin', 'Copenhagen', 'Berlin', '2026-09-20 08:00+02', 'shortest',
          200.0, 150, 0, '2026-09-20 10:30+02', 'DE',
          'Deleted car', 'diesel', 5.5, '2026-09-14', 19.91);
  INSERT INTO trip_cost_lines (trip_id, kind, country_code, distance_km, unit_price, amount_eur, status) VALUES
   ('c0000000-0000-0000-0000-000000000003','fuel','DK',80.0,1.900,8.36,'ok'),
   ('c0000000-0000-0000-0000-000000000003','fuel','DE',120.0,1.750,11.55,'eu_average');

  -- 4) Tallinn -> Riga: no road and no lodging data for EE (only fuel is counted)
  INSERT INTO trips (id, user_id, vehicle_id, title, origin_label, destination_label, departed_at, variant,
                     distance_km, duration_min, nights, arrival_at, destination_country,
                     vehicle_name, fuel_type, consumption_l100, price_date, total_eur)
  VALUES ('c0000000-0000-0000-0000-000000000004', boris, 'b0000000-0000-0000-0000-000000000001',
          'Tallinn - Riga', 'Tallinn', 'Riga', '2026-11-05 10:00+02', 'no_tolls',
          310.0, 230, 0, '2026-11-05 14:50+02', 'LV',
          'VW Passat TDI', 'diesel', 5.5, '2026-11-02', 26.15);
  INSERT INTO trip_cost_lines (trip_id, kind, country_code, distance_km, unit_price, amount_eur, status) VALUES
   ('c0000000-0000-0000-0000-000000000004','fuel','EE',120.0,1.540,10.16,'ok'),
   ('c0000000-0000-0000-0000-000000000004','fuel','LV',190.0,1.530,15.99,'ok'),
   ('c0000000-0000-0000-0000-000000000004','road','EE',NULL,NULL,NULL,'no_data'),
   ('c0000000-0000-0000-0000-000000000004','lodging','LV',NULL,NULL,NULL,'no_data');
END
$$;
