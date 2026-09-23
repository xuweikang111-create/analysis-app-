package com.lobsterai.app.data.web

import com.lobsterai.app.domain.model.WebPageContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebContentExtractor @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun fetch(rawUrl: String): WebPageContent = withContext(Dispatchers.IO) {
        val url = normalizeUrl(rawUrl)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36 LobsterAI/1.0")
            .header("Accept", "text/html,application/xhtml+xml")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("网页请求失败 HTTP ${response.code}")
            val html = response.body?.string().orEmpty()
            require(html.isNotBlank()) { "网页内容为空" }
            val doc = Jsoup.parse(html, response.request.url.toString())
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?.takeIf { it.isNotBlank() } ?: doc.title().ifBlank { url }
            val description = doc.selectFirst("meta[name=description]")?.attr("content")
                ?.takeIf { it.isNotBlank() }
                ?: doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
            val text = extractReadableText(doc.body())
            WebPageContent(url, title.trim(), description.trim(), text)
        }
    }

    fun splitLongText(text: String, maxChars: Int = 12_000): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val paragraphs = text.split(Regex("\\n{2,}"))
        val chunks = mutableListOf<String>()
        var buffer = StringBuilder()
        for (paragraph in paragraphs) {
            if (buffer.length + paragraph.length + 2 > maxChars && buffer.isNotEmpty()) {
                chunks += buffer.toString().trim()
                buffer = StringBuilder()
            }
            if (paragraph.length > maxChars) {
                paragraph.chunked(maxChars).forEach { part ->
                    if (buffer.isNotEmpty()) {
                        chunks += buffer.toString().trim()
                        buffer = StringBuilder()
                    }
                    chunks += part
                }
            } else {
                buffer.append(paragraph).append("\n\n")
            }
        }
        if (buffer.isNotEmpty()) chunks += buffer.toString().trim()
        return chunks.filter { it.isNotBlank() }
    }

    private fun extractReadableText(body: Element?): String {
        if (body == null) return ""
        body.select("script,style,noscript,svg,canvas,iframe,form,nav,footer,header,aside,button,input").remove()

        val obvious = body.selectFirst("article")
            ?: body.selectFirst("main")
            ?: body.selectFirst("[role=main]")
        val root = obvious ?: scoreBestContainer(body)

        root.select(".advertisement,.ads,.ad,.social,.share,.comments,.comment,.related,.recommend,.sidebar").remove()
        val paragraphs = root.select("h1,h2,h3,p,li,pre,blockquote")
            .map { it.text().replace(Regex("\\s+"), " ").trim() }
            .filter { it.length >= 20 || it.startsWith("#") }
            .distinct()
        return paragraphs.joinToString("\n\n").take(200_000)
    }

    private fun scoreBestContainer(body: Element): Element {
        val scores = mutableMapOf<Element, Double>()
        body.select("p,pre,td").forEach { node ->
            val text = node.text().trim()
            if (text.length < 40) return@forEach
            val parent = node.parent() ?: return@forEach
            val grand = parent.parent()
            val base = 1.0 + text.count { it == ',' || it == '，' || it == '。' } + minOf(text.length / 100.0, 3.0)
            scores[parent] = (scores[parent] ?: classWeight(parent)) + base
            if (grand != null) scores[grand] = (scores[grand] ?: classWeight(grand)) + base / 2.0
        }
        return scores.maxByOrNull { (element, score) -> score * (1.0 - linkDensity(element)) }?.key ?: body
    }

    private fun classWeight(element: Element): Double {
        val signature = (element.className() + " " + element.id()).lowercase()
        var score = 0.0
        if (POSITIVE.any { signature.contains(it) }) score += 25.0
        if (NEGATIVE.any { signature.contains(it) }) score -= 25.0
        return score
    }

    private fun linkDensity(element: Element): Double {
        val total = element.text().length.coerceAtLeast(1)
        val link = element.select("a").sumOf { it.text().length }
        return (link.toDouble() / total).coerceIn(0.0, 1.0)
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        val url = if (trimmed.startsWith("https://")) trimmed else if (trimmed.startsWith("http://")) {
            "https://" + trimmed.removePrefix("http://")
        } else {
            "https://$trimmed"
        }
        require(URL_REGEX.matches(url)) { "不是有效 URL" }
        return url
    }

    private companion object {
        val URL_REGEX = Regex("https://[^\\s]+", RegexOption.IGNORE_CASE)
        val POSITIVE = listOf("article", "body", "content", "entry", "main", "page", "post", "text", "blog", "story")
        val NEGATIVE = listOf("ad-", "advert", "comment", "footer", "header", "menu", "nav", "related", "share", "sidebar", "social")
    }
}