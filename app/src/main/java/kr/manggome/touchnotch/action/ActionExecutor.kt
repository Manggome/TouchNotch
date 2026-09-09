package kr.manggome.touchnotch.action

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kr.manggome.touchnotch.data.SettingsStore
import kr.manggome.touchnotch.model.NotchAction
import kr.manggome.touchnotch.record.ScreenCaptureRequestActivity
import kr.manggome.touchnotch.record.ScreenRecordService

/** 제스처에 연결된 동작을 실제로 수행한다. */
class ActionExecutor(
    private val service: AccessibilityService,
    private val store: SettingsStore,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val torch = TorchController(service, handler)

    private var scrollNode: AccessibilityNodeInfo? = null
    private var scrollCount = 0
    private val scrollRunnable = object : Runnable {
        override fun run() {
            val node = scrollNode ?: return
            if (scrollCount++ >= MAX_SCROLL_STEPS) { finishScroll(); return }
            val ok = runCatching {
                node.refresh()
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }.getOrDefault(false)
            if (ok) handler.postDelayed(this, SCROLL_INTERVAL_MS) else finishScroll()
        }
    }

    fun perform(action: NotchAction) {
        when (action) {
            NotchAction.NONE -> Unit
            NotchAction.FLASHLIGHT -> flashlight()
            NotchAction.POWER_MENU -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, "전원 메뉴를 열 수 없습니다")
            NotchAction.SOUND_TOGGLE -> soundToggle()
            NotchAction.SCROLL_TOP -> scrollToTop()
            NotchAction.SCREENSHOT -> global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "스크린샷을 찍을 수 없습니다")
            NotchAction.SCREEN_RECORD -> screenRecord()
            NotchAction.CAMERA -> openCamera()
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        torch.release()
    }

    // ---------------- 개별 동작 ----------------

    private fun flashlight() {
        if (!torch.hasFlash()) { toast("이 기기에서 손전등을 쓸 수 없습니다"); return }
        if (!torch.toggle()) toast("손전등을 켤 수 없습니다 (카메라 사용 중일 수 있어요)")
    }

    private fun global(action: Int, failMessage: String) {
        if (!service.performGlobalAction(action)) toast(failMessage)
    }

    private fun soundToggle() {
        val nm = service.getSystemService(NotificationManager::class.java)
        if (nm != null && !nm.isNotificationPolicyAccessGranted) {
            toast("‘방해 금지 접근’ 권한이 필요합니다")
            openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            return
        }
        val am = service.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            when (am.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> {
                    am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    toast("진동")
                }
                else -> {
                    am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    toast("소리")
                }
            }
        }.onFailure { toast("소리 모드를 바꿀 수 없습니다") }
    }

    private fun scrollToTop() {
        finishScroll()
        val root = service.rootInActiveWindow
        if (root == null) { toast("화면을 읽을 수 없습니다"); return }
        val node = findBestScrollable(root)
        if (node == null) { toast("스크롤할 수 있는 영역이 없습니다"); return }
        scrollNode = node
        scrollCount = 0
        handler.post(scrollRunnable)
    }

    private fun finishScroll() {
        handler.removeCallbacks(scrollRunnable)
        scrollNode = null
        scrollCount = 0
    }

    /** 화면에서 가장 넓은 스크롤 가능 노드를 찾는다 */
    private fun findBestScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        val bounds = android.graphics.Rect()
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.isScrollable) {
                node.getBoundsInScreen(bounds)
                val area = bounds.width() * bounds.height()
                if (area > bestArea) {
                    bestArea = area
                    best = node
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return best
    }

    private fun screenRecord() {
        if (ScreenRecordService.isRecording) {
            ScreenRecordService.stop(service)
            return
        }
        val intent = Intent(service, ScreenCaptureRequestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivitySafely(intent, "화면 녹화를 시작할 수 없습니다. ‘다른 앱 위에 표시’ 권한을 켜주세요")
    }

    private fun openCamera() {
        // 1순위: 기본 카메라 앱, 2순위: 사진 촬영 인텐트, 3순위: 카메라 인텐트를 처리하는 앱 직접 실행
        val candidates = mutableListOf(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        val pm = service.packageManager
        pm.resolveActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), 0)
            ?.activityInfo?.packageName
            ?.let { pkg -> pm.getLaunchIntentForPackage(pkg)?.let { candidates.add(it) } }

        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (trySilently(intent)) return
        }
        toast("카메라 앱을 열 수 없습니다. ‘다른 앱 위에 표시’ 권한을 켜주세요")
    }

    private fun openSettings(action: String) {
        startActivitySafely(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "설정을 열 수 없습니다")
    }

    private fun trySilently(intent: Intent): Boolean =
        runCatching { service.startActivity(intent); true }.getOrDefault(false)

    private fun startActivitySafely(intent: Intent, failMessage: String) {
        runCatching { service.startActivity(intent) }
            .onFailure {
                Log.w(TAG, "startActivity 실패", it)
                toast(failMessage)
            }
    }

    private fun toast(message: String) {
        handler.post { Toast.makeText(service, message, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val TAG = "ActionExecutor"
        const val MAX_SCROLL_STEPS = 45
        const val SCROLL_INTERVAL_MS = 55L
        const val MAX_NODES = 900
    }
}
