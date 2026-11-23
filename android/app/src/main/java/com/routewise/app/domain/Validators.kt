package com.routewise.app.domain

import com.routewise.app.core.Messages
import java.math.BigDecimal

/** Pure validation rules of the UX specification. Every function returns an error text or null when valid. */
object Validators {
    const val MIN_PASSWORD_LENGTH = 10
    const val MAX_PASSWORD_LENGTH = 128
    const val MIN_SEARCH_LENGTH = 3
    const val MIN_DAILY_LIMIT_H = 4
    const val MAX_DAILY_LIMIT_H = 10

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    fun email(value: String): String? =
        if (EMAIL.matches(value.trim())) null else Messages.EMAIL_INVALID

    fun newPassword(value: String): String? =
        if (value.length >= MIN_PASSWORD_LENGTH && value.length <= MAX_PASSWORD_LENGTH) {
            null
        } else {
            Messages.passwordShort(value.length)
        }

    fun privacyAccepted(accepted: Boolean): String? =
        if (accepted) null else Messages.PRIVACY_REQUIRED

    fun vehicleName(value: String): String? =
        if (value.trim().length in 1..60) null else Messages.VEHICLE_NAME

    /** Average consumption, 2.0 to 30.0 l/100 km with at most one decimal. */
    fun consumption(value: String): String? {
        val number = parseDecimal(value) ?: return Messages.CONSUMPTION
        val inRange = number >= BigDecimal("2.0") && number <= BigDecimal("30.0")
        return if (inRange && number.scale() <= 1) null else Messages.CONSUMPTION
    }

    /** Tank size is optional: empty or 20 to 120 litres. */
    fun tank(value: String): String? {
        if (value.isBlank()) return null
        val number = parseDecimal(value) ?: return Messages.TANK
        val inRange = number >= BigDecimal("20") && number <= BigDecimal("120")
        return if (inRange && number.scale() <= 1) null else Messages.TANK
    }

    fun searchQuery(value: String): String? =
        if (value.trim().length >= MIN_SEARCH_LENGTH) null else Messages.SEARCH_SHORT

    fun dailyLimit(hours: Int): String? =
        if (hours in MIN_DAILY_LIMIT_H..MAX_DAILY_LIMIT_H) null else Messages.LIMIT_RANGE

    /** Accepts a dot or a comma as the decimal separator; returns null when the text is not a plain number. */
    fun parseDecimal(value: String): BigDecimal? {
        val text = value.trim().replace(',', '.')
        if (!Regex("^\\d+(\\.\\d+)?$").matches(text)) return null
        return text.toBigDecimalOrNull()
    }
}
