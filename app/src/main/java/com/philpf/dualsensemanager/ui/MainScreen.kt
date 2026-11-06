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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import com.philpf.dualsensemanager.Permissions
import com.philpf.dualsensemanager.core.ControllerPart
import com.philpf.dualsensemanager.core.ControllerView
import com.philpf.dualsensemanager.core.ControllerViews
import com.philpf.dualsensemanager.core.Numbering
import com.philpf.dualsensemanager.core.Players
import com.philpf.dualsensemanager.core.ThemeMode
import com.philpf.dualsensemanager.device.ControllerInput
import com.philpf.dualsensemanager.device.ControllerSource
import com.philpf.dualsensemanager.device.HeadphonesSource
import com.philpf.dualsensemanager.device.SettingsStore
import com.philpf.dualsensemanager.service.LightKeeperService
import com.philpf.dualsensemanager.ui.art.BluetoothIcon
import com.philpf.dualsensemanager.ui.art.CloseIcon
import com.philpf.dualsensemanager.ui.art.GearIcon
import com.philpf.dualsensemanager.ui.art.GridIcon
import com.philpf.dualsensemanager.ui.art.ListIcon
import com.philpf.dualsensemanager.ui.art.OrderIcon
import com.philpf.dualsensemanager.ui.glass.Backdrop
import com.philpf.dualsensemanager.ui.glass.GlassIconButton
import com.philpf.dualsensemanager.ui.glass.GlassSurface
import com.philpf.dualsensemanager.ui.glass.SectionLabel
import com.philpf.dualsensemanager.ui.glass.backdropSource
import com.philpf.dualsensemanager.ui.glass.rememberBackdrop
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.LocalGlass
import kotlin.math.roundToInt

private val BottomBarHeight = 56.dp
private val CellGap = 14.dp
private const val OPAQUE = 0xFF shl 24

/** Opens the settings sheet when passed as the initially open sheet. */
const val OPEN_SETTINGS = "settings"

/** Starts putting the controllers in order when passed as the initially open sheet. */
const val OPEN_ORDER = "order"

/** Opens the first headphones' sheet when passed as the initially open sheet. */
const val OPEN_HEADPHONES = "headphones"

/**
 * The one screen: a cell per controller, each glowing in its light colour, under a floating
 * glass bar; sheets for a controller, a player and settings. Long-press and drag a connected
 * controller to move it: controllers are numbered in this order. Or put them in order from
 * the bar: the list turns into empty places, and each controller that holds L2 + R2 takes the
 * next one. Pressing anything on a
 * controller floods its cell with its colour; its sheet shows which buttons are pressed and
 * where the sticks are.
 *
 * [input] is what the controllers' buttons and sticks are doing. [resumeTick] changes every
 * time the activity resumes, to re-check permissions.
 */
