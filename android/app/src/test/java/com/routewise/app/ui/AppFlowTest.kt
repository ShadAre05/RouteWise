package com.routewise.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.routewise.app.AppContainer
import com.routewise.app.core.Messages
import com.routewise.app.core.SilentLogger
import com.routewise.app.data.api.FakeRouteWiseApi
import com.routewise.app.data.session.InMemoryTokenStore
import com.routewise.app.data.session.Session
import com.routewise.app.ui.navigation.RouteWiseRoot
import com.routewise.app.ui.theme.RouteWiseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** UI and end-to-end tests on the JVM (Robolectric) against the built-in sample-data API. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppFlowTest {
    @get:Rule val rule = createComposeRule()

    private fun container(): AppContainer {
        val session = Session(InMemoryTokenStore())
        return AppContainer(SilentLogger, session, FakeRouteWiseApi(session, latencyMs = 0))
    }

    /** Waits for a node; on timeout the failure names the tag and prints the screen, so a CI log is enough to diagnose it. */
    private fun waitFor(tag: String) {
        try {
            rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Node with tag '$tag' never appeared. Screen:\n" + rule.onRoot().printToString(), e)
        }
    }

    @Test fun loginFormShowsValidationErrorsAndDoesNotSubmit() {
        val container = container()
        rule.setContent { RouteWiseTheme { RouteWiseRoot(container) } }
        rule.onNodeWithTag("tab_login").performClick()
        rule.onNodeWithTag("email").performTextInput("not-an-email")
        // The button is below the visible area of the small test screen, so scroll to it first.
        rule.onNodeWithTag("submit").performScrollTo().performClick()
        rule.onNodeWithText(Messages.EMAIL_INVALID).assertExists()
        rule.onNodeWithText("Enter your password").assertExists()
    }

    @Test fun userCanLogInPlanATripAndSeeTheTotal() {
        val container = container()
        rule.setContent { RouteWiseTheme { RouteWiseRoot(container) } }

        rule.onNodeWithTag("tab_login").performClick()
        rule.onNodeWithTag("email").performTextInput(FakeRouteWiseApi.DEMO_EMAIL)
        rule.onNodeWithTag("password").performTextInput(FakeRouteWiseApi.DEMO_PASSWORD)
        // The button is below the visible area of the small test screen, so scroll to it first.
        rule.onNodeWithTag("submit").performScrollTo().performClick()
        waitFor("calculate")

        rule.onNodeWithTag("origin").performTextInput("Vilnius")
        waitFor("origin_option_Vilnius")
        rule.onNodeWithTag("origin_option_Vilnius").performClick()
        rule.onNodeWithTag("destination").performTextInput("Warsaw")
        waitFor("destination_option_Warsaw")
        rule.onNodeWithTag("destination_option_Warsaw").performClick()
        // The button is below the visible area of the small test screen, so scroll to it first.
        rule.onNodeWithTag("calculate").performScrollTo().performClick()

        waitFor("total")
        rule.onNodeWithTag("total").assertIsDisplayed()
        rule.onNodeWithTag("variant_shortest").assertIsDisplayed()
        rule.onNodeWithTag("saved_badge").assertIsDisplayed()
    }
}
