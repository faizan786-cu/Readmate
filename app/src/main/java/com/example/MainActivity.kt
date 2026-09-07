package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.navigation.ReadMateNavGraph
import com.example.ui.navigation.Screen
import com.example.ui.screens.splash.FirstLaunchSplash
import com.example.ui.theme.ReadMateTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DESTINATION = "extra_destination"
        const val EXTRA_QUOTE_ID = "extra_quote_id"
        const val DESTINATION_WISDOM_REELS = "wisdom_reels"
        const val DESTINATION_DAILY_RECALL = "daily_recall"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = applicationContext as ReadMateApplication
        val isAuthenticated = app.authSessionStorage.isAuthenticated()

        val destination = intent?.getStringExtra(EXTRA_DESTINATION)
        val startDestination = if (!isAuthenticated) {
            Screen.Auth.route
        } else when (destination) {
            DESTINATION_WISDOM_REELS -> Screen.WisdomReels.createRoute()
            DESTINATION_DAILY_RECALL -> Screen.DailyRecall.route
            else -> Screen.Dashboard.route
        }

        // Cold launch splash plays cleanly on startup for 1600ms
        setContent {
            ReadMateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSplash by remember { mutableStateOf(true) }

                    Crossfade(
                        targetState = showSplash,
                        animationSpec = tween(300),
                        label = "MainSplashCrossfade"
                    ) { isSplashVisible ->
                        if (isSplashVisible) {
                            FirstLaunchSplash(
                                onAnimationFinished = {
                                    showSplash = false
                                }
                            )
                        } else {
                            ReadMateNavGraph(startDestination = startDestination)
                        }
                    }
                }
            }
        }
    }
}

