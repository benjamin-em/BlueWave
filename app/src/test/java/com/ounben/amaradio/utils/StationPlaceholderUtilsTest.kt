package com.ounben.amaradio.utils

import com.ounben.amaradio.utils.StationPlaceholderUtils.Placeholder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StationPlaceholderUtilsTest {

    private fun freq(name: String): Placeholder.Frequency =
        StationPlaceholderUtils.extractPlaceholder(name) as Placeholder.Frequency

    private fun twoLine(name: String): Placeholder.TwoLine =
        StationPlaceholderUtils.extractPlaceholder(name) as Placeholder.TwoLine

    @Test
    fun `frequency with FM prefix`() {
        val p = freq("武汉经典音乐广播FM97.1")
        assertEquals("FM", p.band)
        assertEquals("97.1", p.value)
    }

    @Test
    fun `frequency with space after band`() {
        val p = freq("湖北之声 FM 104.6")
        assertEquals("FM", p.band)
        assertEquals("104.6", p.value)
    }

    @Test
    fun `frequency with trailing band marker`() {
        val p = freq("武汉交通广播 92.7 FM")
        assertEquals("FM", p.band)
        assertEquals("92.7", p.value)
    }

    @Test
    fun `frequency with MHz marker normalises to FM`() {
        val p = freq("中国之声 106.1MHz")
        assertEquals("FM", p.band)
        assertEquals("106.1", p.value)
    }

    @Test
    fun `AM band is preserved`() {
        val p = freq("Radio AM 1080")
        assertEquals("AM", p.band)
        assertEquals("1080", p.value)
    }

    @Test
    fun `comma decimal is normalised to dot`() {
        val p = freq("Radio FM 97,1")
        assertEquals("97.1", p.value)
    }

    @Test
    fun `bare number without band marker is not a frequency`() {
        // The old implementation would have rendered "1" here.
        val p = StationPlaceholderUtils.extractPlaceholder("经典音乐广播1台")
        assertTrue(p is Placeholder.TwoLine, "expected two-line, got $p")
    }

    @Test
    fun `six character name splits two over four`() {
        val p = twoLine("武汉新闻广播")
        assertEquals("武汉", p.top)
        assertEquals("新闻广播", p.bottom)
    }

    @Test
    fun `five character name splits two over three`() {
        val p = twoLine("湖北音乐台")
        assertEquals("湖北", p.top)
        assertEquals("音乐台", p.bottom)
    }

    @Test
    fun `four character name splits evenly`() {
        val p = twoLine("中国之声")
        assertEquals("中国", p.top)
        assertEquals("之声", p.bottom)
    }

    @Test
    fun `short name stays on one line`() {
        val p = twoLine("武汉")
        assertEquals("武汉", p.top)
        assertEquals("", p.bottom)
    }

    @Test
    fun `long name is capped at six characters`() {
        val p = twoLine("武汉经典音乐广播电台")
        assertEquals("武汉", p.top)
        assertEquals("经典音乐", p.bottom)
        assertEquals(6, (p.top + p.bottom).length)
    }

    @Test
    fun `cjk is extracted even when mixed with latin`() {
        val p = twoLine("Wuhan 武汉新闻广播")
        assertEquals("武汉", p.top)
        assertEquals("新闻广播", p.bottom)
    }

    @Test
    fun `latin name falls back to initials`() {
        val p = StationPlaceholderUtils.extractPlaceholder("BBC World Service") as Placeholder.Single
        assertEquals("BW", p.text)
    }

    @Test
    fun `single latin word takes first two letters`() {
        val p = StationPlaceholderUtils.extractPlaceholder("Jazzradio") as Placeholder.Single
        assertEquals("JA", p.text)
    }

    @Test
    fun `blank name yields question mark`() {
        val p = StationPlaceholderUtils.extractPlaceholder("   ") as Placeholder.Single
        assertEquals("?", p.text)
    }

    @Test
    fun `null name yields question mark`() {
        val p = StationPlaceholderUtils.extractPlaceholder(null) as Placeholder.Single
        assertEquals("?", p.text)
    }

    @Test
    fun `flattened text stays available for bitmap callers`() {
        assertEquals("97.1", StationPlaceholderUtils.extractPlaceholderText("武汉经典音乐广播FM97.1"))
        assertEquals("武汉新闻广播", StationPlaceholderUtils.extractPlaceholderText("武汉新闻广播"))
    }
}
