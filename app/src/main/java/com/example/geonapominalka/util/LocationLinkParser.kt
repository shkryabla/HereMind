package com.example.geonapominalka.util
import java.net.URLDecoder

/**
 * Универсальный разбор координат из ссылок и текста, которыми делятся картографические
 * приложения (Google Maps, Яндекс Карты, geo:-ссылки других навигаторов и т.п.).
 *
 * Формат этих ссылок нигде официально не документирован и может отличаться версией
 * приложения/регионом — поэтому механизм строится не как список частных случаев под
 * конкретные приложения, а как набор универсальных паттернов (координаты в параметрах
 * !3d!4d, @, ll, q, pt, whatshere[point], geo:), с явным порядком lat/lng для каждого
 * хоста. Новый навигатор добавляется одной записью в HOST_ORDER, без переписывания
 * остального кода.
 */
object LocationLinkParser {

    data class ParsedLocation(val latitude: Double, val longitude: Double, val name: String? = null)

    private enum class Order { LAT_LON, LON_LAT }

    /** Фрагмент хоста -> порядок координат в параметрах ll=/whatshere[point]=/q= и т.п.
     *  Google, OSM и большинство остальных сервисов используют lat,lon — не перечислены,
     *  это порядок по умолчанию. */
    private val HOST_ORDER = listOf(
        "yandex." to Order.LON_LAT,
        "2gis." to Order.LON_LAT
    )

    private val PIN_PATTERN = Regex("""!3d(-?\d{1,3}\.\d+)!4d(-?\d{1,3}\.\d+)""")              // Google: сама точка (lat, lng)
    private val PT_PATTERN = Regex("""[?&]pt=(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""")              // Яндекс: метка (lon, lat)
    private val AT_PATTERN = Regex("""@(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""")                    // Google: центр карты (lat, lng)
    private val QUERY_PARAM_PATTERN =
        Regex("""[?&](?:ll|q|query|whatshere\[point\]|mlat)=(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""")
    private val PLACE_NAME_PATTERN = Regex("""/maps/place/([^/@?]+)""")
    private val BARE_PAIR_PATTERN = Regex("""(-?\d{1,3}\.\d{3,})\s*[,;]\s*(-?\d{1,3}\.\d{3,})""")
    private val LINK_PATTERN = Regex("""https?://\S+""")

    fun extractLinks(text: String): List<String> =
        LINK_PATTERN.findAll(text).map { it.value.trimEnd('.', ',', ')', '>') }.toList()

    /** geo:lat,lng[?q=...] (RFC 5870) — используют OsmAnd, Organic Maps и другие навигаторы. */
    fun parseGeoUri(uri: String): ParsedLocation? {
        if (!uri.startsWith("geo:")) return null
        val parts = uri.removePrefix("geo:").substringBefore("?").split(",")
        if (parts.size < 2) return null
        return validOrNull(parts[0], parts[1].trim())
    }

    /** Разбор ссылки (после раскрытия редиректов) — паттерны пробуются по убыванию точности. */
    fun parseFromUrl(url: String): ParsedLocation? {
        val decoded = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)
        val name = PLACE_NAME_PATTERN.find(decoded)?.groupValues?.get(1)?.replace('+', ' ')

        PIN_PATTERN.find(decoded)?.let { m ->
            val (lat, lng) = m.destructured
            validOrNull(lat, lng, name)?.let { return it }
        }
        PT_PATTERN.find(decoded)?.let { m ->
            val (lon, lat) = m.destructured
            validOrNull(lat, lon, name)?.let { return it }
        }
        val order = HOST_ORDER.firstOrNull { (host, _) -> decoded.contains(host) }?.second ?: Order.LAT_LON
        QUERY_PARAM_PATTERN.find(decoded)?.let { m ->
            val (first, second) = m.destructured
            val result = if (order == Order.LON_LAT) validOrNull(second, first, name) else validOrNull(first, second, name)
            if (result != null) return result
        }
        AT_PATTERN.find(decoded)?.let { m ->
            val (lat, lng) = m.destructured
            validOrNull(lat, lng, name)?.let { return it }
        }
        return null
    }

    /** Голые координаты прямо в тексте сообщения (не внутри ссылки). */
    fun parseFromText(text: String): ParsedLocation? {
        BARE_PAIR_PATTERN.find(text)?.let { m ->
            val (a, b) = m.destructured
            validOrNull(a, b)?.let { return it }
        }
        return null
    }

    private fun validOrNull(lat: String, lng: String, name: String? = null): ParsedLocation? =
        validOrNull(lat.toDoubleOrNull() ?: return null, lng.toDoubleOrNull() ?: return null, name)

    private fun validOrNull(lat: Double, lng: Double, name: String?): ParsedLocation? {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        if (lat == 0.0 && lng == 0.0) return null
        return ParsedLocation(lat, lng, name)
    }
}
