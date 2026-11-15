#!/usr/bin/env python3
"""Generates docs/data_dictionary.md (tables part) and docs/logical_schema.{dot,svg,png} from the live database catalog,
so the documentation cannot drift from the real schema. Descriptions come from the dictionaries below.
Usage: PGHOST=... PGPORT=... PGDATABASE=... python3 docs/tools/gen_docs.py   (needs psql, graphviz)"""
import subprocess, os, json, sys, textwrap

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
def q(sql):
    out = subprocess.run(["psql", "-X", "-At", "-F", "\t", "-U", os.environ.get("PG_SUPERUSER", "postgres"), "-d",
                          os.environ.get("PGDATABASE", "routewise"), "-c", sql], capture_output=True, text=True, check=True).stdout
    return [l.split("\t") for l in out.splitlines() if l]

LAYERS = {  # layer -> (colour, tables)
 "Accounts (E1)":      ("#cfe3fb", ["users", "refresh_tokens"]),
 "Vehicles (E2)":      ("#d3f0d8", ["vehicles"]),
 "Reference data (E4, E5, E7)": ("#fde5c2", ["countries", "fuel_prices", "fuel_import_log", "toll_tariffs", "lodging_rates", "speed_profiles"]),
 "Trip history (E6)":  ("#e4d6f5", ["trips", "trip_cost_lines", "trip_refuel_stops"]),
}
TABLE_DESC = {
 "users": "Registered people and administrators of the app. The only table with personal data (e-mail).",
 "refresh_tokens": "Long-lived login tokens (US-02). Only a hash of the token is stored.",
 "vehicles": "Vehicles of a user: fuel type, consumption, optional tank size (US-03, US-30).",
 "countries": "The 8 supported countries with border geometry (km per country, US-07) and time zone (US-29).",
 "fuel_prices": "Weekly national average fuel prices from the EC Weekly Oil Bulletin; history is kept (US-09, US-31).",
 "fuel_import_log": "Result of every weekly import; a failed import leaves the old prices in place (US-21).",
 "toll_tariffs": "Road charges for passenger cars (tolls and vignettes) entered by the administrator (US-10, US-25).",
 "lodging_rates": "Typical nightly price per country and class (US-14, US-25).",
 "speed_profiles": "Average speed and motorway limit per country and season (US-26).",
 "trips": "Saved calculation (history, US-16/17). Immutable except the title; keeps a snapshot of the vehicle.",
 "trip_cost_lines": "Cost breakdown of a trip: fuel per country, road charges, lodging.",
 "trip_refuel_stops": "Refuel plan of a trip (US-30): where and how much to refuel. A tip, not part of the total.",
}
QUALITY = {
 "users": ["`email` is lower case, valid format, unique.", "`password_hash` is a bcrypt/argon2id hash (CHECK); the plain password never reaches the database tables.",
           "`failed_login_count` between 0 and 5; `locked_until` is set only after 5 failures."],
 "refresh_tokens": ["`expires_at` is after `created_at`.", "A revoked or expired token is never accepted by the backend."],
 "vehicles": ["`consumption_l100` 2.0-30.0; `tank_l` 20-120 or NULL.", "At most one default vehicle per user (partial unique index + trigger)."],
 "countries": ["Exactly the 8 supported countries; `geom` is a valid MultiPolygon in SRID 4326."],
 "fuel_prices": ["`price_eur_l` between 0.500 and 4.000 EUR/l (sanity range).", "One value per country, fuel and bulletin week (UNIQUE).",
                 "Freshness: the newest `bulletin_date` is at most 14 days old; otherwise the app shows the data age (US-12)."],
 "fuel_import_log": ["A successful import always has a bulletin date."],
 "toll_tariffs": ["`rate_eur` >= 0; `checked_on` is the date the administrator verified the value.", "No row for a country means 'no data' in the app."],
 "lodging_rates": ["`nightly_eur` > 0; one row per country and class."],
 "speed_profiles": ["`avg_speed_kmh` 30-130 and not above `motorway_limit_kmh`.", "`source` is mandatory: where the value was verified.", "No row means the routing-engine duration is used."],
 "trips": ["`total_eur` equals the sum of its cost lines (checked at COMMIT).", "`arrival_at` is not before `departed_at`.",
           "A trip's vehicle belongs to the same user; no coordinates are stored (labels are text only)."],
 "trip_cost_lines": ["`amount_eur` is NULL exactly when `status` = 'no_data'."],
 "trip_refuel_stops": ["`seq` unique inside a trip; `liters` > 0; price in the sanity range."],
}
COL = {  # "table.column": description
 "users.id": "Surrogate key (UUID, not guessable).", "users.email": "Login name; stored lower case. Personal data (masked in reports).",
 "users.password_hash": "bcrypt hash made by pgcrypto `crypt()`; never readable by the application role.",
 "users.role": "`user` or `admin` (application-level administrator).", "users.privacy_accepted_at": "When the privacy notice was accepted (US-01).",
 "users.password_changed_at": "Last password change (US-19).", "users.failed_login_count": "Consecutive failed logins (US-02).",
 "users.locked_until": "Account locked until this moment after 5 failures in a row.", "users.created_at": "Registration time.",
 "refresh_tokens.id": "Surrogate key.", "refresh_tokens.user_id": "Owner; deleted together with the user.", "refresh_tokens.token_hash": "Hash of the token (the token itself is never stored).",
 "refresh_tokens.expires_at": "Expiry time.", "refresh_tokens.revoked_at": "Set on logout or password change.", "refresh_tokens.created_at": "Issue time.",
 "vehicles.id": "Surrogate key.", "vehicles.user_id": "Owner.", "vehicles.name": "Display name, e.g. 'Skoda Octavia'.", "vehicles.fuel_type": "`petrol95` or `diesel`.",
 "vehicles.consumption_l100": "Entered consumption, litres per 100 km, used as is.", "vehicles.tank_l": "Tank size in litres; NULL means no refuel plan.",
 "vehicles.is_default": "Pre-selected vehicle.", "vehicles.created_at": "Creation time.", "vehicles.updated_at": "Set by trigger on every update.",
 "countries.code": "ISO 3166-1 alpha-2 code (natural key).", "countries.name": "English name.", "countries.timezone": "IANA zone, e.g. Europe/Vilnius.",
 "countries.geom": "Border geometry, SRID 4326.",
 "fuel_prices.id": "Surrogate key.", "fuel_prices.country_code": "Country.", "fuel_prices.fuel_type": "Fuel.", "fuel_prices.price_eur_l": "EUR per litre, 3 decimals.",
 "fuel_prices.bulletin_date": "Date of the EC bulletin the price comes from.", "fuel_prices.imported_at": "When the row was loaded.",
 "fuel_import_log.id": "Surrogate key.", "fuel_import_log.started_at": "Import start.", "fuel_import_log.status": "`ok` or `failed`.",
 "fuel_import_log.bulletin_date": "Bulletin date (NULL for failures).", "fuel_import_log.message": "Error text or note.",
 "toll_tariffs.id": "Surrogate key.", "toll_tariffs.country_code": "Country.", "toll_tariffs.kind": "`toll` or `vignette`.", "toll_tariffs.name": "Road section or product name.",
 "toll_tariffs.rate_eur": "Rate in EUR.", "toll_tariffs.unit": "`per_km` or `per_trip`.", "toll_tariffs.checked_on": "Date the value was verified.",
 "toll_tariffs.updated_by": "Administrator who changed the row (kept NULL if that user is deleted).", "toll_tariffs.updated_at": "Set by trigger.",
 "lodging_rates.id": "Surrogate key.", "lodging_rates.country_code": "Country.", "lodging_rates.class": "`economy`, `standard`, `comfort`.", "lodging_rates.nightly_eur": "EUR per night.",
 "lodging_rates.checked_on": "Verification date.", "lodging_rates.updated_by": "Administrator.", "lodging_rates.updated_at": "Set by trigger.",
 "speed_profiles.id": "Surrogate key.", "speed_profiles.country_code": "Country.", "speed_profiles.season": "`summer` or `winter` (winter = 1 Nov - 31 Mar).",
 "speed_profiles.avg_speed_kmh": "Average speed used for the travel time.", "speed_profiles.motorway_limit_kmh": "Motorway limit; NULL = no general limit.",
 "speed_profiles.limit_note": "Short note shown with the limit.", "speed_profiles.source": "Where the administrator verified the values.",
 "speed_profiles.checked_on": "Verification date.", "speed_profiles.updated_by": "Administrator.", "speed_profiles.updated_at": "Set by trigger.",
 "trips.id": "Surrogate key.", "trips.user_id": "Owner; trips are deleted with the user (US-20).", "trips.vehicle_id": "Vehicle used; becomes NULL if the vehicle is deleted (US-04).",
 "trips.title": "Name in the history list (editable).", "trips.origin_label": "Origin as text; no coordinates are stored.", "trips.destination_label": "Destination as text.",
 "trips.departed_at": "Device clock at calculation (US-29).", "trips.variant": "`fastest`, `shortest`, `no_tolls`.", "trips.distance_km": "Route length.",
 "trips.duration_min": "Driving time in minutes.", "trips.daily_limit_h": "Daily driving limit 4-10 h (US-08).", "trips.nights": "Number of overnight stops.",
 "trips.stop_city": "Overnight stop city (US-13).", "trips.stop_country": "Country of the stop.", "trips.arrival_at": "Arrival moment (shown in destination local time).",
 "trips.destination_country": "Destination country (time zone for arrival).", "trips.vehicle_name": "Snapshot of the vehicle name.", "trips.fuel_type": "Snapshot of the fuel type.",
 "trips.consumption_l100": "Snapshot of consumption.", "trips.tank_l": "Snapshot of tank size.", "trips.start_fuel": "Start level for the refuel plan (US-30).",
 "trips.refuel_saving_eur": "Estimated saving of the refuel tip; not part of the total.", "trips.price_date": "Oldest bulletin date used.",
 "trips.lodging_entered_eur": "Lodging total typed by the user (US-24); NULL = estimate.", "trips.total_eur": "Sum of visible cost lines.", "trips.created_at": "Save time.",
 "trip_cost_lines.id": "Surrogate key.", "trip_cost_lines.trip_id": "Trip.", "trip_cost_lines.kind": "`fuel`, `road`, `lodging`.", "trip_cost_lines.country_code": "Country of the line.",
 "trip_cost_lines.distance_km": "Km in this country (fuel/road).", "trip_cost_lines.unit_price": "EUR/l, rate or nightly rate.", "trip_cost_lines.amount_eur": "Amount; NULL when no data.",
 "trip_cost_lines.status": "`ok`, `no_data`, `estimate`, `eu_average`, `user_entered`.",
 "trip_refuel_stops.id": "Surrogate key.", "trip_refuel_stops.trip_id": "Trip.", "trip_refuel_stops.seq": "Order of the stop (1, 2, ...).", "trip_refuel_stops.country_code": "Country of the stop.",
 "trip_refuel_stops.km_from_start": "Distance from the origin.", "trip_refuel_stops.liters": "Litres to refuel.", "trip_refuel_stops.price_eur_l": "National average price used.",
}

