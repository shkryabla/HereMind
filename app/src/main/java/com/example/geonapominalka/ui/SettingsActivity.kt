package com.example.geonapominalka.ui
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.geonapominalka.GeoApp
import com.example.geonapominalka.R
import com.example.geonapominalka.databinding.ActivitySettingsBinding
import com.example.geonapominalka.util.Constants
import com.example.geonapominalka.util.TileSources
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
/** Настройки приложения: тема, звук/вибрация уведомлений (через системный экран),
 *  частота опроса местоположения, стиль карты, сброс данных. */
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var app: GeoApp
    // Повторный запрос ACTIVITY_RECOGNITION при включении адаптивного режима, если разрешение не выдано
    private val requestActivityRecognition = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        lifecycleScope.launch { app.settingsRepository.setAdaptiveMode(true) }
        if (!granted) {
            android.widget.Toast.makeText(
                this, R.string.msg_activity_recognition_denied, android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    // На Android 10+ фоновая геолокация запрашивается ОТДЕЛЬНО от обычной — система
    // игнорирует запрос, если он идёт в одной пачке с foreground-разрешением.
    private val requestBackgroundLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* результат не критичен — кнопка сама отражает текущее состояние при следующем открытии экрана */ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        app = GeoApp.from(this)
        setupTheme()
        setupAdaptiveModeAndInterval()
        setupInterval()
        setupMapStyle()
        setupSoundSettingsLink()
        setupReset()
        setupBackgroundLocationLink()
        setupBackgroundWorkLink()
    }
    private fun setupTheme() {
        lifecycleScope.launch {
            app.settingsRepository.theme.collect { theme ->
                val id = when (theme) {
                    "light" -> R.id.radioThemeLight
                    "dark" -> R.id.radioThemeDark
                    else -> R.id.radioThemeSystem
                }
                if (binding.themeGroup.checkedRadioButtonId != id) binding.themeGroup.check(id)
            }
        }
        binding.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radioThemeLight -> "light"
                R.id.radioThemeDark -> "dark"
                else -> "system"
            }
            lifecycleScope.launch { app.settingsRepository.setTheme(value) }
        }
    }
    /**
     * Адаптивный режим — главнее ручного интервала: список фиксированных значений
     * остаётся видимым, но дизейблится, пока адаптивный режим включён (п.6 обсуждения).
     */
    private fun setupAdaptiveModeAndInterval() {
        lifecycleScope.launch {
            app.settingsRepository.adaptiveMode.collect { enabled ->
                if (binding.switchAdaptiveMode.isChecked != enabled) binding.switchAdaptiveMode.isChecked = enabled
                setIntervalGroupEnabled(!enabled)
            }
        }
        binding.switchAdaptiveMode.setOnCheckedChangeListener { _, checked ->
            if (checked && !hasActivityRecognitionPermission()) {
                // Разрешение не выдано — объясняем ещё раз, зачем оно нужно, и запрашиваем повторно.
                // Сама настройка включится в колбэке запроса разрешения (независимо от результата —
                // адаптивный режим и без него работает, просто менее точно, по скорости из GPS).
                binding.switchAdaptiveMode.isChecked = false
                showActivityRecognitionRationale()
            } else {
                lifecycleScope.launch { app.settingsRepository.setAdaptiveMode(checked) }
            }
        }
    }
    private fun showActivityRecognitionRationale() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_activity_recognition_title)
            .setMessage(R.string.dialog_activity_recognition_message)
            .setPositiveButton(R.string.action_continue) { dialog, _ ->
                dialog.dismiss()
                requestActivityRecognition.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }
    private fun hasActivityRecognitionPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
    private fun setIntervalGroupEnabled(enabled: Boolean) {
        binding.intervalGroup.alpha = if (enabled) 1.0f else 0.4f
        for (i in 0 until binding.intervalGroup.childCount) {
            binding.intervalGroup.getChildAt(i).isEnabled = enabled
        }
    }
    private fun setupInterval() {
        lifecycleScope.launch {
            app.settingsRepository.intervalSeconds.collect { seconds ->
                val id = when (seconds) {
                    30 -> R.id.radioInterval30
                    180 -> R.id.radioInterval180
                    300 -> R.id.radioInterval300
                    600 -> R.id.radioInterval600
                    1200 -> R.id.radioInterval1200
                    else -> R.id.radioInterval60
                }
                if (binding.intervalGroup.checkedRadioButtonId != id) binding.intervalGroup.check(id)
            }
        }
        binding.intervalGroup.setOnCheckedChangeListener { _, checkedId ->
            val seconds = when (checkedId) {
                R.id.radioInterval30 -> 30
                R.id.radioInterval180 -> 180
                R.id.radioInterval300 -> 300
                R.id.radioInterval600 -> 600
                R.id.radioInterval1200 -> 1200
                else -> 60
            }
            lifecycleScope.launch { app.settingsRepository.setIntervalSeconds(seconds) }
        }
    }
    /**
     * Стиль карты — реализовано как выбор стиля/слоя, т.к. у бесплатных провайдеров
     * без ключа нет отдельного переключателя "провайдер". Значение хранится тем же
     * ключом mapType, что и переключатель на самой карте.
     */
    private fun setupMapStyle() {
        val idByStyle = mapOf(
            TileSources.MapStyle.VOYAGER to R.id.radioStyleVoyager,
            TileSources.MapStyle.HYBRID to R.id.radioStyleHybrid
        )
        val styleById = idByStyle.entries.associate { (style, id) -> id to style }
        lifecycleScope.launch {
            app.settingsRepository.mapType.collect { index ->
                val id = idByStyle.getValue(TileSources.MapStyle.fromIndex(index))
                if (binding.mapStyleGroup.checkedRadioButtonId != id) binding.mapStyleGroup.check(id)
            }
        }
        binding.mapStyleGroup.setOnCheckedChangeListener { _, checkedId ->
            val style = styleById[checkedId] ?: return@setOnCheckedChangeListener
            lifecycleScope.launch { app.settingsRepository.setMapType(style.ordinal) }
        }
    }
    /**
     * Звук и вибрация канала "Гео-напоминания" настраиваются пользователем напрямую в
     * системных настройках — начиная с Android 8 система сознательно блокирует
     * программное изменение звука/вибрации уже созданного канала (защита от приложений,
     * тайно меняющих пользовательский выбор), поэтому единственный надёжный способ,
     * работающий на любой прошивке — открыть системный экран настроек канала напрямую.
     */
    private fun setupSoundSettingsLink() {
        binding.btnOpenSoundSettings.setOnClickListener {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, Constants.REMINDER_CHANNEL_ID)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            }
            startActivity(intent)
        }
    }
    private fun setupReset() {
        binding.btnResetData.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.dialog_reset_message)
                .setPositiveButton(R.string.action_yes) { d, _ ->
                    lifecycleScope.launch { app.reminderRepository.resetAll() }
                    d.dismiss()
                }
                .setNegativeButton(R.string.action_no) { d, _ -> d.dismiss() }
                .show()
        }
    }
    /**
     * Прямой запрос на исключение из батарейной оптимизации — один системный диалог
     * с кнопкой "Разрешить", без похода вручную по настройкам. Нужен на прошивках
     * (MIUI и др.), где агрессивное энергосбережение мешает приходу уведомлений при
     * заблокированном экране, пока приложение свёрнуто.
     */
    private fun setupBackgroundWorkLink() {
        binding.btnOpenAppSettings.setOnClickListener {
            val powerManager = getSystemService(PowerManager::class.java)
            val alreadyIgnoring = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                powerManager?.isIgnoringBatteryOptimizations(packageName) == true
            if (alreadyIgnoring) {
                openAppDetailsSettings()
                return@setOnClickListener
            }
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (e: ActivityNotFoundException) {
                // Некоторые прошивки блокируют этот системный диалог — открываем общие
                // настройки приложения, там то же самое можно включить вручную.
                openAppDetailsSettings()
            }
        }
    }
    private fun openAppDetailsSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }
    /**
     * Фоновая геолокация ("в любом режиме") — опционально, по требованию Google Play
     * показываем явное раскрытие (Prominent Disclosure) непосредственно перед системным
     * диалогом запроса разрешения, а не просто запрашиваем молча.
     */
    private fun setupBackgroundLocationLink() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            binding.btnRequestBackgroundLocation.visibility = android.view.View.GONE
            return
        }
        binding.btnRequestBackgroundLocation.setOnClickListener {
            if (hasBackgroundLocationPermission()) {
                android.widget.Toast.makeText(
                    this, R.string.msg_background_location_already_granted, android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_background_location_title)
                .setMessage(R.string.dialog_background_location_message)
                .setPositiveButton(R.string.action_continue) { dialog, _ ->
                    dialog.dismiss()
                    requestBackgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }
    private fun hasBackgroundLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
