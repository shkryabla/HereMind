package com.example.geonapominalka.util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Раскрывает короткие ссылки картографических приложений (maps.app.goo.gl,
 * yandex.ru/maps/-/… и т.п.) до финального адреса, переходя по заголовкам Location
 * вручную (HEAD-запрос, без скачивания тела страницы — оно всё равно пустое без
 * JavaScript). Отдельно обрабатывает страницу согласия Google (consent.google.com) —
 * настоящий адрес лежит в параметре continue=.
 */
object LinkRedirectResolver {
    private const val MAX_REDIRECTS = 5
    private const val TIMEOUT_MS = 8000

    /** Возвращает финальный URL после всех редиректов, либо исходный при любой ошибке сети. */
    suspend fun resolveFinalUrl(startUrl: String): String = withContext(Dispatchers.IO) {
        var current = startUrl
        repeat(MAX_REDIRECTS) {
            val connection = try {
                (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "HEAD"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) GeoNapominalka")
                }
            } catch (e: Exception) {
                return@withContext current
            }
            val (code, location) = try {
                connection.responseCode to connection.getHeaderField("Location")
            } catch (e: Exception) {
                return@withContext current
            } finally {
                connection.disconnect()
            }
            if (code !in 300..399 || location == null) return@withContext current
            current = resolveConsentRedirect(location) ?: location
        }
        current
    }

    /** consent.google.com отдаёт форму согласия вместо реальной страницы — настоящий адрес в ?continue= */
    private fun resolveConsentRedirect(url: String): String? {
        if (!url.contains("consent.google.com")) return null
        val marker = "continue="
        val idx = url.indexOf(marker)
        if (idx == -1) return null
        val raw = url.substring(idx + marker.length).substringBefore('&')
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()
    }
}
