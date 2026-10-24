package com.cholobotar.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val g = LocalGlass.current
    Column {
        Text(label, color = g.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val g = LocalGlass.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = g.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Draws the avatar layers stacked (all layers share one canvas size). */
@Composable
fun AvatarFigure(vm: StudioViewModel, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val layers = vm.avatarLayers()
        if (layers.isEmpty()) {
            DefaultAvatar(vm.mouthLevel > 0, Modifier.fillMaxSize())
        } else {
            layers.forEach { l ->
                Image(
                    bitmap = l,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun DefaultAvatar(talking: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension * 0.45f
        drawCircle(Color(0xFFFFC857), r, c)
        drawCircle(Color(0x33FF6B6B), r * 0.14f, Offset(c.x - r * 0.52f, c.y + r * 0.12f))
        drawCircle(Color(0x33FF6B6B), r * 0.14f, Offset(c.x + r * 0.52f, c.y + r * 0.12f))
        drawCircle(Color(0xFF2B2B2B), r * 0.09f, Offset(c.x - r * 0.35f, c.y - r * 0.2f))
        drawCircle(Color(0xFF2B2B2B), r * 0.09f, Offset(c.x + r * 0.35f, c.y - r * 0.2f))
        if (talking) {
            drawOval(
                Color(0xFF7A1F1F),
                topLeft = Offset(c.x - r * 0.22f, c.y + r * 0.2f),
                size = Size(r * 0.44f, r * 0.42f)
            )
        } else {
            drawLine(
                Color(0xFF2B2B2B),
                Offset(c.x - r * 0.28f, c.y + r * 0.35f),
                Offset(c.x + r * 0.28f, c.y + r * 0.35f),
                strokeWidth = r * 0.06f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun Thumb(bmp: ImageBitmap?, label: String, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(g.panel)
            .border(1.dp, g.border, shape),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp, contentDescription = label,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp)
            )
        } else {
            Text("—", color = g.subtext, fontSize = 16.sp)
        }
    }
}

@Composable
fun SlotRow(
    title: String,
    hint: String,
    bmp: ImageBitmap?,
    onPick: () -> Unit,
    onClear: (() -> Unit)?
) {
    val g = LocalGlass.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Thumb(bmp, title, Modifier.size(56.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = g.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = g.subtext, fontSize = 11.sp)
        }
        GlassChip(if (bmp == null) "Choose" else "Change", onClick = onPick)
        if (bmp != null && onClear != null) {
            GlassIconButton("✕", diameter = 36.dp, onClick = onClear)
        }
    }
}
