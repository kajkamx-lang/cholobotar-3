package com.cholobotar.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AvatarScreen(vm: StudioViewModel, onPickSlot: (String) -> Unit) {
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
            Text("Avatar", color = g.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Sob PNG same size-e export koro (jemon 1024x1024), tahole layer gulo thikmoto mile jabe. Kono slot khali rakhle cholbe.",
                color = g.subtext, fontSize = 13.sp
            )

            SectionLabel("Live preview")
            GlassPanel(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(200.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                    AvatarFigure(vm, Modifier.size(184.dp))
                }
            }
            Text(
                "Mic on kore kotha bolo. Volume onujayi mouth 4 ta level-e change hoy. Studio-te avatar-e pinch korle zoom, drag korle move, double-tap korle reset.",
                color = g.subtext, fontSize = 12.sp
            )

            SectionLabel("Base layers")
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AVATAR_SLOTS.forEach { s ->
                        SlotRow(
                            title = s.title,
                            hint = s.hint,
                            bmp = vm.slotBmps[s.key],
                            onPick = { onPickSlot(s.key) },
                            onClear = { vm.clearSlot(s.key) }
                        )
                    }
                }
            }

            SectionLabel("Extra layers (hair, hat, glasses...)")
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (vm.extras.isEmpty()) {
                        Text("Kono extra layer nei.", color = g.subtext, fontSize = 13.sp)
                    }
                    vm.extras.forEachIndexed { index, e ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Thumb(e.bmp, e.name, Modifier.size(48.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, color = g.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Position ${index + 1}/${vm.extras.size}",
                                        color = g.subtext, fontSize = 11.sp
                                    )
                                }
                                GlassIconButton("↑", diameter = 34.dp, onClick = { vm.moveExtra(e.id, -1) })
                                GlassIconButton("↓", diameter = 34.dp, onClick = { vm.moveExtra(e.id, 1) })
                                GlassIconButton("✕", diameter = 34.dp, onClick = { vm.removeExtra(e.id) })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassChip(
                                    if (e.visible) "Visible" else "Hidden",
                                    selected = e.visible,
                                    onClick = { vm.toggleExtra(e.id) }
                                )
                                GlassChip(
                                    if (e.behind) "Behind body" else "In front",
                                    selected = e.behind,
                                    onClick = { vm.toggleExtraBehind(e.id) }
                                )
                            }
                        }
                    }
                    PrimaryButton("+ Add extra layer", onClick = { onPickSlot("extra") })
                    Text(
                        "List-er order = drawing order (niche thaka layer upore ashe).",
                        color = g.subtext, fontSize = 11.sp
                    )
                }
            }
            Box(Modifier.height(8.dp))
        }
    }
}
