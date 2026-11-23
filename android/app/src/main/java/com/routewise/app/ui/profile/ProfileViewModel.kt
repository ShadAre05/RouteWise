package com.routewise.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Messages
import com.routewise.app.core.Notifier
import com.routewise.app.data.AuthRepository
import com.routewise.app.data.api.ApiErrorKind
import com.routewise.app.data.api.ApiException
import com.routewise.app.domain.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(
    val oldPassword: String = "",
    val newPassword: String = "",
    val oldError: String? = null,
    val newError: String? = null,
    val saving: Boolean = false,
    val confirmDelete: Boolean = false,
)

/** SCR-07: change the password, log out, delete the account. */
class ProfileViewModel(
    private val auth: AuthRepository,
    private val errors: ErrorHandler,
    private val notifier: Notifier,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state

    fun onOld(value: String) = _state.update { it.copy(oldPassword = value, oldError = null) }
    fun onNew(value: String) = _state.update { it.copy(newPassword = value, newError = null) }

    fun changePassword() {
        val s = _state.value
        if (s.saving) return
        val oldError = if (s.oldPassword.isEmpty()) "Enter your current password" else null
        val newError = Validators.newPassword(s.newPassword)
        if (oldError != null || newError != null) {
            _state.update { it.copy(oldError = oldError, newError = newError) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                auth.changePassword(s.oldPassword, s.newPassword)
                _state.update { ProfileState() }
                notifier.show("Password changed")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val wrong = e is ApiException && e.kind == ApiErrorKind.UNAUTHORIZED
                _state.update {
                    it.copy(saving = false, oldError = if (wrong) Messages.PASSWORD_WRONG else null)
                }
                if (!wrong) notifier.show(errors.messageFor(e))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                auth.logout()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The local session is already removed; the server will expire the refresh token by itself.
            }
        }
    }

    fun askDelete() = _state.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _state.update { it.copy(confirmDelete = false) }

    fun confirmDelete() {
        _state.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            try {
                auth.deleteAccount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notifier.show(errors.messageFor(e))
            }
        }
    }
}
