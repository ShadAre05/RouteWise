package com.routewise.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.app.core.ErrorHandler
import com.routewise.app.core.Messages
import com.routewise.app.data.AuthRepository
import com.routewise.app.data.api.ApiErrorKind
import com.routewise.app.data.api.ApiException
import com.routewise.app.domain.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthState(
    val registerMode: Boolean = true,
    val email: String = "",
    val password: String = "",
    val privacy: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val privacyError: String? = null,
    /** The e-mail is already registered: the banner offers "Log in instead". */
    val emailTaken: Boolean = false,
    val formError: String? = null,
    val showPrivacy: Boolean = false,
    val loading: Boolean = false,
)

/** SCR-01: create an account or log in. All validation is done here, so it can be tested without the UI. */
class AuthViewModel(
    private val auth: AuthRepository,
    private val errors: ErrorHandler,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state

    fun onEmail(value: String) =
        _state.update { it.copy(email = value, emailError = null, emailTaken = false, formError = null) }

    /** The counter in "Use at least 10 characters (now 6)" follows the typing once the message is shown. */
    fun onPassword(value: String) = _state.update {
        val live = if (it.registerMode && it.passwordError != null) Validators.newPassword(value) else null
        it.copy(password = value, passwordError = live, formError = null)
    }

    fun onPrivacy(value: Boolean) = _state.update { it.copy(privacy = value, privacyError = null) }

    fun showPrivacy(show: Boolean) = _state.update { it.copy(showPrivacy = show) }

    fun onEmailBlur() = _state.update {
        if (it.email.isEmpty()) it else it.copy(emailError = Validators.email(it.email))
    }

    fun onPasswordBlur() = _state.update {
        if (it.registerMode && it.password.isNotEmpty()) it.copy(passwordError = Validators.newPassword(it.password)) else it
    }

    /** Switches between the two forms; the typed e-mail is kept. */
    fun select(register: Boolean) {
        if (_state.value.registerMode == register) return
        _state.update { AuthState(registerMode = register, email = it.email) }
    }

    fun logInInstead() = select(false)

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
        _state.update { it.copy(loading = true, formError = null, emailTaken = false) }
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
            kind == ApiErrorKind.CONFLICT -> state.copy(loading = false, emailTaken = true)
            kind == ApiErrorKind.UNAUTHORIZED && !state.registerMode ->
                state.copy(loading = false, formError = Messages.CREDENTIALS_WRONG)
            else -> state.copy(loading = false, formError = errors.messageFor(e))
        }
    }
}
