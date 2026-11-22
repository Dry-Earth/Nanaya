package io.github.dryearth.Nanaya.music

import android.content.Context

object MusicAssistant {
    suspend fun handle(context: Context, intent: MusicIntent): String = when (intent) {
        is MusicIntent.Play -> MusicRepository.playQuery(context, intent.query).message
        MusicIntent.Random -> MusicRepository.playRandom(context).message
        MusicIntent.Pause -> {
            if (MusicPlayback.nowPlaying.value == null) {
                "现在没有在播放音乐。"
            } else {
                MusicPlayback.pause()
                "已暂停。"
            }
        }
        MusicIntent.Resume -> {
            if (MusicPlayback.nowPlaying.value == null) {
                "还没有播放过歌曲。可以先说「播放稻香」。"
            } else {
                MusicPlayback.resume()
                "继续播放。"
            }
        }
        MusicIntent.Stop -> {
            if (MusicPlayback.nowPlaying.value == null) {
                "现在没有在播放音乐。"
            } else {
                MusicPlayback.stop()
                "已停止播放。"
            }
        }
    }
}
