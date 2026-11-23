package com.routewise.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.routewise.app.ui.components.ConfirmDialog
import com.routewise.app.ui.components.FormField
import com.routewise.app.ui.components.LoadingButton
import com.routewise.app.ui.components.SectionCard

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val s by viewModel.state.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.titleLarge)
        SectionCard {
            Text("Change password", style = MaterialTheme.typography.titleMedium)
            FormField(s.oldPassword, viewModel::onOld, "Current password", s.oldError, tag = "old_password", password = true)
            FormField(s.newPassword, viewModel::onNew, "New password", s.newError, tag = "new_password", password = true, supporting = "At least 10 characters")
            LoadingButton("Change password", s.saving, viewModel::changePassword, tag = "change_password")
        }
        OutlinedButton(onClick = viewModel::logout, modifier = Modifier.testTag("logout")) { Text("Log out") }
        TextButton(onClick = viewModel::askDelete, modifier = Modifier.testTag("delete_account")) {
            Text("Delete account", color = MaterialTheme.colorScheme.error)
        }
    }
    if (s.confirmDelete) {
        ConfirmDialog(
            title = "Delete your account?",
            text = "Your cars and saved trips are deleted permanently. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}
