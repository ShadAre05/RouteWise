package com.routewise.app.ui

import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Messages
import com.routewise.app.core.Notifier
import com.routewise.app.core.SilentLogger
import com.routewise.app.data.AuthRepository
import com.routewise.app.data.api.FakeRouteWiseApi
import com.routewise.app.data.session.InMemoryTokenStore
import com.routewise.app.data.session.Session
import com.routewise.app.ui.profile.ProfileViewModel
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private lateinit var session: Session
    private lateinit var vm: ProfileViewModel

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        session = Session(InMemoryTokenStore())
        val api = FakeRouteWiseApi(session, latencyMs = 0)
        val auth = AuthRepository(api, session)
        kotlinx.coroutines.runBlocking { auth.login(FakeRouteWiseApi.DEMO_EMAIL, FakeRouteWiseApi.DEMO_PASSWORD) }
        vm = ProfileViewModel(auth, ErrorHandler(SilentLogger), Notifier())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun profileShowsTheEmailOfTheSession() = runTest {
        assertEquals(FakeRouteWiseApi.DEMO_EMAIL, vm.state.value.email)
    }

    @Test fun repeatedPasswordMustMatch() = runTest {
        vm.openChange()
        vm.onOld(FakeRouteWiseApi.DEMO_PASSWORD)
        vm.onNew("another-password-1")
        vm.onRepeat("another-password-2")
        vm.changePassword()
        advanceUntilIdle()
        assertEquals(Messages.PASSWORD_MISMATCH, vm.state.value.repeatError)
        assertEquals(true, vm.state.value.changeOpen)
    }

    @Test fun newPasswordMustDifferFromTheCurrentOne() = runTest {
        vm.openChange()
        vm.onOld("same-password-123")
        vm.onNew("same-password-123")
        vm.onRepeat("same-password-123")
        vm.changePassword()
        advanceUntilIdle()
        assertEquals(Messages.PASSWORD_SAME, vm.state.value.newError)
    }

    @Test fun wrongCurrentPasswordKeepsTheFormOpen() = runTest {
        vm.openChange()
        vm.onOld("not-the-password")
        vm.onNew("another-password-1")
        vm.onRepeat("another-password-1")
        vm.changePassword()
        advanceUntilIdle()
        assertEquals(Messages.PASSWORD_WRONG, vm.state.value.oldError)
        assertEquals(true, vm.state.value.changeOpen)
    }

    @Test fun validChangeClosesTheForm() = runTest {
        vm.openChange()
        vm.onOld(FakeRouteWiseApi.DEMO_PASSWORD)
        vm.onNew("another-password-1")
        vm.onRepeat("another-password-1")
        vm.changePassword()
        advanceUntilIdle()
        assertEquals(false, vm.state.value.changeOpen)
    }

    @Test fun deletingNeedsTheRightPassword() = runTest {
        vm.openDelete()
        vm.onDeletePassword("wrong-password-1")
        vm.confirmDelete()
        advanceUntilIdle()
        assertEquals(Messages.DELETE_WRONG_PASSWORD, vm.state.value.deleteError)
        assertNotNull(session.current)
    }

    @Test fun deletingWithTheRightPasswordEndsTheSession() = runTest {
        vm.openDelete()
        vm.onDeletePassword(FakeRouteWiseApi.DEMO_PASSWORD)
        vm.confirmDelete()
        advanceUntilIdle()
        assertNull(session.current)
    }
}
