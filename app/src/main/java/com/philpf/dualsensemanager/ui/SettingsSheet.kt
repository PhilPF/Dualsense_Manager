package com.philpf.dualsensemanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.core.AppIconChoice
import com.philpf.dualsensemanager.core.ThemeMode
import com.philpf.dualsensemanager.device.SettingsStore
import com.philpf.dualsensemanager.ui.art.CloseIcon
import com.philpf.dualsensemanager.ui.glass.GlassIconButton
import com.philpf.dualsensemanager.ui.glass.GlassSwitch
import com.philpf.dualsensemanager.ui.glass.SectionLabel
import com.philpf.dualsensemanager.ui.glass.SegmentedControl
import com.philpf.dualsensemanager.ui.glass.SettingRow
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.LocalGlass
import com.philpf.dualsensemanager.widget.ControllersIcon
import kotlin.math.roundToInt

/**
 * Theme and font, battery notifications, and whether the app icon takes the colours of the
 * connected controllers ([colors]).
 */
@Composable
fun ColumnScope.SettingsSheetContent(settings: SettingsStore, theme: ThemeMode, colors: List<Int>, onClose: () -> Unit) {
    val glass = LocalGlass.current
    val context = LocalContext.current
    val lowAlerts by settings.lowBatteryAlerts.collectAsState()
    val fullAlerts by settings.fullBatteryAlerts.collectAsState()
    val monospace by settings.monospace.collectAsState()
    val iconFollows by settings.iconFollowsControllers.collectAsState()

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Settings", style = GlassType.largeTitle, modifier = Modifier.weight(1f).padding(start = 6.dp))
        GlassIconButton(onClick = onClose, label = "Close", size = 36.dp) {
            CloseIcon(glass.textSecondary, Modifier.size(14.dp))
        }
    }

    Spacer(Modifier.height(22.dp))
    SectionLabel("Appearance")
    SegmentedControl(
        options = listOf("System", "Light", "Dark"),
        selectedIndex = theme.ordinal,
        onSelect = { settings.setTheme(ThemeMode.entries[it]) },
    )
    Spacer(Modifier.height(10.dp))
    SegmentedControl(
        options = listOf("Default font", "Monospaced"),
        selectedIndex = if (monospace) 1 else 0,
        onSelect = { settings.setMonospace(it == 1) },
    )

    Spacer(Modifier.height(24.dp))
    SectionLabel("Notifications")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(glass.controlFill),
    ) {
        SettingRow("Low battery") {
            GlassSwitch(checked = lowAlerts, onCheckedChange = settings::setLowBatteryAlerts, label = "Low battery")
        }
        Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(glass.textPrimary.copy(alpha = 0.08f)))
        SettingRow("Fully charged") {
            GlassSwitch(checked = fullAlerts, onCheckedChange = settings::setFullBatteryAlerts, label = "Fully charged")
        }
    }

    Spacer(Modifier.height(24.dp))
    SectionLabel("App icon")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(glass.controlFill)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The icon the launcher shows, as a launcher shows it: the middle 72 of its 108 dp.
        val key = if (iconFollows) AppIconChoice.key(colors) else AppIconChoice.PLAIN_KEY
        val icon = remember(key) { ControllersIcon.render(context, AppIconChoice.colors(key), 216).asImageBitmap() }
        Canvas(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))) {
            drawImage(
                icon,
                srcOffset = IntOffset(36, 36),
                srcSize = IntSize(144, 144),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            )
        }
        Text("Controllers' colours", style = GlassType.body, modifier = Modifier.weight(1f).padding(start = 14.dp))
        GlassSwitch(checked = iconFollows, onCheckedChange = settings::setIconFollowsControllers, label = "App icon in the controllers' colours")
    }
}
