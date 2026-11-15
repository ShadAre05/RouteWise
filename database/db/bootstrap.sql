-- Run ONCE as superuser (scripts/bootstrap.sh). Creates database objects that need superuser rights:
-- roles, the database, the extensions and the schema. No password is stored in this file:
-- they are passed as psql variables from the environment (.env, not committed).
--
-- Role model (least privilege, the application never runs as superuser):
--   rw_owner      owns the schema and runs migrations (LOGIN, no superuser)
--   app_read      group: read reference data and masked/aggregated views
--   app_write     group: work with the user's own data (RLS-restricted)
--   rw_admin      group: maintain reference data and fuel price imports
--   rw_app        login of the mobile-app backend  -> member of app_read + app_write
--   rw_reporting  login for read-only reporting      -> member of app_read
--   rw_admin_user login for the administrator       -> member of app_read + rw_admin
\set ON_ERROR_STOP on

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE', 'rw_owner', :'RW_OWNER_PASSWORD')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rw_owner') \gexec
SELECT format('CREATE ROLE %I NOLOGIN', r) FROM unnest(ARRAY['app_read','app_write','rw_admin']) r
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) \gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER', 'rw_app', :'RW_APP_PASSWORD')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rw_app') \gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER', 'rw_reporting', :'RW_REPORTING_PASSWORD')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rw_reporting') \gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER', 'rw_admin_user', :'RW_ADMIN_PASSWORD')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rw_admin_user') \gexec

GRANT app_read TO app_write;            -- whoever writes user data also reads reference data
GRANT app_write TO rw_app;
GRANT app_read TO rw_reporting;
GRANT app_read, rw_admin TO rw_admin_user;

SELECT format('CREATE DATABASE %I OWNER rw_owner', :'RW_DB')
 WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'RW_DB') \gexec

\connect :RW_DB
CREATE EXTENSION IF NOT EXISTS postgis;     -- geometry type, ST_Intersection (km per country, US-07)
CREATE EXTENSION IF NOT EXISTS pgcrypto;    -- crypt()/gen_salt(): bcrypt password hashing (NFR-4)
REVOKE ALL ON DATABASE :"RW_DB" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"RW_DB" TO rw_owner, rw_app, rw_reporting, rw_admin_user;
CREATE SCHEMA IF NOT EXISTS rw AUTHORIZATION rw_owner;
REVOKE ALL ON SCHEMA rw FROM PUBLIC;
GRANT USAGE ON SCHEMA rw TO app_read, app_write, rw_admin;
ALTER ROLE rw_owner   SET search_path = rw, public;
ALTER ROLE rw_app     SET search_path = rw, public;
ALTER ROLE rw_reporting SET search_path = rw, public;
ALTER ROLE rw_admin_user SET search_path = rw, public;
