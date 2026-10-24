package com.cholobotar.app

import kotlin.math.min

class VideoSize(val w: Int, val h: Int)

/** format: 0 = 9:16, 1 = 16:9, 2 = 1:1. shortSide = 360/480/720/1080. */
fun videoSizeFor(shortSide: Int, format: Int): VideoSize {
    fun even(x: Int) = (x / 2) * 2
    return when (format) {
        1 -> VideoSize(even(shortSide * 16 / 9), shortSide)
        2 -> VideoSize(shortSide, shortSide)
        else -> VideoSize(shortSide, even(shortSide * 16 / 9))
    }
}

fun formatAspect(format: Int): Float = when (format) {
    1 -> 16f / 9f
    2 -> 1f
    else -> 9f / 16f
}

fun bitrateFor(w: Int, h: Int, fps: Int): Int =
    (w.toLong() * h * fps * 0.15).toInt().coerceIn(1_000_000, 20_000_000)

class FrameRect(val left: Float, val top: Float, val w: Float, val h: Float)

/** Largest rectangle of the given aspect (w/h) centered inside the stage. */
fun fitFrame(stageW: Float, stageH: Float, aspect: Float): FrameRect {
    if (stageW <= 0f || stageH <= 0f) return FrameRect(0f, 0f, 0f, 0f)
    val w: Float
    val h: Float
    if (stageW / stageH > aspect) {
        h = stageH
        w = h * aspect
    } else {
        w = stageW
        h = w / aspect
    }
    return FrameRect((stageW - w) / 2f, (stageH - h) / 2f, w, h)
}

fun avatarBaseSize(frameW: Float, frameH: Float): Float = 0.8f * min(frameW, frameH)

fun timeText(totalSec: Int): String = "%02d:%02d".format(totalSec / 60, totalSec % 60)
