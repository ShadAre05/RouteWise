package com.routewise.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display formatting that matches the UX specification: "€166.44", "1,030 km", "11 h 10 min", "11 Nov 2026". */
object Formatting {
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val timeFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)

    fun eur(amount: String?): String {
        val value = amount?.toBigDecimalOrNull() ?: return "no data"
        return "€" + String.format(Locale.ENGLISH, "%,.2f", value.setScale(2, RoundingMode.HALF_UP))
    }

    fun signedEur(delta: BigDecimal): String {
        val sign = if (delta.signum() > 0) "+" else if (delta.signum() < 0) "−" else ""
        return sign + eur(delta.abs().toPlainString())
    }

    /** Difference of two totals as in the route chips: "−8.32" (the unit is clear from the total next to it). */
    fun signedAmount(delta: BigDecimal): String {
        val sign = if (delta.signum() > 0) "+" else if (delta.signum() < 0) "−" else ""
        return sign + String.format(Locale.ENGLISH, "%,.2f", delta.abs().setScale(2, RoundingMode.HALF_UP))
    }

    fun km(distance: String?): String {
        val value = distance?.toBigDecimalOrNull() ?: return "-"
        return String.format(Locale.ENGLISH, "%,.0f km", value)
    }

    fun duration(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours == 0) "$rest min" else "$hours h ${rest.toString().padStart(2, '0')} min"
    }

    fun signedMinutes(delta: Int): String {
        val sign = if (delta > 0) "+" else if (delta < 0) "−" else ""
        return "$sign${kotlin.math.abs(delta)} min"
    }

    fun date(iso: String?): String =
        parse(iso)?.format(dateFormat) ?: try {
            java.time.LocalDate.parse(iso?.take(10) ?: "").format(dateFormat)
        } catch (e: java.time.format.DateTimeParseException) {
            "-"
        }

    fun dateTime(iso: String?): String = parse(iso)?.format(timeFormat) ?: "-"

    private fun parse(iso: String?): OffsetDateTime? =
        try {
            if (iso == null) null else OffsetDateTime.parse(iso)
        } catch (e: java.time.format.DateTimeParseException) {
            null
        }

    fun variantName(variant: String): String = when (variant) {
        "fastest" -> "Fastest"
        "shortest" -> "Shortest"
        "no_tolls" -> "No tolls"
        else -> variant
    }

    fun fuelName(fuelType: String): String = if (fuelType == "diesel") "Diesel" else "Petrol 95"

    fun countryName(code: String?): String = when (code) {
        "LT" -> "Lithuania"
        "LV" -> "Latvia"
        "EE" -> "Estonia"
        "PL" -> "Poland"
        "DE" -> "Germany"
        "DK" -> "Denmark"
        "CZ" -> "Czechia"
        "SK" -> "Slovakia"
        "BY" -> "Belarus"
        "AT" -> "Austria"
        "FR" -> "France"
        null -> "-"
        else -> code
    }
}