tables = [t for _, (_, ts) in LAYERS.items() for t in ts]
cols = q("""SELECT c.relname, a.attnum, a.attname, format_type(a.atttypid, a.atttypmod), a.attnotnull,
                   coalesce(pg_get_expr(d.adbin, d.adrelid),''), a.attidentity
              FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace
              LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
             WHERE n.nspname='rw' AND c.relkind='r' AND a.attnum>0 AND NOT a.attisdropped
             ORDER BY c.relname, a.attnum""")
cons = q("""SELECT c.relname, con.contype, con.conname, pg_get_constraintdef(con.oid), con.conkey::text,
                   coalesce(f.relname,'')
              FROM pg_constraint con JOIN pg_class c ON c.oid=con.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
              LEFT JOIN pg_class f ON f.oid=con.confrelid
             WHERE n.nspname='rw' AND c.relkind='r' ORDER BY c.relname, con.contype, con.conname""")
attnames = {(r[0], int(r[1])): r[2] for r in cols}
pk, fk, uq = {}, {}, {}
for t, typ, name, d, keys, ref in cons:
    ks = [attnames[(t, int(x))] for x in keys.strip("{}").split(",") if x]
    if typ == "p": pk[t] = ks
    if typ == "f":
        for k in ks: fk[(t, k)] = ref
    if typ == "u": uq[t] = uq.get(t, []) + [ks]

