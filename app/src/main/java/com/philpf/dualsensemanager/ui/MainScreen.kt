package com.philpf.dualsensemanager.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.philpf.dualsensemanager.Permissions
import com.philpf.dualsensemanager.core.ControllerPart
import com.philpf.dualsensemanager.core.ControllerView
import com.philpf.dualsensemanager.core.ControllerViews
import com.philpf.dualsensemanager.core.Numbering
import com.philpf.dualsensemanager.core.Players
import com.philpf.dualsensemanager.core.PressOrder
import com.philpf.dualsensemanager.core.ThemeMode
import com.philpf.dualsensemanager.device.ControllerInput
import com.philpf.dualsensemanager.device.ControllerSource
import com.philpf.dualsensemanager.device.SettingsStore
import com.philpf.dualsensemanager.service.LightKeeperService
import com.philpf.dualsensemanager.ui.art.BluetoothIcon
import com.philpf.dualsensemanager.ui.art.CloseIcon
import com.philpf.dualsensemanager.ui.art.GearIcon
import com.philpf.dualsensemanager.ui.art.OrderIcon
import com.philpf.dualsensemanager.ui.glass.Backdrop
import com.philpf.dualsensemanager.ui.glass.GlassIconButton
import com.philpf.dualsensemanager.ui.glass.GlassSurface
import com.philpf.dualsensemanager.ui.glass.backdropSource
import com.philpf.dualsensemanager.ui.glass.rememberBackdrop
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.LocalGlass
import kotlin.math.roundToInt

private val TopBarHeight = 56.dp
private val CellGap = 14.dp
private const val OPAQUE = 0xFF shl 24

/** Opens the settings sheet when passed as the initially open sheet. */
const val OPEN_SETTINGS = "settings"

/** Starts putting the controllers in order when passed as the initially open sheet. */
const val OPEN_ORDER = "order"

/**
 * The one screen: a cell per controller, each glowing in its light colour, under a floating
 * glass bar; sheets for a controller, a player and settings. Long-press and drag a connected
 * controller to move it: controllers are numbered in this order. Or put them in order from
 * the bar: each player presses a button on their controller in turn. Pressing anything on a
 * controller floods its cell with its colour; its sheet shows which buttons are pressed and
 * where the sticks are.
 *
 * [input] is what the controllers' buttons and sticks are doing. [resumeTick] changes every
 * time the activity resumes, to re-check permissions.
 */
