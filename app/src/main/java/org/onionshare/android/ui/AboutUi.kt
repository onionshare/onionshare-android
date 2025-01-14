package org.onionshare.android.ui

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.onionshare.android.BuildConfig.VERSION_NAME
import org.onionshare.android.R
import org.onionshare.android.ui.theme.OnionshareTheme
import org.onionshare.android.ui.theme.topBar

@Composable
fun AboutUi(navController: NavHostController) {
    Scaffold(topBar = {
        AboutActionBar(navController, R.string.about_title)
    }) { innerPadding ->
        val scrollableState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollableState)
        ) {
            AboutHeader(modifier = Modifier.padding(top = 32.dp))
            Text(
                text = stringResource(R.string.about_text),
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(top = 24.dp),
            )
            TextList(
                headline = stringResource(R.string.about_contributors), items = listOf(
                    stringResource(R.string.about_contributor_creator, "Micah Lee"),
                    stringResource(R.string.about_contributor_android, "Torsten Grote"),
                    stringResource(R.string.about_contributor_android, "Michael Rogers"),
                    stringResource(R.string.about_contributor_pm, "Nathan Freitas"),
                    stringResource(R.string.about_contributor_design, "Glenn Sorrentino"),
                )
            )
            TextList(
                headline = stringResource(R.string.about_contributing_orgs), items = listOf(
                    "Guardian Project",
                    "The Calyx Institute",
                    "Tor Project",
                    "Briar Project",
                )
            )
            Column(modifier = Modifier.padding(top = 24.dp)) {
                val uriHandler = LocalUriHandler.current
                Text(
                    text = stringResource(R.string.about_links_title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.body1,
                )
                Text(
                    text = stringResource(R.string.about_links_homepage),
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.secondary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { uriHandler.openUri("https://onionshare.org") }
                )
                Text(
                    text = stringResource(R.string.about_links_github),
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.secondary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { uriHandler.openUri("https://github.com/onionshare") }
                )
                Text(
                    text = stringResource(R.string.about_links_privacy_policy),
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.secondary,
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 8.dp)
                        .clickable { uriHandler.openUri("https://onionshare.org/privacy") }
                )
            }
        }
    }
}

@Composable
fun AboutActionBar(
    navController: NavHostController,
    @StringRes res: Int,
) = TopAppBar(
    backgroundColor = MaterialTheme.colors.topBar,
    navigationIcon = {
        IconButton(onClick = { navController.popBackStack() }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
    },
    title = { Text(stringResource(res)) },
)

@Composable
fun AboutHeader(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_logo_about),
            contentDescription = null, // decorative element
        )
        Text(
            text = stringResource(R.string.about_app_version, VERSION_NAME),
            style = MaterialTheme.typography.body1,
            modifier = Modifier
                .padding(top = 16.dp)
                .alpha(0.75f)
        )
    }
}

@Composable
fun TextList(headline: String, items: List<String>) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = headline,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.body1,
        )
        items.iterator().forEach { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AboutPreview() = OnionshareTheme {
    Surface(color = MaterialTheme.colors.background) {
        AboutUi(rememberNavController())
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AboutPreviewDark() = AboutPreview()
