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
    val email: String = "",
    val showPrivacy: Boolean = false,
    val changeOpen: Boolean = false,
    val oldPassword: String = "",
    val newPassword: String = "",
    val repeatPassword: String = "",
    val oldError: String? = null,
    val newError: String? = null,
    val repeatError: String? = null,
    val saving: Boolean = false,
    val deleteOpen: Boolean = false,
    val deletePassword: String = "",
    val deleteError: String? = null,
    val deleting: Boolean = false,
)

/** SCR-07: privacy notice, change password, log out, delete account. */
class ProfileViewModel(
    private val auth: AuthRepository,
    private val errors: ErrorHandler,
    private val notifier: Notifier,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState(email = auth.email.orEmpty()))
    val state: StateFlow<ProfileState> = _state

    fun showPrivacy(show: Boolean) = _state.update { it.copy(showPrivacy = show) }

    // ---- change password ------------------------------------------------------------------------------------

    fun openChange() = _state.update { it.copy(changeOpen = true) }

    fun closeChange() = _state.update {
        it.copy(changeOpen = false, oldPassword = "", newPassword = "", repeatPassword = "", oldError = null, newError = null, repeatError = null)
    }

    fun onOld(value: String) = _state.update { it.copy(oldPassword = value, oldError = null) }

    fun onNew(value: String) = _state.update {
        val live = if (it.newError != null) Validators.newPassword(value) else null
        it.copy(newPassword = value, newError = live)
    }

    fun onRepeat(value: String) = _state.update { it.copy(repeatPassword = value, repeatError = null) }

    fun onNewBlur() = _state.update {
        if (it.newPassword.isEmpty()) it else it.copy(newError = Validators.newPassword(it.newPassword))
    }

    fun onRepeatBlur() = _state.update {
        if (it.repeatPassword.isEmpty()) it
        else it.copy(repeatError = if (it.repeatPassword == it.newPassword) null else Messages.PASSWORD_MISMATCH)
    }

    fun changePassword() {
        val s = _state.value
        if (s.saving) return
        val oldError = if (s.oldPassword.isEmpty()) "Enter your current password" else null
        val newError = Validators.newPassword(s.newPassword)
            ?: if (s.newPassword == s.oldPassword) Messages.PASSWORD_SAME else null
        val repeatError = if (s.repeatPassword == s.newPassword) null else Messages.PASSWORD_MISMATCH
        if (oldError != null || newError != null || repeatError != null) {
            _state.update { it.copy(oldError = oldError, newError = newError, repeatError = repeatError) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                auth.changePassword(s.oldPassword, s.newPassword)
                closeChange()
                _state.update { it.copy(saving = false) }
                notifier.show(Messages.PASSWORD_CHANGED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val wrong = e is ApiException && e.kind == ApiErrorKind.UNAUTHORIZED
                _state.update { it.copy(saving = false, oldError = if (wrong) Messages.PASSWORD_WRONG else null) }
                if (!wrong) notifier.show(errors.messageFor(e))
            }
        }
    }

    // ---- log out and delete ---------------------------------------------------------------------------------

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

    fun openDelete() = _state.update { it.copy(deleteOpen = true, deletePassword = "", deleteError = null) }

    fun closeDelete() = _state.update { it.copy(deleteOpen = false, deletePassword = "", deleteError = null) }

    fun onDeletePassword(value: String) = _state.update { it.copy(deletePassword = value, deleteError = null) }

    fun confirmDelete() {
        val s = _state.value
        if (s.deleting || s.deletePassword.isEmpty()) return
        _state.update { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            try {
                auth.deleteAccount(s.deletePassword)
                _state.update { it.copy(deleting = false, deleteOpen = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val wrong = e is ApiException && e.kind == ApiErrorKind.UNAUTHORIZED
                _state.update {
                    it.copy(deleting = false, deleteError = if (wrong) Messages.DELETE_WRONG_PASSWORD else errors.messageFor(e))
                }
            }
        }
    }
}
