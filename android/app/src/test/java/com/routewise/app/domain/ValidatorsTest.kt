package com.routewise.app.domain

import com.routewise.app.core.Messages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {
    @Test fun emailAcceptsNormalAddress() = assertNull(Validators.email("name@example.com"))

    @Test fun emailRejectsMissingDomain() = assertEquals(Messages.EMAIL_INVALID, Validators.email("name@"))

    @Test fun passwordNeedsTenCharacters() {
        assertEquals(Messages.passwordShort(9), Validators.newPassword("123456789"))
        assertNull(Validators.newPassword("1234567890"))
    }

    @Test fun privacyMustBeAccepted() {
        assertEquals(Messages.PRIVACY_REQUIRED, Validators.privacyAccepted(false))
        assertNull(Validators.privacyAccepted(true))
    }

    @Test fun vehicleNameLimits() {
        assertNotNull(Validators.vehicleName("  "))
        assertNull(Validators.vehicleName("Skoda"))
        assertNotNull(Validators.vehicleName("x".repeat(61)))
    }

    @Test fun consumptionRangeAndDecimalComma() {
        assertNull(Validators.consumption("5,8"))
        assertNull(Validators.consumption("2.0"))
        assertNull(Validators.consumption("30"))
        assertNotNull(Validators.consumption("1.9"))
        assertNotNull(Validators.consumption("30.1"))
        assertNotNull(Validators.consumption("abc"))
    }

    @Test fun tankRange() {
        assertNull(Validators.tank("50"))
        assertNotNull(Validators.tank("10"))
        assertNotNull(Validators.tank("121"))
    }

    @Test fun searchNeedsThreeCharacters() {
        assertEquals(Messages.SEARCH_SHORT, Validators.searchQuery("Vi"))
        assertNull(Validators.searchQuery("Vil"))
    }

    @Test fun dailyLimitRange() {
        assertNotNull(Validators.dailyLimit(3))
        assertNull(Validators.dailyLimit(4))
        assertNull(Validators.dailyLimit(10))
        assertNotNull(Validators.dailyLimit(11))
    }
}
