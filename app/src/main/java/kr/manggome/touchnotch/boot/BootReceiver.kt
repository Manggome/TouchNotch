package kr.manggome.touchnotch.boot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kr.manggome.touchnotch.data.SettingsStore
import kr.manggome.touchnotch.service.NotchAccessibilityService

/**
 * 재부팅 / 앱 업데이트 후 접근성 서비스가 살아났는지 확인한다.
 *
 * 사용자가 접근성 설정에서 켜둔 서비스는 재부팅하면 시스템이 자동으로 다시 연결한다.
 * 앱이 자기 접근성 서비스를 코드로 켜는 것은 불가능하다 (WRITE_SECURE_SETTINGS 는 시스템 전용).
 *
 * 그래서 여기서는 강제로 켜는 대신, 부팅 후 일정 시간이 지나도 서비스가 붙지 않았으면
 * (삼성 절전 기능 등이 껐을 때) 알림으로 알려주는 안전장치 역할만 한다.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            ACTION_MY_PACKAGE_REPLACED,
            -> {
                Log.i(TAG, "부팅/업데이트 감지 (${intent.action}) — 상태 확인 예약")
                scheduleCheck(context)
            }

            ACTION_CHECK -> runCheck(context)
        }
    }

    private fun runCheck(context: Context) {
        val store = SettingsStore(context)
        if (NotchAccessibilityService.isRunning) {
            Log.i(TAG, "접근성 서비스 정상 동작 중")
            ServiceStatusNotifier.cancel(context)
            return
        }
        if (!store.notifyIfNotRunning) return
        Log.w(TAG, "접근성 서비스가 꺼져 있음 — 알림 표시")
        ServiceStatusNotifier.notifyDisabled(context)
    }

    companion object {
        private const val TAG = "BootReceiver"
        private const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
        const val ACTION_CHECK = "kr.manggome.touchnotch.CHECK_SERVICE"

        /** 부팅 직후에는 아직 서비스가 붙는 중일 수 있어 여유를 둔다 */
        private const val CHECK_DELAY_MS = 90_000L

        fun scheduleCheck(context: Context, delayMs: Long = CHECK_DELAY_MS) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, BootReceiver::class.java).setAction(ACTION_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + delayMs,
                    pending,
                )
            }.onFailure { Log.w(TAG, "알람 예약 실패", it) }
        }

        private const val REQUEST_CODE = 7301
    }
}
