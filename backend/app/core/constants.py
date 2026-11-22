"""Named constants: no magic strings or numbers in the rest of the code."""

from decimal import Decimal

ROLE_USER = "user"
ROLE_ADMIN = "admin"

TOKEN_TYPE_ACCESS = "access"
RLS_SETTING_NAME = "app.current_user_id"  # read by rw.current_app_user() in the database
REQUEST_ID_HEADER = "X-Request-ID"

API_PREFIX = "/api/v1"
MIN_PASSWORD_LENGTH = 8
MAX_PASSWORD_LENGTH = 128

DEFAULT_DAILY_LIMIT_H = 8.0
MIN_DAILY_LIMIT_H = 4.0
MAX_DAILY_LIMIT_H = 10.0
FUEL_RESERVE_FRACTION = Decimal("0.10")  # US-30: keep 10 % of the tank
START_FUEL_FRACTION = {
    "full": Decimal("1.00"),
    "three_quarter": Decimal("0.75"),
    "half": Decimal("0.50"),
    "quarter": Decimal("0.25"),
}
WINTER_MONTHS = frozenset({11, 12, 1, 2, 3})  # US-26: 1 Nov - 31 Mar
HOURS_PER_DAY = 24
MINUTES_PER_HOUR = 60
METERS_PER_KM = 1000
MONEY_QUANT = Decimal("0.01")
LITRE_QUANT = Decimal("0.1")

DEFAULT_PAGE_SIZE = 20
MAX_PAGE_SIZE = 100

# line kinds / statuses (same values as the PostgreSQL enums cost_kind and line_status)
KIND_FUEL, KIND_ROAD, KIND_LODGING = "fuel", "road", "lodging"
STATUS_OK, STATUS_NO_DATA, STATUS_ESTIMATE = "ok", "no_data", "estimate"
STATUS_EU_AVERAGE, STATUS_USER_ENTERED = "eu_average", "user_entered"
TARIFF_TOLL, TARIFF_VIGNETTE = "toll", "vignette"
UNIT_PER_KM, UNIT_PER_TRIP = "per_km", "per_trip"
VARIANT_FASTEST, VARIANT_SHORTEST, VARIANT_NO_TOLLS = "fastest", "shortest", "no_tolls"

# PostgreSQL SQLSTATE codes used to translate database errors
PG_UNIQUE_VIOLATION = "23505"
PG_CHECK_VIOLATION = "23514"
PG_FOREIGN_KEY_VIOLATION = "23503"
PG_INSUFFICIENT_PRIVILEGE = "42501"
