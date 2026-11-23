package com.routewise.app.core

/** All user-visible texts that come from the UX specification, in one place (naming consistency, section 5.6). */
object Messages {
    const val EMAIL_INVALID = "Enter a valid e-mail, for example name@example.com"
    const val PRIVACY_REQUIRED = "Accept the privacy notice to continue"
    const val EMAIL_TAKEN = "This e-mail is already registered. Log in instead"
    const val CREDENTIALS_WRONG = "E-mail or password is not correct"
    const val LOGIN_BLOCKED = "Too many attempts. Login is blocked for 10 minutes."
    const val VEHICLE_NAME = "Enter a name (1 – 60 characters)"
    const val CONSUMPTION = "Enter a value from 2.0 to 30.0 l/100 km"
    const val TANK = "Enter 20 to 120 l or leave the field empty"
    const val SEARCH_SHORT = "Type at least 3 characters"
    const val LIMIT_RANGE = "Choose 4 to 10 hours"
    const val NO_CAR = "No car yet. Add your car to calculate costs."
    const val NO_ROUTE = "No road route found. Move the marker to a road and try again."
    const val CONNECTION = "We could not reach the server. Check your internet connection and try again. Your trip points are kept."
    const val SLOW = "This takes longer than usual"
    const val SET_DESTINATION = "Set the destination to calculate"
    const val SET_START = "Set the start to calculate"
    const val OUTSIDE_AREA = "Destination is outside the supported countries. Choose a point in LT, LV, EE, PL, DE, DK, CZ or SK."
    const val START_OUTSIDE_AREA = "Start is outside the supported countries. Choose a point in LT, LV, EE, PL, DE, DK, CZ or SK."
    const val PASSWORD_MISMATCH = "Passwords do not match"
    const val PASSWORD_SAME = "The new password must differ from the current one"
    const val DELETE_WRONG_PASSWORD = "Wrong password. Nothing was deleted."
    const val LATE_ARRIVAL = "Late arrival: ask the hotel about late check-in"
    const val NO_TOLLS_UNAVAILABLE = "“No tolls” is not available: every route in this area uses toll roads."
    const val TRIP_DELETED = "Trip deleted"
    const val PASSWORD_CHANGED = "Password changed"

    fun serverError(status: Int?) =
        "The calculation service did not answer in time. Try again in a minute. Error code: SRV-${status ?: 503}"

    fun carNotSaved(fields: Int) = "The car was not saved. Fix the $fields field${if (fields == 1) "" else "s"} marked above."

    fun pricesOld(date: String) = "Prices are older than 14 days. Fuel prices of $date are used."
    const val SAVE_FAILED = "Could not save. Check your connection and try again."
    const val SESSION_EXPIRED = "Your session has ended. Log in again."
    const val UNKNOWN = "Something went wrong. Try again."
    const val PASSWORD_WRONG = "Current password is not correct"

    fun passwordShort(length: Int) = "Use at least 10 characters (now $length)"

    fun noPlaces(query: String) = "No places found for “$query”. Check the spelling."
}
