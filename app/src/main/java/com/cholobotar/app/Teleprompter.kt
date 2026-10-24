package com.cholobotar.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class TeleprompterState {
    var script by mutableStateOf(ParsedScript(emptyList(), emptyList()))
    var wpm by mutableIntStateOf(140)
    var playing by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)   // fractional word index
    var countdown by mutableIntStateOf(0)
    var fontSp by mutableIntStateOf(30)
    var mirror by mutableStateOf(false)
    var alpha by mutableFloatStateOf(0.85f)
    private var next = 0

    fun load(raw: String) {
        script = parseScript(raw)
        seekTo(0)
    }

    fun tick(dtSec: Float, onMarker: (ScriptMarker) -> Unit) {
        if (!playing) return
        val markers = script.markers
        progress += wpm / 60f * dtSec
        while (next < markers.size && progress >= markers[next].wordIndex) {
            onMarker(markers[next])
            next++
        }
        if (progress >= script.words.size) playing = false
    }

    fun seekTo(word: Int) {
        progress = word.toFloat()
        val i = script.markers.indexOfFirst { it.wordIndex >= word }
        next = if (i < 0) script.markers.size else i
    }
}

@Composable
fun TeleprompterView(state: TeleprompterState, onTap: () -> Unit, modifier: Modifier = Modifier) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewH by remember { mutableIntStateOf(0) }
    val words = state.script.words
    val text = remember(words) { words.joinToString(" ") }
    val offsets = remember(words) {
        var c = 0
        words.map { w -> val start = c; c += w.length + 1; start }
    }

    val targetTop: Float = layout?.let { l ->
        if (offsets.isEmpty() || l.layoutInput.text.text != text) 0f
        else {
            val i = state.progress.toInt().coerceIn(0, offsets.lastIndex)
            l.getLineTop(l.getLineForOffset(offsets[i]))
        }
    } ?: 0f
    val top by animateFloatAsState(targetTop, tween(350, easing = LinearEasing), label = "prompterTop")

    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = state.alpha * 0.85f))
            .onSizeChanged { viewH = it.height }
            .graphicsLayer { scaleX = if (state.mirror) -1f else 1f }
            .drawBehind {
                drawRect(
                    Color(0xFFFFD54F),
                    topLeft = Offset(0f, size.height * 0.3f),
                    size = Size(4.dp.toPx(), (state.fontSp * 1.4f).sp.toPx())
                )
            }
            .pointerInput(onTap) { detectTapGestures { onTap() } }
    ) {
        if (text.isEmpty()) {
            Text(
                "Script tab-e script lekho",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Text(
                text = text,
                color = Color.White,
                fontSize = state.fontSp.sp,
                lineHeight = (state.fontSp * 1.4f).sp,
                textAlign = TextAlign.Center,
                onTextLayout = { layout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .graphicsLayer { translationY = -top + viewH * 0.3f }
            )
        }
        if (state.countdown > 0) {
            Text(
                state.countdown.toString(),
                color = Color(0xFFFFD54F),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (!state.playing) {
            Text(
                "tap = play",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
            )
        }
    }
}
