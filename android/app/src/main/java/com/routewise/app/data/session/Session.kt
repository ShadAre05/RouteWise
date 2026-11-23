package com.routewise.app.data.session

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Tokens(val accessToken: String, val refreshToken: String, val email: String? = null)

/** Persistent storage of the tokens. The real implementation encrypts them with a key from the Android Keystore. */
interface TokenStore {
    fun load(): Tokens?
    fun save(tokens: Tokens)
    fun clear()
}

class InMemoryTokenStore(private var tokens: Tokens? = null) : TokenStore {
    override fun load(): Tokens? = tokens
    override fun save(tokens: Tokens) {
        this.tokens = tokens
    }

    override fun clear() {
        tokens = null
    }
}

class SecureTokenStore(context: Context) : TokenStore {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun load(): Tokens? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return Tokens(access, refresh, prefs.getString(KEY_EMAIL, null))
    }

    override fun save(tokens: Tokens) {
        prefs.edit().putString(KEY_ACCESS, tokens.accessToken).putString(KEY_REFRESH, tokens.refreshToken)
            .putString(KEY_EMAIL, tokens.email).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "routewise_session"
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"
        const val KEY_EMAIL = "email"
    }
}

/** The signed-in state of the app. The UI observes [tokens]; the API client reads and renews them. */
class Session(private val store: TokenStore) {
    private val _tokens = MutableStateFlow(store.load())
    private val _expired = MutableStateFlow(false)

    val tokens: StateFlow<Tokens?> get() = _tokens

    /** True after the server refused to renew the tokens: the login screen explains why the user is back there. */
    val expired: StateFlow<Boolean> get() = _expired

    val current: Tokens? get() = _tokens.value

    fun update(tokens: Tokens) {
        store.save(tokens)
        _expired.value = false
        _tokens.value = tokens
    }

    fun clear(expired: Boolean = false) {
        store.clear()
        _expired.value = expired
        _tokens.value = null
    }
}
