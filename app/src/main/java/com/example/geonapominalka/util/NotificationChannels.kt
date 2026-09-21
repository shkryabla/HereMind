package com.example.geonapominalka.util
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.geonapominalka.R

/**
 * Создаёт оба канала уведомлений один раз при первом запуске приложения.
 *
 * Звук и вибрация канала "Гео-напоминания" здесь НЕ задаются — начиная с Android 8,
 * система намеренно блокирует программное изменение звука/вибрации канала после его
 * создания (это защита пользовательских настроек от приложений). Попытки обойти это
 * через delete+recreate канала здесь были и не сработали надёжно на всех прошивках —
 * поэтому звук пользователь настраивает сам через системные настройки канала
 * (см. кнопку в SettingsActivity), это единственный способ, который гарантированно
 * работает на любом устройстве.
 */
object NotificationChannels {
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notification_channel_description)
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        if (manager.getNotificationChannel(Constants.REMINDER_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    Constants.REMINDER_CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.reminder_channel_description)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                }
            )
        }
    }
}
