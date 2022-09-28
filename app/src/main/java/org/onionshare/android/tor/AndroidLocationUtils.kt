package org.onionshare.android.tor

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat.getSystemService
import org.slf4j.LoggerFactory.getLogger
import java.util.Locale
import java.util.Locale.getAvailableLocales
import javax.inject.Inject

class AndroidLocationUtils @Inject constructor(app: Application) {

    companion object {
        private val LOG = getLogger(AndroidLocationUtils::class.java)
    }

    private val appContext: Context = app.applicationContext

    val currentCountryName: String
        get() = getAvailableLocales().find { locale ->
            locale.country.equals(currentCountryIso, ignoreCase = true)
        }?.displayCountry ?: currentCountryIso

    /**
     * This guesses the current country from the first of these sources that
     * succeeds (also in order of likelihood of being correct):
     *
     *  * Phone network. This works even when no SIM card is inserted, or a foreign SIM card is inserted.
     *  * SIM card. This is only an heuristic and assumes the user is not roaming.
     *  * User locale. This is an even worse heuristic.
     *
     * Note: this is very similar to [this API](https://android.googlesource.com/platform/frameworks/base/+/cd92588%5E/location/java/android/location/CountryDetector.java)
     * except it seems that Google doesn't want us to use it for some reason -
     * both that class and `Context.COUNTRY_CODE` are annotated `@hide`.
     */
    @get:SuppressLint("DefaultLocale")
    val currentCountryIso: String
        get() {
            var countryCode: String? = countryFromPhoneNetwork
            if (!countryCode.isNullOrEmpty()) return countryCode.uppercase(Locale.getDefault())
            LOG.info("Falling back to SIM card country")
            countryCode = countryFromSimCard
            if (!countryCode.isNullOrEmpty()) return countryCode.uppercase(Locale.getDefault())
            LOG.info("Falling back to user-defined locale")
            return Locale.getDefault().country
        }
    private val countryFromPhoneNetwork: String?
        get() = getSystemService(appContext, TelephonyManager::class.java)?.networkCountryIso
    private val countryFromSimCard: String?
        get() = getSystemService(appContext, TelephonyManager::class.java)?.simCountryIso
}
