package com.example.geonapominalka.util
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Тесты на реальных ссылках, которыми поделились Google Maps и Яндекс Карты (см.
 * обсуждение фичи шаринга места). Раскрытые (после редиректа) варианты — как отдаёт
 * реальный сервер на день написания теста; если формат ссылок изменится, эти тесты
 * первыми покажут поломку парсера.
 */
class LocationLinkParserTest {

    @Test
    fun `google resolved url - pin coordinates from data block`() {
        val url = "https://www.google.com/maps/place/Кирово-Чепецк,+Кировская+обл.,+613040/" +
            "data=!4m6!3m5!1s0x43fc92b8b6bfa6b1:0x227fca3529c74662!7e2!8m2!3d58.5431045!4d50.0213727" +
            "!18m1!1e1?coh=192189&entry=gps"
        val result = LocationLinkParser.parseFromUrl(url)
        assertNotNull(result)
        assertEquals(58.5431045, result!!.latitude, 0.000001)
        assertEquals(50.0213727, result.longitude, 0.000001)
        assertEquals("Кирово-Чепецк, Кировская обл., 613040", result.name)
    }

    @Test
    fun `yandex resolved url - whatshere point is lon,lat`() {
        val url = "https://yandex.ru/maps?whatshere%5Bpoint%5D=-0.12918717378576128%2C51.50908689754588" +
            "&whatshere%5Bzoom%5D=16.77047&ll=-0.12918717378576128%2C51.50908689715104&z=16.77047" +
            "&si=j46e23y6p3f87ay05ctfnaraem"
        val result = LocationLinkParser.parseFromUrl(url)
        assertNotNull(result)
        assertEquals(51.50908689754588, result!!.latitude, 0.000001)
        assertEquals(-0.12918717378576128, result.longitude, 0.000001)
    }

    @Test
    fun `google at-pattern fallback when no pin data present`() {
        val url = "https://www.google.com/maps/@55.7558,37.6173,15z"
        val result = LocationLinkParser.parseFromUrl(url)
        assertNotNull(result)
        assertEquals(55.7558, result!!.latitude, 0.000001)
        assertEquals(37.6173, result.longitude, 0.000001)
    }

    @Test
    fun `geo uri is parsed directly`() {
        val result = LocationLinkParser.parseGeoUri("geo:55.7558,37.6173?q=55.7558,37.6173(Точка)")
        assertNotNull(result)
        assertEquals(55.7558, result!!.latitude, 0.000001)
        assertEquals(37.6173, result.longitude, 0.000001)
    }

    @Test
    fun `zero coordinates are rejected`() {
        assertNull(LocationLinkParser.parseGeoUri("geo:0,0"))
    }

    @Test
    fun `out of range coordinates are rejected`() {
        assertNull(LocationLinkParser.parseFromUrl("https://example.com/?ll=200.0,50.0"))
    }

    @Test
    fun `bare coordinates in free text are found`() {
        val result = LocationLinkParser.parseFromText("Встретимся тут: 55.7558, 37.6173, ок?")
        assertNotNull(result)
        assertEquals(55.7558, result!!.latitude, 0.000001)
        assertEquals(37.6173, result.longitude, 0.000001)
    }

    @Test
    fun `extractLinks finds the http link inside surrounding text`() {
        val links = LocationLinkParser.extractLinks("Смотри: https://maps.app.goo.gl/Ujcjxkr8yMHTsX9J8 — рядом с домом.")
        assertEquals(listOf("https://maps.app.goo.gl/Ujcjxkr8yMHTsX9J8"), links)
    }
}
