package com.cholobotar.app

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

class StudioViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("cholobotar", Context.MODE_PRIVATE)

    val gate = VoiceGate()
    val bounce = BounceEngine()
    val tp = TeleprompterState()
    private val mic = MicCapture(gate)
    private val sfx = SfxPlayer()
    private val recorder by lazy { SceneRecorder(app, this) }

    // ---- general ----
    var dark by mutableStateOf(false)
        private set
    var micOn by mutableStateOf(false)
        private set
    var meter by mutableFloatStateOf(0f)
        private set
    var tick by mutableLongStateOf(0L)
        private set
    var sensitivityDb by mutableFloatStateOf(-38f)
        private set

    // ---- avatar ----
    val slotBmps = mutableStateMapOf<String, ImageBitmap>()
    var extras by mutableStateOf<List<ExtraLayer>>(emptyList())
        private set
    var mouthLevel by mutableIntStateOf(0)
        private set
    var eyesClosed by mutableStateOf(false)
        private set
    var avatarScale by mutableFloatStateOf(1f)
        private set
    var avatarOffX by mutableFloatStateOf(0f)
        private set
    var avatarOffY by mutableFloatStateOf(0.15f)
        private set

    // ---- voice reaction ----
    var bounceOn by mutableStateOf(true)
        private set
    var bounceAmount by mutableFloatStateOf(0.5f)
        private set
    var bounceSpeed by mutableFloatStateOf(6.5f)
        private set
    var shakeOn by mutableStateOf(true)
        private set
    var blinkOn by mutableStateOf(true)
        private set

    // ---- backgrounds / sounds ----
    var bgItems by mutableStateOf<List<BgItem>>(PRESET_BGS)
        private set
    var bgId by mutableStateOf("p_studio")
        private set
    var bgStill by mutableStateOf<ImageBitmap?>(null)
        private set
    var sfxItems by mutableStateOf<List<SfxItem>>(emptyList())
        private set
    var sfxVolume by mutableFloatStateOf(1f)
        private set

    // ---- script ----
    var scriptText by mutableStateOf(DEFAULT_SCRIPT)
        private set

    // ---- recording ----
    var recording by mutableStateOf(false)
        private set
    var recSeconds by mutableIntStateOf(0)
        private set
    var recRes by mutableIntStateOf(2)
        private set
    var recFormat by mutableIntStateOf(0)
        private set
    var recFps by mutableIntStateOf(30)
        private set
    var showGuide by mutableStateOf(true)
        private set

    private var countdownJob: Job? = null
    private var blinkTimer = 2f
    private var recStartNs = 0L

    init {
        dark = prefs.getBoolean("dark", false)
        scriptText = prefs.getString("script", DEFAULT_SCRIPT) ?: DEFAULT_SCRIPT
        sensitivityDb = prefs.getFloat("openDb", -38f)
        gate.openDb = sensitivityDb
        tp.wpm = prefs.getInt("wpm", 140)
        tp.fontSp = prefs.getInt("font", 30)
        tp.alpha = prefs.getFloat("alpha", 0.85f)
        tp.load(scriptText)
        bounceOn = prefs.getBoolean("bounceOn", true)
        bounceAmount = prefs.getFloat("bounceAmount", 0.5f)
        bounceSpeed = prefs.getFloat("bounceSpeed", 6.5f)
        shakeOn = prefs.getBoolean("shakeOn", true)
        blinkOn = prefs.getBoolean("blinkOn", true)
        applyBounce()
        avatarScale = prefs.getFloat("avScale", 1f)
        avatarOffX = prefs.getFloat("avOffX", 0f)
        avatarOffY = prefs.getFloat("avOffY", 0.15f)
        recRes = prefs.getInt("recRes", 2).coerceIn(0, RES_OPTIONS.size - 1)
        recFormat = prefs.getInt("recFormat", 0)
        recFps = prefs.getInt("recFps", 30)
        showGuide = prefs.getBoolean("guide", true)
        sfxVolume = prefs.getFloat("sfxVol", 1f)
        sfx.volume = sfxVolume
        viewModelScope.launch(Dispatchers.IO) { loadFiles() }
    }

    // ───────── per-frame update (called from the UI frame loop) ─────────
    fun frame(dt: Float, nowNanos: Long) {
        bounce.update(dt, gate.isOpen, gate.level)
        val lvl = if (!gate.isOpen) 0 else (1 + (gate.level * 3f).toInt()).coerceIn(1, 3)
        if (lvl != mouthLevel) mouthLevel = lvl
        meter = gate.level
        tick = nowNanos
        if (blinkOn) {
            blinkTimer -= dt
            if (blinkTimer <= 0f) {
                if (eyesClosed) {
                    eyesClosed = false
                    blinkTimer = 2f + Random.nextFloat() * 3f
                } else {
                    eyesClosed = true
                    blinkTimer = 0.12f
                }
            }
        } else if (eyesClosed) {
            eyesClosed = false
        }
        if (recording) recSeconds = ((nowNanos - recStartNs) / 1_000_000_000L).toInt()
        tp.tick(dt) { onMarker(it) }
    }

    private fun onMarker(m: ScriptMarker) {
        when (m.kind) {
            "BG" -> findBg(m.value)?.let { selectBg(it.id) }
            "SFX" -> sfxItems.firstOrNull { it.name.equals(m.value, ignoreCase = true) }?.let { playSfx(it) }
        }
    }

    // ───────── avatar layers (shared by the UI and the recorder) ─────────
    fun avatarLayers(): List<ImageBitmap> {
        val out = ArrayList<ImageBitmap>()
        for (e in extras) if (e.visible && e.behind) out.add(e.bmp)
        slotBmps["body"]?.let { out.add(it) }
        val eyes = if (eyesClosed) (slotBmps["eyes_closed"] ?: slotBmps["eyes_open"]) else slotBmps["eyes_open"]
        if (eyes != null) out.add(eyes)
        val mouth = resolveMouth()
        if (mouth != null) out.add(mouth)
        for (e in extras) if (e.visible && !e.behind) out.add(e.bmp)
        return out
    }

    private fun resolveMouth(): ImageBitmap? {
        val lvl = mouthLevel
        if (lvl == 0) {
            return slotBmps["mouth0"] ?: slotBmps["mouth1"] ?: slotBmps["mouth2"] ?: slotBmps["mouth3"]
        }
        for (i in lvl..3) {
            val b = slotBmps["mouth$i"]
            if (b != null) return b
        }
        for (i in lvl - 1 downTo 1) {
            val b = slotBmps["mouth$i"]
            if (b != null) return b
        }
        return slotBmps["mouth0"]
    }

    fun importSlot(slot: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(app.filesDir, "avatar").apply { mkdirs() }
                if (slot == "extra") {
                    val n = prefs.getInt("extraCounter", 0) + 1
                    prefs.edit().putInt("extraCounter", n).apply()
                    val id = "e$n"
                    val f = File(dir, "extra_$id.png")
                    copyUri(uri, f)
                    val bmp = decodeFile(f, 1024)
                    if (bmp == null) {
                        f.delete()
                        toast("Image load hoyni")
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        extras = extras + ExtraLayer(id, "Layer $n", true, false, bmp)
                        saveExtras()
                    }
                } else {
                    val f = File(dir, "$slot.png")
                    copyUri(uri, f)
                    val bmp = decodeFile(f, 1024)
                    if (bmp == null) {
                        f.delete()
                        toast("Image load hoyni")
                        return@launch
                    }
                    withContext(Dispatchers.Main) { slotBmps[slot] = bmp }
                }
            } catch (e: Exception) {
                toast("Image load hoyni")
            }
        }
    }

    fun clearSlot(slot: String) {
        File(File(app.filesDir, "avatar"), "$slot.png").delete()
        slotBmps.remove(slot)
    }

    fun toggleExtra(id: String) {
        extras = extras.map { if (it.id == id) it.copyWith(visible = !it.visible) else it }
        saveExtras()
    }

    fun toggleExtraBehind(id: String) {
        extras = extras.map { if (it.id == id) it.copyWith(behind = !it.behind) else it }
        saveExtras()
    }

    fun moveExtra(id: String, dir: Int) {
        val list = extras.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j < 0 || j >= list.size) return
        val t = list[i]
        list[i] = list[j]
        list[j] = t
        extras = list
        saveExtras()
    }

    fun removeExtra(id: String) {
        File(File(app.filesDir, "avatar"), "extra_$id.png").delete()
        extras = extras.filter { it.id != id }
        saveExtras()
    }

    private fun saveExtras() {
        val s = extras.joinToString("\n") {
            "${it.id}|${it.name.replace("|", " ")}|${if (it.visible) 1 else 0}|${if (it.behind) 1 else 0}"
        }
        prefs.edit().putString("extras", s).apply()
    }

    fun transformAvatar(dx: Float, dy: Float, zoom: Float) {
        avatarScale = (avatarScale * zoom).coerceIn(0.3f, 3f)
        avatarOffX = (avatarOffX + dx).coerceIn(-1f, 1f)
        avatarOffY = (avatarOffY + dy).coerceIn(-1f, 1f)
    }

    fun resetAvatarTransform() {
        avatarScale = 1f
        avatarOffX = 0f
        avatarOffY = 0.15f
        saveAvatarTransform()
    }

    private fun saveAvatarTransform() {
        prefs.edit().putFloat("avScale", avatarScale).putFloat("avOffX", avatarOffX)
            .putFloat("avOffY", avatarOffY).apply()
    }

    // ───────── voice reaction settings ─────────
    private fun applyBounce() {
        bounce.enabled = bounceOn
        bounce.shake = shakeOn
        bounce.amplitude = 0.01f + bounceAmount * 0.11f
        bounce.frequency = bounceSpeed
    }

    fun setBounceOn(v: Boolean) { bounceOn = v; prefs.edit().putBoolean("bounceOn", v).apply(); applyBounce() }
    fun setBounceAmount(v: Float) { bounceAmount = v; prefs.edit().putFloat("bounceAmount", v).apply(); applyBounce() }
    fun setBounceSpeed(v: Float) { bounceSpeed = v; prefs.edit().putFloat("bounceSpeed", v).apply(); applyBounce() }
    fun setShakeOn(v: Boolean) { shakeOn = v; prefs.edit().putBoolean("shakeOn", v).apply(); applyBounce() }
    fun setBlinkOn(v: Boolean) { blinkOn = v; prefs.edit().putBoolean("blinkOn", v).apply() }

    // ───────── mic ─────────
    fun setMic(on: Boolean) {
        if (!on && recording) stopRecording()
        micOn = on
        if (on) mic.start(viewModelScope) else mic.stop()
    }

    fun setSensitivity(db: Float) {
        sensitivityDb = db
        gate.openDb = db
        prefs.edit().putFloat("openDb", db).apply()
    }

    // ───────── backgrounds ─────────
    fun currentBg(): BgItem? = bgItems.firstOrNull { it.id == bgId }

    private fun findBg(name: String): BgItem? = bgItems.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun selectBg(id: String) {
        val item = bgItems.firstOrNull { it.id == id } ?: return
        bgId = id
        prefs.edit().putString("bgId", id).apply()
        val path = item.path
        if (item.kind == "color" || path == null) {
            bgStill = null
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = if (item.kind == "image") decodeFile(File(path), 1280) else videoFrame(path)
            withContext(Dispatchers.Main) { if (bgId == id) bgStill = bmp }
        }
    }

    fun addBgFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mime = app.contentResolver.getType(uri) ?: ""
                val isVideo = mime.startsWith("video/")
                if (!isVideo && !mime.startsWith("image/")) {
                    toast("Photo ba video choose koro")
                    return@launch
                }
                val n = prefs.getInt("bgCounter", 0) + 1
                prefs.edit().putInt("bgCounter", n).apply()
                val id = "g$n"
                val dir = File(app.filesDir, "bgs").apply { mkdirs() }
                val f = File(dir, if (isVideo) "$id.mp4" else "$id.img")
                copyUri(uri, f)
                val thumb = makeThumb(f, isVideo)
                if (thumb == null) {
                    f.delete()
                    toast("File load hoyni")
                    return@launch
                }
                val name = (if (isVideo) "VIDEO" else "PHOTO") + n
                val item = BgItem(id, name, if (isVideo) "video" else "image", emptyList(), f.path, thumb)
                withContext(Dispatchers.Main) {
                    bgItems = bgItems + item
                    saveBgMeta()
                    selectBg(id)
                }
            } catch (e: Exception) {
                toast("Background add hoyni")
            }
        }
    }

    fun removeBg(id: String) {
        val item = bgItems.firstOrNull { it.id == id } ?: return
        if (item.kind == "color") return
        item.path?.let { File(it).delete() }
        bgItems = bgItems.filter { it.id != id }
        saveBgMeta()
        if (bgId == id) selectBg("p_studio")
    }

    private fun saveBgMeta() {
        val s = bgItems.filter { it.kind != "color" && it.path != null }
            .joinToString("\n") { "${it.id}|${it.kind}|${it.name}|${File(it.path!!).name}" }
        prefs.edit().putString("gallery", s).apply()
    }

    // ───────── sounds ─────────
    fun addSfxFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val n = prefs.getInt("sfxCounter", 0) + 1
                prefs.edit().putInt("sfxCounter", n).apply()
                val id = "s$n"
                val dir = File(app.filesDir, "sfx").apply { mkdirs() }
                val f = File(dir, "$id.snd")
                copyUri(uri, f)
                val raw = displayName(uri)?.substringBeforeLast('.') ?: "SOUND$n"
                val base = raw.replace(Regex("[^A-Za-z0-9]"), "_").uppercase().take(14).ifEmpty { "SOUND$n" }
                var name = base
                var k = 2
                while (sfxItems.any { it.name == name }) {
                    name = base + k
                    k++
                }
                val item = SfxItem(id, name, f.path)
                withContext(Dispatchers.Main) {
                    sfxItems = sfxItems + item
                    saveSfxMeta()
                }
            } catch (e: Exception) {
                toast("Sound add hoyni")
            }
        }
    }

    fun removeSfx(id: String) {
        sfxItems.firstOrNull { it.id == id }?.let { File(it.path).delete() }
        sfxItems = sfxItems.filter { it.id != id }
        saveSfxMeta()
    }

    fun playSfx(item: SfxItem) = sfx.play(item.path)

    fun setSfxVolume(v: Float) {
        sfxVolume = v
        sfx.volume = v
        prefs.edit().putFloat("sfxVol", v).apply()
    }

    private fun saveSfxMeta() {
        val s = sfxItems.joinToString("\n") { "${it.id}|${it.name}|${File(it.path).name}" }
        prefs.edit().putString("sfxList", s).apply()
    }

    // ───────── recording ─────────
    fun setRecRes(i: Int) {
        if (recording) return
        recRes = i
        prefs.edit().putInt("recRes", i).apply()
    }

    fun setRecFormat(i: Int) {
        if (recording) return
        recFormat = i
        prefs.edit().putInt("recFormat", i).apply()
    }

    fun setRecFps(v: Int) {
        if (recording) return
        recFps = v
        prefs.edit().putInt("recFps", v).apply()
    }

    fun setShowGuide(v: Boolean) {
        showGuide = v
        prefs.edit().putBoolean("guide", v).apply()
    }

    fun toggleRecording() {
        if (recording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        val size = videoSizeFor(RES_OPTIONS[recRes], recFormat)
        val fps = recFps
        val withAudio = micOn
        viewModelScope.launch(Dispatchers.IO) {
            val ok = recorder.start(size.w, size.h, fps, bitrateFor(size.w, size.h, fps), withAudio)
            withContext(Dispatchers.Main) {
                if (ok) {
                    recSeconds = 0
                    recStartNs = System.nanoTime()
                    recording = true
                    if (withAudio) {
                        mic.pcmSink = { b, n -> recorder.feedPcm(b, n) }
                    } else {
                        toast("Mic off, tai video-te audio thakbe na")
                    }
                } else {
                    toast("Record shuru hoyni. Kom resolution ba fps try koro")
                }
            }
        }
    }

    fun stopRecording() {
        if (!recording) return
        recording = false
        mic.pcmSink = null
        viewModelScope.launch(Dispatchers.IO) {
            val ok = recorder.stop()
            toast(if (ok) "Saved: Movies/Cholobotar" else "Video save korte somossa hoyeche")
        }
    }

    fun onBackground() {
        if (recording) stopRecording()
        saveAvatarTransform()
    }

    // ───────── script / teleprompter ─────────
    fun updateScript(text: String) {
        scriptText = text
        tp.load(text)
        prefs.edit().putString("script", text).apply()
    }

    fun appendToScript(extra: String) = updateScript(scriptText + extra)

    fun setWpm(v: Int) {
        tp.wpm = v.coerceIn(60, 300)
        prefs.edit().putInt("wpm", tp.wpm).apply()
    }

    fun setFont(v: Int) {
        tp.fontSp = v.coerceIn(18, 72)
        prefs.edit().putInt("font", tp.fontSp).apply()
    }

    fun setPrompterAlpha(v: Float) {
        tp.alpha = v.coerceIn(0.2f, 1f)
        prefs.edit().putFloat("alpha", tp.alpha).apply()
    }

    fun togglePlay() {
        if (tp.playing || tp.countdown > 0) {
            countdownJob?.cancel()
            tp.countdown = 0
            tp.playing = false
            return
        }
        if (tp.script.words.isEmpty()) return
        if (tp.progress >= tp.script.words.size) tp.seekTo(0)
        countdownJob = viewModelScope.launch {
            for (n in 3 downTo 1) {
                tp.countdown = n
                kotlinx.coroutines.delay(1000)
            }
            tp.countdown = 0
            tp.playing = true
        }
    }

    fun restart() {
        countdownJob?.cancel()
        tp.countdown = 0
        tp.playing = false
        tp.seekTo(0)
    }

    fun toggleDark() {
        dark = !dark
        prefs.edit().putBoolean("dark", dark).apply()
    }

    // ───────── files ─────────
    private fun loadFiles() {
        val dir = File(app.filesDir, "avatar").apply { mkdirs() }
        // migrate Phase 1 two-PNG avatar
        val oldClosed = File(app.filesDir, "closed.png")
        val oldOpen = File(app.filesDir, "open.png")
        if (oldClosed.exists() && !File(dir, "mouth0.png").exists()) oldClosed.copyTo(File(dir, "mouth0.png"))
        if (oldOpen.exists() && !File(dir, "mouth3.png").exists()) oldOpen.copyTo(File(dir, "mouth3.png"))
        for (s in AVATAR_SLOTS) {
            val b = decodeFile(File(dir, "${s.key}.png"), 1024)
            if (b != null) slotBmps[s.key] = b
        }

        val ex = ArrayList<ExtraLayer>()
        for (line in (prefs.getString("extras", "") ?: "").lines()) {
            val p = line.split("|")
            if (p.size < 4) continue
            val bmp = decodeFile(File(dir, "extra_${p[0]}.png"), 1024) ?: continue
            ex.add(ExtraLayer(p[0], p[1], p[2] == "1", p[3] == "1", bmp))
        }
        extras = ex

        val list = ArrayList<BgItem>(PRESET_BGS)
        val bdir = File(app.filesDir, "bgs")
        for (line in (prefs.getString("gallery", "") ?: "").lines()) {
            val p = line.split("|")
            if (p.size < 4) continue
            val f = File(bdir, p[3])
            if (!f.exists()) continue
            val thumb = makeThumb(f, p[1] == "video") ?: continue
            list.add(BgItem(p[0], p[2], p[1], emptyList(), f.path, thumb))
        }
        bgItems = list

        val sdir = File(app.filesDir, "sfx")
        val sl = ArrayList<SfxItem>()
        for (line in (prefs.getString("sfxList", "") ?: "").lines()) {
            val p = line.split("|")
            if (p.size < 3) continue
            val f = File(sdir, p[2])
            if (f.exists()) sl.add(SfxItem(p[0], p[1], f.path))
        }
        sfxItems = sl

        val saved = prefs.getString("bgId", "p_studio") ?: "p_studio"
        selectBg(if (list.any { it.id == saved }) saved else "p_studio")
    }

    private fun copyUri(uri: Uri, f: File) {
        val input = app.contentResolver.openInputStream(uri) ?: throw java.io.IOException("cannot open")
        input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
    }

    private fun displayName(uri: Uri): String? =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun decodeFile(f: File, maxDim: Int): ImageBitmap? {
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()
    }

    private fun videoFrame(path: String): ImageBitmap? {
        return try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(path)
                r.getFrameAtTime(0)?.asImageBitmap()
            } finally {
                r.release()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun makeThumb(f: File, video: Boolean): ImageBitmap? {
        if (!video) return decodeFile(f, 240)
        return try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(f.path)
                r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 240)?.asImageBitmap()
            } finally {
                r.release()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun toast(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCleared() {
        mic.pcmSink = null
        mic.stop()
        sfx.releaseAll()
        super.onCleared()
    }
}
