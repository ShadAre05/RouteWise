package com.routewise.app.domain

import com.routewise.app.data.OpenMeteoWeather
import com.routewise.app.data.Weather
import com.routewise.app.data.api.PointDto
import com.routewise.app.data.api.TripDto
import com.routewise.app.data.api.TripRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StopsWeatherTest {
    private val request = TripRequest(
        originLabel = "Vilnius, Lithuania", destinationLabel = "Prague, Czechia",
        origin = PointDto(25.2797, 54.6872), destination = PointDto(14.4378, 50.0755), dailyLimitH = 8,
    )

    private fun trip(nights: Int = 1) = TripDto(
        id = "1", title = "t", originLabel = "Vilnius, Lithuania", destinationLabel = "Prague, Czechia",
        departedAt = "2026-11-11T08:00:00+02:00", variant = "fastest", distanceKm = "1030.0", durationMin = 800,
        nights = nights, dailyLimitH = "8.0", vehicleName = "c", fuelType = "diesel", consumptionL100 = "5.8",
        priceDate = "2026-11-03", totalEur = "100.00",
    )

    @Test fun noNightsMeansNoStop() = assertNull(Stops.firstStop(request, trip(nights = 0)))

    @Test fun stopIsAPlaceAlongTheRouteAtTheDailyLimit() {
        val stop = Stops.firstStop(request, trip())!!
        assertEquals(480, stop.afterMinutes)
        assertEquals(618, stop.km)
        assertEquals(LocalDate.of(2026, 11, 11), stop.checkIn)
        assertEquals(LocalDate.of(2026, 11, 12), stop.checkOut)
        assertTrue(stop.place.countryCode in listOf("PL", "LT"))
    }

    @Test fun vilniusWarsawWithFourHoursStopsNearBialystokNotInWarsaw() {
        val warsaw = request.copy(destinationLabel = "Warsaw, Poland", destination = PointDto(21.0122, 52.2297), dailyLimitH = 4)
        val stop = Stops.firstStop(warsaw, trip().copy(durationMin = 360, distanceKm = "390.0", dailyLimitH = "4.0"))!!
        assertEquals("Bialystok", stop.place.name)
    }

    @Test fun bookingLinkCarriesCityDatesAndGuests() {
        val stop = OvernightStop(Place("Warsaw", "PL", 21.0, 52.2), 640, 480, LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12))
        assertEquals(
            "https://www.booking.com/searchresults.html?ss=Warsaw%2C+Poland&checkin=2026-11-11&checkout=2026-11-12&group_adults=2&no_rooms=1",
            Stops.bookingUrl(stop, 2),
        )
    }

    @Test fun forecastResponseIsParsed() {
        val body = """{"daily":{"time":["2026-11-12"],"weather_code":[61],"temperature_2m_max":[7.4],
            |"temperature_2m_min":[1.6],"precipitation_probability_max":[40]}}""".trimMargin()
        val weather = OpenMeteoWeather.parse(body, LocalDate.of(2026, 11, 12))!!
        assertEquals(7, weather.maxC)
        assertEquals(2, weather.minC)
        assertEquals(40, weather.rainPercent)
        assertEquals("Prague, 12 Nov: 2…7 °C, rain · 40 % chance of precipitation", WeatherText.line("Prague", weather))
    }

    @Test fun brokenForecastGivesNoWeather() {
        assertNull(OpenMeteoWeather.parse("""{"error":true}""", LocalDate.of(2026, 11, 12)))
        assertNotNull(Weather(LocalDate.of(2026, 11, 12), 0, 1, null, 0))
    }
}