@Composable
fun MainScreen(
    source: ControllerSource,
    settings: SettingsStore,
    input: ControllerInput,
    theme: ThemeMode,
    resumeTick: Int,
    askForPermissions: Boolean,
    initiallyOpen: String? = null,
) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val haptics = LocalHapticFeedback.current
    val profiles by source.profiles.collectAsState()
    val players by source.players.collectAsState()
    val live by source.live.collectAsState()
    val previews by source.previews.collectAsState()
    val pressed by input.pressed.collectAsState()
    val presses by input.presses.collectAsState()
    // Read only while drawing the open sheet's controller, so moving a stick only redraws it.
    val sticks = input.sticks.collectAsState()
    val views = remember(profiles, players, live) { ControllerViews.build(profiles.values, players, live) }
    val sortedPlayers = remember(players) { players.values.sortedBy { it.name.lowercase() } }
    val now = remember(resumeTick, live) { System.currentTimeMillis() }

    fun lightOf(view: ControllerView): Color = Color(OPAQUE or (previews[view.id] ?: view.color))
    fun pressedOf(view: ControllerView): Set<ControllerPart> = view.live?.let { pressed[it.deviceId] } ?: emptySet()

    // Controller sheet. shownId keeps its content during the closing animation.
    var openId by rememberSaveable { mutableStateOf(initiallyOpen?.takeIf { it != OPEN_SETTINGS && it != OPEN_ORDER }) }
    var shownId by rememberSaveable { mutableStateOf(openId) }
    var settingsOpen by rememberSaveable { mutableStateOf(initiallyOpen == OPEN_SETTINGS) }
    val openView = views.firstOrNull { it.id == openId }
    val shownView = views.firstOrNull { it.id == shownId }
    LaunchedEffect(openView == null) { if (openView == null) openId = null }

    // Player sheet: editing an existing player, or creating one for a controller.
    var editPlayerId by rememberSaveable { mutableStateOf<String?>(null) }
    var newPlayerFor by rememberSaveable { mutableStateOf<String?>(null) }
    var playerSheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    val playerSheetOpen = editPlayerId != null || newPlayerFor != null
    val editedPlayer = editPlayerId?.let { players[it] }
    val playerPreviewOn = editPlayerId?.let { id -> profiles.values.firstOrNull { it.playerId == id }?.id } ?: newPlayerFor
    fun closePlayerSheet() {
        playerPreviewOn?.let(source::endPreview)
        editPlayerId = null
        newPlayerFor = null
    }

    // While a controller sheet is open, the other controllers' glows fade so its colour stands out.
    val othersGlow by animateFloatAsState(if (openView != null) 0.12f else 1f, tween(300), label = "othersGlow")

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

    // Putting the controllers in order by pressing: each connected controller's first press
    // takes the next number, and its player lights and place in the list follow at once.
    var ordering by remember { mutableStateOf(if (initiallyOpen == OPEN_ORDER) PressOrder() else null) }
    val latestViews by rememberUpdatedState(views)
    LaunchedEffect(ordering != null) {
        if (ordering == null) return@LaunchedEffect
        var seen = input.presses.value
        input.presses.collect { counts ->
            val fresh = counts.filter { (device, count) -> count > (seen[device] ?: 0) }.keys
            seen = counts
            for (device in fresh) {
                val view = latestViews.firstOrNull { it.live?.deviceId == device } ?: continue
                val current = ordering ?: break
                val next = current.press(view.id)
                if (next == current) continue
                ordering = next
                source.numberInOrder(next.order(latestViews.map { it.id }))
                source.nudge(view.id)
            }
            if (ordering?.complete(latestViews.filter { it.isConnected }.map { it.id }) == true) ordering = null
        }
    }
    BackHandler(enabled = ordering != null) { ordering = null }

    // Reordering connected controllers by dragging.
    val pitch = with(LocalDensity.current) { (CellHeight + CellGap).toPx() }
    val connectedIds = views.filter { it.isConnected }.map { it.id }
    val latestConnectedIds by rememberUpdatedState(connectedIds)
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dragFrom = connectedIds.indexOf(dragId)
    val dragTo = if (dragFrom < 0) -1 else (dragFrom + (dragOffset / pitch).roundToInt()).coerceIn(0, connectedIds.lastIndex)

    // Where each connected controller's light is on screen; read while drawing, so scrolling
    // only redraws the background.
    val glowAt = remember { mutableStateMapOf<String, Offset>() }
    val background = rememberBackdrop()
    val content = rememberBackdrop()

    Box(Modifier.fillMaxSize().background(glass.backgroundBottom)) {
        Box(Modifier.fillMaxSize().backdropSource(content)) {
            GlowBackground(
                spots = {
                    views.filter { it.isConnected }.mapNotNull { v ->
                        val at = glowAt[v.id] ?: return@mapNotNull null
                        val focus = if (shownId == null || v.id == shownId) 1f else othersGlow
                        GlowSpot(at, lightOf(v), focus)
                    }
                },
                glass = glass,
                modifier = Modifier.fillMaxSize().backdropSource(background),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(CellGap),
            ) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(TopBarHeight + 2.dp - CellGap))
                if (views.isEmpty()) EmptyCell(background, onBluetooth = { openBluetoothSettings(context) })
                for (view in views) {
                    key(view.id) {
                        val index = connectedIds.indexOf(view.id)
                        val shift = when {
                            dragId == null || index < 0 || view.id == dragId -> 0f
                            index in (dragFrom + 1)..dragTo -> -pitch
                            index in dragTo until dragFrom -> pitch
                            else -> 0f
                        }
                        val animatedShift by animateFloatAsState(shift, tween(180), label = "reorder")
                        val dragging = view.id == dragId
                        ControllerCell(
                            view = view,
                            light = lightOf(view),
                            backdrop = background,
                            now = now,
                            presses = view.live?.let { presses[it.deviceId] } ?: 0,
                            onOpen = {
                                openId = view.id
                                shownId = view.id
                            },
                            onFind = { source.identify(view.id) },
                            onGlow = { glowAt[view.id] = it },
                            modifier = Modifier
                                .zIndex(if (dragging) 1f else 0f)
                                .offset { IntOffset(0, (if (dragging) dragOffset else animatedShift).roundToInt()) }
                                .scale(if (dragging) 1.03f else 1f)
                                .then(
                                    if (index < 0) {
                                        Modifier
                                    } else {
                                        Modifier.pointerInput(view.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    dragId = view.id
                                                    dragOffset = 0f
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragOffset += amount.y
                                                },
                                                onDragEnd = {
                                                    val ids = latestConnectedIds
                                                    val from = ids.indexOf(view.id)
                                                    val to = (from + (dragOffset / pitch).roundToInt()).coerceIn(0, ids.lastIndex)
                                                    if (from >= 0 && to != from) source.reorder(Numbering.moved(ids, from, to))
                                                    dragId = null
                                                    dragOffset = 0f
                                                },
                                                onDragCancel = {
                                                    dragId = null
                                                    dragOffset = 0f
                                                },
                                            )
                                        }
                                    },
                                ),
                        )
                    }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }

        TopBar(
            backdrop = content,
            showPermission = !nearbyGranted,
            onPermission = askAgain,
            onSettings = { settingsOpen = true },
            ordering = ordering?.next,
            canOrder = connectedIds.size >= 2,
            onOrder = { ordering = PressOrder() },
            onStopOrdering = { ordering = null },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        GlassSheet(visible = openView != null, backdrop = content, onDismiss = { openId = null }) {
            val view = shownView
            if (view != null) {
                ControllerSheetContent(
                    view = view,
                    light = lightOf(view),
                    players = sortedPlayers,
                    pressed = pressedOf(view),
                    sticks = { view.live?.let { sticks.value[it.deviceId] } },
                    source = source,
                    now = now,
                    onEditPlayer = {
                        playerSheetFor = view.id
                        editPlayerId = it.id
                    },
                    onNewPlayer = {
                        playerSheetFor = view.id
                        newPlayerFor = view.id
                    },
                    onClose = { openId = null },
                )
            }
        }
        GlassSheet(visible = playerSheetOpen, backdrop = content, onDismiss = ::closePlayerSheet) {
            val controllerId = playerSheetFor
            if (controllerId != null) {
                val controller = views.firstOrNull { it.id == controllerId }
                PlayerSheetContent(
                    player = editedPlayer,
                    controllerId = controllerId,
                    previewOn = playerPreviewOn,
                    initialColor = controller?.let { previews[it.id] ?: it.color } ?: 0x0030FF,
                    defaultName = Players.defaultName(players.values.map { it.name }),
                    source = source,
                    onDone = ::closePlayerSheet,
                )
            }
        }
        GlassSheet(visible = settingsOpen, backdrop = content, onDismiss = { settingsOpen = false }) {
            SettingsSheetContent(
                settings = settings,
                theme = theme,
                colors = views.filter { it.isConnected }.map { it.color },
                onClose = { settingsOpen = false },
            )
        }
    }
}

