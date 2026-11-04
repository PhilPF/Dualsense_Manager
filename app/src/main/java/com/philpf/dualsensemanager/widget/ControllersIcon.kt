package com.philpf.dualsensemanager.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.philpf.dualsensemanager.R
import com.philpf.dualsensemanager.core.IconLights

/**
 * Draws the app's icon in given light colours (as tools/app_icons.py draws the shipped ones):
 * their lights blurred together behind glass, under the controller in lines. Settings shows it
 * as a preview of the icon the launcher will show.
 */
object ControllersIcon {
    private const val BASE = 0xFF0B0E1A.toInt()

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
