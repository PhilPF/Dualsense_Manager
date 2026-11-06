package com.philpf.dualsensemanager.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.ui.glass.Backdrop
import com.philpf.dualsensemanager.ui.glass.GlassSurface
import com.philpf.dualsensemanager.ui.theme.GlassType
import com.philpf.dualsensemanager.ui.theme.LocalGlass

/**
 * A place still free while the controllers are put in order: its number, and L2 + R2 drawn as
 * the controller's triggers, to hold on the controller that should take it. The [next] place
 * to take breathes; the others wait, dimmed. [l2] and [r2] light while a waiting controller
 * holds that trigger. A wide panel, or a [square] card for the grid.
 */
@Composable
fun EmptySlot(
    number: Int,
    next: Boolean,
    l2: Boolean,
    r2: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    square: Boolean = false,
) {
    val glass = LocalGlass.current
    val breathe = rememberInfiniteTransition(label = "slot").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .fillMaxWidth()
            .then(if (square) Modifier.fillMaxSize() else Modifier.height(CellHeight))
            .semantics { contentDescription = "Place $number: hold L2 and R2" },
        shape = CellShape,
    ) {
        if (square) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (next) breathe.value else 0.4f }
                    .padding(18.dp),
            ) {
                Text("$number", style = GlassType.largeTitle, color = glass.textSecondary)
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    TriggerKey("L2", lit = l2, right = false)
                    Text("+", style = GlassType.title, color = glass.textSecondary, modifier = Modifier.padding(horizontal = 10.dp))
                    TriggerKey("R2", lit = r2, right = true)
                }
                Spacer(Modifier.weight(1f))
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (next) breathe.value else 0.4f }
                    .padding(horizontal = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$number", style = GlassType.largeTitle, color = glass.textSecondary, modifier = Modifier.width(44.dp))
                Spacer(Modifier.weight(1f))
                TriggerKey("L2", lit = l2, right = false)
                Text("+", style = GlassType.title, color = glass.textSecondary, modifier = Modifier.padding(horizontal = 14.dp))
                TriggerKey("R2", lit = r2, right = true)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(44.dp))
            }
        }
    }
}

/** A trigger as a key: rounded, higher on its outer side like the controller's own, with its label. */
@Composable
private fun TriggerKey(label: String, lit: Boolean, right: Boolean) {
    val glass = LocalGlass.current
    val outer = 17.dp
    val inner = 7.dp
    val shape = if (right) {
        RoundedCornerShape(topStart = inner, topEnd = outer, bottomEnd = inner, bottomStart = inner)
    } else {
        RoundedCornerShape(topStart = outer, topEnd = inner, bottomEnd = inner, bottomStart = inner)
    }
    val ink = glass.textPrimary
    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 38.dp)
            .clip(shape)
            .background(if (lit) ink.copy(alpha = 0.24f) else Color.Transparent)
            .border(2.dp, ink.copy(alpha = if (lit) 1f else 0.75f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = GlassType.headline, color = ink)
    }
}
