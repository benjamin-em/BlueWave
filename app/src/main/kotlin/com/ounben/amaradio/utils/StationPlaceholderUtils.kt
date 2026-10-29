package com.ounben.amaradio.utils

import java.util.*

/**
 * Utility class to generate dynamic placeholders for radio stations.
 */
object StationPlaceholderUtils {

    // Defined Material Design color palette (AARRGGBB)
    private val MATERIAL_PALETTE = longArrayOf(
        0xFF2196F3, // Blue
        0xFFF44336, // Red
        0xFF4CAF50, // Green
        0xFFFF9800, // Orange
        0xFF9C27B0, // Purple
        0xFF009688, // Teal
        0xFFE91E63  // Pink
    )

    /** How a placeholder should be drawn: a dial frequency, or the name over two lines. */
    sealed class Placeholder {
        /** "FM" sitting above-left, the frequency larger and to the right. */
        data class Frequency(val band: String, val value: String) : Placeholder()
        /** Up to 6 characters split over two lines, the first line larger. */
        data class TwoLine(val top: String, val bottom: String) : Placeholder()
        /** Single short token (latin initials, or "?"). */
        data class Single(val text: String) : Placeholder()
    }

    // FM97.1 / FM 97.1 / 97.1 FM / 97.1MHz — the digits must be tied to a band
    // marker, so a stray number in a name is not mistaken for a frequency.
    private val FREQ_PREFIXED = Regex("""(?i)\b(FM|AM)\s*(\d{2,4}(?:[.,]\d{1,2})?)""")
    private val FREQ_SUFFIXED = Regex("""(?i)(\d{2,4}(?:[.,]\d{1,2})?)\s*(FM|AM|MHZ|兆赫)""")

    /**
     * Decides how to render the placeholder for a station name.
     */
    fun extractPlaceholder(name: String?): Placeholder {
        val input = name?.trim() ?: return Placeholder.Single("?")
        if (input.isEmpty()) return Placeholder.Single("?")

        // 1. A dial frequency wins, whichever side the band marker sits on.
        FREQ_PREFIXED.find(input)?.let { m ->
            return Placeholder.Frequency(
                band = m.groupValues[1].uppercase(Locale.ROOT),
                value = m.groupValues[2].replace(',', '.')
            )
        }
        FREQ_SUFFIXED.find(input)?.let { m ->
            val marker = m.groupValues[2].uppercase(Locale.ROOT)
            val band = if (marker == "AM") "AM" else "FM"
            return Placeholder.Frequency(
                band = band,
                value = m.groupValues[1].replace(',', '.')
            )
        }

        // 2. CJK names: up to 6 characters over two lines, split so the top line
        //    carries the place/brand ("武汉" / "新闻广播").
        val cjk = input.filter { isCjk(it) }
        if (cjk.length >= 2) {
            val take = cjk.take(6)
            val top: String
            val bottom: String
            if (take.length <= 3) {
                top = take
                bottom = ""
            } else {
                // 4 -> 2/2, 5 -> 2/3, 6 -> 2/4 keeps the leading place name intact.
                val head = if (take.length >= 5) 2 else take.length / 2
                top = take.take(head)
                bottom = take.drop(head)
            }
            return Placeholder.TwoLine(top, bottom)
        }

        // 3. Latin fallback: initials of the first two words, else first two letters.
        val words = input.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val text = if (words.size >= 2) {
            (words[0].take(1) + words[1].take(1)).uppercase(Locale.ROOT)
        } else {
            input.take(2).uppercase(Locale.ROOT)
        }
        return Placeholder.Single(text)
    }

    private fun isCjk(c: Char): Boolean = c.code in 0x4E00..0x9FFF

    /**
     * Flattened placeholder text, for callers that can only draw a single string.
     */
    fun extractPlaceholderText(name: String?): String =
        when (val p = extractPlaceholder(name)) {
            is Placeholder.Frequency -> p.value
            is Placeholder.TwoLine -> p.top + p.bottom
            is Placeholder.Single -> p.text
        }

    /**
     * Determines a deterministic color based on the stationUuid.
     */
    fun getPlaceholderColor(stationUuid: String?): Long {
        if (stationUuid.isNullOrEmpty()) return MATERIAL_PALETTE[0]

        // Ensure a positive index using bitwise AND with 0x7FFFFFFF
        val hash = stationUuid.hashCode() and 0x7FFFFFFF
        val index = hash % MATERIAL_PALETTE.size
        
        return MATERIAL_PALETTE[index]
    }

    /**
     * Creates a placeholder bitmap with text and background color.
     */
    fun createPlaceholderBitmap(name: String, uuid: String, size: Int = 512): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Background color
        val color = getPlaceholderColor(uuid).toInt()
        canvas.drawColor(color)
        
        // Text
        val text = extractPlaceholderText(name)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            this.textSize = size / 2.5f
            this.textAlign = android.graphics.Paint.Align.CENTER
            this.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(text, xPos, yPos, paint)
        
        return bitmap
    }
}
