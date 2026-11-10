package com.kinepulse.player.player.subtitle

import java.io.BufferedReader
import java.io.StringReader

object SubtitleParsers {

    fun parseAuto(rawContent: String, fileNameHint: String = ""): List<SubtitleCue> {
        val trimmed = rawContent.trimStart('\uFEFF', ' ', '\n', '\r')
        return when {
            trimmed.startsWith("WEBVTT", ignoreCase = true) || fileNameHint.endsWith(".vtt", true) ->
                parseWebVtt(trimmed)
            trimmed.contains("[Events]", ignoreCase = true) || fileNameHint.endsWith(".ass", true) || fileNameHint.endsWith(".ssa", true) ->
                parseAss(trimmed)
            else -> parseSrt(trimmed)
        }
    }

    fun parseSrt(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = content.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
        val timeRegex = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})")

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val timeLineIndex = lines.indexOfFirst { timeRegex.containsMatchIn(it) }
            if (timeLineIndex == -1) continue

            val match = timeRegex.find(lines[timeLineIndex]) ?: continue
            val startMs = toMs(match.groupValues[1], match.groupValues[2], match.groupValues[3], match.groupValues[4])
            val endMs = toMs(match.groupValues[5], match.groupValues[6], match.groupValues[7], match.groupValues[8])
            val textLines = lines.subList(timeLineIndex + 1, lines.size)
            val cleanText = stripTags(textLines.joinToString("\n"))
            if (cleanText.isNotEmpty() && endMs > startMs) {
                cues.add(SubtitleCue(startMs, endMs, cleanText))
            }
        }
        return cues.sortedBy { it.startTimeMs }
    }

    fun parseWebVtt(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.replace("\r\n", "\n").lines()
        val timeRegex = Regex("(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})[.](\\d{3})\\s*-->\\s*(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})[.](\\d{3})")
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            val match = timeRegex.find(line)
            if (match != null) {
                val h1 = match.groupValues[1].ifEmpty { "0" }
                val startMs = toMs(h1, match.groupValues[2], match.groupValues[3], match.groupValues[4])
                val h2 = match.groupValues[5].ifEmpty { "0" }
                val endMs = toMs(h2, match.groupValues[6], match.groupValues[7], match.groupValues[8])
                i++
                val textBuilder = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank()) {
                    textBuilder.add(lines[i].trim())
                    i++
                }
                val clean = stripTags(textBuilder.joinToString("\n"))
                if (clean.isNotEmpty()) {
                    cues.add(SubtitleCue(startMs, endMs, clean))
                }
            }
            i++
        }
        return cues.sortedBy { it.startTimeMs }
    }

    fun parseAss(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val reader = BufferedReader(StringReader(content))
        val assTimeRegex = Regex("(\\d+):(\\d{2}):(\\d{2})[.](\\d{2})")
        reader.forEachLine { raw ->
            val line = raw.trim()
            if (line.startsWith("Dialogue:", ignoreCase = true)) {
                val parts = line.substringAfter("Dialogue:").split(",", limit = 10)
                if (parts.size >= 10) {
                    val startMatch = assTimeRegex.find(parts[1].trim())
                    val endMatch = assTimeRegex.find(parts[2].trim())
                    if (startMatch != null && endMatch != null) {
                        val sMs = toMs(
                            startMatch.groupValues[1],
                            startMatch.groupValues[2],
                            startMatch.groupValues[3],
                            (startMatch.groupValues[4].toInt() * 10).toString()
                        )
                        val eMs = toMs(
                            endMatch.groupValues[1],
                            endMatch.groupValues[2],
                            endMatch.groupValues[3],
                            (endMatch.groupValues[4].toInt() * 10).toString()
                        )
                        val rawDialogue = parts[9]
                            .replace("\\N", "\n")
                            .replace("\\n", "\n")
                            .replace(Regex("\\{[^}]*\\}"), "")
                        val clean = stripTags(rawDialogue).trim()
                        if (clean.isNotEmpty()) {
                            cues.add(SubtitleCue(sMs, eMs, clean))
                        }
                    }
                }
            }
        }
        return cues.sortedBy { it.startTimeMs }
    }

    private fun toMs(h: String, m: String, s: String, ms: String): Long {
        return h.toLong() * 3600_000L + m.toLong() * 60_000L + s.toLong() * 1000L + ms.toLong()
    }

    private fun stripTags(input: String): String {
        return input.replace(Regex("<[^>]*>"), "").trim()
    }
}
