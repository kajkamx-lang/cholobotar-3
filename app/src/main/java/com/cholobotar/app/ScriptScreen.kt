package com.cholobotar.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun ScriptScreen(vm: StudioViewModel) {
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
            Text("Script", color = g.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Marker likhle prompter-e dekha jabe na. Oi shobde pouchale background change hobe ba sound bajbe. Niche chip tap korle script-er shesh-e marker jure jabe.",
                color = g.subtext, fontSize = 13.sp
            )

            SectionLabel("Insert background")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vm.bgItems.forEach { b ->
                    GlassChip(b.name, onClick = { vm.appendToScript(" [BG:${b.name}] ") })
                }
            }
            SectionLabel("Insert sound")
            if (vm.sfxItems.isEmpty()) {
                Text("Studio > Sounds tab-e sound add korle ekhane dekhabe.", color = g.subtext, fontSize = 12.sp)
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vm.sfxItems.forEach { s ->
                        GlassChip("♪ ${s.name}", onClick = { vm.appendToScript(" [SFX:${s.name}] ") })
                    }
                }
            }

            SectionLabel("Your script")
            GlassPanel(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = vm.scriptText,
                    onValueChange = { vm.updateScript(it) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = g.text,
                        unfocusedTextColor = g.text,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = g.accent
                    )
                )
            }
            val words = vm.tp.script.words.size
            val mins = words / vm.tp.wpm.toFloat()
            Text(
                "$words words • about ${"%.1f".format(mins)} min at ${vm.tp.wpm} WPM",
                color = g.subtext, fontSize = 12.sp
            )

            SectionLabel("Prompter")
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabeledSlider("Speed: ${vm.tp.wpm} WPM", vm.tp.wpm.toFloat(), 60f..300f) { vm.setWpm(it.roundToInt()) }
                    LabeledSlider("Font size: ${vm.tp.fontSp} sp", vm.tp.fontSp.toFloat(), 18f..72f) { vm.setFont(it.roundToInt()) }
                    LabeledSlider("Background opacity", vm.tp.alpha, 0.2f..1f) { vm.setPrompterAlpha(it) }
                    Spacer(Modifier.height(4.dp))
                    Segmented(listOf("Normal", "Mirror"), if (vm.tp.mirror) 1 else 0, { vm.tp.mirror = (it == 1) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
