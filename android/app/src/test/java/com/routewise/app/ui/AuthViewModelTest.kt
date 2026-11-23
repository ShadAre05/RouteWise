package com.routewise.app.ui

import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Messages
import com.routewise.app.core.SilentLogger
import com.routewise.app.data.AuthRepository
import com.routewise.app.data.api.FakeRouteWiseApi
import com.routewise.app.data.session.InMemoryTokenStore
import com.routewise.app.data.session.Session
import com.routewise.app.ui.auth.AuthViewModel
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
class AuthViewModelTest {
    private lateinit var session: Session
    private lateinit var vm: AuthViewModel

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        session = Session(InMemoryTokenStore())
        val api = FakeRouteWiseApi(session, latencyMs = 0)
        vm = AuthViewModel(AuthRepository(api, session), ErrorHandler(SilentLogger))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun invalidEmailIsReportedWithoutCallingTheServer() = runTest {
        vm.onEmail("not-an-email")
        vm.onPassword("whatever-123")
        vm.submit()
        advanceUntilIdle()
        assertEquals(Messages.EMAIL_INVALID, vm.state.value.emailError)
        assertNull(session.current)
    }

    @Test fun registrationNeedsPrivacyAndLongPassword() = runTest {
        vm.switchMode()
        vm.onEmail("new@example.com")
        vm.onPassword("short")
        vm.submit()
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(Messages.passwordShort(5), s.passwordError)
        assertEquals(Messages.PRIVACY_REQUIRED, s.privacyError)
    }

    @Test fun wrongPasswordShowsOneClearMessage() = runTest {
        vm.onEmail(FakeRouteWiseApi.DEMO_EMAIL)
        vm.onPassword("wrong-password")
        vm.submit()
        advanceUntilIdle()
        assertEquals(Messages.CREDENTIALS_WRONG, vm.state.value.formError)
        assertEquals(false, vm.state.value.loading)
    }

    @Test fun successfulLoginStoresTheSession() = runTest {
        vm.onEmail(FakeRouteWiseApi.DEMO_EMAIL)
        vm.onPassword(FakeRouteWiseApi.DEMO_PASSWORD)
        vm.submit()
        advanceUntilIdle()
        assertNotNull(session.current)
    }

    @Test fun registeringTakenEmailMarksTheEmailField() = runTest {
        vm.switchMode()
        vm.onEmail(FakeRouteWiseApi.DEMO_EMAIL)
        vm.onPassword("long-enough-password")
        vm.onPrivacy(true)
        vm.submit()
        advanceUntilIdle()
        assertEquals(Messages.EMAIL_TAKEN, vm.state.value.emailError)
    }
}
