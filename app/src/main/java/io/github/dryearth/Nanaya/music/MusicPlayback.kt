package io.github.dryearth.Nanaya.music

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Request headers keyed by the exact media URL. QuickJS plugins often need a
 * Referer/User-Agent to serve audio; the playback service reads this map through
 * a ResolvingDataSource.
 */
object MediaHeaders {
    private val headers = ConcurrentHashMap<String, Map<String, String>>()

    fun put(url: String, value: Map<String, String>) {
        if (value.isEmpty()) return
        headers[url] = value.toMap()
        if (headers.size > 40) {
            headers.keys.take(headers.size - 40).forEach { headers.remove(it) }
        }
    }

    fun get(url: String): Map<String, String> = headers[url] ?: emptyMap()
}

/** Thin controller around the MediaSessionService-owned ExoPlayer. */
object MusicPlayback {
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val _nowPlaying = MutableStateFlow<MusicTrack?>(null)
    val nowPlaying: StateFlow<MusicTrack?> = _nowPlaying.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun play(context: Context, track: MusicTrack, source: MusicSourceInfo) {
        MediaHeaders.put(source.url, source.headers)
        _error.value = null
        _nowPlaying.value = track
        connect(context) { player ->
            val item = MediaItem.Builder()
                .setUri(source.url)
                .setMediaId(track.id.ifBlank { source.url })
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.artwork?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() })
                        .build(),
                )
                .build()
            player.setMediaItem(item)
            player.prepare()
            player.play()
        }
    }

    fun toggle() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun resume() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) return
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
            player.prepare()
        }
        player.play()
    }

    fun stop() {
        val player = controller ?: return
        player.stop()
        player.clearMediaItems()
        _isPlaying.value = false
    }

    fun clearError() {
        _error.value = null
    }

    private fun connect(context: Context, action: (MediaController) -> Unit) {
        val existing = controller
        if (existing != null) {
            action(existing)
            return
        }
        pending += action
        if (connecting) return
        connecting = true
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            connecting = false
            try {
                val player = future.get()
                controller = player
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) _isPlaying.value = false
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _error.value = error.message ?: "播放失败"
                        _isPlaying.value = false
                    }
                })
                pending.forEach { it(player) }
                pending.clear()
            } catch (e: Exception) {
                pending.clear()
                _error.value = "无法连接播放服务：${e.message}"
            }
        }, ContextCompat.getMainExecutor(appContext))
    }
}
