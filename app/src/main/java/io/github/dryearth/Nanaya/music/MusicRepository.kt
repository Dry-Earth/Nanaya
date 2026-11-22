package io.github.dryearth.Nanaya.music

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class PlayOutcome(
    val ok: Boolean,
    val message: String,
    val track: MusicTrack? = null,
)

object MusicRepository {
    private val randomQueries = listOf("热歌", "流行", "华语", "新歌", "轻音乐")

    private val _lastErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val lastErrors: StateFlow<Map<String, String>> = _lastErrors.asStateFlow()

    suspend fun searchAll(context: Context, query: String, perPluginLimit: Int = 15): List<MusicTrack> {
        val plugins = MusicPluginStore.list(context).filter { it.enabled }
        if (plugins.isEmpty()) {
            _lastErrors.value = emptyMap()
            return emptyList()
        }
        val errors = ConcurrentHashMap<String, String>()
        val results = coroutineScope {
            plugins.map { plugin ->
                async(Dispatchers.Default) {
                    runCatching {
                        MusicFreeEngine.search(context, plugin.platform, query)
                    }.onSuccess { tracks ->
                        MusicFreeEngine.log("插件 ${plugin.platform} 返回 ${tracks.size} 首")
                    }.onFailure { error ->
                        errors[plugin.platform] = (error.message ?: error.toString())
                            .substringBefore('\n')
                            .take(200)
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }
        _lastErrors.value = errors.toMap()
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
            val error = lastErrors.value.entries.firstOrNull()
            return PlayOutcome(
                false,
                if (error != null) {
                    "搜索「$trimmed」失败：${error.key}：${error.value}"
                } else {
                    "没有找到「$trimmed」。可以换个歌名或歌手，也可以先在音乐页搜索。"
                },
            )
        }
        return startQueue(context, tracks, 0)
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
        val shuffled = pool.shuffled(Random(System.currentTimeMillis())).take(20)
        return startQueue(context, shuffled, 0)
    }

    suspend fun playTrack(context: Context, track: MusicTrack): PlayOutcome =
        startQueue(context, listOf(track), 0)

    suspend fun playList(context: Context, items: List<MusicTrack>, startIndex: Int): PlayOutcome {
        if (items.isEmpty()) return PlayOutcome(false, "列表是空的")
        return startQueue(context, items, startIndex)
    }

    suspend fun resolveSource(context: Context, track: MusicTrack): MusicSourceInfo? {
        for (quality in listOf("standard", "high", "low")) {
            val source = runCatching {
                MusicFreeEngine.mediaSource(context, track.platform, track, quality)
            }.getOrNull()
            if (source != null) return source
        }
        return null
    }

    private suspend fun startQueue(context: Context, items: List<MusicTrack>, preferred: Int): PlayOutcome {
        val ordered = items.take(50)
        if (ordered.isEmpty()) return PlayOutcome(false, "列表是空的")
        val start = preferred.coerceIn(0, ordered.lastIndex)
        val attempts = (start until ordered.size) + (0 until start)
        for (i in attempts) {
            val track = ordered[i]
            val source = resolveSource(context, track) ?: continue
            withContext(Dispatchers.Main) {
                MusicQueue.start(context, ordered, i, source)
            }
            val label = if (track.artist.isBlank()) track.title else "${track.title} — ${track.artist}"
            return PlayOutcome(
                true,
                "正在播放《$label》（${track.platform}），队列 ${ordered.size} 首",
                track,
            )
        }
        return PlayOutcome(
            false,
            "找到 ${ordered.size} 个结果，但没有拿到可播放地址。插件可能暂时失效，换一首或换个音源试试。",
        )
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
