package io.github.dryearth.Nanaya.music

sealed interface MusicIntent {
    data class Play(val query: String) : MusicIntent
    data object Random : MusicIntent
    data object Pause : MusicIntent
    data object Resume : MusicIntent
    data object Stop : MusicIntent
}

/**
 * Fully offline command parser for the assistant. No cloud model is involved.
 */
object MusicIntentParser {
    private val pauseWords = setOf(
        "暂停", "暂停播放", "先暂停", "停一下", "停下", "别唱了", "安静一下",
    )
    private val resumeWords = setOf(
        "继续", "继续播放", "接着放", "接着播", "恢复播放", "继续放",
    )
    private val stopWords = setOf(
        "停止", "停止播放", "停止音乐", "关闭音乐", "关掉音乐", "别放了", "退出播放",
    )

    private val prefixes = listOf(
        "我想听", "我想听听", "我想播放", "帮我播放", "给我播放", "请播放",
        "播放一下", "播放", "放一首", "放首", "放一下", "放点", "放个",
        "来一首", "来首", "来点", "来一个", "点一首", "点首", "点歌",
        "听听", "听一下", "听首", "听一首", "放",
    ).sortedByDescending { it.length }

    private val genericLeft = setOf(
        "", "歌", "首歌", "歌曲", "音乐", "点歌", "一些歌", "一点音乐", "点音乐",
        "首歌来听", "歌来听", "音乐来听", "歌听听", "随便", "随便听", "随便放",
        "什么都行", "都行", "你看着办", "来", "好", "好听", "点的",
    )

    fun parse(raw: String): MusicIntent? {
        val text = raw.trim().trim('。', '，', ',', '！', '!', '？', '?', '、')
        if (text.isBlank()) return null
        val compact = text.replace(Regex("\\s+"), "")

        if (compact in pauseWords) return MusicIntent.Pause
        if (compact in resumeWords) return MusicIntent.Resume
        if (compact in stopWords) return MusicIntent.Stop

        var body: String? = null
        for (prefix in prefixes) {
            if (compact.startsWith(prefix)) {
                body = compact.removePrefix(prefix)
                break
            }
        }
        if (body == null) {
            // "我想播放稻香了" / "能不能播放稻香" style sentences.
            val marker = "播放"
            val index = compact.indexOf(marker)
            if (index >= 0 && index <= 6) {
                body = compact.substring(index + marker.length)
            }
        }
        if (body == null) return null

        body = clean(body)
        if (body.isEmpty() || body in genericLeft) return MusicIntent.Random
        if (body.contains("器") && body.contains("怎么")) return null
        return MusicIntent.Play(body)
    }

    private fun clean(value: String): String {
        var body = value.trim()
        val lead = listOf("请", "帮我", "给我", "麻烦", "我想", "想要", "想", "你", "一下")
        var changed = true
        while (changed) {
            changed = false
            for (prefix in lead) {
                if (body.startsWith(prefix)) {
                    body = body.removePrefix(prefix)
                    changed = true
                }
            }
        }
        val tail = listOf(
            "来听听", "来听", "听听", "一下吧", "一下", "吧", "好吗", "可以吗",
            "谢谢", "好不好", "行吗", "行不行", "的歌", "歌曲", "这首歌", "首",
            "音乐", "听听看", "看看",
        )
        changed = true
        while (changed) {
            changed = false
            for (suffix in tail) {
                if (body.endsWith(suffix) && body.length > suffix.length) {
                    body = body.removeSuffix(suffix)
                    changed = true
                }
            }
        }
        if (body == "歌" || body == "音乐" || body == "歌曲") return ""
        return body
    }
}
