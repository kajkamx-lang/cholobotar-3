package com.cholobotar.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: StudioViewModel, onToggleMic: () -> Unit) {
    val g = LocalGlass.current
    Backdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Settings", color = g.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)

            SectionLabel("Microphone")
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        if (vm.micOn) "Stop mic" else "Start mic",
                        filled = !vm.micOn,
                        onClick = onToggleMic
                    )
                    LinearProgressIndicator(
                        progress = { vm.meter },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = g.accent,
                        trackColor = g.border
                    )
                    Text(
                        if (vm.mouthLevel > 0) "Speaking (mouth level ${vm.mouthLevel})" else "Silent",
                        color = g.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    LabeledSlider(
                        "Sensitivity: ${vm.sensitivityDb.roundToInt()} dB",
                        vm.sensitivityDb, -60f..-20f
                    ) { vm.setSensitivity(it) }
                    Text(
                        "Kotha bolle meter nore na hole slider bam-e (kom dB) nao.",
                        color = g.subtext, fontSize = 12.sp
                    )
                }
            }

            SectionLabel("Voice reaction")
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToggleRow("Bounce when speaking", vm.bounceOn) { vm.updateBounceOn(it) }
                    if (vm.bounceOn) {
                        LabeledSlider("Bounce strength", vm.bounceAmount, 0f..1f) { vm.updateBounceAmount(it) }
                        LabeledSlider("Bounce speed: ${"%.1f".format(vm.bounceSpeed)} Hz", vm.bounceSpeed, 3f..10f) { vm.updateBounceSpeed(it) }
                        ToggleRow("Side shake", vm.shakeOn) { vm.updateShakeOn(it) }
                    }
                    ToggleRow("Auto blink", vm.blinkOn) { vm.updateBlinkOn(it) }
                }
            }

            SectionLabel("Recording")
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Resolution", color = g.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Segmented(RES_OPTIONS.map { "${it}p" }, vm.recRes, { vm.updateRecRes(it) })
                    Text("Format", color = g.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Segmented(listOf("9:16 Shorts", "16:9 Wide", "1:1 Square"), vm.recFormat, { vm.updateRecFormat(it) })
                    Text("Frame rate", color = g.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Segmented(listOf("30 fps", "60 fps"), if (vm.recFps == 60) 1 else 0, { vm.updateRecFps(if (it == 1) 60 else 30) })
                    ToggleRow("Show frame guide on Studio", vm.showGuide) { vm.updateShowGuide(it) }
                    Text(
                        "Video save hoy Movies/Cholobotar folder-e (Gallery-te dekhbe). Mic on thakle audio-o record hoy. Prompter ar button video-te ashe na. Imported sound effect video-te mix hoy na, shudhu speaker-e bajbe.",
                        color = g.subtext, fontSize = 12.sp
                    )
                }
            }

            SectionLabel("Sound effects")
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    LabeledSlider("Volume", vm.sfxVolume, 0f..1f) { vm.updateSfxVolume(it) }
                }
            }

            SectionLabel("Appearance")
            Segmented(listOf("Light Studio", "AMOLED Dark"), if (vm.dark) 1 else 0, {
                if ((it == 1) != vm.dark) vm.toggleDark()
            })

            SectionLabel("About")
            Text(
                "Cholobotar v0.3 • Background video loop live preview-te cholbe, kintu recording-e shudhu prothom frame ashe.",
                color = g.subtext, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
