/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

private val LRC_TIMESTAMP_HINT = Regex("""\[\d{1,2}:\d{2}""")

private val WORD_TIMESTAMP_HINT = Regex("""<\w+:\d+\.\d+:\d+\.\d+""")

/**
 * Whether raw lyrics text appears to be time-synced (LRC-style), including when a BOM or
 * leading blank lines precede the first `[mm:ss.xx]` tag.
 */
fun lyricsTextLooksSynced(lyrics: String?): Boolean {
    if (lyrics.isNullOrBlank()) return false
    val t = lyrics.trim().removePrefix("\uFEFF").trimStart()
    if (t.startsWith('[')) return true
    return LRC_TIMESTAMP_HINT.containsMatchIn(t.take(4096))
}

fun lyricsTextLooksKaraoke(lyrics: String?): Boolean {
    if (lyrics.isNullOrBlank()) return false
    return WORD_TIMESTAMP_HINT.containsMatchIn(lyrics.take(4096))
}