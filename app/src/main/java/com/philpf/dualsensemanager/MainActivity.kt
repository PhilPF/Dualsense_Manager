package com.philpf.dualsensemanager

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.philpf.dualsensemanager.service.LightKeeperService
import com.philpf.dualsensemanager.ui.MainScreen
import com.philpf.dualsensemanager.ui.theme.GlassTheme

class MainActivity : ComponentActivity() {

    private val app: DualsenseApp get() = application as DualsenseApp
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            GlassTheme {
                MainScreen(app = app, resumeTick = resumeTick)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.manager.setUiVisible(true)
        // Controllers may have connected while the service could not be started.
        if (app.manager.live.value.isNotEmpty()) LightKeeperService.ensureRunning(this)
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    override fun onStop() {
        app.manager.setUiVisible(false)
        super.onStop()
    }
}
