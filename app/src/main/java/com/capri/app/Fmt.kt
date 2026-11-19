package com.capri.app

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

/** Small text helpers for her *action* emotes. */
object Fmt {
    private val action = Regex("\\*([^*]+)\\*")
    private val spaces = Regex("\\s+")

    /** Shows *actions* in italics with a soft colour, without the asterisks. */
    fun styled(s: String): CharSequence {
        val sb = SpannableStringBuilder()
        var last = 0
        for (m in action.findAll(s)) {
            sb.append(s.substring(last, m.range.first))
            val start = sb.length
            sb.append(m.groupValues[1])
            sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(
                ForegroundColorSpan(Color.parseColor("#C9A7FF")),
                start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            last = m.range.last + 1
        }
        sb.append(s.substring(last))
        return sb
    }

    /** Removes *actions* so text-to-speech doesn't read them out. */
    fun stripActions(s: String): String = s.replace(action, " ").replace(spaces, " ").trim()
}
