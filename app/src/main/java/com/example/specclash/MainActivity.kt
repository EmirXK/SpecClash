package com.example.specclash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import com.example.specclash.ui.home.HomeScreen
import com.example.specclash.ui.theme.SpecClashTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: keep system bars transparent and let our content
        // (incl. dynamic dark theme) decide the icon color.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb(),
            ) { true },
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb(),
            ) { true },
        )
        setContent {
            SpecClashTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                ) { _ ->
                    HomeScreen()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    SpecClashTheme {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) { _ ->
            HomeScreen()
        }
    }
}

