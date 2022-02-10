package org.onionshare.android.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.onionshare.android.ui.theme.OnionshareTheme

const val ROUTE_SHARE = "share"
const val ROUTE_ABOUT = "about"

@Composable
fun MainUi(
    viewModel: MainViewModel,
    onFabClicked: () -> Unit,
    onSheetButtonClicked: () -> Unit,
) = OnionshareTheme {
    Surface(color = MaterialTheme.colors.background) {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = ROUTE_SHARE) {
            composable(ROUTE_SHARE) {
                ShareUi(
                    navController = navController,
                    stateFlow = viewModel.shareState,
                    onFabClicked = onFabClicked,
                    onFileRemove = viewModel::removeFile,
                    onRemoveAll = viewModel::removeAll,
                    onSheetButtonClicked = onSheetButtonClicked,
                )
            }
            composable(ROUTE_ABOUT) { AboutUi(navController) }
        }
    }
}