# ------------------------------------------------------------------ graphviz
def node(t):
    rows = []
    for r in cols:
        if r[0] != t: continue
        n, typ = r[2], r[3].replace("character varying", "varchar").replace("timestamp with time zone", "timestamptz").replace("character", "char").replace("rw.", "")
        flag = "PK" if n in pk.get(t, []) else ("FK" if (t, n) in fk else "")
        nn = "" if r[4] == "t" else " ?"
        rows.append(f'<TR><TD ALIGN="LEFT" PORT="{n}"><FONT POINT-SIZE="9" COLOR="#555555">{flag:<2}</FONT> {n}</TD><TD ALIGN="LEFT"><FONT POINT-SIZE="9" COLOR="#444444">{typ}{nn}</FONT></TD></TR>')
    return f'{t} [shape=plain, label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="3" BGCOLOR="white"><TR><TD COLSPAN="2" BGCOLOR="#2b2b2b"><FONT COLOR="white"><B>{t}</B></FONT></TD></TR>{"".join(rows)}</TABLE>>];'

dot = ['digraph rw {', 'graph [rankdir=TB, fontname="Helvetica", fontsize=12, nodesep=0.5, ranksep=0.9, newrank=true, splines=true, concentrate=false, labelloc=t, pad=0.3, '
       'label="RouteWise - logical schema (PostgreSQL 16 + PostGIS). PK = primary key, FK = foreign key, ? = nullable"];',
       'node [fontname="Helvetica", fontsize=10]; edge [color="#555555", arrowsize=0.7];']
