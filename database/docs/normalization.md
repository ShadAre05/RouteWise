# Normalization (3NF) and deliberate exceptions

**Definition used.** 1NF: atomic values, no repeating groups. 2NF: every non-key column depends on the whole key. 3NF: no non-key column depends on another non-key column (no transitive dependencies).

## Checks per table

| Table | Key | Result |
|---|---|---|
| `users` | `id` | all columns describe the user; e-mail is a candidate key (UNIQUE) |
| `refresh_tokens` | `id` | one row per token; user data is not repeated, only `user_id` |
| `vehicles` | `id` | no data of the owner except `user_id` |
| `countries` | `code` | name, time zone and borders depend only on the country |
| `fuel_prices` | `id`, candidate key (`country_code`, `fuel_type`, `bulletin_date`) | price depends on the whole candidate key; the country name is not repeated |
| `toll_tariffs`, `lodging_rates`, `speed_profiles` | `id`, candidate keys (country, kind/class/season …) | values depend on the whole candidate key |
| `trips` | `id` | see exceptions below |
| `trip_cost_lines` | `id` | one row per cost line: **repeating groups are moved out of `trips`** |
| `trip_refuel_stops` | `id`, (`trip_id`, `seq`) | the refuel plan is a table, **not a JSON column** (1NF) |

## What was changed to reach 3NF
- The refuel plan (stops with country, km, litres, price) is stored in `trip_refuel_stops`, one row per stop, instead of a JSON document. No JSON columns exist in the database (a test checks it).
- Cost lines are a separate table (a trip has a variable number of lines).
- Country names are stored once, in `countries`; every other table references the code.

## Deliberate, documented denormalization (history snapshot)
1. **`trips.vehicle_name`, `fuel_type`, `consumption_l100`, `tank_l`** repeat vehicle data. This is *not* a transitive dependency on `vehicle_id`: the values describe the vehicle **as it was at the moment of the calculation**. The user may later edit or delete the vehicle, but an old trip must keep showing what it was calculated with (US-04, US-16). Dependence is on the trip (`id`), not on the current vehicle row.
2. **`trips.price_date`** and prices in `trip_cost_lines.unit_price` are historical facts for the same reason: prices in `fuel_prices` keep changing.
3. **`trips.total_eur`** is derived (sum of `trip_cost_lines.amount_eur`). It is kept because the history list shows totals for many trips and must not recompute them; consistency is enforced by the deferred trigger `trips_total_check`, so the redundancy cannot produce a contradiction.

These three are the only intentional departures, and each one is protected by a constraint or a trigger.
