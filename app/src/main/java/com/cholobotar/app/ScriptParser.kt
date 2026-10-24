package com.cholobotar.app

class ScriptMarker(val wordIndex: Int, val kind: String, val value: String)
class ParsedScript(val words: List<String>, val markers: List<ScriptMarker>)

private val MARKER_RE = Regex("\\[(EMOTE|BG|SFX):([A-Za-z0-9_]+)\\]")

/** Splits a script into visible words and hidden markers. A marker fires right before the next word. */
fun parseScript(raw: String): ParsedScript {
    val words = ArrayList<String>()
    val markers = ArrayList<ScriptMarker>()
    for (token in raw.split(Regex("\\s+"))) {
        if (token.isEmpty()) continue
        for (m in MARKER_RE.findAll(token)) {
            markers.add(ScriptMarker(words.size, m.groupValues[1], m.groupValues[2].uppercase()))
        }
        val rest = MARKER_RE.replace(token, "")
        if (rest.isNotEmpty()) words.add(rest)
    }
    return ParsedScript(words, markers)
}
