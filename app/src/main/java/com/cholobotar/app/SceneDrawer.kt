package com.cholobotar.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min

/** Draws the stage (background + avatar) with plain Android Canvas. Used by the video recorder. */
object SceneDrawer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()

    fun draw(vm: StudioViewModel, canvas: Canvas, w: Int, h: Int) {
        val wf = w.toFloat()
        val hf = h.toFloat()

        val bg = vm.currentBg()
        val still = vm.bgStill
        if (bg != null && bg.kind != "color" && still != null) {
            val b = still.asAndroidBitmap()
            val s = max(wf / b.width, hf / b.height)
            val sw = wf / s
            val sh = hf / s
            src.set(
                ((b.width - sw) / 2f).toInt(), ((b.height - sh) / 2f).toInt(),
                ((b.width + sw) / 2f).toInt(), ((b.height + sh) / 2f).toInt()
            )
            dst.set(0f, 0f, wf, hf)
            canvas.drawBitmap(b, src, dst, paint)
        } else {
            val cols = bg?.colors ?: listOf(Color.Black)
            val c0 = (cols.firstOrNull() ?: Color.Black).toArgb()
            val c1 = (cols.lastOrNull() ?: Color.Black).toArgb()
            paint.shader = LinearGradient(0f, 0f, 0f, hf, c0, c1, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, wf, hf, paint)
            paint.shader = null
        }

        val size = avatarBaseSize(wf, hf) * vm.avatarScale
        val cx = wf / 2f + vm.avatarOffX * wf + vm.bounce.shakeX * size
        val cy = hf / 2f + vm.avatarOffY * hf + vm.bounce.offsetY * size
        val layers = vm.avatarLayers()
        if (layers.isEmpty()) {
            drawDefault(canvas, cx, cy, size, vm.mouthLevel > 0)
        } else {
            for (l in layers) fitDraw(canvas, l.asAndroidBitmap(), cx, cy, size)
        }
    }

    private fun fitDraw(canvas: Canvas, b: Bitmap, cx: Float, cy: Float, size: Float) {
        val s = size / max(b.width, b.height).toFloat()
        val dw = b.width * s
        val dh = b.height * s
        dst.set(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f)
        canvas.drawBitmap(b, null, dst, paint)
    }

    private fun drawDefault(canvas: Canvas, cx: Float, cy: Float, size: Float, talking: Boolean) {
        val r = size * 0.45f
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFC857.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = 0x33FF6B6B
        canvas.drawCircle(cx - r * 0.52f, cy + r * 0.12f, r * 0.14f, paint)
        canvas.drawCircle(cx + r * 0.52f, cy + r * 0.12f, r * 0.14f, paint)
        paint.color = 0xFF2B2B2B.toInt()
        canvas.drawCircle(cx - r * 0.35f, cy - r * 0.2f, r * 0.09f, paint)
        canvas.drawCircle(cx + r * 0.35f, cy - r * 0.2f, r * 0.09f, paint)
        if (talking) {
            paint.color = 0xFF7A1F1F.toInt()
            dst.set(cx - r * 0.22f, cy + r * 0.2f, cx + r * 0.22f, cy + r * 0.62f)
            canvas.drawOval(dst, paint)
        } else {
            paint.strokeWidth = r * 0.06f
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx - r * 0.28f, cy + r * 0.35f, cx + r * 0.28f, cy + r * 0.35f, paint)
        }
    }

    @Suppress("unused")
    private fun clampMin(a: Float, b: Float) = min(a, b)
}