for i, (layer, (colr, ts)) in enumerate(LAYERS.items()):
    dot.append(f'subgraph cluster_{i} {{ label="{layer}"; style="rounded,filled"; fillcolor="{colr}"; color="#888888"; fontsize=12;')
    dot += [node(t) for t in ts]; dot.append('}')
seen = set()
for (t, c), ref in sorted(fk.items()):
    style = "dashed" if c == "updated_by" else "solid"
    dot.append(f'{t} -> {ref} [arrowhead=normal, style={style}, tooltip="{t}.{c}", xlabel=""];')
dot.append('}')
open(os.path.join(ROOT, "docs", "logical_schema.dot"), "w").write("\n".join(dot))
for fmt in ("svg", "png"):
    subprocess.run(["dot", f"-T{fmt}", "-Gdpi=130", os.path.join(ROOT, "docs", "logical_schema.dot"), "-o", os.path.join(ROOT, "docs", f"logical_schema.{fmt}")], check=True)

# ------------------------------------------------------------------ markdown
md = ["# Data dictionary", "",
      "Generated from the live schema by `docs/tools/gen_docs.py` (types, keys and nullability come from the database catalog; descriptions are written by hand).",
      "Legend: **PK** primary key, **FK** foreign key (`-> table`), **UQ** part of a unique constraint, **NN** NOT NULL. Money is `numeric`, times are `timestamptz`.", ""]
for layer, (_, ts) in LAYERS.items():
    md += [f"## Layer: {layer}", ""]
    for t in ts:
        md += [f"### `{t}`", "", TABLE_DESC[t], "", "| Column | Type | Keys | Null | Default | Meaning |", "|---|---|---|---|---|---|"]
        for r in cols:
            if r[0] != t: continue
            n = r[2]; typ = r[3]
            k = []
            if n in pk.get(t, []): k.append("PK")
            if (t, n) in fk: k.append(f"FK -> {fk[(t, n)]}")
            if any(n in u for u in uq.get(t, [])): k.append("UQ")
            d = "identity" if r[6] else r[5].replace("|", "\\|")
            md.append(f"| `{n}` | {typ} | {', '.join(k)} | {'NN' if r[4]=='t' else 'null'} | {d} | {COL[t + '.' + n]} |")
        md += ["", "**Constraints**", ""]
        for t2, typ, name, d, keys, ref in cons:
            if t2 == t and typ in ("c", "u", "f"):
                md.append(f"- `{name}`: `{d}`")
        md += ["", "**Data-quality expectations**", ""] + [f"- {x}" for x in QUALITY[t]] + [""]
open(os.path.join(ROOT, "docs", "data_dictionary_tables.md"), "w").write("\n".join(md))
# column coverage check
missing = [f"{r[0]}.{r[2]}" for r in cols if r[0] in tables and f"{r[0]}.{r[2]}" not in COL]
if missing: sys.exit("undocumented columns: " + ", ".join(missing))
print("ok:", len(cols), "columns documented")
