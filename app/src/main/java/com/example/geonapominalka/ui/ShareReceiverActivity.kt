package com.example.geonapominalka.ui
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.geonapominalka.R
import com.example.geonapominalka.databinding.ActivityShareReceiverBinding
import com.example.geonapominalka.util.Constants
import com.example.geonapominalka.util.LinkRedirectResolver
import com.example.geonapominalka.util.LocationLinkParser
import com.example.geonapominalka.util.NominatimGeocoder
import kotlinx.coroutines.launch

/**
 * Принимает шаринг местоположения из других приложений (Google/Яндекс Карты и т.п. —
 * пункт "Поделиться" -> напомниТут) и geo:-ссылки навигаторов (OsmAnd, Organic Maps),
 * извлекает координаты (см. LocationLinkParser) и открывает создание задачи с уже
 * подставленной точкой. Экран без постоянного UI — только индикатор на время раскрытия
 * коротких ссылок, дальше сразу передаёт управление TaskEditActivity/MainActivity.
 */
class ShareReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityShareReceiverBinding.inflate(layoutInflater).let { setContentView(it.root) }
        resolveAndOpen()
    }

    private fun resolveAndOpen() {
        lifecycleScope.launch {
            val geoUri = intent.data?.takeIf { it.scheme == "geo" }?.toString()
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)

            val result = geoUri?.let { LocationLinkParser.parseGeoUri(it) }
                ?: sharedText?.let { resolveFromSharedText(it) }
                ?: sharedText?.let { LocationLinkParser.parseFromText(it) }

            if (result != null) {
                openTaskEdit(result.latitude, result.longitude, result.name)
            } else {
                Toast.makeText(this@ShareReceiverActivity, R.string.msg_share_location_not_found, Toast.LENGTH_LONG).show()
                startActivity(Intent(this@ShareReceiverActivity, MainActivity::class.java))
            }
            finish()
        }
    }

    /** Достаёт первую ссылку из текста, раскрывает редиректы (если это короткая ссылка)
     *  и разбирает координаты. Имя места добираем reverse-геокодингом, если ссылка его не дала. */
    private suspend fun resolveFromSharedText(text: String): LocationLinkParser.ParsedLocation? {
        val link = LocationLinkParser.extractLinks(text).firstOrNull() ?: return null
        val finalUrl = LinkRedirectResolver.resolveFinalUrl(link)
        val parsed = LocationLinkParser.parseFromUrl(finalUrl) ?: return null
        if (parsed.name != null) return parsed
        val address = runCatching { NominatimGeocoder.reverse(parsed.latitude, parsed.longitude) }.getOrNull()
        return parsed.copy(name = address)
    }

    private fun openTaskEdit(lat: Double, lng: Double, name: String?) {
        startActivity(Intent(this, TaskEditActivity::class.java).apply {
            putExtra(Constants.EXTRA_INITIAL_LAT, lat)
            putExtra(Constants.EXTRA_INITIAL_LNG, lng)
            name?.let { putExtra(Constants.EXTRA_INITIAL_NAME, it) }
        })
    }
}
