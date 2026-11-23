package com.routewise.app.data

import com.routewise.app.core.SilentLogger
import com.routewise.app.data.api.FakeRouteWiseApi
import com.routewise.app.data.api.PointDto
import com.routewise.app.data.api.TripRequest
import com.routewise.app.data.session.InMemoryTokenStore
import com.routewise.app.data.session.Session
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TripCalculatorTest {
    private lateinit var api: FakeRouteWiseApi
    private lateinit var calculator: TripCalculator

    private val request = TripRequest(
        originLabel = "Vilnius, Lithuania", destinationLabel = "Warsaw, Poland",
        origin = PointDto(25.2797, 54.6872), destination = PointDto(21.0122, 52.2297),
    )

    @Before fun setUp() = runTest {
        api = FakeRouteWiseApi(Session(InMemoryTokenStore()), latencyMs = 0)
        api.login(FakeRouteWiseApi.DEMO_EMAIL, FakeRouteWiseApi.DEMO_PASSWORD)
        calculator = TripCalculator(api, SilentLogger)
    }

    @Test fun oneCalculationLeavesOneEntryInHistory() = runTest {
        val result = calculator.calculate(request)
        assertEquals(1, api.listTrips(50, 0).total)
        assertEquals("fastest", result.selected.variant)
        assertEquals(3, result.summaries.size)
    }

    @Test fun selectingAnotherRouteReplacesTheEntry() = runTest {
        val first = calculator.calculate(request)
        val second = calculator.select(first, "shortest")
        val page = api.listTrips(50, 0)
        assertEquals(1, page.total)
        assertEquals("shortest", page.items.single().variant)
        assertEquals("shortest", second.selected.variant)
    }

    @Test fun selectingTheSameRouteDoesNothing() = runTest {
        val first = calculator.calculate(request)
        assertTrue(calculator.select(first, "fastest") === first)
        assertEquals(1, api.listTrips(50, 0).total)
    }

    @Test fun deltasAreComparedWithFastest() = runTest {
        val result = calculator.calculate(request)
        assertEquals(0, result.deltaEur("fastest")!!.signum())
    }
}
