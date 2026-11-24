package com.routewise.app.data

import com.routewise.app.data.api.FakeRouteWiseApi
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPriceDateTest {
    @Test
    fun `demo prices are three days old relative to the given day`() {
        assertEquals("2026-11-22", FakeRouteWiseApi.demoPriceDate(LocalDate.of(2026, 11, 25)))
    }

    @Test
    fun `demo prices never look stale in demo mode`() {
        val date = LocalDate.parse(FakeRouteWiseApi.demoPriceDate())
        assertTrue(ChronoUnit.DAYS.between(date, LocalDate.now()) <= 14)
    }
}
