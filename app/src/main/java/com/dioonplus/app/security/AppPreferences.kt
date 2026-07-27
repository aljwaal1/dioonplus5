package com.dioonplus.app.security

import android.content.Context
import android.util.Base64
import com.dioonplus.app.util.CurrencyOption
import java.security.MessageDigest
import java.security.SecureRandom

class AppPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "dioon_plus_preferences",
        Context.MODE_PRIVATE,
    )

    fun hasPin(): Boolean = preferences.contains(KEY_PIN_HASH) && preferences.contains(KEY_PIN_SALT)

    fun setPin(pin: String) {
        require(pin.matches(Regex("\\d{4,6}"))) { "رمز PIN يجب أن يتكون من 4 إلى 6 أرقام" }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = hashPin(pin, salt)
        preferences.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saltText = preferences.getString(KEY_PIN_SALT, null) ?: return false
        val expectedText = preferences.getString(KEY_PIN_HASH, null) ?: return false
        return runCatching {
            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val expected = Base64.decode(expectedText, Base64.NO_WRAP)
            MessageDigest.isEqual(expected, hashPin(pin, salt))
        }.getOrDefault(false)
    }

    fun clearPin() {
        preferences.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).apply()
    }

    var soundEnabled: Boolean
        get() = preferences.getBoolean(KEY_SOUND, true)
        set(value) = preferences.edit().putBoolean(KEY_SOUND, value).apply()

    var vibrationEnabled: Boolean
        get() = preferences.getBoolean(KEY_VIBRATION, true)
        set(value) = preferences.edit().putBoolean(KEY_VIBRATION, value).apply()

    var recoveryEmail: String
        get() = preferences.getString(KEY_RECOVERY_EMAIL, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_RECOVERY_EMAIL, value.trim()).apply()

    var currency: CurrencyOption
        get() = CurrencyOption.fromCode(preferences.getString(KEY_CURRENCY, CurrencyOption.JOD.code))
        set(value) = preferences.edit().putString(KEY_CURRENCY, value.code).apply()

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest()
    }

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_RECOVERY_EMAIL = "recovery_email"
        private const val KEY_CURRENCY = "currency_code"
    }
}
