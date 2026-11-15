# Access roles, passwords, personal data

## Roles (least privilege)
| Role | Type | Purpose | Rights |
|---|---|---|---|
| `rw_owner` | login, not superuser | owns the schema, runs migrations | all on schema `rw` (only used by the deployment) |
| `app_read` | group (NOLOGIN) | read reference data | SELECT on reference tables, `mv_latest_fuel_prices`, trend and speed views |
| `app_write` | group (NOLOGIN), member of `app_read` | the user's own data | SELECT/INSERT/UPDATE/DELETE on `vehicles`, `refresh_tokens`; SELECT/INSERT/DELETE on trips, lines, stops; `UPDATE (title)` only on `trips`; SELECT on non-secret columns of `users`; EXECUTE on login/registration functions |
| `rw_admin` | group (NOLOGIN) | maintain reference data | INSERT/UPDATE/DELETE on tariffs, lodging, speed profiles; INSERT on prices and import log; EXECUTE on import procedures |
| `rw_app` | login | **the application back-end** | member of `app_write` (and so of `app_read`); `NOSUPERUSER` |
| `rw_reporting` | login | reports | SELECT only on `v_users_masked`, `v_trips_anonymized` |
| `rw_admin_user` | login | the database administrator for reference data | member of `app_read`, `rw_admin` |

The application connects as `rw_app`. It is **not** a superuser, does not own tables, cannot `DROP`, `CREATE`, `TRUNCATE`, cannot read `password_hash`, cannot change reference data and cannot change the amounts of a saved trip. All of this is checked by `db/tests/01_schema_logic_security.sql`, and the last test step logs in as `rw_app` and asserts `rolsuper = false`.

**Adding new objects.** Each migration that creates a table/view/function must contain its own `GRANT` lines. `ALTER DEFAULT PRIVILEGES` already removes access for `PUBLIC` on new tables and functions, so a forgotten GRANT fails closed (nobody can use the object) instead of open.

## Row-level security (RLS)
RLS is enabled on `users`, `refresh_tokens`, `vehicles`, `trips`, `trip_cost_lines`, `trip_refuel_stops`. Policies compare the row's owner with `current_app_user()` (the session setting `app.current_user_id`). Without the setting nobody sees anything. Even a bug in the back-end query (a missing `WHERE user_id = ...`) cannot expose another user's data. The owner `rw_owner` is not subject to RLS (it runs migrations and the SECURITY DEFINER functions).

## Passwords
- Only a hash is stored. `register_user`/`change_password` use `crypt(password, gen_salt('bf', 12))` from pgcrypto: **bcrypt, cost 12, random salt per password**. MD5 and SHA-1 are not used anywhere.
- A CHECK constraint accepts only `$2a/$2b/$2y$..` (bcrypt) or `$argon2id$` values, so a plain or MD5 value cannot be inserted by mistake. (If the back-end prefers to hash with Argon2 itself, the format is allowed.)
- `rw_app` has no SELECT privilege on `users.password_hash`; the hash is compared only inside `authenticate()` (SECURITY DEFINER with a fixed `search_path`).
- Refresh tokens: only a hash is stored.
- **No secret is stored in the repository.** Database passwords come from environment variables (`.env`, ignored by Git; `.env.example` has placeholders). Test users get random passwords that nobody knows.

## Personal data and masking
The only personal data is the e-mail address (and the trips of that person). Measures:
- `mask_email('anna.k@example.com') = 'a***@example.com'`.
- `v_users_masked`: pseudonym (first 12 hex characters of SHA-256 of the random UUID), masked e-mail, role, registration date. Reporting role has no access to the real table.
- `v_trips_anonymized`: no user id (pseudonym instead), no title, no origin/destination text, no vehicle name, departure rounded to the month.
- Coordinates are never stored (only text labels), GPS position is not saved (US-28).
- Erasure: `delete_account()` deletes the user and, by cascade, vehicles, tokens, trips, lines and stops (US-20, GDPR).
