
## Views

| View | Purpose | Notes |
|---|---|---|
| `mv_latest_fuel_prices` (materialized) | latest price per country and fuel | refreshed by `finish_fuel_import` after a successful weekly import; unique index allows `REFRESH ... CONCURRENTLY` |
| `v_fuel_price_trend` | last 12 bulletins per country and fuel (US-31) | window function `row_number()` |
| `v_current_speed_profiles` | speed profile of the current season (US-26) | uses `season_for(current_date)` |
| `v_trip_history` | history list with arrival in the destination's local time (US-17, US-29) | `security_invoker`: row-level security of the caller applies |
| `v_trip_cost_summary` | fuel / road / lodging totals per trip | `security_invoker` |
| `v_users_masked` | users with masked e-mail and pseudonym | for reporting; see masking in `security.md` |
| `v_trips_anonymized` | trips without user id, titles, place names | month instead of exact time |

## Functions, procedures, triggers

| Object | Kind | What it does |
|---|---|---|
| `register_user`, `authenticate`, `change_password`, `delete_account` | functions (SECURITY DEFINER) | bcrypt hashing, lock after 5 failures, token revocation, erasure (US-01, 02, 19, 20) |
| `import_fuel_price`, `finish_fuel_import` | procedures | weekly import, log, refresh of the materialized view (US-09, US-21) |
| `season_for`, `local_arrival`, `mask_email`, `current_app_user` | functions | helpers |
| `trg_set_updated_at` | trigger | `updated_at` on vehicles and reference tables |
| `trg_vehicle_default`, `trg_vehicle_after_delete` | triggers | exactly one default vehicle per user |
| `trg_password_changed` | trigger | stamps the change and revokes refresh tokens |
| `trg_trip_immutable` | trigger | saved trip is read-only except the title |
| `trg_check_trip_total` | deferred constraint trigger | `total_eur` = sum of cost lines, checked at COMMIT |
| `trg_trip_vehicle_owner` | trigger | trip's vehicle belongs to the same user |
