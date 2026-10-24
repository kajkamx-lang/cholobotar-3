package com.cholobotar.app

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

class GlassColors(
    val panel: Color,
    val border: Color,
    val borderHi: Color,
    val text: Color,
    val subtext: Color,
    val accent: Color,
    val bg: Brush,
    val blobA: Color,
    val blobB: Color
)

val LightGlass = GlassColors(
    panel = Color(0xCCFFFFFF),
    border = Color(0x1A000000),
    borderHi = Color(0xFFFFFFFF),
    text = Color(0xFF14181F),
    subtext = Color(0xFF5A6272),
    accent = Color(0xFF6C4DFF),
    bg = Brush.verticalGradient(listOf(Color(0xFFF4F1FF), Color(0xFFEAF2FF))),
    blobA = Color(0x66A78BFA),
    blobB = Color(0x66F9A8D4)
)

val DarkGlass = GlassColors(
    panel = Color(0x1AFFFFFF),
    border = Color(0x26FFFFFF),
    borderHi = Color(0x55FFFFFF),
    text = Color.White,
    subtext = Color(0xFFB0B0C0),
    accent = Color(0xFF8E6CFF),
    bg = SolidColor(Color.Black),
    blobA = Color(0x338E6CFF),
    blobB = Color(0x2200B8D9)
)

/** Fixed dark glass used on the Studio stage so it stays readable over any background. */
val StudioGlass = GlassColors(
    panel = Color(0x99101018),
    border = Color(0x26FFFFFF),
    borderHi = Color(0x55FFFFFF),
    text = Color.White,
    subtext = Color(0xFFC4C6D6),
    accent = Color(0xFF8E6CFF),
    bg = SolidColor(Color.Black),
    blobA = Color.Transparent,
    blobB = Color.Transparent
)

val LocalGlass = staticCompositionLocalOf { LightGlass }

@Composable
fun CholobotarTheme(dark: Boolean, content: @Composable () -> Unit) {
    val g = if (dark) DarkGlass else LightGlass
    val scheme = if (dark) {
        darkColorScheme(
            primary = g.accent, background = Color.Black, surface = Color.Black,
            onBackground = Color.White, onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = g.accent, background = Color(0xFFF4F1FF), surface = Color(0xFFF7F9FD),
            onBackground = Color(0xFF14181F), onSurface = Color(0xFF14181F)
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            if (activity != null) {
                WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalGlass provides g) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun Backdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val g = LocalGlass.current
    Box(
        modifier
            .background(g.bg)
            .drawBehind {
                val r = size.width * 0.9f
                val c1 = Offset(size.width * 0.95f, size.height * 0.08f)
                val c2 = Offset(size.width * 0.05f, size.height * 0.85f)
                drawCircle(Brush.radialGradient(listOf(g.blobA, Color.Transparent), center = c1, radius = r), r, c1)
                drawCircle(Brush.radialGradient(listOf(g.blobB, Color.Transparent), center = c2, radius = r), r, c2)
            },
        content = content
    )
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val g = LocalGlass.current
    Box(
        modifier
            .clip(shape)
            .background(g.panel)
            .border(1.dp, Brush.linearGradient(listOf(g.borderHi, g.border)), shape),
        content = content
    )
}

@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) g.accent.copy(alpha = 0.35f) else g.panel)
            .border(1.dp, if (selected) g.accent else g.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = g.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GlassIconButton(
    label: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 40.dp,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val g = LocalGlass.current
    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(if (selected) g.accent.copy(alpha = 0.35f) else g.panel)
            .border(1.dp, if (selected) g.accent else g.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = g.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PlayPauseButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, diameter: Dp = 46.dp) {
    val g = LocalGlass.current
    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(g.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(diameter * 0.38f)) {
            val w = size.width
            val h = size.height
            if (playing) {
                drawRoundRect(Color.White, Offset(w * 0.08f, 0f), Size(w * 0.30f, h), CornerRadius(3f, 3f))
                drawRoundRect(Color.White, Offset(w * 0.62f, 0f), Size(w * 0.30f, h), CornerRadius(3f, 3f))
            } else {
                val p = Path()
                p.moveTo(w * 0.15f, 0f)
                p.lineTo(w, h / 2f)
                p.lineTo(w * 0.15f, h)
                p.close()
                drawPath(p, Color.White)
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, filled: Boolean = true, onClick: () -> Unit) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (filled) g.accent else g.panel)
            .border(1.dp, if (filled) g.accent else g.border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (filled) Color.White else g.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    val g = LocalGlass.current
    Text(
        text.uppercase(),
        color = g.accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(g.panel)
            .border(1.dp, g.border, shape)
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (sel) g.accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (sel) Color.White else g.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    val g = LocalGlass.current
    val labels = listOf("Studio", "Avatar", "Script", "Settings")
    GlassPanel(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(Modifier.padding(6.dp)) {
            labels.forEachIndexed { i, label ->
                val sel = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (sel) g.accent else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (sel) Color.White else g.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
