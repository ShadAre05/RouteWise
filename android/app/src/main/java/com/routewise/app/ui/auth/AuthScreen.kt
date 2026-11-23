package com.routewise.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import com.routewise.app.ui.components.LoadingButton

@Composable
fun AuthScreen(viewModel: AuthViewModel, sessionExpired: Boolean) {
    val s by viewModel.state.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text("RouteWise", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text("Know the cost of your road trip before you leave.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        if (sessionExpired) {
            Text(Messages.SESSION_EXPIRED, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("session_expired"))
        }
        Text(if (s.registerMode) "Create account" else "Log in", style = MaterialTheme.typography.titleLarge)
        FormField(s.email, viewModel::onEmail, "E-mail", s.emailError, tag = "email", keyboard = KeyboardType.Email)
        FormField(
            s.password, viewModel::onPassword, "Password", s.passwordError, tag = "password", password = true,
            supporting = if (s.registerMode) "At least 10 characters" else null,
        )
        if (s.registerMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = s.privacy, onCheckedChange = viewModel::onPrivacy, modifier = Modifier.testTag("privacy"))
                Text("I accept the privacy notice", style = MaterialTheme.typography.bodyMedium)
            }
            s.privacyError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        s.formError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().testTag("form_error"))
        }
        Spacer(Modifier.height(8.dp))
        LoadingButton(if (s.registerMode) "Create account" else "Log in", s.loading, viewModel::submit, tag = "submit")
        TextButton(onClick = viewModel::switchMode, modifier = Modifier.testTag("switch_mode")) {
            Text(if (s.registerMode) "I already have an account" else "Create an account")
        }
    }
}
