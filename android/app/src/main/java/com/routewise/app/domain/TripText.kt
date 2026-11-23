package com.routewise.app.domain

import com.routewise.app.core.Messages
import com.routewise.app.data.api.CostLineDto
import com.routewise.app.data.api.TripDto
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Texts of a trip that the Budget and the Trip detail screens share (UX SCR-04 and SCR-06). */
object TripText {
    private val arrivalFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)

    private fun city(label: String) = label.substringBefore(",").trim()

    /** "Vilnius → Prague" from the stored labels. */
    fun routeTitle(trip: TripDto): String = "${city(trip.originLabel)} → ${city(trip.destinationLabel)}"

    /** "LT 150 · PL 740 · DE 140 km": the text alternative to the map. Empty when the trip has no fuel lines. */
    fun kmByCountry(trip: TripDto): String {
        val parts = trip.lines.filter { it.kind == "fuel" && it.country != null && it.distanceKm != null }
            .map { "${it.country} ${BigDecimal(it.distanceKm!!).setScale(0, RoundingMode.HALF_UP).toPlainString()}" }
        return if (parts.isEmpty()) "" else parts.joinToString(" · ") + " km"
    }

    fun isPriceOld(priceDate: String, today: LocalDate): Boolean =
        try {
            ChronoUnit.DAYS.between(LocalDate.parse(priceDate.take(10)), today) > 14
        } catch (e: DateTimeParseException) {
            false
        }

    /** "Arrive 12 Nov, 11:51 (Prague) · hotel check-in ~14:00" with the check-in hints of the specification. */
    fun arrivalLine(trip: TripDto): String? {
        val arrival = try {
            OffsetDateTime.parse(trip.arrivalAt ?: return null)
        } catch (e: DateTimeParseException) {
            return null
        }
        val base = "Arrive ${arrival.format(arrivalFormat)} (${city(trip.destinationLabel)})"
        val hint = when {
            trip.nights == 0 -> ""
            arrival.hour < 14 -> " · hotel check-in ~14:00"
            else -> ""
        }
        return base + hint
    }

    fun isLateArrival(trip: TripDto): Boolean =
        try {
            OffsetDateTime.parse(trip.arrivalAt ?: return false).hour >= 22
        } catch (e: DateTimeParseException) {
            false
        }

    fun lineTitle(line: CostLineDto, nights: Int): String {
        val place = Formatting.countryName(line.country)
        return when (line.kind) {
            "fuel" -> "Fuel · $place"
            "road" -> "Road charges · $place"
            "lodging" -> "Lodging · $nights night${if (nights == 1) "" else "s"}"
            else -> line.kind
        }
    }

    /** The second line of a cost row: how the figure was made, never hiding an estimate. */
    fun lineDetail(line: CostLineDto, destinationLabel: String): String? = when (line.status) {
        "no_data" -> "No data for this country"
        "eu_average" -> "EU average used (no price)"
        "user_entered" -> "Entered by you"
        "estimate" -> "Estimate · ${city(destinationLabel)}"
        else -> when (line.kind) {
            "fuel" -> {
                val km = line.distanceKm?.toBigDecimalOrNull()?.setScale(0, RoundingMode.HALF_UP)?.toPlainString()
                val price = line.unitPrice?.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.toPlainString()
                if (km != null && price != null) "$km km × $price €/l" else null
            }
            "road" -> "Included in total"
            else -> null
        }
    }

    fun lateArrivalNote(trip: TripDto): String? = if (isLateArrival(trip)) Messages.LATE_ARRIVAL else null
}
