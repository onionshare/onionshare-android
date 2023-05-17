package org.onionshare.android.ui.settings

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV
import androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme
import androidx.security.crypto.MasterKey
import androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM
import org.briarproject.onionwrapper.LocationUtils
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val PREF_AUTO_BRIDGES = "autoBridges"
private const val PREF_CUSTOM_BRIDGES = "customBridges"
private val VALID_BRIDGE_PREFIXES = listOf("obfs4", "meek_lite", "snowflake")

@Singleton
class SettingsManager @Inject constructor(
    app: Application,
    private val locationUtils: LocationUtils,
) {
    private val masterKeyAlias = MasterKey.Builder(app).setKeyScheme(AES256_GCM).build()
    private val sharedPreferences = EncryptedSharedPreferences.create(
        app, "secret_shared_prefs", masterKeyAlias,
        AES256_SIV, PrefValueEncryptionScheme.AES256_GCM
    )

    private val _automaticBridges = mutableStateOf(sharedPreferences.getBoolean(PREF_AUTO_BRIDGES, true))
    val automaticBridges: State<Boolean> = _automaticBridges

    private val _customBridges =
        mutableStateOf<List<String>>(sharedPreferences.getStringSet(PREF_CUSTOM_BRIDGES, emptySet())!!.toList())
    val customBridges: State<List<String>> = _customBridges

    val currentCountry: String
        get() = Locale.getAvailableLocales().find { locale ->
            locale.country.equals(locationUtils.currentCountry, ignoreCase = true)
        }?.displayCountry ?: locationUtils.currentCountry

    fun setAutomaticBridges(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_AUTO_BRIDGES, enabled).apply()
        _automaticBridges.value = enabled
    }

    fun addCustomBridges(text: String): Int {
        val newBridges = text.split('\n').filter { bridgeLine ->
            // TODO validate more?
            bridgeLine.isNotBlank() && VALID_BRIDGE_PREFIXES.any { bridgeLine.startsWith(it) }
        }
        updateCustomBridges(customBridges.value.toSet() + newBridges)
        return newBridges.size
    }

    fun removeCustomBridge(bridge: String) {
        val set = customBridges.value.toMutableSet().apply { remove(bridge) }
        updateCustomBridges(set)
    }

    private fun updateCustomBridges(set: Set<String>) {
        sharedPreferences.edit().putStringSet(PREF_CUSTOM_BRIDGES, set).apply()
        _customBridges.value = set.toList()
    }

}
