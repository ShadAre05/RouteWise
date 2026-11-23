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
    const val CONNECTION = "No connection. Check your internet connection and try again."
    const val SERVER_DOWN = "The service is not available now. Try again in a few minutes."
    const val SAVE_FAILED = "Could not save. Check your connection and try again."
    const val SESSION_EXPIRED = "Your session has ended. Log in again."
    const val UNKNOWN = "Something went wrong. Try again."
    const val PASSWORD_WRONG = "The current password is wrong"

    fun passwordShort(length: Int) = "Use at least 10 characters (now $length)"

    fun noPlaces(query: String) = "No places found for “$query”. Check the spelling."
}
