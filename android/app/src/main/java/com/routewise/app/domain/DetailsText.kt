package com.routewise.app.domain

import com.routewise.app.core.Messages
import com.routewise.app.data.api.SpeedProfileDto
import com.routewise.app.data.api.TrendPointDto
import com.routewise.app.data.api.TripDto
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Texts of the route details (SCR-10) and of the fuel price trend (SCR-12). */
object DetailsText {

    data class SpeedRow(val country: String, val title: String, val detail: String, val missing: Boolean)

    fun season(today: LocalDate): String =
        if (today.monthValue >= 11 || today.monthValue <= 3) "winter (1 Nov – 31 Mar)" else "summer (1 Apr – 31 Oct)"

    /** One row per country of the route, in route order, with the speed and limit of the season. */
    fun speedRows(trip: TripDto, profiles: List<SpeedProfileDto>): List<SpeedRow> =
        trip.lines.filter { it.kind == "fuel" && it.country != null && it.distanceKm != null }.map { line ->
            val code = line.country!!
            val name = Formatting.countryName(code)
            val km = BigDecimal(line.distanceKm!!).setScale(0, RoundingMode.HALF_UP).toPlainString()
            val profile = profiles.firstOrNull { it.country == code }
            val avg = profile?.avgSpeedKmh?.toBigDecimalOrNull()
            if (avg == null) {
                SpeedRow(code, "$name · $km km", Messages.noSpeedData(name), true)
            } else {
                val limit = profile.motorwayLimitKmh?.let { "limit $it" } ?: (profile.limitNote ?: "no limit")
                SpeedRow(code, "$name · $km km", "Average ${avg.setScale(0, RoundingMode.HALF_UP).toPlainString()} km/h · $limit", false)
            }
        }

    data class TrendRow(val date: String, val text: String)

    /** Newest first: "3 Nov" and "1.620 · −0.010" (the change against the previous week). */
    fun trendRows(points: List<TrendPointDto>): List<TrendRow> {
        val sorted = points.sortedBy { it.bulletinDate }
        return sorted.mapIndexed { index, point ->
            val price = BigDecimal(point.priceEurL)
            val change = if (index == 0) null else price - BigDecimal(sorted[index - 1].priceEurL)
            TrendRow(shortDate(point.bulletinDate), "${price.toPlainString()}" + (change?.let { " · ${signed(it)}" } ?: ""))
        }.reversed()
    }

    private val shortFormat = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)

    private fun shortDate(iso: String): String =
        try {
            LocalDate.parse(iso.take(10)).format(shortFormat)
        } catch (e: java.time.format.DateTimeParseException) {
            iso
        }

    private fun signed(value: BigDecimal): String =
        when (value.signum()) {
            0 -> "0.000"
            1 -> "+" + value.setScale(3, RoundingMode.HALF_UP).toPlainString()
            else -> "−" + value.abs().setScale(3, RoundingMode.HALF_UP).toPlainString()
        }

    fun trendNote(points: List<TrendPointDto>): String? = if (points.size in 1..11) Messages.fewWeeks(points.size) else null

}
