package com.philpf.dualsensemanager

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.philpf.dualsensemanager.core.ControllerPart
import com.philpf.dualsensemanager.core.ControllerViews
import com.philpf.dualsensemanager.core.ThemeMode
import com.philpf.dualsensemanager.device.ControllerSource
import com.philpf.dualsensemanager.device.DemoSource
import com.philpf.dualsensemanager.device.InputHighlights
import com.philpf.dualsensemanager.service.LightKeeperService
import com.philpf.dualsensemanager.ui.MainScreen
import com.philpf.dualsensemanager.ui.RemoteViewsPreview
import com.philpf.dualsensemanager.ui.theme.DualsenseTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private val app: DualsenseApp get() = application as DualsenseApp
    private var resumeTick by mutableIntStateOf(0)
    private val highlights = InputHighlights()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Debug builds can show sample controllers, for screenshots: --ez demo true
        // [--es theme light|dark] [--es open settings|demo-1|demo-2|demo-3] [--es press CROSS,L1]
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val demo = debuggable && intent.getBooleanExtra("demo", false)
        val source: ControllerSource = if (demo) DemoSource() else app.manager
        val forcedTheme = if (demo) {
            intent.getStringExtra("theme")?.let { name -> ThemeMode.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        } else {
            null
        }
        val initiallyOpen = if (demo) intent.getStringExtra("open") else null
        val pressed: StateFlow<Map<Int, Set<ControllerPart>>> = if (demo && intent.hasExtra("press")) {
            val parts = intent.getStringExtra("press").orEmpty().split(',')
                .mapNotNull { name -> ControllerPart.entries.firstOrNull { it.name == name.trim() } }
            MutableStateFlow(mapOf(DemoSource.FIRST_DEVICE_ID to parts.toSet()))
        } else {
            highlights.pressed
        }

        setContent {
            val stored by app.settings.theme.collectAsState()
            val mode = forcedTheme ?: stored
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            DualsenseTheme(dark = dark) {
                if (demo && initiallyOpen == "widget") {
                    val views = ControllerViews.build(source.profiles.value.values, source.players.value, source.live.value)
                    RemoteViewsPreview(views.filter { it.isConnected })
                    return@DualsenseTheme
                }
                MainScreen(
                    source = source,
                    settings = app.settings,
                    pressedFlow = pressed,
                    theme = mode,
                    resumeTick = resumeTick,
                    askForPermissions = !demo,
                    initiallyOpen = initiallyOpen,
                )
            }
        }
    }

    // Controller buttons light up on the screen instead of acting on it.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        highlights.onKey(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean =
        highlights.onMotion(ev) || super.dispatchGenericMotionEvent(ev)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        highlights.noteTouchpad(ev)
        return super.dispatchTouchEvent(ev)
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
