package com.kinepulse.player

import com.kinepulse.player.data.media.VideoGrouper
import com.kinepulse.player.player.subtitle.SubtitleParsers
import com.kinepulse.player.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreUnitTests {

    @Test
    fun testSrtParser() {
        val sampleSrt = """
            1
            00:00:01,500 --> 00:00:04,000
            <i>Hello KinePulse!</i>
            Second line

            2
            00:01:00,000 --> 00:01:02,250
            Cue Two
        """.trimIndent()

        val cues = SubtitleParsers.parseSrt(sampleSrt)
        assertEquals(2, cues.size)
        assertEquals(1500L, cues[0].startTimeMs)
        assertEquals(4000L, cues[0].endTimeMs)
        assertEquals("Hello KinePulse!\nSecond line", cues[0].text)
    }

    @Test
    fun testWebVttParser() {
        val sampleVtt = """
            WEBVTT

            00:05.000 --> 00:08.500
            Welcome to WebVTT
        """.trimIndent()
        val cues = SubtitleParsers.parseWebVtt(sampleVtt)
        assertEquals(1, cues.size)
        assertEquals(5000L, cues[0].startTimeMs)
        assertEquals(8500L, cues[0].endTimeMs)
        assertEquals("Welcome to WebVTT", cues[0].text)
    }

    @Test
    fun testAssParser() {
        val sampleAss = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:10.50,0:00:15.00,Default,,0,0,0,,{\\pos(400,570)}Styled ASS\\NLine 2
        """.trimIndent()
        val cues = SubtitleParsers.parseAss(sampleAss)
        assertEquals(1, cues.size)
        assertEquals(10_500L, cues[0].startTimeMs)
        assertEquals(15_000L, cues[0].endTimeMs)
        assertEquals("Styled ASS\nLine 2", cues[0].text)
    }

    @Test
    fun testTimeFormattingAndParsing() {
        assertEquals("02:05", TimeUtils.formatMs(125_000L))
        assertEquals("01:01:01", TimeUtils.formatMs(3_661_000L))
        assertEquals(750_000L, TimeUtils.parseHhMmSsToMs("00:12:30"))
    }

    @Test
    fun testVideoGroupPrefixExtraction() {
        assertEquals("Planet Earth", VideoGrouper.extractPrefixKey("Planet_Earth_S01E01.mp4"))
        assertEquals("Nature Doc", VideoGrouper.extractPrefixKey("Nature Doc Part 2"))
    }
}
