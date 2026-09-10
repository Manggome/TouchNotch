package kr.manggome.touchnotch.boot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kr.manggome.touchnotch.R
import kr.manggome.touchnotch.ui.MainActivity

/** 접근성 서비스가 꺼져 있을 때 알려주는 알림 */
object ServiceStatusNotifier {

    private const val CHANNEL_ID = "service_status"
    private const val NOTIFICATION_ID = 4212

    fun notifyDisabled(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "서비스 상태",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openSettings = PendingIntent.getActivity(
            context,
            1,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notch)
            .setContentTitle("터치노치가 꺼져 있어요")
            .setContentText("접근성 서비스가 다시 켜지지 않았습니다. 눌러서 켜주세요.")
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(0, "접근성 설정 열기", openSettings)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }
}
