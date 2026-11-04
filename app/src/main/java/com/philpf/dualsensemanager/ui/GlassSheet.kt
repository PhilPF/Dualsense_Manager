package com.philpf.dualsensemanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.philpf.dualsensemanager.ui.glass.Backdrop
import com.philpf.dualsensemanager.ui.glass.GlassSurface
import com.philpf.dualsensemanager.ui.theme.LocalGlass

/**
 * A glass sheet that slides up from the bottom over a dimmed screen. Tapping outside it or
 * pressing Back closes it.
 */
@Composable
fun BoxScope.GlassSheet(
    visible: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlass.current
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(glass.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
    ) {
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            shape = RoundedCornerShape(34.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 22.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(glass.textTertiary.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}
