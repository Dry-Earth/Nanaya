package io.github.dryearth.Nanaya.music

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class PlayOutcome(
    val ok: Boolean,
    val message: String,
    val track: MusicTrack? = null,
)

object MusicRepository {
    private val randomQueries = listOf("热歌", "流行", "华语", "新歌", "轻音乐")

    suspend fun searchAll(context: Context, query: String, perPluginLimit: Int = 15): List<MusicTrack> {
        val plugins = MusicPluginStore.list(context).filter { it.enabled }
        if (plugins.isEmpty()) return emptyList()
        val results = coroutineScope {
            plugins.map { plugin ->
                async(Dispatchers.Default) {
                    runCatching {
                        MusicFreeEngine.search(context, plugin.platform, query)
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }
        return results.flatten()
            .sortedWith(
                compareByDescending<MusicTrack> { score(it, query) }
                    .thenBy { it.title.length },
            )
            .take(perPluginLimit * plugins.size.coerceAtLeast(1))
    }

    suspend fun playQuery(context: Context, query: String): PlayOutcome {
        val plugins = MusicPluginStore.list(context).filter { it.enabled }
        if (plugins.isEmpty()) {
            return PlayOutcome(false, "还没有安装音源插件。请到「工具 → 音乐 → 插件」安装 MusicFree 插件。")
        }
        val trimmed = query.trim()
        if (trimmed.isBlank()) return playRandom(context)
        val tracks = runCatching { searchAll(context, trimmed) }.getOrDefault(emptyList())
        if (tracks.isEmpty()) {
            return PlayOutcome(false, "没有找到「$trimmed」。可以换个歌名或歌手，也可以先在音乐页搜索。")
        }
        val outcome = playFirstPlayable(context, tracks.take(6))
        return if (outcome.ok) outcome else PlayOutcome(
            false,
            "找到 ${tracks.size} 个结果，但没有拿到可播放地址。插件可能暂时失效，换一首或换个音源试试。",
        )
    }

    suspend fun playRandom(context: Context): PlayOutcome {
        val plugins = MusicPluginStore.list(context).filter { it.enabled }
        if (plugins.isEmpty()) {
            return PlayOutcome(false, "还没有安装音源插件。请到「工具 → 音乐 → 插件」安装 MusicFree 插件。")
        }
        val query = randomQueries.random()
        val tracks = runCatching { searchAll(context, query) }.getOrDefault(emptyList())
        if (tracks.isEmpty()) {
            return PlayOutcome(false, "没有搜到可播放的歌曲，先在音乐页搜索一首吧。")
        }
        val pool = tracks.filter { it.durationMs in 60_000..12 * 60_000L }.ifEmpty { tracks }
        val shuffled = pool.shuffled(Random(System.currentTimeMillis())).take(6)
        val outcome = playFirstPlayable(context, shuffled)
        return if (outcome.ok) outcome else PlayOutcome(false, "随机歌曲没有拿到播放地址，稍后再试。")
    }

    suspend fun playTrack(context: Context, track: MusicTrack): PlayOutcome =
        playFirstPlayable(context, listOf(track))

    private suspend fun playFirstPlayable(context: Context, tracks: List<MusicTrack>): PlayOutcome {
        for (track in tracks) {
            val qualities = listOf("standard", "high", "low")
            for (quality in qualities) {
                val source = runCatching {
                    MusicFreeEngine.mediaSource(context, track.platform, track, quality)
                }.getOrNull()
                if (source != null) {
                    withContext(Dispatchers.Main) {
                        MusicPlayback.play(context, track, source)
                    }
                    val label = if (track.artist.isBlank()) track.title else "${track.title} — ${track.artist}"
                    return PlayOutcome(true, "正在播放《$label》（${track.platform}）", track)
                }
            }
        }
        return PlayOutcome(false, "没有拿到播放地址")
    }

    private fun score(track: MusicTrack, query: String): Int {
        val q = normalize(query)
        if (q.isBlank()) return 0
        val title = normalize(track.title)
        val artist = normalize(track.artist)
        var score = 0
        when {
            title == q -> score += 120
            title.contains(q) -> score += 70
            q.contains(title) && title.length >= 2 -> score += 40
        }
        when {
            artist == q -> score += 90
            artist.contains(q) -> score += 55
            q.contains(artist) && artist.length >= 2 -> score += 30
        }
        val duration = track.durationMs
        score += when {
            duration == 0L -> -5
            duration in 60_000..8 * 60_000L -> 25
            duration > 20 * 60_000L -> -90
            duration > 12 * 60_000L -> -20
            else -> 5
        }
        val titleArtist = "$title $artist"
        if (titleArtist.contains(q)) score += 10
        return score
    }

    private fun normalize(value: String): String = buildString {
        for (ch in value.lowercase()) {
            if (ch.isLetterOrDigit() || ch.code in 0x4e00..0x9fff) append(ch)
        }
    }
}
