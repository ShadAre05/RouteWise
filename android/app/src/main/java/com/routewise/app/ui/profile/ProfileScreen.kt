package com.routewise.app.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.routewise.app.ui.auth.PrivacyDialog
import com.routewise.app.ui.components.FormField
import com.routewise.app.ui.components.InfoBanner
import com.routewise.app.ui.components.LoadingButton

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val s by viewModel.state.collectAsState()
    BackHandler(enabled = s.changeOpen) { viewModel.closeChange() }

    if (s.changeOpen) {
        ChangePasswordScreen(s, viewModel)
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Profile", style = MaterialTheme.typography.titleLarge)
            Text(s.email, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).testTag("profile_email"))
            Text("Driver account", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            ProfileRow("Privacy notice", "What is stored and why", "privacy_row") { viewModel.showPrivacy(true) }
            ProfileRow("Change password", "Update your sign-in password", "change_password_row", viewModel::openChange)
            ProfileRow("Log out", null, "logout", viewModel::logout)
            ProfileRow("Delete account", "Removes all your data", "delete_account", viewModel::openDelete, danger = true)
        }
    }
    if (s.showPrivacy) PrivacyDialog(onClose = { viewModel.showPrivacy(false) })
    if (s.deleteOpen) DeleteDialog(s, viewModel)
}

@Composable
private fun ProfileRow(title: String, subtitle: String?, tag: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp).testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Text("›")
    }
}

@Composable
private fun ChangePasswordScreen(s: ProfileState, vm: ProfileViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = vm::closeChange, modifier = Modifier.testTag("change_back")) { Text("← Back") }
        Text("Change password", style = MaterialTheme.typography.titleLarge)
        InfoBanner("Use at least 10 characters. You stay logged in on this phone.", tag = "password_info")
        FormField(s.oldPassword, vm::onOld, "Current password", s.oldError, tag = "old_password", password = true)
        FormField(s.newPassword, vm::onNew, "New password", s.newError, tag = "new_password", password = true, onBlur = vm::onNewBlur)
        FormField(s.repeatPassword, vm::onRepeat, "Repeat new password", s.repeatError, tag = "repeat_password", password = true, onBlur = vm::onRepeatBlur)
        LoadingButton("Save new password", s.saving, vm::changePassword, tag = "change_password")
    }
}

@Composable
private fun DeleteDialog(s: ProfileState, vm: ProfileViewModel) {
    AlertDialog(
        onDismissRequest = vm::closeDelete,
        title = { Text("Delete your account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("All your cars, trips and profile data will be erased. Backups are cleared within 30 days. This cannot be undone.")
                FormField(s.deletePassword, vm::onDeletePassword, "Confirm with your password", s.deleteError, tag = "delete_password", password = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = vm::confirmDelete,
                enabled = s.deletePassword.isNotEmpty() && !s.deleting,
                modifier = Modifier.testTag("confirm_delete"),
            ) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = vm::closeDelete) { Text("Cancel") } },
    )
}
