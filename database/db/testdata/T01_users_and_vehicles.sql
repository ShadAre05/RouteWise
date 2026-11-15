-- Test data 01: users and vehicles. SYNTHETIC. Passwords are random and unknown (hashes only);
-- the tests create their own users with known passwords inside a rolled-back transaction.
-- Fixed UUIDs keep the data deterministic. Idempotent.
DO $$
DECLARE
  anna  constant uuid := '11111111-1111-1111-1111-111111111111';
  boris constant uuid := '22222222-2222-2222-2222-222222222222';
  adm   constant uuid := '33333333-3333-3333-3333-333333333333';
  lock_ constant uuid := '44444444-4444-4444-4444-444444444444';
  empty constant uuid := '55555555-5555-5555-5555-555555555555';
BEGIN
  IF EXISTS (SELECT 1 FROM users WHERE id = anna) THEN RETURN; END IF;
  INSERT INTO users (id, email, password_hash, role, privacy_accepted_at, locked_until, failed_login_count) VALUES
   (anna,  'anna.k@example.com',    crypt(encode(gen_random_bytes(16),'hex'), gen_salt('bf', 10)), 'user',  now() - interval '90 days', NULL, 0),
   (boris, 'boris@example.com',     crypt(encode(gen_random_bytes(16),'hex'), gen_salt('bf', 10)), 'user',  now() - interval '60 days', NULL, 2),
   (adm,   'admin@example.com',     crypt(encode(gen_random_bytes(16),'hex'), gen_salt('bf', 10)), 'admin', now() - interval '120 days', NULL, 0),
   (lock_, 'locked.user@example.com', crypt(encode(gen_random_bytes(16),'hex'), gen_salt('bf', 10)), 'user', now() - interval '10 days', now() + interval '10 minutes', 0),
   (empty, 'new.user@example.com',  crypt(encode(gen_random_bytes(16),'hex'), gen_salt('bf', 10)), 'user',  now(), NULL, 0);

  INSERT INTO vehicles (id, user_id, name, fuel_type, consumption_l100, tank_l, is_default) VALUES
   ('a0000000-0000-0000-0000-000000000001', anna,  'Skoda Octavia',       'petrol95', 7.0, 50.0, true),
   ('a0000000-0000-0000-0000-000000000002', anna,  'Old Golf (no tank data)', 'petrol95', 8.5, NULL, false),   -- edge: no tank => no refuel plan
   ('b0000000-0000-0000-0000-000000000001', boris, 'VW Passat TDI',        'diesel',   5.5, 66.0, true),
   ('b0000000-0000-0000-0000-000000000002', boris, 'Tiny city car',        'petrol95', 2.0, 20.0, false);      -- edge: lower bounds of the CHECKs

  -- refresh tokens: one active, one expired, one revoked (hashes only, random)
  INSERT INTO refresh_tokens (user_id, token_hash, expires_at, created_at, revoked_at) VALUES
   (anna,  encode(digest(gen_random_bytes(16),'sha256'),'hex'), now() + interval '20 days', now() - interval '1 day',  NULL),
   (anna,  encode(digest(gen_random_bytes(16),'sha256'),'hex'), now() - interval '1 day',   now() - interval '31 days', NULL),
   (boris, encode(digest(gen_random_bytes(16),'sha256'),'hex'), now() + interval '10 days', now() - interval '2 days',  now() - interval '1 day');

  UPDATE toll_tariffs SET updated_by = adm WHERE updated_by IS NULL;
END
$$;
