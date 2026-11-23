package com.routewise.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    private fun waitFor(tag: String) = rule.waitUntil(5_000) {
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun loginFormShowsValidationErrorsAndDoesNotSubmit() {
        rule.setContent { RouteWiseTheme { RouteWiseRoot(container()) } }
        rule.onNodeWithTag("email").performTextInput("not-an-email")
        rule.onNodeWithTag("submit").performClick()
        rule.onNodeWithText(Messages.EMAIL_INVALID).assertIsDisplayed()
        rule.onNodeWithText("Enter your password").assertIsDisplayed()
    }

    @Test fun userCanLogInPlanATripAndSeeTheTotal() {
        rule.setContent { RouteWiseTheme { RouteWiseRoot(container()) } }

        rule.onNodeWithTag("email").performTextInput(FakeRouteWiseApi.DEMO_EMAIL)
        rule.onNodeWithTag("password").performTextInput(FakeRouteWiseApi.DEMO_PASSWORD)
        rule.onNodeWithTag("submit").performClick()
        waitFor("calculate")

        rule.onNodeWithTag("origin").performTextInput("Vilnius")
        waitFor("origin_option_Vilnius")
        rule.onNodeWithTag("origin_option_Vilnius").performClick()
        rule.onNodeWithTag("destination").performTextInput("Warsaw")
        waitFor("destination_option_Warsaw")
        rule.onNodeWithTag("destination_option_Warsaw").performClick()
        rule.onNodeWithTag("calculate").performClick()

        waitFor("total")
        rule.onNodeWithTag("total").assertIsDisplayed()
        rule.onNodeWithTag("variant_shortest").assertIsDisplayed()
    }
}
