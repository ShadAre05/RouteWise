package com.routewise.app.ui

import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Messages
import com.routewise.app.core.Notifier
import com.routewise.app.core.SilentLogger
import com.routewise.app.data.Calculation
import com.routewise.app.data.ReferenceRepository
import com.routewise.app.data.TripCalculator
import com.routewise.app.data.TripRepository
import com.routewise.app.data.api.ApiErrorKind
import com.routewise.app.data.api.ApiException
import com.routewise.app.data.api.FakeRouteWiseApi
import com.routewise.app.data.api.PointDto
import com.routewise.app.data.api.RouteWiseApi
import com.routewise.app.data.api.TripPageDto
import com.routewise.app.data.api.TripRequest
import com.routewise.app.data.session.InMemoryTokenStore
import com.routewise.app.data.session.Session
import com.routewise.app.ui.details.FuelTrendViewModel
import com.routewise.app.ui.details.LodgingViewModel
import com.routewise.app.ui.details.RouteDetailsViewModel
import com.routewise.app.ui.history.HistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Network that can be switched off, to test the offline history. */
private class SwitchableApi(private val inner: RouteWiseApi) : RouteWiseApi by inner {
    var offline = false
    override suspend fun listTrips(limit: Int, offset: Int): TripPageDto {
        if (offline) throw ApiException(ApiErrorKind.NETWORK)
        return inner.listTrips(limit, offset)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelsTest {
    private lateinit var fake: FakeRouteWiseApi
    private lateinit var api: SwitchableApi
    private lateinit var calculator: TripCalculator
    private lateinit var holder: MutableStateFlow<Calculation?>
    private val errors = ErrorHandler(SilentLogger)
    private val notifier = Notifier()

    private val request = TripRequest(
        originLabel = "Vilnius, Lithuania", destinationLabel = "Prague, Czechia",
        origin = PointDto(25.2797, 54.6872), destination = PointDto(14.4378, 50.0755), startFuel = "half",
    )

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        fake = FakeRouteWiseApi(Session(InMemoryTokenStore()), latencyMs = 0)
        runBlocking { fake.login(FakeRouteWiseApi.DEMO_EMAIL, FakeRouteWiseApi.DEMO_PASSWORD) }
        api = SwitchableApi(fake)
        calculator = TripCalculator(api, SilentLogger)
        holder = MutableStateFlow(runBlocking { calculator.calculate(request) })
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun lodgingPriceReplacesTheEstimateAndKeepsOneHistoryEntry() = runTest {
        val vm = LodgingViewModel(calculator, holder, errors, notifier)
        val before = holder.value!!.selected
        assertTrue(before.nights > 0)
        vm.onPrice("61.00")
        vm.apply()
        advanceUntilIdle()
        val lodging = holder.value!!.selected.lines.first { it.kind == "lodging" }
        assertEquals("user_entered", lodging.status)
        assertEquals("61.00", lodging.amountEur)
        assertEquals(1, api.listTrips(50, 0).total)
    }

    @Test fun invalidPriceShowsTheMessageAndChangesNothing() = runTest {
        val vm = LodgingViewModel(calculator, holder, errors, notifier)
        val before = holder.value
        vm.onPrice("0")
        vm.apply()
        advanceUntilIdle()
        assertEquals(Messages.LODGING_AMOUNT, vm.state.value.error)
        assertTrue(before === holder.value)
    }

    @Test fun useEstimateRestoresTheEstimate() = runTest {
        val vm = LodgingViewModel(calculator, holder, errors, notifier)
        vm.onPrice("61")
        vm.apply()
        advanceUntilIdle()
        vm.useEstimate()
        advanceUntilIdle()
        assertEquals("estimate", holder.value!!.selected.lines.first { it.kind == "lodging" }.status)
    }

    @Test fun trendLoadsTheFuelOfTheCar() = runTest {
        val vm = FuelTrendViewModel(holder, ReferenceRepository(api), errors)
        vm.start()
        advanceUntilIdle()
        assertEquals("diesel", vm.state.value.fuelType)
        assertTrue(vm.state.value.countries.isNotEmpty())
        val points = (vm.state.value.points as Load.Ready).data
        assertEquals(12, points.size)
        assertTrue(points.zipWithNext().all { (a, b) -> a.bulletinDate < b.bulletinDate })
    }

    @Test fun missingHistoryIsAnEmptyListNotAnError() = runTest {
        val vm = FuelTrendViewModel(holder, ReferenceRepository(api), errors)
        vm.start()
        advanceUntilIdle()
        vm.select("XX")
        advanceUntilIdle()
        assertTrue((vm.state.value.points as Load.Ready).data.isEmpty())
    }

    @Test fun routeDetailsListEveryCountryOfTheRoute() = runTest {
        val vm = RouteDetailsViewModel(holder, ReferenceRepository(api), errors)
        vm.load()
        advanceUntilIdle()
        assertTrue((vm.rows.value as Load.Ready).data.isNotEmpty())
    }

    @Test fun withoutACacheOfflineIsAnError() = runTest {
        api.offline = true
        val vm = HistoryViewModel(TripRepository(api), errors, notifier)
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.list.value is Load.Failed)
        assertFalse(vm.offline.value)
    }

    @Test fun offlineViewUsesTheCacheOfTheSameRepository() = runTest {
        val repo = TripRepository(api)
        val vm = HistoryViewModel(repo, errors, notifier)
        vm.load()
        advanceUntilIdle()
        api.offline = true
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.offline.value)
        assertEquals(1, (vm.list.value as Load.Ready).data.size)
    }
}
