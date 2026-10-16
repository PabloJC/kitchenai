package com.kitchenai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kitchenai.ui.App

class MainActivity : ComponentActivity() {
    // Read on every frame the splash is up; the splash replaces SessionGate's spinner until it flips.
    @Volatile private var sessionSettled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !sessionSettled }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App(onSessionSettled = { sessionSettled = true }) }
    }
}
