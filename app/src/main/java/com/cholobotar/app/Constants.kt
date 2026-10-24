package com.cholobotar.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

const val DEFAULT_SCRIPT =
    "Hey everyone, welcome back to the channel! Today we are talking about something absolutely wild. " +
    "[BG:DARK] Nobody saw this coming, and honestly I still cannot believe it. " +
    "[BG:STUDIO] Anyway, let us get into it. " +
    "Tap this prompter to pause or play, and edit this text in the Script tab."

/** kind: "color" | "image" | "video" */
class BgItem(
    val id: String,
    val name: String,
    val kind: String,
    val colors: List<Color> = emptyList(),
    val path: String? = null,
    val thumb: ImageBitmap? = null
)

class SfxItem(val id: String, val name: String, val path: String)

val PRESET_BGS: List<BgItem> = listOf(
    BgItem("p_studio", "STUDIO", "color", listOf(Color(0xFFEAF0FF), Color(0xFFFDF2F8))),
    BgItem("p_dark", "DARK", "color", listOf(Color(0xFF0B0B12), Color(0xFF1B1B2B))),
    BgItem("p_green", "GREEN", "color", listOf(Color(0xFF00B140), Color(0xFF00B140))),
    BgItem("p_blue", "BLUE", "color", listOf(Color(0xFF1E3C72), Color(0xFF2A5298))),
    BgItem("p_sunset", "SUNSET", "color", listOf(Color(0xFFFF9A5A), Color(0xFF8E3A8E)))
)

val RES_OPTIONS = listOf(360, 480, 720, 1080)

class SlotDef(val key: String, val title: String, val hint: String)

val AVATAR_SLOTS = listOf(
    SlotDef("body", "Body / base", "Pura character ba body. Sobar niche thake."),
    SlotDef("eyes_open", "Eyes open", "Normal chokh."),
    SlotDef("eyes_closed", "Eyes closed", "Blink-er somoy dekhabe."),
    SlotDef("mouth0", "Mouth: closed", "Kotha na bolle."),
    SlotDef("mouth1", "Mouth: small", "Aste kotha."),
    SlotDef("mouth2", "Mouth: medium", "Normal kotha."),
    SlotDef("mouth3", "Mouth: wide", "Joray kotha / chitkar.")
)

class ExtraLayer(
    val id: String,
    val name: String,
    val visible: Boolean,
    val behind: Boolean,
    val bmp: ImageBitmap
) {
    fun copyWith(visible: Boolean = this.visible, behind: Boolean = this.behind) =
        ExtraLayer(id, name, visible, behind, bmp)
}
