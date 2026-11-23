package com.routewise.app.domain

import com.routewise.app.core.Messages
import com.routewise.app.data.api.CostLineDto
import com.routewise.app.data.api.SpeedProfileDto
import com.routewise.app.data.api.TrendPointDto
import com.routewise.app.data.api.TripDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DetailsTextTest {
    private fun trip(vararg lines: CostLineDto) = TripDto(
        id = "1", title = "t", originLabel = "Vilnius, Lithuania", destinationLabel = "Prague, Czechia",
        departedAt = "2026-11-11T20:00:00+02:00", variant = "fastest", distanceKm = "1030.0", durationMin = 670,
        vehicleName = "My Skoda", fuelType = "diesel", consumptionL100 = "5.8", priceDate = "2026-11-03",
        totalEur = "166.44", lines = lines.toList(),
    )

    private val lt = CostLineDto("fuel", "LT", "150.0", "1.7", "15.0")
    private val de = CostLineDto("fuel", "DE", "140.0", "1.7", "15.0")
    private val sk = CostLineDto("fuel", "SK", "60.0", "1.6", "6.0")
    private val profiles = listOf(
        SpeedProfileDto("LT", "winter", "85.0", 110, null),
        SpeedProfileDto("DE", "winter", "103.0", null, "no general limit, advised 130"),
    )

    @Test fun speedRowShowsAverageAndLimit() {
        val rows = DetailsText.speedRows(trip(lt), profiles)
        assertEquals("Lithuania · 150 km", rows.single().title)
        assertEquals("Average 85 km/h · limit 110", rows.single().detail)
    }

    @Test fun countryWithoutLimitShowsTheNote() {
        assertEquals("Average 103 km/h · no general limit, advised 130", DetailsText.speedRows(trip(de), profiles).single().detail)
    }

    @Test fun missingProfileUsesTheRoutingEngineMessage() {
        val row = DetailsText.speedRows(trip(sk), profiles).single()
        assertTrue(row.missing)
        assertEquals(Messages.noSpeedData("Slovakia"), row.detail)
    }

    @Test fun roadLinesAreNotCountryRows() {
        assertEquals(1, DetailsText.speedRows(trip(lt, CostLineDto("road", "LT", null, null, "5.0")), profiles).size)
    }

    @Test fun seasonFollowsTheMonth() {
        assertTrue(DetailsText.season(LocalDate.of(2026, 11, 24)).startsWith("winter"))
        assertTrue(DetailsText.season(LocalDate.of(2026, 7, 1)).startsWith("summer"))
    }

    private val points = listOf(
        TrendPointDto("2026-11-02", "1.620"),
        TrendPointDto("2026-10-26", "1.630"),
        TrendPointDto("2026-11-09", "1.620"),
    )

    @Test fun trendIsNewestFirstWithChangeAgainstThePreviousWeek() {
        val rows = DetailsText.trendRows(points)
        assertEquals("9 Nov", rows[0].date)
        assertEquals("1.620 · 0.000", rows[0].text)
        assertEquals("1.620 · −0.010", rows[1].text)
        assertEquals("1.630", rows[2].text)
    }

    @Test fun fewerThanTwelveWeeksAreExplained() {
        assertEquals(Messages.fewWeeks(3), DetailsText.trendNote(points))
        assertNull(DetailsText.trendNote(List(12) { TrendPointDto("2026-01-0${it % 9 + 1}", "1.5") }))
        assertFalse(DetailsText.trendNote(emptyList()) != null)
    }

    @Test fun lodgingPriceRules() {
        assertNull(Validators.lodgingPrice("61.00"))
        assertNull(Validators.lodgingPrice("61,5"))
        assertEquals(Messages.LODGING_AMOUNT, Validators.lodgingPrice("0"))
        assertEquals(Messages.LODGING_AMOUNT, Validators.lodgingPrice("12.345"))
        assertEquals(Messages.LODGING_AMOUNT, Validators.lodgingPrice("123456"))
        assertEquals(Messages.LODGING_DIGITS, Validators.lodgingPrice("abc"))
        assertEquals(Messages.LODGING_AMOUNT, Validators.lodgingPrice(""))
    }
}
