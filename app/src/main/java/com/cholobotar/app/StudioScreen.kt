package com.cholobotar.app

import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

@Composable
fun StudioScreen(
    vm: StudioViewModel,
    onToggleMic: () -> Unit,
    onAddBg: () -> Unit,
    onAddSfx: () -> Unit
) {
    var showPrompter by rememberSaveable { mutableStateOf(true) }
    var uiHidden by rememberSaveable { mutableStateOf(false) }
    var stage by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val frame = fitFrame(stage.width.toFloat(), stage.height.toFloat(), formatAspect(vm.recFormat))
    val avatarPx = avatarBaseSize(frame.w, frame.h) * vm.avatarScale
    val avatarDp = with(density) { avatarPx.toDp() }
    val cx = frame.left + frame.w / 2f + vm.avatarOffX * frame.w
    val cy = frame.top + frame.h / 2f + vm.avatarOffY * frame.h

    CompositionLocalProvider(LocalGlass provides StudioGlass) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { stage = it }
                .pointerInput(stage, vm.recFormat) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (frame.w > 0f && frame.h > 0f) {
                            vm.transformAvatar(pan.x / frame.w, pan.y / frame.h, zoom)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { vm.resetAvatarTransform() })
                }
        ) {
            StageBackground(vm)
            if (vm.showGuide) FrameGuide(frame)

            Box(
                Modifier
                    .offset { IntOffset((cx - avatarPx / 2f).roundToInt(), (cy - avatarPx / 2f).roundToInt()) }
                    .size(avatarDp)
                    .graphicsLayer {
                        vm.tick // read per frame so the layer redraws
                        translationX = vm.bounce.shakeX * avatarPx
                        translationY = vm.bounce.offsetY * avatarPx
                    }
            ) {
                AvatarFigure(vm, Modifier.fillMaxSize())
            }

            if (uiHidden) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RecButton(vm)
                    GlassChip("Show UI", onClick = { uiHidden = false })
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StudioTopBar(
                        vm = vm,
                        showPrompter = showPrompter,
                        onTogglePrompter = { showPrompter = !showPrompter },
                        onHide = { uiHidden = true },
                        onToggleMic = onToggleMic
                    )
                    if (showPrompter) {
                        TeleprompterView(
                            vm.tp,
                            onTap = { vm.togglePlay() },
                            modifier = Modifier.fillMaxWidth().height(170.dp)
                        )
                        PrompterControls(vm)
                    }
                    Spacer(Modifier.weight(1f))
                    PadDock(vm, onAddBg, onAddSfx)
                }
            }
        }
    }
}

@Composable
fun StageBackground(vm: StudioViewModel) {
    val bg = vm.currentBg()
    val still = vm.bgStill
    val path = bg?.path
    when {
        bg == null -> Box(Modifier.fillMaxSize().background(Color.Black))
        bg.kind == "image" -> {
            if (still != null) {
                Image(
                    bitmap = still, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
        bg.kind == "video" && path != null -> VideoBackground(path)
        else -> Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(bg.colors.ifEmpty { listOf(Color.Black, Color.DarkGray) })
            )
        )
    }
}

@Composable
fun VideoBackground(path: String) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                tag = path
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                }
                setVideoPath(path)
                start()
            }
        },
        update = { v ->
            if (v.tag != path) {
                v.tag = path
                v.setVideoPath(path)
                v.start()
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun FrameGuide(frame: FrameRect) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Color(0xAAFFFFFF),
            topLeft = Offset(frame.left, frame.top),
            size = Size(frame.w, frame.h),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f)))
        )
    }
}

@Composable
fun StudioTopBar(
    vm: StudioViewModel,
    showPrompter: Boolean,
    onTogglePrompter: () -> Unit,
    onHide: () -> Unit,
    onToggleMic: () -> Unit
) {
    val g = LocalGlass.current
    val on = vm.micOn
    val shape = RoundedCornerShape(50)
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier
                .clip(shape)
                .background(g.panel)
                .border(1.dp, if (on) Color(0xFF4ADE80) else g.border, shape)
                .clickable(onClick = onToggleMic)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (on) Color(0xFF4ADE80) else g.subtext)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (on) "MIC ON" else "MIC OFF",
                color = g.text, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
        RecButton(vm)
        Box {
            GlassChip("${RES_OPTIONS[vm.recRes]}p ▾", onClick = { if (!vm.recording) menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                RES_OPTIONS.forEachIndexed { i, r ->
                    DropdownMenuItem(
                        text = { Text("${r}p") },
                        onClick = {
                            vm.updateRecRes(i)
                            menu = false
                        }
                    )
                }
            }
        }
        GlassChip("Prompter", selected = showPrompter, onClick = onTogglePrompter)
        GlassChip("Hide UI", onClick = onHide)
    }
}

