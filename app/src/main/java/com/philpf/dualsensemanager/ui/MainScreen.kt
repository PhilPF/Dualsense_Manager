package com.philpf.dualsensemanager.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.Permissions
import com.philpf.dualsensemanager.core.ControllerView
import com.philpf.dualsensemanager.core.ControllerViews
import com.philpf.dualsensemanager.core.ThemeMode
import com.philpf.dualsensemanager.device.ControllerSource
import com.philpf.dualsensemanager.device.SettingsStore
import com.philpf.dualsensemanager.service.LightKeeperService
import com.philpf.dualsensemanager.ui.art.BluetoothIcon
import com.philpf.dualsensemanager.ui.art.GearIcon
import com.philpf.dualsensemanager.ui.glass.Backdrop
import com.philpf.dualsensemanager.ui.glass.GlassIconButton
import com.philpf.dualsensemanager.ui.glass.GlassSurface
import com.philpf.dualsensemanager.ui.glass.backdropSource
import com.philpf.dualsensemanager.ui.glass.rememberBackdrop
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.LocalGlass

private val TopBarHeight = 56.dp
private const val OPAQUE = 0xFF shl 24

/** Opens the settings sheet when passed as the initially open sheet. */
const val OPEN_SETTINGS = "settings"

/**
 * The one screen: a cell per controller, each glowing in its light colour, under a floating
 * glass bar; sheets for a controller and for settings.
 *
 * [resumeTick] changes every time the activity resumes, to re-check permissions.
 */
@Composable
fun MainScreen(
    source: ControllerSource,
    settings: SettingsStore,
    theme: ThemeMode,
    resumeTick: Int,
    askForPermissions: Boolean,
    initiallyOpen: String? = null,
) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val profiles by source.profiles.collectAsState()
    val players by source.players.collectAsState()
    val live by source.live.collectAsState()
    val previews by source.previews.collectAsState()
    val views = remember(profiles, players, live) { ControllerViews.build(profiles.values, players, live) }
    val sortedPlayers = remember(players) { players.values.sortedBy { it.name.lowercase() } }
    val now = remember(resumeTick, live) { System.currentTimeMillis() }

    fun lightOf(view: ControllerView): Color = Color(OPAQUE or (previews[view.id] ?: view.color))

    // Sheets. shownId keeps the controller sheet's content during its closing animation.
    var openId by rememberSaveable { mutableStateOf(initiallyOpen?.takeIf { it != OPEN_SETTINGS }) }
    var shownId by rememberSaveable { mutableStateOf(openId) }
    var settingsOpen by rememberSaveable { mutableStateOf(initiallyOpen == OPEN_SETTINGS) }
    val openView = views.firstOrNull { it.id == openId }
    val shownView = views.firstOrNull { it.id == shownId }
    LaunchedEffect(openView == null) { if (openView == null) openId = null }

    // Permissions: asked once automatically; afterwards from the bar's Bluetooth button.
    var permissionTick by remember { mutableIntStateOf(0) }
    var askedHere by remember { mutableStateOf(false) }
    val nearbyGranted = remember(resumeTick, permissionTick) { Permissions.hasBluetoothConnect(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionTick++
        if (source.live.value.isNotEmpty()) LightKeeperService.ensureRunning(context)
    }
    LaunchedEffect(Unit) {
        if (askForPermissions && !settings.askedForPermissions && !Permissions.hasBluetoothConnect(context)) {
            settings.askedForPermissions = true
            askedHere = true
            permissionLauncher.launch(Permissions.requested)
        }
    }
    val askAgain = {
        if (!askedHere) {
            askedHere = true
            permissionLauncher.launch(Permissions.requested)
        } else {
            openAppSettings(context)
        }
    }

    // Where each connected controller's light is on screen; read while drawing, so scrolling
    // only redraws the background.
    val glowAt = remember { mutableStateMapOf<String, Offset>() }
    val background = rememberBackdrop()
    val content = rememberBackdrop()

    Box(Modifier.fillMaxSize().background(glass.backgroundBottom)) {
        Box(Modifier.fillMaxSize().backdropSource(content)) {
            GlowBackground(
                spots = { views.filter { it.isConnected }.mapNotNull { v -> glowAt[v.id]?.let { GlowSpot(it, lightOf(v)) } } },
                glass = glass,
                modifier = Modifier.fillMaxSize().backdropSource(background),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(TopBarHeight + 2.dp))
                if (views.isEmpty()) EmptyCell(background, onBluetooth = { openBluetoothSettings(context) })
                for (view in views) {
                    ControllerCell(
                        view = view,
                        light = lightOf(view),
                        backdrop = background,
                        now = now,
                        onOpen = {
                            openId = view.id
                            shownId = view.id
                        },
                        onFind = { source.identify(view.id) },
                        onGlow = { glowAt[view.id] = it },
                    )
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }

        TopBar(
            backdrop = content,
            showPermission = !nearbyGranted,
            onPermission = askAgain,
            onSettings = { settingsOpen = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        GlassSheet(visible = openView != null, backdrop = content, onDismiss = { openId = null }) {
            val view = shownView
            if (view != null) {
                ControllerSheetContent(
                    view = view,
                    light = lightOf(view),
                    players = sortedPlayers,
                    source = source,
                    now = now,
                    onClose = { openId = null },
                )
            }
        }
        GlassSheet(visible = settingsOpen, backdrop = content, onDismiss = { settingsOpen = false }) {
            SettingsSheetContent(settings = settings, theme = theme, onClose = { settingsOpen = false })
        }
    }
}

/** Floating glass capsule: the app's name, and buttons for permissions (when missing) and settings. */
@Composable
private fun TopBar(
    backdrop: Backdrop,
    showPermission: Boolean,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(TopBarHeight),
        shape = CircleShape,
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 22.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Dualsense Manager",
                style = GlassType.headline.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (showPermission) {
                GlassIconButton(onClick = onPermission, label = "Allow Nearby devices") {
                    BluetoothIcon(glass.danger, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
            }
            GlassIconButton(onClick = onSettings, label = "Settings") {
                GearIcon(glass.textPrimary, Modifier.size(22.dp))
            }
        }
    }
}

private fun openBluetoothSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}
