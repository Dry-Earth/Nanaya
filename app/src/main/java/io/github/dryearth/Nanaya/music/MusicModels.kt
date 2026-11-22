package io.github.dryearth.Nanaya.music

import org.json.JSONObject

/** A track returned by a MusicFree plugin. [rawJson] keeps every plugin-specific field. */
data class MusicTrack(
    val platform: String,
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artwork: String?,
    val durationMs: Long,
    val rawJson: String,
) {
    val subtitle: String
        get() = buildString {
            if (artist.isNotBlank()) append(artist)
            if (album.isNotBlank() && album != title) {
                if (isNotEmpty()) append(" · ")
                append(album)
            }
            if (isNotEmpty()) append(" · ")
            append(platform)
        }
}

data class MusicSourceInfo(
    val url: String,
    val headers: Map<String, String>,
)

data class InstalledPlugin(
    val id: String,
    val platform: String,
    val version: String,
    val enabled: Boolean,
    val fileName: String,
    val sourceUrl: String? = null,
)

data class ParsedPluginMeta(
    val platform: String,
    val version: String,
    val supported: List<String>,
)

internal fun parseTrack(platform: String, item: JSONObject): MusicTrack? {
    val title = item.optString("title").ifBlank { item.optString("name") }.trim()
    if (title.isBlank()) return null
    val id = item.optString("id").ifBlank {
        item.optString("bvid").ifBlank { title }
    }
    val artist = item.optString("artist").ifBlank { item.optString("singer") }.trim()
    val album = item.optString("album").trim()
    val artwork = item.optString("artwork").ifBlank { null }
    val rawDuration = item.optLong("duration", 0L)
    val durationMs = when {
        rawDuration <= 0L -> 0L
        rawDuration > 300_000L -> rawDuration
        else -> rawDuration * 1000L
    }
    val raw = JSONObject(item.toString()).put("platform", platform)
    return MusicTrack(
        platform = platform,
        id = id,
        title = title,
        artist = artist,
        album = album,
        artwork = artwork,
        durationMs = durationMs,
        rawJson = raw.toString(),
    )
}

internal fun parseSource(json: String): MusicSourceInfo? {
    val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val url = obj.optString("url").trim()
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    val headers = LinkedHashMap<String, String>()
    obj.optJSONObject("headers")?.let { h ->
        val keys = h.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.isNotBlank() && !h.isNull(key)) headers[key] = h.optString(key)
        }
    }
    obj.optString("userAgent").takeIf { it.isNotBlank() }?.let {
        if (headers.keys.none { k -> k.equals("user-agent", ignoreCase = true) }) {
            headers["User-Agent"] = it
        }
    }
    return MusicSourceInfo(url, headers)
}
