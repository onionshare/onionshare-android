package org.onionshare.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import dagger.hilt.android.AndroidEntryPoint
import org.onionshare.android.ui.theme.OnionshareTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnionshareTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    Greeting("OnionSharer")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
    ) {
        Text(text = "Hello $name!", modifier = Modifier
            .padding(all = Dp(16.0f))
            .align(Alignment.Center)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    OnionshareTheme {
        Greeting("Android with a long name a preview")
    }
}
