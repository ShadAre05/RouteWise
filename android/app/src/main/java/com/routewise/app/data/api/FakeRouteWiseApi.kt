package com.routewise.app.data.api

import com.routewise.app.data.session.Session
import com.routewise.app.data.session.Tokens
import com.routewise.app.domain.PlaceCatalog
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sample-data implementation of [RouteWiseApi] for the demo mode and for tests. It behaves like the real service
 * (same errors, same data shapes) but keeps everything in memory and estimates routes from straight-line
 * distances. Figures are illustrative and must never be presented as real prices.
 */
class FakeRouteWiseApi(
    private val session: Session,
    private val latencyMs: Long = 400,
    private val now: () -> OffsetDateTime = { OffsetDateTime.now() },
) : RouteWiseApi {

    private val passwords = mutableMapOf(DEMO_EMAIL to DEMO_PASSWORD)
    private val vehicles = mutableListOf<VehicleDto>()
    private val trips = mutableListOf<TripDto>()
    private var counter = 0

    init {
        vehicles += VehicleDto(newId(), "My Skoda", "diesel", "5.8", "50.0", true)
    }

    // ---- accounts -------------------------------------------------------------------------------------------

    override suspend fun register(email: String, password: String): TokenResponse {
        pause()
        val key = email.trim().lowercase()
        if (passwords.containsKey(key)) throw ApiException(ApiErrorKind.CONFLICT, "this e-mail is already registered")
        passwords[key] = password
        if (key != DEMO_EMAIL) {
            vehicles.clear()
            trips.clear()
        }
        return issueTokens()
    }

    override suspend fun login(email: String, password: String): TokenResponse {
        pause()
        if (passwords[email.trim().lowercase()] != password) {
            throw ApiException(ApiErrorKind.UNAUTHORIZED, "invalid e-mail or password")
        }
        return issueTokens()
    }

    override suspend fun logout() {
        pause()
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String) {
        pause()
        requireSignedIn()
        val owner = passwords.entries.firstOrNull { it.value == oldPassword }
            ?: throw ApiException(ApiErrorKind.UNAUTHORIZED, "the current password is wrong")
        passwords[owner.key] = newPassword
    }

    override suspend fun deleteAccount() {
        pause()
        requireSignedIn()
        vehicles.clear()
        trips.clear()
    }

    // ---- vehicles -------------------------------------------------------------------------------------------

    override suspend fun listVehicles(): List<VehicleDto> {
        pause()
        requireSignedIn()
        return vehicles.toList()
    }

    override suspend fun createVehicle(request: VehicleRequest): VehicleDto {
        pause()
        requireSignedIn()
        val makeDefault = request.isDefault == true || vehicles.isEmpty()
        if (makeDefault) clearDefault()
        val vehicle = VehicleDto(
            id = newId(),
            name = request.name.orEmpty(),
            fuelType = request.fuelType ?: "petrol95",
            consumptionL100 = request.consumptionL100 ?: "6.0",
            tankL = request.tankL,
            isDefault = makeDefault,
        )
        vehicles += vehicle
        return vehicle
    }

    override suspend fun updateVehicle(id: String, request: VehicleRequest): VehicleDto {
        pause()
        requireSignedIn()
        val index = vehicles.indexOfFirst { it.id == id }
        if (index < 0) throw ApiException(ApiErrorKind.NOT_FOUND, "vehicle not found")
        if (request.isDefault == true) clearDefault()
        val old = vehicles[index]
        val updated = old.copy(
            name = request.name ?: old.name,
            fuelType = request.fuelType ?: old.fuelType,
            consumptionL100 = request.consumptionL100 ?: old.consumptionL100,
            tankL = request.tankL ?: old.tankL,
            isDefault = request.isDefault ?: old.isDefault,
        )
        vehicles[index] = updated
        return updated
    }

    override suspend fun deleteVehicle(id: String) {
        pause()
        requireSignedIn()
        if (!vehicles.removeAll { it.id == id }) throw ApiException(ApiErrorKind.NOT_FOUND, "vehicle not found")
    }

    // ---- trips ----------------------------------------------------------------------------------------------

    override suspend fun createTrip(request: TripRequest): TripDto {
        pause()
        requireSignedIn()
        val vehicle = vehicles.firstOrNull { it.id == request.vehicleId } ?: vehicles.firstOrNull { it.isDefault }
            ?: throw ApiException(ApiErrorKind.VALIDATION, "no vehicle: add a vehicle or pass an existing vehicle_id")
        val trip = calculate(request, vehicle)
        trips.add(0, trip)
        return trip
    }

    override suspend fun listTrips(limit: Int, offset: Int): TripPageDto {
        pause()
        requireSignedIn()
        return TripPageDto(trips.drop(offset).take(limit), trips.size, limit, offset)
    }

    override suspend fun getTrip(id: String): TripDto {
        pause()
        requireSignedIn()
        return trips.firstOrNull { it.id == id } ?: throw ApiException(ApiErrorKind.NOT_FOUND, "trip not found")
    }

    override suspend fun deleteTrip(id: String) {
        pause()
        requireSignedIn()
        if (!trips.removeAll { it.id == id }) throw ApiException(ApiErrorKind.NOT_FOUND, "trip not found")
    }

    override suspend fun fuelTrend(country: String, fuelType: String): List<TrendPointDto> {
        pause()
        val base = PETROL[country.uppercase()]?.toBigDecimalOrNull() ?: throw ApiException(ApiErrorKind.NOT_FOUND, "no price history")
        val shift = if (fuelType == "diesel") BigDecimal("0.060") else BigDecimal.ZERO
        val last = java.time.LocalDate.parse(DEMO_PRICE_DATE)
        return (11 downTo 0).map { weeksAgo ->
            val wiggle = BigDecimal(((11 - weeksAgo) * 7 % 5) - 2).multiply(BigDecimal("0.006"))
            TrendPointDto(last.minusWeeks(weeksAgo.toLong()).toString(), (base + shift + wiggle).setScale(3).toPlainString())
        }
    }

    override suspend fun speedProfiles(): List<SpeedProfileDto> {
        pause()
        return SPEEDS.map { (country, v) ->
            SpeedProfileDto(country, "winter", v.first, v.second, if (v.second == null) "no general limit, advised 130" else null)
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private suspend fun pause() {
        if (latencyMs > 0) delay(latencyMs)
    }

    private fun requireSignedIn() {
        if (session.current == null) throw ApiException(ApiErrorKind.UNAUTHORIZED, "missing bearer token")
    }

    private fun issueTokens(): TokenResponse {
        counter++
        val tokens = TokenResponse("demo-access-$counter", "demo-refresh-$counter", 900)
        session.update(Tokens(tokens.accessToken, tokens.refreshToken))
        return tokens
    }

    private fun clearDefault() {
        for (i in vehicles.indices) vehicles[i] = vehicles[i].copy(isDefault = false)
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun calculate(request: TripRequest, vehicle: VehicleDto): TripDto {
        val straight = haversineKm(request.origin, request.destination)
        val factor = when (request.variant) {
            "shortest" -> 1.16
            "no_tolls" -> 1.24
            else -> 1.25
        }
        val speed = when (request.variant) {
            "shortest" -> 66.0
            "no_tolls" -> 62.0
            else -> 78.0
        }
        val distance = straight * factor
        val minutes = ceil(distance / speed * 60).toInt()
        val limit = request.dailyLimitH ?: 8
        val nights = if (minutes <= limit * 60) 0 else ceil(minutes / (limit * 60.0)).toInt() - 1

        val from = nearestCountry(request.origin)
        val to = nearestCountry(request.destination)
        val consumption = BigDecimal(vehicle.consumptionL100)
        val lines = mutableListOf<CostLineDto>()
        val countries = if (from == to) listOf(from) else listOf(from, to)
        val share = BigDecimal(distance).divide(BigDecimal(countries.size), 1, RoundingMode.HALF_UP)
        for (country in countries) {
            val price = fuelPrice(country, vehicle.fuelType)
            val amount = share.multiply(consumption).divide(BigDecimal(100)).multiply(price).setScale(2, RoundingMode.HALF_UP)
            lines += CostLineDto("fuel", country, share.toPlainString(), price.toPlainString(), amount.toPlainString(), "ok")
        }
        if (request.variant != "no_tolls") {
            VIGNETTES[to]?.let { lines += CostLineDto("road", to, null, null, it, "ok") }
        }
        if (nights > 0) {
            val perNight = LODGING[request.lodgingClass] ?: LODGING.getValue("standard")
            val entered = request.lodgingEnteredEur?.toBigDecimalOrNull()
            val amount = entered ?: perNight.multiply(BigDecimal(nights))
            lines += CostLineDto("lodging", to, null, null, amount.setScale(2, RoundingMode.HALF_UP).toPlainString(), if (entered != null) "user_entered" else "estimate")
        }
        val total = lines.fold(BigDecimal.ZERO) { sum, line -> sum + BigDecimal(line.amountEur ?: "0") }
        val start = now()
        // Demo refuel plan: one stop in the cheaper country of the route, only when the start fuel level was given.
        val stops = if (request.startFuel != null && countries.size > 1) {
            val cheaper = countries.minByOrNull { fuelPrice(it, vehicle.fuelType) } ?: from
            listOf(RefuelStopDto(1, cheaper, (distance / 2).toBigDecimal().setScale(0, RoundingMode.HALF_UP).toPlainString(), "30.0", fuelPrice(cheaper, vehicle.fuelType).toPlainString()))
        } else {
            null
        }
        return TripDto(
            id = newId(),
            title = "${request.originLabel} - ${request.destinationLabel}",
            originLabel = request.originLabel,
            destinationLabel = request.destinationLabel,
            departedAt = start.toString(),
            arrivalAt = start.plusMinutes(minutes.toLong() + nights * 600L).toString(),
            destinationCountry = to,
            variant = request.variant,
            distanceKm = BigDecimal(distance).setScale(1, RoundingMode.HALF_UP).toPlainString(),
            durationMin = minutes,
            dailyLimitH = "$limit.0",
            nights = nights,
            vehicleName = vehicle.name,
            fuelType = vehicle.fuelType,
            consumptionL100 = vehicle.consumptionL100,
            startFuel = request.startFuel,
            refuelSavingEur = stops?.let { "2.40" },
            priceDate = DEMO_PRICE_DATE,
            totalEur = total.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            createdAt = start.toString(),
            lines = lines,
            refuelStops = stops.orEmpty(),
        )
    }

    private fun fuelPrice(country: String, fuelType: String): BigDecimal {
        val petrol = BigDecimal(PETROL[country] ?: "1.600")
        return if (fuelType == "diesel") petrol.subtract(BigDecimal("0.100")) else petrol
    }

    private fun nearestCountry(point: PointDto): String =
        PlaceCatalog.all.minByOrNull { haversineKm(point, PointDto(it.lon, it.lat)) }?.countryCode ?: "PL"

    private fun haversineKm(a: PointDto, b: PointDto): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val cosine = sin(lat1) * sin(lat2) + cos(lat1) * cos(lat2) * cos(dLon)
        return 6371.0 * acos(cosine.coerceIn(-1.0, 1.0))
    }

    companion object {
        const val DEMO_EMAIL = "demo@routewise.app"
        const val DEMO_PASSWORD = "demo-password-1"
        const val DEMO_PRICE_DATE = "2026-11-09"
        private val PETROL = mapOf(
            "LT" to "1.554", "LV" to "1.520", "EE" to "1.580", "PL" to "1.400",
            "DE" to "1.712", "DK" to "1.854", "CZ" to "1.476", "SK" to "1.620",
        )
        private val SPEEDS = mapOf<String, Pair<String, Int?>>(
            "LT" to ("85.0" to 110), "LV" to ("82.0" to 110), "EE" to ("80.0" to 110), "PL" to ("92.0" to 140),
            "DE" to ("103.0" to null), "DK" to ("98.0" to 130), "CZ" to ("84.0" to 130),
        )
        private val VIGNETTES = mapOf("CZ" to "13.50", "SK" to "12.00")
        private val LODGING = mapOf(
            "economy" to BigDecimal("45.00"),
            "standard" to BigDecimal("70.00"),
            "comfort" to BigDecimal("110.00"),
        )
    }
}
