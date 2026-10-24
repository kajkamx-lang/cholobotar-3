package com.cholobotar.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

const val MIC_SR = 44_100

/** Mic loudness -> speaking or not, with hysteresis so it does not flicker. */
class VoiceGate {
    @Volatile
    var openDb: Float = -38f

    @Volatile
    var holdMs: Long = 90L

    @Volatile
    var level: Float = 0f
        private set

    @Volatile
    var isOpen: Boolean = false
        private set

    private var lastLoudAt = 0L
    private val closeDb: Float get() = openDb - 6f

    fun process(pcm: ShortArray, n: Int, nowMs: Long) {
        if (n <= 0) return
        var sum = 0.0
        for (i in 0 until n) {
            val s = pcm[i] / 32768.0
            sum += s * s
        }
        val db = (20.0 * log10(sqrt(sum / n) + 1e-9)).toFloat()
        level = ((db - closeDb) / 30f).coerceIn(0f, 1f)
        if (db > openDb) {
            isOpen = true
            lastLoudAt = nowMs
        } else if (isOpen && db < closeDb && nowMs - lastLoudAt > holdMs) {
            isOpen = false
        }
    }

    fun reset() {
        isOpen = false
        level = 0f
    }
}

/** Procedural bounce + shake while speaking. Amplitude is a fraction of the avatar size. */
class BounceEngine {
    @Volatile
    var enabled: Boolean = true

    @Volatile
    var shake: Boolean = true

    @Volatile
    var amplitude: Float = 0.06f

    @Volatile
    var frequency: Float = 6.5f

    var decay: Float = 0.82f

    private var phase = 0f
    private var energy = 0f

    var offsetY: Float = 0f
        private set
    var shakeX: Float = 0f
        private set

    fun clear() {
        energy = 0f
        offsetY = 0f
        shakeX = 0f
    }

    fun update(dt: Float, speaking: Boolean, level: Float) {
        if (!enabled) {
            clear()
            return
        }
        energy = if (speaking) max(energy, max(level, 0.35f)) else energy * decay.pow(dt * 60f)
        if (energy < 0.01f) {
            clear()
            return
        }
        phase += 2f * PI.toFloat() * frequency * dt
        offsetY = -abs(sin(phase)) * amplitude * energy
        shakeX = if (shake) sin(phase * 3.1f) * amplitude * 0.5f * energy else 0f
    }
}

class MicCapture(private val gate: VoiceGate) {
    private var job: Job? = null

    /** Receives raw PCM (16-bit mono, MIC_SR) so the recorder can encode it. */
    @Volatile
    var pcmSink: ((ShortArray, Int) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                MIC_SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return@launch
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC, MIC_SR,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
                )
            } catch (e: Exception) {
                return@launch
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return@launch
            }
            val buf = ShortArray(1024)
            try {
                rec.startRecording()
                while (isActive) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        gate.process(buf, n, SystemClock.elapsedRealtime())
                        pcmSink?.invoke(buf, n)
                    } else if (n < 0) {
                        break
                    }
                }
            } finally {
                try { rec.stop() } catch (e: Exception) { }
                rec.release()
                gate.reset()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
