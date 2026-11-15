# Indexes (what each one is for)

An index is added only for a query the application really runs. Every index slows writes a little, so there are no "just in case" indexes.

| Index | Table (columns) | Query it serves | Why |
|---|---|---|---|
| `users_email_uq` (unique) | users (email) | login by e-mail (US-02), duplicate check at registration | exact lookup on every login |
| `refresh_tokens_user_active` (partial, `WHERE revoked_at IS NULL`) | refresh_tokens (user_id) | revoke all active tokens on password change | small: only active tokens are indexed |
| `refresh_tokens.token_hash` (unique) | refresh_tokens | token check on every request | exact lookup |
| `vehicles_user_idx` | vehicles (user_id) | vehicle list of a user; RLS filter | every vehicle query filters by owner |
| `vehicles_one_default_uq` (partial unique, `WHERE is_default`) | vehicles (user_id) | — | integrity: one default per user |
| `trips_user_created_idx` | trips (user_id, created_at DESC) | history, newest first (US-17) | one index serves filter + sort |
| `trips_vehicle_idx` | trips (vehicle_id) | `ON DELETE SET NULL` when a vehicle is deleted | without it each vehicle delete scans all trips |
| `trip_cost_lines_trip_idx` | trip_cost_lines (trip_id) | cost lines of a trip, cascade delete | FK column |
| `trip_refuel_stops_uq` (unique) | trip_refuel_stops (trip_id, seq) | stops of a trip in order | integrity + lookup |
| `fuel_prices_uq` (unique) | fuel_prices (country_code, fuel_type, bulletin_date) | latest price, 12-week trend, duplicate import | an ascending unique index is also read backwards for `ORDER BY bulletin_date DESC`, so no second index is needed |
| `mv_latest_fuel_prices_uq` | materialized view | price lookup per calculation | allows `REFRESH ... CONCURRENTLY` |
| `toll_tariffs_country_idx` | toll_tariffs (country_code, kind) | road charges of the countries on a route | |
| `countries_geom_gix` (GiST) | countries (geom) | which country contains a point / how long is the route in each country (US-07) | spatial index |
| unique constraints of `lodging_rates`, `speed_profiles` | (country, class) / (country, season) | lookups of the app | integrity + lookup |

## Evidence (real `EXPLAIN` output, PostgreSQL 16, 2 000 users × 20 trips = 40 000 trips)

```
-- login by e-mail
Index Scan using users_email_uq on users
  Index Cond: ((email)::text = 'u77@example.com'::text)

-- history of one user
Limit
  ->  Sort (Sort Key: trips.created_at DESC)
        ->  Bitmap Heap Scan on trips
              ->  Bitmap Index Scan on trips_user_created_idx
                    Index Cond: (user_id = $0)

-- which country contains a point
Index Scan using countries_geom_gix on countries
  Index Cond: (geom && '0101000020E6...'::geometry)
```
Notes: with only ~20 rows per user the planner chooses a bitmap scan of the same index and sorts the small result; with a user who has hundreds of trips it walks the index in `created_at DESC` order. The tables `fuel_prices` (≈190 rows) and `mv_latest_fuel_prices` (16 rows) are so small that PostgreSQL correctly prefers a sequential scan; their indexes are there for integrity and for future growth (the price history grows by 16 rows a week).

How to reproduce: load the test data and run `EXPLAIN (COSTS OFF) <query>`. For a large volume, inside a transaction that is rolled back afterwards, insert users and trips with `generate_series`, run `ANALYZE trips`, and repeat the `EXPLAIN`.
