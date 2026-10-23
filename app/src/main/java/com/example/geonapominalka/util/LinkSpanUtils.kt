package com.example.geonapominalka.util
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView

/** Делает отдельные подстроки текста кликабельными ссылками, открывающими браузер. */
object LinkSpanUtils {
    /** @param links карта "подстрока в тексте" -> "URL, который откроется по тапу на неё". */
    fun applyLinks(view: TextView, text: String, links: Map<String, String>) {
        val spannable = SpannableString(text)
        for ((target, url) in links) {
            val start = text.indexOf(target)
            if (start == -1) continue
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        widget.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    override fun updateDrawState(ds: TextPaint) {
                        ds.isUnderlineText = true
                    }
                },
                start, start + target.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        view.text = spannable
        view.movementMethod = LinkMovementMethod.getInstance()
        view.highlightColor = Color.TRANSPARENT
    }
}