@Composable
fun RecButton(vm: StudioViewModel) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(50)
    val rec = vm.recording
    val red = Color(0xFFE5383B)
    Row(
        Modifier
            .clip(shape)
            .background(if (rec) red else g.panel)
            .border(1.dp, if (rec) red else g.border, shape)
            .clickable { vm.toggleRecording() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(if (rec) RoundedCornerShape(2.dp) else CircleShape)
                .background(if (rec) Color.White else red)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (rec) timeText(vm.recSeconds) else "REC",
            color = if (rec) Color.White else g.text,
            fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PrompterControls(vm: StudioViewModel) {
    val g = LocalGlass.current
    val running = vm.tp.playing || vm.tp.countdown > 0
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PlayPauseButton(running, onClick = { vm.togglePlay() })
            GlassIconButton("↺", diameter = 38.dp, onClick = { vm.restart() })
            Spacer(Modifier.weight(1f))
            GlassIconButton("−", diameter = 36.dp, onClick = { vm.setWpm(vm.tp.wpm - 10) })
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(42.dp)) {
                Text("${vm.tp.wpm}", color = g.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("WPM", color = g.subtext, fontSize = 9.sp)
            }
            GlassIconButton("+", diameter = 36.dp, onClick = { vm.setWpm(vm.tp.wpm + 10) })
            Spacer(Modifier.weight(1f))
            GlassIconButton("A−", diameter = 36.dp, onClick = { vm.setFont(vm.tp.fontSp - 2) })
            GlassIconButton("A+", diameter = 36.dp, onClick = { vm.setFont(vm.tp.fontSp + 2) })
        }
    }
}

@Composable
fun PadDock(vm: StudioViewModel, onAddBg: () -> Unit, onAddSfx: () -> Unit) {
    val g = LocalGlass.current
    val haptics = LocalHapticFeedback.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf(true) }
    var del by remember { mutableStateOf<Pair<Int, String>?>(null) }

    GlassPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlassChip("Backgrounds", selected = tab == 0, onClick = { tab = 0; open = true })
                GlassChip("Sounds", selected = tab == 1, onClick = { tab = 1; open = true })
                Spacer(Modifier.weight(1f))
                Text(
                    if (open) "Hide ▾" else "Show ▴",
                    color = g.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { open = !open }.padding(8.dp)
                )
            }
            if (open) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tab == 0) {
                        vm.bgItems.forEach { b ->
                            BgTile(
                                item = b,
                                selected = b.id == vm.bgId,
                                onTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.selectBg(b.id)
                                },
                                onLong = { if (b.kind != "color") del = Pair(0, b.id) }
                            )
                        }
                        AddTile("+ Photo / Video", onAddBg)
                    } else {
                        vm.sfxItems.forEach { s ->
                            SoundTile(
                                item = s,
                                onTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.playSfx(s)
                                },
                                onLong = { del = Pair(1, s.id) }
                            )
                        }
                        AddTile("+ Sound", onAddSfx)
                    }
                }
            }
        }
    }

    val d = del
    if (d != null) {
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("Delete?") },
            text = { Text("Eta app theke muche jabe.") },
            confirmButton = {
                TextButton(onClick = {
                    if (d.first == 0) vm.removeBg(d.second) else vm.removeSfx(d.second)
                    del = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { del = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun BgTile(item: BgItem, selected: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(16.dp)
    val t = item.thumb
    Box(
        Modifier
            .size(72.dp)
            .clip(shape)
            .background(Brush.verticalGradient(item.colors.ifEmpty { listOf(Color.Gray, Color.DarkGray) }))
            .pointerInput(item.id) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() })
            }
    ) {
        if (t != null) {
            Image(
                bitmap = t, contentDescription = item.name,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            item.name,
            color = Color.White, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(vertical = 2.dp)
        )
        Box(
            Modifier
                .fillMaxSize()
                .border(if (selected) 3.dp else 1.dp, if (selected) g.accent else g.border, shape)
        )
    }
}

@Composable
fun SoundTile(item: SfxItem, onTap: () -> Unit, onLong: () -> Unit) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .size(72.dp)
            .clip(shape)
            .background(g.accent.copy(alpha = 0.18f))
            .border(1.dp, g.border, shape)
            .pointerInput(item.id) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() })
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("♪", color = g.accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            item.name, color = g.text, fontSize = 9.sp, maxLines = 1,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun AddTile(label: String, onClick: () -> Unit) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .size(72.dp)
            .clip(shape)
            .border(1.dp, g.accent, shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("+", color = g.accent, fontSize = 26.sp, fontWeight = FontWeight.Light)
        Text(
            label.removePrefix("+ "), color = g.subtext, fontSize = 9.sp, maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
