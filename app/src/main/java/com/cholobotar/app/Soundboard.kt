package com.cholobotar.app

import android.media.MediaPlayer

/** Plays imported sound effects. Each tap starts its own player so sounds can overlap. */
class SfxPlayer {
    @Volatile
    var volume: Float = 1f

    private val active: MutableSet<MediaPlayer> = java.util.Collections.synchronizedSet(HashSet<MediaPlayer>())

    fun play(path: String) {
        try {
            val mp = MediaPlayer()
            mp.setDataSource(path)
            mp.setVolume(volume, volume)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener {
                active.remove(it)
                it.release()
            }
            mp.setOnErrorListener { p, _, _ ->
                active.remove(p)
                p.release()
                true
            }
            active.add(mp)
            mp.prepareAsync()
        } catch (e: Exception) {
            // ignore: bad file
        }
    }

    fun releaseAll() {
        synchronized(active) {
            for (m in active) {
                try { m.release() } catch (e: Exception) { }
            }
            active.clear()
        }
    }
}
