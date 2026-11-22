package io.github.dryearth.Nanaya.music

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sequential queue around the single-item MediaSession player.
 *
 * Sources are resolved lazily when a track starts, so a dead source is skipped
 * and the next search result is tried. Queue order follows the search results
 * (or the selected source filter in the music screen).
 */
object MusicQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appContext: Context? = null
    private var generation = 0

    private val _tracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val tracks: StateFlow<List<MusicTrack>> = _tracks.asStateFlow()

    private val _index = MutableStateFlow(-1)
    val index: StateFlow<Int> = _index.asStateFlow()

    init {
        MusicPlayback.onPlaybackEnded = { next() }
        MusicPlayback.onPlaybackError = { next() }
    }

    fun start(
        context: Context,
        items: List<MusicTrack>,
        startIndex: Int,
        firstSource: MusicSourceInfo? = null,
    ) {
        appContext = context.applicationContext
        val list = items.take(50)
        if (list.isEmpty()) return
        _tracks.value = list
        val index = startIndex.coerceIn(0, list.lastIndex)
        val gen = ++generation
        if (firstSource != null) {
            _index.value = index
            MusicPlayback.play(context, list[index], firstSource)
        } else {
            resolveFrom(index, gen)
        }
    }

    fun next() {
        if (_tracks.value.isEmpty()) return
        resolveFrom(_index.value + 1, generation)
    }

    fun previous() {
        if (_tracks.value.isEmpty()) return
        if (_index.value > 0) resolveFrom(_index.value - 1, generation)
    }

    fun stop() {
        generation++
        MusicPlayback.stop()
        _tracks.value = emptyList()
        _index.value = -1
    }

    fun clear() {
        generation++
        _tracks.value = emptyList()
        _index.value = -1
    }

    private fun resolveFrom(start: Int, gen: Int) {
        val context = appContext ?: return
        val list = _tracks.value
        if (list.isEmpty()) return
        scope.launch {
            var cursor = start
            while (cursor in list.indices) {
                if (gen != generation) return@launch
                val source = runCatching {
                    MusicRepository.resolveSource(context, list[cursor])
                }.getOrNull()
                if (gen != generation) return@launch
                if (source != null) {
                    _index.value = cursor
                    MusicPlayback.play(context, list[cursor], source)
                    return@launch
                }
                cursor++
            }
            _index.value = -1
        }
    }
}
