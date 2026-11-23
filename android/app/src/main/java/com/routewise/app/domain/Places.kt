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
        // Towns along the main corridors, so that an overnight stop is not placed in a far-away big city.
        Place("Panevezys", "LT", 24.3575, 55.7348),
        Place("Marijampole", "LT", 23.3540, 54.5599),
        Place("Siauliai", "LT", 23.3137, 55.9349),
        Place("Jelgava", "LV", 23.7214, 56.6511),
        Place("Liepaja", "LV", 21.0108, 56.5047),
        Place("Parnu", "EE", 24.4971, 58.3859),
        Place("Suwalki", "PL", 22.9309, 54.1118),
        Place("Bialystok", "PL", 23.1688, 53.1325),
        Place("Olsztyn", "PL", 20.4801, 53.7784),
        Place("Lodz", "PL", 19.4560, 51.7592),
        Place("Lublin", "PL", 22.5684, 51.2465),
        Place("Katowice", "PL", 19.0238, 50.2649),
        Place("Szczecin", "PL", 14.5528, 53.4285),
        Place("Hannover", "DE", 9.7320, 52.3759),
        Place("Magdeburg", "DE", 11.6276, 52.1205),
        Place("Leipzig", "DE", 12.3731, 51.3397),
        Place("Nuremberg", "DE", 11.0767, 49.4521),
        Place("Rostock", "DE", 12.0991, 54.0924),
        Place("Odense", "DK", 10.4024, 55.4038),
        Place("Pilsen", "CZ", 13.3736, 49.7384),
        Place("Olomouc", "CZ", 17.2509, 49.5938),
        Place("Zilina", "SK", 18.7394, 49.2231),
    )

    /** Well-known places outside the eight countries. They are found by the search so that the app can explain the rule. */
    val unsupported: List<Place> = listOf(
        Place("Minsk", "BY", 27.5615, 53.9006),
        Place("Vienna", "AT", 16.3738, 48.2082),
        Place("Paris", "FR", 2.3522, 48.8566),
    )

    fun isSupported(place: Place): Boolean = all.any { it.countryCode == place.countryCode }

    /** Case-insensitive search by part of the name; the UI requires at least three characters. */
    fun search(query: String): List<Place> {
        val text = query.trim()
        if (text.length < Validators.MIN_SEARCH_LENGTH) return emptyList()
        return (all + unsupported).filter { it.name.contains(text, ignoreCase = true) }
    }
}
