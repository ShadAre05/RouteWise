package com.routewise.app.ui

import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Notifier
import com.routewise.app.core.SilentLogger
import com.routewise.app.data.VehicleRepository
import com.routewise.app.data.api.FakeRouteWiseApi
import com.routewise.app.data.session.InMemoryTokenStore
import com.routewise.app.data.session.Session
import com.routewise.app.ui.vehicles.VehiclesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehiclesViewModelTest {
    private lateinit var vm: VehiclesViewModel

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        val session = Session(InMemoryTokenStore())
        val api = FakeRouteWiseApi(session, latencyMs = 0)
        kotlinx.coroutines.runBlocking { api.login(FakeRouteWiseApi.DEMO_EMAIL, FakeRouteWiseApi.DEMO_PASSWORD) }
        vm = VehiclesViewModel(VehicleRepository(api), ErrorHandler(SilentLogger), Notifier())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun listLoadsTheDemoCar() = runTest {
        advanceUntilIdle()
        val state = vm.list.value as Load.Ready
        assertEquals("My Skoda", state.data.single().name)
    }

    @Test fun invalidFormShowsFieldErrorsAndStaysOpen() = runTest {
        advanceUntilIdle()
        vm.startAdd()
        vm.onConsumption("45")
        vm.save()
        advanceUntilIdle()
        val form = vm.form.value
        assertNotNull(form?.nameError)
        assertNotNull(form?.consumptionError)
    }

    @Test fun validFormAddsTheCarAndClosesTheForm() = runTest {
        advanceUntilIdle()
        vm.startAdd()
        vm.onName("Golf")
        vm.onConsumption("6,2")
        vm.save()
        advanceUntilIdle()
        assertEquals(null, vm.form.value)
        assertTrue((vm.list.value as Load.Ready).data.any { it.name == "Golf" && it.consumptionL100 == "6.2" })
    }

    @Test fun deleteNeedsConfirmation() = runTest {
        advanceUntilIdle()
        val car = (vm.list.value as Load.Ready).data.single()
        vm.askDelete(car)
        advanceUntilIdle()
        assertEquals(1, (vm.list.value as Load.Ready).data.size)
        vm.confirmDelete()
        advanceUntilIdle()
        assertEquals(0, (vm.list.value as Load.Ready).data.size)
    }
}
