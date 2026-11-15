# Data dictionary — RouteWise

DBMS: **PostgreSQL 16 + PostGIS 3** (extensions `postgis`, `pgcrypto`), schema `rw`. Track: OLTP (many small transactions: login, save a trip, read history).

## Layers

| Layer | Colour in the diagram | Tables | Who changes it |
|---|---|---|---|
| Accounts (E1) | blue | `users`, `refresh_tokens` | the user, through functions |
| Vehicles (E2) | green | `vehicles` | the user |
| Reference data (E4, E5, E7) | orange | `countries`, `fuel_prices`, `fuel_import_log`, `toll_tariffs`, `lodging_rates`, `speed_profiles` | the weekly import and the administrator |
| Trip history (E6) | violet | `trips`, `trip_cost_lines`, `trip_refuel_stops` | created once when a trip is saved; afterwards read-only (except the title) |

Why layers: reference data changes rarely and is read by everybody; user data is private and protected by row-level security; history must never change after saving. Each layer therefore gets different privileges (see `security.md`).

A separate analytical layer is **not** needed: the app is OLTP, and reporting is covered by views (`v_*`) and one materialized view.

![Logical schema](logical_schema.png)

## Enumerations (ENUM types)

| Type | Values | Used in |
|---|---|---|
| `user_role` | user, admin | users.role |
| `fuel_type` | petrol95, diesel | vehicles, fuel_prices, trips |
| `route_variant` | fastest, shortest, no_tolls | trips.variant |
| `tariff_kind` | toll, vignette | toll_tariffs.kind |
| `tariff_unit` | per_km, per_trip | toll_tariffs.unit |
| `lodging_class` | economy, standard, comfort | lodging_rates.class |
| `season` | summer, winter | speed_profiles.season |
| `cost_kind` | fuel, road, lodging | trip_cost_lines.kind |
| `line_status` | ok, no_data, estimate, eu_average, user_entered | trip_cost_lines.status |
| `import_status` | ok, failed | fuel_import_log.status |
| `fuel_level` | full, three_quarter, half, quarter | trips.start_fuel |

Enumerations are used instead of lookup tables because the sets are small, fixed by the product (changing them changes application code too) and checked by the database without a join.

