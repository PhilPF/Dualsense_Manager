package com.philpf.dualsensemanager.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.DualsenseApp
import com.philpf.dualsensemanager.Permissions
import com.philpf.dualsensemanager.core.ControllerRow
import com.philpf.dualsensemanager.core.ControllerRows
import com.philpf.dualsensemanager.service.LightKeeperService
import com.philpf.dualsensemanager.ui.art.ControllerArt
import com.philpf.dualsensemanager.ui.glass.Backdrop
import com.philpf.dualsensemanager.ui.glass.GlassButton
import com.philpf.dualsensemanager.ui.glass.GlassSurface
import com.philpf.dualsensemanager.ui.glass.SectionLabel
import com.philpf.dualsensemanager.ui.glass.backdropSource
import com.philpf.dualsensemanager.ui.glass.rememberBackdrop
import com.philpf.dualsensemanager.ui.theme.GlassColors
import com.philpf.dualsensemanager.ui.theme.GlassType

private val TopBarHeight = 56.dp
private const val OPAQUE = 0xFF shl 24

/**
 * The one screen: connected controllers as glass cards over a glow of their light colours,
 * remembered ones below, and a sheet to rename and recolour a controller.
 *
 * [resumeTick] changes every time the activity resumes, to re-check permissions.
 */
@Composable
fun MainScreen(app: DualsenseApp, resumeTick: Int) {
    val context = LocalContext.current
    val manager = app.manager
    val profiles by app.store.profiles.collectAsState()
    val live by manager.live.collectAsState()
    val previews by manager.previews.collectAsState()
    val rows = remember(profiles, live) { ControllerRows.build(profiles.values, live) }
    val connected = rows.filter { it.isConnected }
    val away = rows.filterNot { it.isConnected }
    val now = remember(resumeTick, live) { System.currentTimeMillis() }

    fun lightOf(row: ControllerRow): Color = Color(OPAQUE or (previews[row.id] ?: row.profile.color))

    // The sheet: openId is what is open; shownId keeps the content during the closing animation.
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var shownId by rememberSaveable { mutableStateOf<String?>(null) }
    val openRow = rows.firstOrNull { it.id == openId }
    val shownRow = rows.firstOrNull { it.id == shownId }
    LaunchedEffect(openRow == null) { if (openRow == null) openId = null }
    BackHandler(enabled = openRow != null) { openId = null }
    val open: (ControllerRow) -> Unit = {
        openId = it.id
        shownId = it.id
    }

    var permissionTick by remember { mutableIntStateOf(0) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val nearbyGranted = remember(resumeTick, permissionTick) { Permissions.hasBluetoothConnect(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionTick++
        if (manager.live.value.isNotEmpty()) LightKeeperService.ensureRunning(context)
    }

    val background = rememberBackdrop()
    val content = rememberBackdrop()

    Box(Modifier.fillMaxSize().background(GlassColors.Background)) {
        Box(Modifier.fillMaxSize().backdropSource(content)) {
            AuroraBackground(connected.map(::lightOf), Modifier.fillMaxSize().backdropSource(background))
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(TopBarHeight + 28.dp))

                if (!nearbyGranted) {
                    PermissionCard(background) {
                        if (!askedOnce) {
                            askedOnce = true
                            permissionLauncher.launch(Permissions.requested)
                        } else {
                            openAppSettings(context)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                if (connected.isEmpty()) {
                    EmptyState(background) { openBluetoothSettings(context) }
                } else {
                    SectionLabel("Connected")
                    for (row in connected) {
                        ControllerCard(row, lightOf(row), background) { open(row) }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                if (away.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Not connected")
                    AwayGroup(away, ::lightOf, background, now, open)
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Each controller keeps its number, nickname and colour, and gets them back " +
                        "whenever it connects, over Bluetooth or USB.",
                    style = GlassType.caption,
                    color = GlassColors.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                Spacer(Modifier.height(24.dp))
            }
        }

        TopBar(content, connected.map(::lightOf), Modifier.align(Alignment.TopCenter))

        AnimatedVisibility(
            visible = openRow != null,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x73000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        openId = null
                    },
            )
        }
        AnimatedVisibility(
            visible = openRow != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            val row = shownRow
            if (row != null) {
                ControllerSheet(
                    row = row,
                    light = lightOf(row),
                    backdrop = content,
                    manager = manager,
                    now = now,
                    onClose = { openId = null },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp),
                )
            }
        }
    }
}

/** Floating glass capsule with the app name and the colours of the connected controllers. */
@Composable
private fun TopBar(backdrop: Backdrop, lights: List<Color>, modifier: Modifier = Modifier) {
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
            Modifier.fillMaxSize().padding(start = 22.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Dualsense Manager",
                style = GlassType.headline.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (lights.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (light in lights.take(6)) LightDot(light)
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = if (lights.isEmpty()) "None connected" else "${lights.size} connected",
                style = GlassType.caption,
                color = GlassColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun LightDot(light: Color) {
    Canvas(Modifier.size(10.dp)) {
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(lerp(light, Color.White, 0.55f), light), radius = r), radius = r)
    }
}

@Composable
private fun PermissionCard(backdrop: Backdrop, onAllow: () -> Unit) {
    GlassSurface(backdrop = backdrop, modifier = Modifier.fillMaxWidth(), tint = GlassColors.Accent.copy(alpha = 0.06f)) {
        Column(Modifier.padding(20.dp)) {
            Text("Apply colours automatically", style = GlassType.headline)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Allow “Nearby devices” so the app notices when a controller connects while it " +
                    "is closed and sets its colour straight away" +
                    (if (Build.VERSION.SDK_INT >= 34) ", and so it can keep the colours on in the background" else "") +
                    ". A quiet notification shows each connected controller's battery.",
                style = GlassType.callout,
                color = GlassColors.TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            GlassButton("Allow", onClick = onAllow, primary = true, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun EmptyState(backdrop: Backdrop, onOpenBluetooth: () -> Unit) {
    GlassSurface(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ControllerArt(light = GlassColors.TextTertiary, lit = false, modifier = Modifier.width(170.dp))
            Spacer(Modifier.height(16.dp))
            Text("No controller connected", style = GlassType.title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Press the PS button to wake a paired DualSense. To pair a new one, hold PS and " +
                    "Create until the light bar blinks, then choose “DualSense Wireless Controller” " +
                    "in Bluetooth settings.",
                style = GlassType.callout,
                color = GlassColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            GlassButton("Open Bluetooth settings", onClick = onOpenBluetooth, primary = true)
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
