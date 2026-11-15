# Integrity and transaction handling

## Integrity mechanisms
| Mechanism | Where | Examples |
|---|---|---|
| Primary keys | every table | UUID for user data (not guessable), identity `bigint/int` for service tables, `countries.code` natural key |
| Foreign keys | all relations | `ON DELETE CASCADE` for data owned by a user (account erasure, US-20); `ON DELETE SET NULL` for `trips.vehicle_id` and `updated_by` (history survives); no action for reference data (a country cannot be deleted while used) |
| NOT NULL | all mandatory columns | `users.email`, `trips.total_eur`, `speed_profiles.source` |
| UNIQUE | business keys | e-mail; (country, fuel, bulletin date); (country, class); (country, season); (trip, seq) |
| CHECK | domain rules | consumption 2.0–30.0, tank 20–120, price 0.500–4.000, `avg_speed <= motorway limit`, `arrival >= departure`, bcrypt/argon2 hash format |
| Partial unique index | `vehicles_one_default_uq` | at most one default vehicle per user |
| Triggers | see data dictionary | default vehicle, history immutability, total check, password change |
| Row-level security | user tables | a user sees only their own rows (see `security.md`) |
| Data types | everywhere | `numeric` for money and prices (no floating point), `timestamptz` for moments, `geometry` for borders, ENUM for closed sets |

## Transactions
PostgreSQL default isolation **READ COMMITTED** is used: transactions are short and touch the rows of one user, so write conflicts are rare. Every statement is atomic; the places that need several statements use one explicit transaction:

| Operation | Transaction content | Guarantee |
|---|---|---|
| Save a trip (US-16) | `INSERT trips` + `INSERT trip_cost_lines` (+ `trip_refuel_stops`) in **one** transaction | all or nothing; the deferred trigger checks `total_eur` at COMMIT, so the order of inserts is free and a wrong total makes the whole COMMIT fail |
| Login (US-02) | `authenticate()` runs `SELECT ... FOR UPDATE` on the user row | concurrent wrong logins cannot skip the counter; lock after 5 failures |
| Change password (US-19) | `UPDATE users` + trigger `UPDATE refresh_tokens` | the new password and token revocation cannot be separated |
| Delete account (US-20) | `DELETE users` + cascade | all personal data disappears together |
| Weekly price import (US-09, US-21) | one `CALL import_fuel_price` per row, then `finish_fuel_import` | a duplicate week is ignored (`ON CONFLICT DO NOTHING`); a failed import is logged and the materialized view is **not** refreshed, so old prices stay |
| Default vehicle | trigger inside the statement's transaction | the partial unique index would reject a second default even under concurrency |

Back-end rule: set `SET LOCAL app.current_user_id = '<uuid>'` inside the request transaction (it is reset automatically at COMMIT/ROLLBACK, so pooled connections cannot leak the user).

## Failure behaviour (tested)
A violated rule raises SQLSTATE `23514` (check), `23505` (unique), `23503` (foreign key), `42501` (privilege). The tests in `db/tests` assert the exact code for each rule.
