package com.cholobotar.app

import android.app.Application
import android.content.ContentValues
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Muxer wrapper: waits until every expected track has a format, buffering samples until then. */
private class Mux(private val muxer: MediaMuxer, private val expected: Int) {
    private var added = 0
    private var started = false
    private class Pending(val track: Int, val bytes: ByteArray, val info: MediaCodec.BufferInfo)
    private val pending = ArrayList<Pending>()

    @Synchronized
    fun addTrack(fmt: MediaFormat): Int {
        val t = muxer.addTrack(fmt)
        added++
        if (added == expected) {
            muxer.start()
            started = true
            for (p in pending) muxer.writeSampleData(p.track, ByteBuffer.wrap(p.bytes), p.info)
            pending.clear()
        }
        return t
    }

    @Synchronized
    fun write(track: Int, buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (started) {
            muxer.writeSampleData(track, buf, info)
        } else {
            val bytes = ByteArray(info.size)
            buf.get(bytes)
            val i = MediaCodec.BufferInfo()
            i.set(0, info.size, info.presentationTimeUs, info.flags)
            pending.add(Pending(track, bytes, i))
        }
    }

    @Synchronized
    fun finish(): Boolean {
        var ok = started
        try {
            if (started) muxer.stop()
        } catch (e: Exception) {
            ok = false
        }
        try { muxer.release() } catch (e: Exception) { }
        return ok
    }
}

/**
 * Records the stage (SceneDrawer) into an MP4 in Movies/Cholobotar.
 * Video: Canvas -> MediaCodec input surface (H.264). Audio: mic PCM -> AAC.
 * Both tracks are timestamped against one monotonic clock (baseUs) so they stay in sync.
 */
class SceneRecorder(private val app: Application, private val vm: StudioViewModel) {
    private var uri: Uri? = null
    private var pfd: ParcelFileDescriptor? = null
    private var mux: Mux? = null
    private var vCodec: MediaCodec? = null
    private var aCodec: MediaCodec? = null
    private var surface: Surface? = null
    private var renderThread: Thread? = null
    private var vDrain: Thread? = null
    private var aDrain: Thread? = null

    @Volatile
    private var running = false
    private var baseUs = 0L
    private var audioSamples = 0L
    private var audioStartUs = -1L
    private var w = 0
    private var h = 0
    private var fps = 30

    @Synchronized
    fun start(w: Int, h: Int, fps: Int, bitrate: Int, withAudio: Boolean): Boolean {
        if (running) return false
        this.w = w
        this.h = h
        this.fps = fps
        try {
            val values = ContentValues()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            values.put(MediaStore.Video.Media.DISPLAY_NAME, "Cholobotar_$stamp.mp4")
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Cholobotar")
            values.put(MediaStore.Video.Media.IS_PENDING, 1)
            val u = app.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (u == null) return false
            uri = u
            val p = app.contentResolver.openFileDescriptor(u, "w")
            if (p == null) {
                abort()
                return false
            }
            pfd = p
            mux = Mux(
                MediaMuxer(p.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4),
                if (withAudio) 2 else 1
            )

            val vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h)
            vf.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            vf.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            vf.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            val vc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            vc.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = vc.createInputSurface()
            vc.start()
            vCodec = vc

            if (withAudio) {
                val af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, MIC_SR, 1)
                af.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                af.setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
                af.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                val ac = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                ac.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                ac.start()
                aCodec = ac
            }

