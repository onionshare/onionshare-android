package org.onionshare.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.onionshare.android.ui.settings.MyBridgesUi
import org.onionshare.android.ui.settings.SettingsTorUi
import org.onionshare.android.ui.settings.SettingsUi
import org.onionshare.android.ui.share.ShareUiSetup
import org.onionshare.android.ui.theme.OnionshareTheme

const val ROUTE_SHARE = "share"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_SETTINGS_TOR = "settings-tor"
const val ROUTE_SETTINGS_MY_BRIDGES = "settings-myBridges"
const val ROUTE_ABOUT = "about"

@Composable
fun MainUi(viewModel: MainViewModel) = OnionshareTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = ROUTE_SHARE) {
            composable(ROUTE_SHARE) {
                ShareUiSetup(navController, viewModel)
            }
            composable(ROUTE_SETTINGS) {
                SettingsUi(navController)
            }
            composable(ROUTE_SETTINGS_TOR) {
                SettingsTorUi(navController)
            }
            composable(ROUTE_SETTINGS_MY_BRIDGES) {
                MyBridgesUi(navController)
            }
            composable(ROUTE_ABOUT) {
                AboutUi(navController)
            }
        }
    }
}
