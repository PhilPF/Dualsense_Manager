package com.philpf.dualsensemanager.widget

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Icon
import com.philpf.dualsensemanager.MainActivity
import com.philpf.dualsensemanager.R
import com.philpf.dualsensemanager.core.IconLights

/**
 * The app's icon in the colours of the connected controllers: their lights blurred together
 * behind glass, under the controller in lines. Android does not let an app change its own
 * launcher icon, so this is a home-screen shortcut that the app redraws when controllers
 * connect or disconnect. Without controllers it looks like the launcher icon.
 */
object ControllersIcon {
    private const val ID = "controllers_icon"
    private const val BASE = 0xFF0B0E1A.toInt()

    fun canAdd(context: Context): Boolean =
        context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    /** Asks the home screen to add the icon; it shows its own confirmation. */
    fun add(context: Context, colors: List<Int>) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        manager.requestPinShortcut(shortcut(context, colors), null)
    }

    /** Redraws the icon with [colors] (the connected controllers'), if it is on the home screen. */
    fun update(context: Context, colors: List<Int>) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        if (manager.pinnedShortcuts.none { it.id == ID }) return
        manager.updateShortcuts(listOf(shortcut(context, colors)))
    }

    private fun shortcut(context: Context, colors: List<Int>): ShortcutInfo =
        ShortcutInfo.Builder(context, ID)
            .setShortLabel(context.getString(R.string.app_name))
            .setIcon(Icon.createWithAdaptiveBitmap(render(context, colors)))
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
            .build()

    /**
     * The full adaptive-icon square (108 dp, of which launchers show the middle 72) at [size]
     * pixels. Same layers and stops as ic_launcher_background and ic_launcher_foreground.
     */
    fun render(context: Context, colors: List<Int>, size: Int = 432): Bitmap {
        val shown = colors.ifEmpty { IconLights.DEFAULT_COLORS }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val unit = size / 108f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(BASE)
        for ((light, rgb) in IconLights.layout(shown.size).zip(shown)) {
            val color = rgb and 0xFFFFFF
            paint.shader = RadialGradient(
                light.x * unit,
                light.y * unit,
                light.radius * unit,
                intArrayOf((0xC7 shl 24) or color, (0x5C shl 24) or color, color),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(light.x * unit, light.y * unit, light.radius * unit, paint)
        }
        // Frosted glass: a faint white veil and a sheen at the top.
        paint.shader = null
        paint.color = 0x0DFFFFFF
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = LinearGradient(0f, 0f, 0f, 60f * unit, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        val controller = requireNotNull(context.getDrawable(R.drawable.ic_launcher_foreground))
        controller.setBounds(0, 0, size, size)
        controller.draw(canvas)
        return bitmap
    }
}
