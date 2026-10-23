package com.example.geonapominalka.ui
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.geonapominalka.R
import com.example.geonapominalka.databinding.ActivityAboutBinding
import com.example.geonapominalka.util.LinkSpanUtils
/** Экран "О приложении": что делает приложение, версия, атрибуция источников карты. */
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.versionLabel.text = getString(R.string.label_app_version, versionName())
        LinkSpanUtils.applyLinks(
            binding.attributionText,
            getString(R.string.label_map_attribution),
            mapOf(
                "OpenStreetMap" to "https://www.openstreetmap.org/copyright",
                "CARTO" to "https://carto.com/attributions"
            )
        )
    }
    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
    } catch (e: Exception) {
        "—"
    }
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
