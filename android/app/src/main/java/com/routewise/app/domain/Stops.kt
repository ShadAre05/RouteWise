package com.routewise.app.domain

import com.routewise.app.data.api.TripDto
import com.routewise.app.data.api.TripRequest
import java.net.URLEncoder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The first overnight stop: where the daily driving limit is reached, and the dates for a hotel search. */
data class OvernightStop(val place: Place, val km: Int, val afterMinutes: Int, val checkIn: LocalDate, val checkOut: LocalDate)

object Stops {
    /**
     * The API does not return the city of the stop, so it is estimated: the point where the daily limit is reached
     * is taken on the straight line between start and destination, and the nearest city of the built-in list is used.
     * The result is approximate and is labelled so on the screen.
     */
    fun firstStop(request: TripRequest, trip: TripDto): OvernightStop? {
        if (trip.nights <= 0 || trip.durationMin <= 0) return null
        val limitMin = ((trip.dailyLimitH?.toDoubleOrNull() ?: request.dailyLimitH?.toDouble() ?: 8.0) * 60).roundToInt()
        val fraction = min(0.95, limitMin.toDouble() / trip.durationMin)
        val lat = request.origin.lat + (request.destination.lat - request.origin.lat) * fraction
        val lon = request.origin.lon + (request.destination.lon - request.origin.lon) * fraction
        val place = PlaceCatalog.all.minByOrNull { distanceKm(lat, lon, it.lat, it.lon) } ?: return null
        val km = ((trip.distanceKm.toDoubleOrNull() ?: 0.0) * fraction).roundToInt()
        val day = try {
            OffsetDateTime.parse(trip.departedAt).toLocalDate()
        } catch (e: DateTimeParseException) {
            return null
        }
        return OvernightStop(place, km, limitMin, day, day.plusDays(1))
    }

    /** Link that opens a hotel search with the city, dates and guests filled in. */
    fun bookingUrl(stop: OvernightStop, guests: Int): String {
        val city = URLEncoder.encode("${stop.place.name}, ${Formatting.countryName(stop.place.countryCode)}", "UTF-8")
        return "https://www.booking.com/searchresults.html?ss=$city&checkin=${stop.checkIn}&checkout=${stop.checkOut}" +
            "&group_adults=$guests&no_rooms=1"
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val a = lat1 * PI / 180
        val b = lat2 * PI / 180
        val cosine = sin(a) * sin(b) + cos(a) * cos(b) * cos((lon2 - lon1) * PI / 180)
        return 6371.0 * acos(cosine.coerceIn(-1.0, 1.0))
    }
}

/** Words for the WMO weather codes that the forecast service returns. */
object WeatherText {
    fun describe(code: Int): String = when (code) {
        0 -> "clear"
        1, 2 -> "mostly clear"
        3 -> "overcast"
        45, 48 -> "fog"
        in 51..57 -> "drizzle"
        in 61..67 -> "rain"
        in 71..77 -> "snow"
        in 80..82 -> "rain showers"
        85, 86 -> "snow showers"
        in 95..99 -> "thunderstorm"
        else -> "changeable"
    }

    fun line(city: String, w: com.routewise.app.data.Weather): String {
        val day = w.date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH))
        val rain = w.rainPercent?.let { " · $it % chance of precipitation" } ?: ""
        return "$city, $day: ${w.minC}…${w.maxC} °C, ${describe(w.code)}$rain"
    }
}
