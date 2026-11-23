package com.routewise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class FormattingTest {
    @Test fun durationShowsHoursAndMinutes() {
        assertEquals("2 h 05 min", Formatting.duration(125))
        assertEquals("45 min", Formatting.duration(45))
    }

    @Test fun signedEurShowsDirection() {
        assertEquals("+", Formatting.signedEur(BigDecimal("3.50")).take(1))
        assertEquals("−", Formatting.signedEur(BigDecimal("-3.50")).take(1))
    }

    @Test fun missingAmountIsNotShownAsZero() = assertEquals("no data", Formatting.eur(null))

    @Test fun variantNames() {
        assertEquals("No tolls", Formatting.variantName("no_tolls"))
        assertEquals("Fastest", Formatting.variantName("fastest"))
    }
}
