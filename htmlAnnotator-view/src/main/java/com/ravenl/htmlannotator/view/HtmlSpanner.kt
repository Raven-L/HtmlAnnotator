package com.ravenl.htmlannotator.view

import android.graphics.Typeface
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.util.ArrayMap
import com.ravenl.htmlannotator.core.handler.AppendLinesHandler
import com.ravenl.htmlannotator.core.handler.ListItemHandler
import com.ravenl.htmlannotator.core.handler.ParagraphHandler
import com.ravenl.htmlannotator.core.handler.TagHandler
import com.ravenl.htmlannotator.core.toHtmlAnnotation
import com.ravenl.htmlannotator.core.util.Logger
import com.ravenl.htmlannotator.view.css.CSSSpannedHandler
import com.ravenl.htmlannotator.view.handler.LinkSpannedHandler
import com.ravenl.htmlannotator.view.handler.MultipleSpanHandler
import com.ravenl.htmlannotator.view.handler.PreSpannedHandler
import com.ravenl.htmlannotator.view.handler.SingleSpanHandler
import com.ravenl.htmlannotator.view.spans.CenterSpan
import com.ravenl.htmlannotator.view.styler.SpannedStyler
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class HtmlSpanner(
    private val cache: HtmlSpannerCache? = null,
    preTagHandlers: Map<String, TagHandler>? = defaultPreTagHandlers,
    preCSSHandlers: Map<String, CSSSpannedHandler>? = defaultPreCSSHandlers,
    val isStripExtraWhiteSpace: Boolean = defaultIsStripExtraWhiteSpace
) {

    private val handlers: MutableMap<String, TagHandler>

    private val cssHandlers: MutableMap<String, CSSSpannedHandler>

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            handlers = ArrayMap()
            cssHandlers = ArrayMap()
        } else {
            handlers = HashMap()
            cssHandlers = HashMap()
        }

        registerBuiltInHandlers(preTagHandlers)
        registerBuiltInCssHandlers(preCSSHandlers)
    }

    fun registerHandler(tagName: String, handler: TagHandler) {
        handlers[tagName] = handler
    }

    fun unregisterHandler(tagName: String) {
        handlers.remove(tagName)
    }

    fun registerCssHandler(property: String, handler: CSSSpannedHandler) {
        cssHandlers[property] = handler
    }

    fun unregisterCssHandler(property: String) {
        cssHandlers.remove(property)
    }

    suspend fun from(
        html: String,
        baseUri: String = "",
        getExternalCSS: (suspend (link: String) -> String)? = null
    ): Spannable {
        cache?.get(html)?.also { cacheValue ->
            return cacheValue
        }
        return from(Jsoup.parse(html, baseUri), getExternalCSS).also { value ->
            cache?.put(html, value)
        }
    }

    private suspend fun from(
        doc: Document,
        getExternalCSS: (suspend (link: String) -> String)? = null
    ): Spannable {
        val (body, tagStylers, cssBlocks) = toHtmlAnnotation(doc, handlers, logger, getExternalCSS)
        return SpannableStringBuilder(body).apply {
            tagStylers.forEach { src ->
                val styler = src as? SpannedStyler
                if (styler != null) {
                    styler.addStyle(this)
                } else {
                    logger.e(TAG) { "TagStyler is not SpannedStyler: $src" }
                }
            }

            val cssStylerList = ArrayList<SpannedStyler>()
            cssBlocks.forEach { block ->
                block.declarations.forEach { declaration ->
                    with(declaration) {
                        val cssHandler = cssHandlers[property]
                        if (cssHandler != null) {
                            cssHandler.addCssStyler(cssStylerList, block.start, block.end, value)
                        } else {
                            logger.w(TAG) { "unsupported css: $property: $value" }
                        }
                    }
                }
            }

            cssStylerList.forEach { styler ->
                styler.addStyle(this)
            }
        }
    }

    private fun registerBuiltInHandlers(pre: Map<String, TagHandler>?) {
        pre?.also { map ->
            handlers.putAll(map)
        }

        fun registerHandlerIfAbsent(tag: String, getHandler: () -> TagHandler) {
            if (pre?.containsKey(tag) != true) {
                registerHandler(tag, getHandler())
            }
        }

        val italicHandler by lazy {
            SingleSpanHandler(false) { StyleSpan(Typeface.ITALIC) }
        }

        registerHandlerIfAbsent("i") { italicHandler }
        registerHandlerIfAbsent("em") { italicHandler }
        registerHandlerIfAbsent("cite") { italicHandler }
        registerHandlerIfAbsent("dfn") { italicHandler }

        val boldHandler by lazy {
            SingleSpanHandler(false) { StyleSpan(Typeface.BOLD) }
        }

        registerHandlerIfAbsent("b") { boldHandler }
        registerHandlerIfAbsent("strong") { boldHandler }

        val marginHandler by lazy {
            SingleSpanHandler { LeadingMarginSpan.Standard(30) }
        }
        registerHandlerIfAbsent("blockquote") { marginHandler }
        registerHandlerIfAbsent("ul") { marginHandler }
        registerHandlerIfAbsent("ol") { marginHandler }

        registerHandlerIfAbsent("li") {
            ListItemHandler()
        }

        registerHandlerIfAbsent("br") { AppendLinesHandler(isStripExtraWhiteSpace, 1) }


        val pHandler by lazy(boldHandler) { ParagraphHandler() }

        registerHandlerIfAbsent("p") { pHandler }
        registerHandlerIfAbsent("div") { pHandler }


        registerHandlerIfAbsent("h1") {
            MultipleSpanHandler { listOf(StyleSpan(Typeface.BOLD), RelativeSizeSpan(2f)) }
        }
        registerHandlerIfAbsent("h2") {
            MultipleSpanHandler { listOf(StyleSpan(Typeface.BOLD), RelativeSizeSpan(1.5f)) }
        }
        registerHandlerIfAbsent("h3") {
            MultipleSpanHandler { listOf(StyleSpan(Typeface.BOLD), RelativeSizeSpan(1.17f)) }
        }
        registerHandlerIfAbsent("h4") {
            MultipleSpanHandler { listOf(StyleSpan(Typeface.BOLD), RelativeSizeSpan(1f)) }
        }
        registerHandlerIfAbsent("h5") {
            MultipleSpanHandler { listOf(StyleSpan(Typeface.BOLD), RelativeSizeSpan(0.83f)) }
        }
        registerHandlerIfAbsent("h6") {
            MultipleSpanHandler { listOf(StyleSpan(Typeface.BOLD), RelativeSizeSpan(0.67f)) }
        }


        registerHandlerIfAbsent("tt") { SingleSpanHandler { TypefaceSpan("monospace") } }


        registerHandlerIfAbsent("pre") { PreSpannedHandler(isStripExtraWhiteSpace) }


        registerHandlerIfAbsent("big") {
            MultipleSpanHandler(false) { listOf(StyleSpan(Typeface.BOLD), RelativeSizeSpan(1.25f)) }
        }

        registerHandlerIfAbsent("small") {
            MultipleSpanHandler(false) { listOf(StyleSpan(Typeface.BOLD), RelativeSizeSpan(0.8f)) }
        }


        registerHandlerIfAbsent("sub") {
            MultipleSpanHandler(false) { listOf(SubscriptSpan(), RelativeSizeSpan(0.7f)) }
        }

        registerHandlerIfAbsent("sup") {
            MultipleSpanHandler(false) { listOf(SuperscriptSpan(), RelativeSizeSpan(0.7f)) }
        }


        registerHandlerIfAbsent("center") {
            SingleSpanHandler { CenterSpan() }
        }


        registerHandlerIfAbsent("a") { LinkSpannedHandler() }

        registerHandlerIfAbsent("span") { TagHandler() }
    }

    private fun registerBuiltInCssHandlers(pre: Map<String, CSSSpannedHandler>?) {
        pre?.also { map ->
            cssHandlers.putAll(map)
        }

        fun registerHandlerIfAbsent(tag: String, getHandler: () -> CSSSpannedHandler) {
            if (pre?.containsKey(tag) != true) {
                registerCssHandler(tag, getHandler())
            }
        }
    }

    companion object {
        private const val TAG = "HtmlSpanner"

        var logger: Logger = Logger()
        var defaultPreTagHandlers: Map<String, TagHandler>? = null
        var defaultPreCSSHandlers: Map<String, CSSSpannedHandler>? = null
        var defaultIsStripExtraWhiteSpace: Boolean = true
    }
}