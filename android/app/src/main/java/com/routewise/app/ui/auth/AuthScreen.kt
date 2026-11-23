package com.routewise.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.routewise.app.core.Messages
import com.routewise.app.ui.components.FormField
import com.routewise.app.ui.components.InfoBanner
import com.routewise.app.ui.components.LoadingButton

@Composable
fun AuthScreen(viewModel: AuthViewModel, sessionExpired: Boolean) {
    val s by viewModel.state.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("RouteWise", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text("Know the cost of your trip before you drive", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))

        TabRow(selectedTabIndex = if (s.registerMode) 0 else 1) {
            Tab(selected = s.registerMode, onClick = { viewModel.select(true) }, text = { Text("Register") }, modifier = Modifier.testTag("tab_register"))
            Tab(selected = !s.registerMode, onClick = { viewModel.select(false) }, text = { Text("Log in") }, modifier = Modifier.testTag("tab_login"))
        }
        Spacer(Modifier.height(8.dp))

        if (sessionExpired) {
            InfoBanner(Messages.SESSION_EXPIRED, warning = true, tag = "session_expired")
        }
        if (s.emailTaken) {
            InfoBanner(
                Messages.EMAIL_TAKEN.substringBefore(" Log in"),
                warning = true,
                actionLabel = "Log in instead",
                onAction = viewModel::logInInstead,
                tag = "email_taken",
            )
        }
        s.formError?.let { InfoBanner(it, warning = true, tag = "form_error") }

        FormField(
            s.email, viewModel::onEmail, "E-mail", s.emailError, tag = "email",
            keyboard = KeyboardType.Email, placeholder = "name@example.com", onBlur = viewModel::onEmailBlur,
        )
        FormField(
            s.password, viewModel::onPassword, "Password", s.passwordError, tag = "password", password = true,
            placeholder = "Your password", onBlur = viewModel::onPasswordBlur,
        )
        if (s.registerMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = s.privacy, onCheckedChange = viewModel::onPrivacy, modifier = Modifier.testTag("privacy"))
                Text("I accept the privacy notice: ", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { viewModel.showPrivacy(true) }, modifier = Modifier.testTag("privacy_link")) {
                    Text("what data is stored and how to delete it")
                }
            }
            s.privacyError?.let {
                Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("privacy_error"))
            }
        }
        Spacer(Modifier.height(8.dp))
        LoadingButton(if (s.registerMode) "Create account" else "Log in", s.loading, viewModel::submit, tag = "submit")
    }
    if (s.showPrivacy) PrivacyDialog(onClose = { viewModel.showPrivacy(false) })
}

@Composable
fun PrivacyDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Privacy notice") },
        text = {
            Text(
                "RouteWise stores your e-mail address, your cars and your saved trips so that you can find them again. " +
                    "Passwords are stored only as a salted hash. Fuel prices come from the European Commission Weekly Oil Bulletin. " +
                    "You can delete a trip at any time and delete your whole account in Profile; all your data is then erased.",
            )
        },
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("privacy_close")) { Text("Close") } },
    )
}
