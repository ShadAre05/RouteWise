package com.routewise.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.app.core.Messages
import com.routewise.app.data.AuthRepository
import com.routewise.app.data.api.ApiErrorKind
import com.routewise.app.data.api.ApiException
import com.routewise.app.core.ErrorHandler
import com.routewise.app.domain.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthState(
    val registerMode: Boolean = false,
    val email: String = "",
    val password: String = "",
    val privacy: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val privacyError: String? = null,
    val formError: String? = null,
    val loading: Boolean = false,
)

/** SCR-01: log in or create an account. All validation is done here, so it can be tested without the UI. */
class AuthViewModel(
    private val auth: AuthRepository,
    private val errors: ErrorHandler,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state

    fun onEmail(value: String) = _state.update { it.copy(email = value, emailError = null, formError = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, passwordError = null, formError = null) }
    fun onPrivacy(value: Boolean) = _state.update { it.copy(privacy = value, privacyError = null) }
    fun switchMode() = _state.update { AuthState(registerMode = !it.registerMode, email = it.email) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val emailError = Validators.email(s.email)
        val passwordError = if (s.registerMode) Validators.newPassword(s.password)
        else if (s.password.isEmpty()) "Enter your password" else null
        val privacyError = if (s.registerMode) Validators.privacyAccepted(s.privacy) else null
        if (emailError != null || passwordError != null || privacyError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError, privacyError = privacyError) }
            return
        }
        _state.update { it.copy(loading = true, formError = null) }
        viewModelScope.launch {
            try {
                if (s.registerMode) auth.register(s.email, s.password) else auth.login(s.email, s.password)
                _state.update { it.copy(loading = false, password = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { fail(it, e) }
            }
        }
    }

    private fun fail(state: AuthState, e: Exception): AuthState {
        val kind = (e as? ApiException)?.kind
        return when {
            kind == ApiErrorKind.CONFLICT -> state.copy(loading = false, emailError = Messages.EMAIL_TAKEN)
            kind == ApiErrorKind.UNAUTHORIZED && !state.registerMode ->
                state.copy(loading = false, formError = Messages.CREDENTIALS_WRONG)
            else -> state.copy(loading = false, formError = errors.messageFor(e))
        }
    }
}
