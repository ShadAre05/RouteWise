package com.routewise.app.domain

import com.routewise.app.data.api.CostLineDto
import com.routewise.app.data.api.TripDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripTextTest {
    private val fuelPoland = CostLineDto("fuel", "PL", "740.0", "1.620", "119.99", "ok")
    private val fuelLithuania = CostLineDto("fuel", "LT", "150.0", "1.780", "15.49", "ok")

    private fun trip(arrival: String? = "2026-11-12T11:51:00+01:00", nights: Int = 1, lines: List<CostLineDto> = listOf(fuelLithuania, fuelPoland)) = TripDto(
        id = "1", title = "t", originLabel = "Vilnius, Lithuania", destinationLabel = "Prague, Czechia",
        departedAt = "2026-11-11T20:00:00+02:00", arrivalAt = arrival, variant = "fastest", distanceKm = "1030.0",
        durationMin = 670, nights = nights, vehicleName = "My Skoda", fuelType = "diesel", consumptionL100 = "5.8",
        priceDate = "2026-11-03", totalEur = "166.44", lines = lines,
    )

    @Test fun routeTitleUsesCityNames() = assertEquals("Vilnius → Prague", TripText.routeTitle(trip()))

    @Test fun kmByCountryListsFuelLines() = assertEquals("LT 150 · PL 740 km", TripText.kmByCountry(trip()))

    @Test fun pricesOlderThanFourteenDaysAreOld() {
        assertTrue(TripText.isPriceOld("2026-10-12", LocalDate.of(2026, 11, 3)))
        assertFalse(TripText.isPriceOld("2026-10-20", LocalDate.of(2026, 11, 3)))
    }

    @Test fun arrivalBeforeCheckInShowsTheHint() {
        assertEquals("Arrive 12 Nov, 11:51 (Prague) · hotel check-in ~14:00", TripText.arrivalLine(trip()))
    }

    @Test fun lateArrivalIsRecognised() {
        assertTrue(TripText.isLateArrival(trip(arrival = "2026-11-12T22:30:00+01:00")))
        assertFalse(TripText.isLateArrival(trip()))
    }

    @Test fun fuelDetailShowsDistanceAndPrice() = assertEquals("150 km × 1.78 €/l", TripText.lineDetail(fuelLithuania, "Prague, Czechia"))

    @Test fun missingDataIsLabelledNotZero() {
        val line = CostLineDto("road", "PL", null, null, null, "no_data")
        assertEquals("No data for this country", TripText.lineDetail(line, "Prague, Czechia"))
    }

    @Test fun euAverageIsLabelled() {
        assertEquals("EU average used (no price)", TripText.lineDetail(fuelPoland.copy(status = "eu_average"), "x"))
    }

    @Test fun unknownArrivalGivesNoLine() = assertNull(TripText.arrivalLine(trip(arrival = null)))
}
