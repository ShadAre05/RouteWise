package com.routewise.app.domain

/** A city the user can choose as the start or the destination. */
data class Place(val name: String, val countryCode: String, val lon: Double, val lat: Double) {
    val label: String get() = "$name, ${Formatting.countryName(countryCode)}"
}

/**
 * Built-in list of cities of the eight supported countries. It replaces the map and address search of the UX
 * specification (SCR-03): a point is chosen by typing at least three characters of a city name.
 */
object PlaceCatalog {
    val all: List<Place> = listOf(
        Place("Vilnius", "LT", 25.2797, 54.6872),
        Place("Kaunas", "LT", 23.9036, 54.8985),
        Place("Klaipeda", "LT", 21.1443, 55.7033),
        Place("Riga", "LV", 24.1052, 56.9496),
        Place("Daugavpils", "LV", 26.5362, 55.8747),
        Place("Tallinn", "EE", 24.7536, 59.4370),
        Place("Tartu", "EE", 26.7290, 58.3780),
        Place("Warsaw", "PL", 21.0122, 52.2297),
        Place("Krakow", "PL", 19.9450, 50.0647),
        Place("Gdansk", "PL", 18.6466, 54.3520),
        Place("Poznan", "PL", 16.9252, 52.4064),
        Place("Wroclaw", "PL", 17.0385, 51.1079),
        Place("Berlin", "DE", 13.4050, 52.5200),
        Place("Hamburg", "DE", 9.9937, 53.5511),
        Place("Munich", "DE", 11.5820, 48.1351),
        Place("Dresden", "DE", 13.7373, 51.0504),
        Place("Copenhagen", "DK", 12.5683, 55.6761),
        Place("Aarhus", "DK", 10.2039, 56.1629),
        Place("Prague", "CZ", 14.4378, 50.0755),
        Place("Brno", "CZ", 16.6068, 49.1951),
        Place("Ostrava", "CZ", 18.2625, 49.8209),
        Place("Bratislava", "SK", 17.1077, 48.1486),
        Place("Kosice", "SK", 21.2611, 48.7164),
    )

    /** Case-insensitive search by part of the name; the UI requires at least three characters. */
    fun search(query: String): List<Place> {
        val text = query.trim()
        if (text.length < Validators.MIN_SEARCH_LENGTH) return emptyList()
        return all.filter { it.name.contains(text, ignoreCase = true) }
    }
}