            baseUs = System.nanoTime() / 1000L
            audioSamples = 0L
            audioStartUs = -1L
            running = true
            startThreads(vc, surface!!, mux!!)
            return true
        } catch (e: Exception) {
            running = false
            abort()
            return false
        }
    }

    private fun startThreads(vc: MediaCodec, surf: Surface, m: Mux) {
        val t = Thread {
            val frameNs = 1_000_000_000L / fps
            var next = System.nanoTime()
            while (running) {
                try {
                    val canvas = try {
                        surf.lockHardwareCanvas()
                    } catch (e: Exception) {
                        surf.lockCanvas(null)
                    }
                    try {
                        SceneDrawer.draw(vm, canvas, w, h)
                    } finally {
                        surf.unlockCanvasAndPost(canvas)
                    }
                } catch (e: Exception) {
                    // skip this frame
                }
                next += frameNs
                val sleepMs = (next - System.nanoTime()) / 1_000_000L
                if (sleepMs > 0) Thread.sleep(sleepMs) else next = System.nanoTime()
            }
        }
        t.name = "cb-render"
        t.start()
        renderThread = t

        val vd = Thread { drain(vc, m, true) }
        vd.name = "cb-vdrain"
        vd.start()
        vDrain = vd

        val ac = aCodec
        if (ac != null) {
            val ad = Thread { drain(ac, m, false) }
            ad.name = "cb-adrain"
            ad.start()
            aDrain = ad
        }
    }

    private fun drain(codec: MediaCodec, m: Mux, video: Boolean) {
        val info = MediaCodec.BufferInfo()
        var track = -1
        var last = -1L
        try {
            while (true) {
                val idx = codec.dequeueOutputBuffer(info, 10_000)
                if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = m.addTrack(codec.outputFormat)
                } else if (idx >= 0) {
                    val buf = codec.getOutputBuffer(idx)
                    val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (buf != null && info.size > 0 && !isConfig && track >= 0) {
                        var pts = info.presentationTimeUs
                        if (video) {
                            pts -= baseUs
                            if (pts < 0L || pts > 86_400_000_000L) pts = System.nanoTime() / 1000L - baseUs
                        }
                        if (pts <= last) pts = last + 1L
                        last = pts
                        info.presentationTimeUs = pts
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        m.write(track, buf, info)
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        } catch (e: Exception) {
            // codec released or stopped
        }
    }

    /** Called from the mic thread with raw PCM. */
    fun feedPcm(pcm: ShortArray, n: Int) {
        val ac = aCodec ?: return
        if (!running) return
        try {
            val idx = ac.dequeueInputBuffer(10_000)
            if (idx < 0) return
            val ib = ac.getInputBuffer(idx) ?: return
            ib.clear()
            val bytes = n * 2
            if (bytes > ib.capacity()) {
                ac.queueInputBuffer(idx, 0, 0, 0L, 0)
                return
            }
            ib.order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until n) ib.putShort(pcm[i])
            val nowUs = System.nanoTime() / 1000L - baseUs
            if (audioStartUs < 0L) audioStartUs = (nowUs - n * 1_000_000L / MIC_SR).coerceAtLeast(0L)
            val pts = audioStartUs + audioSamples * 1_000_000L / MIC_SR
            audioSamples += n
            ac.queueInputBuffer(idx, 0, bytes, pts, 0)
        } catch (e: Exception) {
            // drop this chunk
        }
    }

    fun stop(): Boolean {
        if (!running) return false
        running = false
        var ok = true
        try {
            renderThread?.join(2000)
            vCodec?.signalEndOfInputStream()
            val ac = aCodec
            if (ac != null) {
                var sent = false
                var tries = 0
                while (!sent && tries < 20) {
                    val idx = ac.dequeueInputBuffer(20_000)
                    if (idx >= 0) {
                        val pts = audioStartUs.coerceAtLeast(0L) + audioSamples * 1_000_000L / MIC_SR
                        ac.queueInputBuffer(idx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sent = true
                    }
                    tries++
                }
            }
            vDrain?.join(4000)
            aDrain?.join(4000)
        } catch (e: Exception) {
            ok = false
        }
        val saved = mux?.finish() ?: false
        releaseAll()
        val u = uri
        if (u != null) {
            if (saved) {
                val v = ContentValues()
                v.put(MediaStore.Video.Media.IS_PENDING, 0)
                app.contentResolver.update(u, v, null, null)
            } else {
                app.contentResolver.delete(u, null, null)
            }
        }
        uri = null
        return ok && saved
    }

    private fun releaseAll() {
        try { vCodec?.stop() } catch (e: Exception) { }
        try { vCodec?.release() } catch (e: Exception) { }
        try { aCodec?.stop() } catch (e: Exception) { }
        try { aCodec?.release() } catch (e: Exception) { }
        try { surface?.release() } catch (e: Exception) { }
        try { pfd?.close() } catch (e: Exception) { }
        vCodec = null
        aCodec = null
        surface = null
        pfd = null
        mux = null
        renderThread = null
        vDrain = null
        aDrain = null
    }

    private fun abort() {
        releaseAll()
        val u = uri
        if (u != null) {
            try { app.contentResolver.delete(u, null, null) } catch (e: Exception) { }
        }
        uri = null
    }
}
