package com.routewise.app.ui

import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Messages
import com.routewise.app.core.SilentLogger
import com.routewise.app.data.Calculation
import com.routewise.app.data.TripCalculator
import com.routewise.app.data.VehicleRepository
import com.routewise.app.data.api.FakeRouteWiseApi
import com.routewise.app.data.session.InMemoryTokenStore
import com.routewise.app.data.session.Session
import com.routewise.app.domain.PlaceCatalog
import com.routewise.app.ui.plan.PlanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTest {
    private lateinit var vm: PlanViewModel
    private lateinit var result: MutableStateFlow<Calculation?>

    private fun place(name: String) = PlaceCatalog.search(name).first()

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        val session = Session(InMemoryTokenStore())
        val api = FakeRouteWiseApi(session, latencyMs = 0)
        kotlinx.coroutines.runBlocking { api.login(FakeRouteWiseApi.DEMO_EMAIL, FakeRouteWiseApi.DEMO_PASSWORD) }
        result = MutableStateFlow(null)
        vm = PlanViewModel(VehicleRepository(api), TripCalculator(api, SilentLogger), ErrorHandler(SilentLogger), result)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun calculateIsDisabledWithAReasonUntilBothPointsAreSet() = runTest {
        assertFalse(vm.state.value.canCalculate)
        assertEquals(Messages.SET_DESTINATION, vm.state.value.hint)
        vm.pickDestination(place("Prague"))
        assertEquals(Messages.SET_START, vm.state.value.hint)
        vm.pickOrigin(place("Vilnius"))
        assertTrue(vm.state.value.canCalculate)
        assertNull(vm.state.value.hint)
    }

    @Test fun placeOutsideTheSupportedCountriesIsRefused() = runTest {
        vm.pickDestination(place("Minsk"))
        assertNull(vm.state.value.destination.selected)
        assertEquals(Messages.OUTSIDE_AREA, vm.state.value.destination.error)
    }

    @Test fun searchNeedsThreeCharactersAndReportsUnknownPlaces() = runTest {
        vm.onOriginQuery("Vi")
        assertTrue(vm.state.value.origin.suggestions.isEmpty())
        vm.onOriginQuery("Zzzz")
        assertEquals(Messages.noPlaces("Zzzz"), vm.state.value.origin.error)
    }

    @Test fun dailyLimitStopsAtFourAndTenHours() = runTest {
        repeat(10) { vm.stepLimit(-1) }
        assertEquals(4, vm.state.value.dailyLimitH)
        repeat(10) { vm.stepLimit(1) }
        assertEquals(10, vm.state.value.dailyLimitH)
    }

    @Test fun fuelAtStartIsShownOnlyForACarWithATank() = runTest {
        vm.refreshVehicles()
        advanceUntilIdle()
        assertTrue(vm.state.value.showFuelAtStart)
    }

    @Test fun calculatingStoresTheResultAndKeepsOneHistoryEntry() = runTest {
        vm.refreshVehicles()
        advanceUntilIdle()
        vm.pickOrigin(place("Vilnius"))
        vm.pickDestination(place("Warsaw"))
        var opened = false
        vm.calculate { opened = true }
        advanceUntilIdle()
        assertTrue(opened)
        assertNotNull(result.value)
        assertEquals("full", result.value!!.request.startFuel)
    }
}