/**
 * Floating glass capsule: the app's name, and buttons for permissions (when missing), putting the
 * controllers in order (with two or more connected) and settings. While [ordering], it asks for
 * a press for that place instead.
 */
@Composable
private fun TopBar(
    backdrop: Backdrop,
    showPermission: Boolean,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    ordering: Int?,
    canOrder: Boolean,
    onOrder: () -> Unit,
    onStopOrdering: () -> Unit,
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
            if (ordering != null) {
                Text(
                    text = "Player $ordering · press any button",
                    style = GlassType.headline.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                GlassIconButton(onClick = onStopOrdering, label = "Done") {
                    CloseIcon(glass.textPrimary, Modifier.size(16.dp))
                }
            } else {
                Text(
                    text = "Dualsense Manager",
                    style = GlassType.headline.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (canOrder) {
                    GlassIconButton(onClick = onOrder, label = "Put in order by pressing a button") {
                        OrderIcon(glass.textPrimary, Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                }
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
}

/**
 * The controller's own page in the system's Bluetooth settings (Android's Settings app), whose
 * "Disconnect" button turns it off. The list of Bluetooth devices instead when its [address] is
 * unknown or the page is missing (some makers' settings apps do not have it).
 */
internal fun openControllerSettings(context: Context, address: String?) {
    val page = address?.let {
        Intent(ACTION_DEVICE_PAGE)
            .setPackage(SETTINGS_PACKAGE)
            .putExtra(EXTRA_PAGE_ARGUMENTS, Bundle().apply { putString(KEY_DEVICE_ADDRESS, it) })
    }
    val opened = page != null && try {
        context.startActivity(page)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
    if (!opened) openBluetoothSettings(context)
}

// The device page of AOSP's Settings (Settings$BluetoothDeviceDetailActivity, exported to apps
// holding BLUETOOTH_CONNECT since Android 12) and how it is told which device to show.
private const val SETTINGS_PACKAGE = "com.android.settings"
private const val ACTION_DEVICE_PAGE = "com.android.settings.BLUETOOTH_DEVICE_DETAIL_SETTINGS"
private const val EXTRA_PAGE_ARGUMENTS = ":settings:show_fragment_args"
private const val KEY_DEVICE_ADDRESS = "device_address"

/** The system's Bluetooth settings (or all settings, when that page is missing). */
internal fun openBluetoothSettings(context: Context) {
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