@Composable
fun MainScreen(
    source: ControllerSource,
    headphones: HeadphonesSource,
    settings: SettingsStore,
    input: ControllerInput,
    theme: ThemeMode,
    resumeTick: Int,
    askForPermissions: Boolean,
    initiallyOpen: String? = null,
    forceGrid: Boolean = false,
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
    val headphoneList by headphones.headphones.collectAsState()
    val headphoneUses by headphones.interactions.collectAsState()
    val storedGrid by settings.grid.collectAsState()
    val grid = forceGrid || storedGrid
    val columns = if (grid) 2 else 1

    // Headphones' sheet; shownHeadphones keeps its content during the closing animation.
    var openHeadphones by rememberSaveable {
        mutableStateOf(if (initiallyOpen == OPEN_HEADPHONES) headphones.headphones.value.firstOrNull()?.address else null)
    }
    var shownHeadphones by rememberSaveable { mutableStateOf(openHeadphones) }
    val openPhones = headphoneList.firstOrNull { it.address == openHeadphones }
    val shownPhones = headphoneList.firstOrNull { it.address == shownHeadphones }
    val sortedPlayers = remember(players) { players.values.sortedBy { it.name.lowercase() } }
    val now = remember(resumeTick, live) { System.currentTimeMillis() }

    fun lightOf(view: ControllerView): Color = Color(OPAQUE or (previews[view.id] ?: view.color))
    fun pressedOf(view: ControllerView): Set<ControllerPart> = view.live?.let { pressed[it.deviceId] } ?: emptySet()

    // Controller sheet. shownId keeps its content during the closing animation.
    var openId by rememberSaveable { mutableStateOf(initiallyOpen?.takeIf { it != OPEN_SETTINGS && it != OPEN_ORDER && it != OPEN_HEADPHONES }) }
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
    LaunchedEffect(nearbyGranted) { if (nearbyGranted) headphones.refresh() }
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

    // Putting the controllers in order: the list turns into empty places, and each controller
    // that holds L2 + R2 takes the next one (see core/PressOrder).
    val ordering by source.ordering.collectAsState()
    LaunchedEffect(Unit) { if (initiallyOpen == OPEN_ORDER) source.startOrdering() }
    val latestViews by rememberUpdatedState(views)
    LaunchedEffect(ordering != null) {
        if (ordering == null) return@LaunchedEffect
        input.pressed.collect { held ->
            for ((device, parts) in held) {
                if (ControllerPart.L2 !in parts || ControllerPart.R2 !in parts) continue
                val view = latestViews.firstOrNull { it.live?.deviceId == device } ?: continue
                source.joinOrder(view.id)
            }
        }
    }
    BackHandler(enabled = ordering != null) { source.stopOrdering() }

    // Reordering connected controllers by dragging: up and down in the list, also across in the
    // grid. Only numbered controllers can be dragged: their numbers follow the order.
    val connectedIds = views.filter { it.isConnected }.map { it.id }
    val draggableIds = views.filter { it.isConnected && it.profile.number > 0 }.map { it.id }
    val latestDraggableIds by rememberUpdatedState(draggableIds)
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Where each connected controller's light is on screen; read while drawing, so scrolling
    // only redraws the background.
    val glowAt = remember { mutableStateMapOf<String, Offset>() }
    val background = rememberBackdrop()
    val content = rememberBackdrop()

    Box(Modifier.fillMaxSize().background(glass.backgroundBottom)) {
        Box(Modifier.fillMaxSize().backdropSource(content)) {
            GlowBackground(
                spots = {
                    views.filter { v -> v.isConnected && ordering?.let { v.id in it.joined } != false }.mapNotNull { v ->
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
            ) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Text(
                    text = "Dualsense Manager",
                    style = GlassType.largeTitle,
                    modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 22.dp),
                )

                if (headphoneList.isNotEmpty()) {
                    SectionLabel("Headphones")
                    CellGrid(columns, CellGap, if (grid) null else CellHeight) {
                        for (pair in headphoneList) {
                            key(pair.address) {
                                HeadphonesCell(
                                    headphones = pair,
                                    backdrop = background,
                                    onOpen = {
                                        openHeadphones = pair.address
                                        shownHeadphones = pair.address
                                    },
                                    onStepNoise = {
                                        pair.noise?.let { headphones.setNoise(pair.address, it.copy(mode = it.nextMode())) }
                                    },
                                    square = grid,
                                    presses = headphoneUses[pair.address] ?: 0,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                }

                SectionLabel("Controllers")
                if (views.isEmpty()) EmptyCell(background, onBluetooth = { openBluetoothSettings(context) })

                @Composable
                fun Cell(view: ControllerView, modifier: Modifier = Modifier) {
                    val onOpen = {
                        openId = view.id
                        shownId = view.id
                    }
                    val viewPresses = view.live?.let { presses[it.deviceId] } ?: 0
                    if (grid) {
                        ControllerCard(
                            view = view,
                            light = lightOf(view),
                            backdrop = background,
                            now = now,
                            presses = viewPresses,
                            onOpen = onOpen,
                            onFind = { source.identify(view.id) },
                            onGlow = { glowAt[view.id] = it },
                            modifier = modifier,
                        )
                    } else {
                        ControllerCell(
                            view = view,
                            light = lightOf(view),
                            backdrop = background,
                            now = now,
                            presses = viewPresses,
                            onOpen = onOpen,
                            onFind = { source.identify(view.id) },
                            onGlow = { glowAt[view.id] = it },
                            modifier = modifier,
                        )
                    }
                }

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cellWidth = (maxWidth - CellGap * (columns - 1)) / columns
                    val cellHeight = if (grid) cellWidth else CellHeight
                    val density = LocalDensity.current
                    val colPitch = with(density) { (cellWidth + CellGap).toPx() }
                    val rowPitch = with(density) { (cellHeight + CellGap).toPx() }
                    fun position(index: Int) = Offset((index % columns) * colPitch, (index / columns).toFloat() * rowPitch)
                    fun target(ids: List<String>, from: Int, offset: Offset): Int {
                        val down = (offset.y / rowPitch).roundToInt()
                        val across = if (columns > 1) (offset.x / colPitch).roundToInt() else 0
                        return (from + down * columns + across).coerceIn(0, ids.lastIndex)
                    }
                    val dragFrom = draggableIds.indexOf(dragId)
                    val dragOrder = if (dragFrom < 0) draggableIds else Numbering.moved(draggableIds, dragFrom, target(draggableIds, dragFrom, dragOffset))

                    CellGrid(columns, CellGap, if (grid) null else CellHeight) {
                        val order = ordering
                        if (order != null) {
                            // One place per connected controller: those taken show it, the rest
                            // wait for L2 + R2, lighting each trigger while a waiting controller
                            // holds it.
                            val placed = order.joined.mapNotNull { id -> views.firstOrNull { it.id == id && it.isConnected } }
                            val waiting = order.waiting(connectedIds)
                            fun held(part: ControllerPart) = waiting.any { id ->
                                val device = views.firstOrNull { it.id == id }?.live?.deviceId
                                device != null && part in pressed[device].orEmpty()
                            }
                            val heldL2 = held(ControllerPart.L2)
                            val heldR2 = held(ControllerPart.R2)
                            for (place in 0 until placed.size + waiting.size) {
                                key("place-$place") {
                                    Crossfade(targetState = placed.getOrNull(place)?.id, label = "place") { id ->
                                        val view = views.firstOrNull { it.id == id }
                                        if (view != null) {
                                            Cell(view)
                                        } else {
                                            EmptySlot(
                                                number = place + 1,
                                                next = place == placed.size,
                                                l2 = heldL2,
                                                r2 = heldR2,
                                                backdrop = background,
                                                square = grid,
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            for (view in views) {
                                key(view.id) {
                                    val index = draggableIds.indexOf(view.id)
                                    val shift = if (dragId == null || index < 0 || view.id == dragId) {
                                        Offset.Zero
                                    } else {
                                        position(dragOrder.indexOf(view.id)) - position(index)
                                    }
                                    val animatedShift by animateOffsetAsState(shift, tween(180), label = "reorder")
                                    val dragging = view.id == dragId
                                    Cell(
                                        view = view,
                                        modifier = Modifier
                                            .zIndex(if (dragging) 1f else 0f)
                                            .offset { (if (dragging) dragOffset else animatedShift).round() }
                                            .scale(if (dragging) 1.03f else 1f)
                                            .then(
                                                if (index < 0) {
                                                    Modifier
                                                } else {
                                                    Modifier.pointerInput(view.id, columns, colPitch, rowPitch) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = {
                                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                dragId = view.id
                                                                dragOffset = Offset.Zero
                                                            },
                                                            onDrag = { change, amount ->
                                                                change.consume()
                                                                dragOffset += amount
                                                            },
                                                            onDragEnd = {
                                                                val ids = latestDraggableIds
                                                                val from = ids.indexOf(view.id)
                                                                if (from >= 0) {
                                                                    val to = target(ids, from, dragOffset)
                                                                    if (to != from) source.reorder(Numbering.moved(ids, from, to))
                                                                }
                                                                dragId = null
                                                                dragOffset = Offset.Zero
                                                            },
                                                            onDragCancel = {
                                                                dragId = null
                                                                dragOffset = Offset.Zero
                                                            },
                                                        )
                                                    }
                                                },
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
                // Room to scroll the last cell above the bar.
                Spacer(Modifier.height(BottomBarHeight + 34.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }

        BottomBar(
            backdrop = content,
            grid = grid,
            onToggleLayout = { settings.setGrid(!grid) },
            showPermission = !nearbyGranted,
            onPermission = askAgain,
            onSettings = { settingsOpen = true },
            ordering = ordering != null,
            canOrder = connectedIds.size >= 2,
            onOrder = source::startOrdering,
            onStopOrdering = source::stopOrdering,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        GlassSheet(visible = openPhones != null, backdrop = content, onDismiss = { openHeadphones = null }) {
            val pair = shownPhones
            if (pair != null) HeadphonesSheetContent(pair, headphones, onClose = { openHeadphones = null })
        }

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
 * Floating glass capsule at the bottom: buttons for the layout (list or grid), putting the
 * controllers in order (with two or more connected), permissions (when missing) and settings;
 * while ordering, only a button to stop.
 */
@Composable
private fun BottomBar(
    backdrop: Backdrop,
    grid: Boolean,
    onToggleLayout: () -> Unit,
    showPermission: Boolean,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    ordering: Boolean,
    canOrder: Boolean,
    onOrder: () -> Unit,
    onStopOrdering: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .height(BottomBarHeight),
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (ordering) {
                GlassIconButton(onClick = onStopOrdering, label = "Done") {
                    CloseIcon(glass.textPrimary, Modifier.size(16.dp))
                }
            } else {
                GlassIconButton(onClick = onToggleLayout, label = if (grid) "Show as a list" else "Show as a grid") {
                    if (grid) ListIcon(glass.textPrimary) else GridIcon(glass.textPrimary)
                }
                if (canOrder) {
                    GlassIconButton(onClick = onOrder, label = "Put the controllers in order") {
                        OrderIcon(glass.textPrimary, Modifier.size(21.dp))
                    }
                }
                if (showPermission) {
                    GlassIconButton(onClick = onPermission, label = "Allow Nearby devices") {
                        BluetoothIcon(glass.danger, Modifier.size(18.dp))
                    }
                }
                GlassIconButton(onClick = onSettings, label = "Settings") {
                    GearIcon(glass.textPrimary, Modifier.size(22.dp))
                }
            }
        }
    }
}

/**
 * A device's own page in the system's Bluetooth settings (Android's Settings app), whose
 * "Disconnect" button disconnects it (a controller then turns off). The list of Bluetooth
 * devices instead when its [address] is unknown or the page is missing (some makers' settings
 * apps do not have it).
 */
internal fun openDeviceSettings(context: Context, address: String?) {
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
