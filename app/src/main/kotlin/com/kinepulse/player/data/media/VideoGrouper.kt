package com.kinepulse.player.data.media

sealed class VideoListEntry {
    data class SingleVideo(val video: VideoItem) : VideoListEntry()
    data class VideoGroup(val prefix: String, val videos: List<VideoItem>) : VideoListEntry()
    data class SectionHeader(val title: String) : VideoListEntry()
}

object VideoGrouper {
    fun groupVideos(videos: List<VideoItem>, enableGrouping: Boolean = true): List<VideoListEntry> {
        if (!enableGrouping || videos.size < 2) {
            return videos.map { VideoListEntry.SingleVideo(it) }
        }
        val buckets = linkedMapOf<String, MutableList<VideoItem>>()
        for (video in videos) {
            val key = extractPrefixKey(video.title)
            buckets.getOrPut(key) { mutableListOf() }.add(video)
        }
        val entries = mutableListOf<VideoListEntry>()
        for ((key, items) in buckets) {
            if (items.size >= 2 && key.length >= 5) {
                entries.add(VideoListEntry.VideoGroup(key, items))
            } else {
                items.forEach { entries.add(VideoListEntry.SingleVideo(it)) }
            }
        }
        return entries
    }

    fun extractPrefixKey(title: String): String {
        val cleaned = title.replace(Regex("[_\\-.]+"), " ").trim()
        val episodePattern = Regex("^(.*?)(?:\\s+(?:S\\d+E\\d+|E\\d+|Ep\\s*\\d+|Part\\s*\\d+|\\d+)).*$", RegexOption.IGNORE_CASE)
        val match = episodePattern.find(cleaned)
        if (match != null && match.groupValues[1].length >= 4) {
            return match.groupValues[1].trim()
        }
        val words = cleaned.split(" ")
        return if (words.size >= 2) "${words[0]} ${words[1]}" else cleaned.take(8)
    }
}
