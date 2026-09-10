package kr.manggome.touchnotch.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt
import kr.manggome.touchnotch.action.ActionExecutor
import kr.manggome.touchnotch.boot.ServiceStatusNotifier
import kr.manggome.touchnotch.data.ScreenDetector
import kr.manggome.touchnotch.data.SettingsStore
import kr.manggome.touchnotch.model.DetectedCutout
import kr.manggome.touchnotch.model.Gesture
import kr.manggome.touchnotch.model.NotchAction
import kr.manggome.touchnotch.model.NotchProfile
import kr.manggome.touchnotch.model.ScreenProfileKey

/**
 * 노치 영역 오버레이를 띄우고 제스처를 처리하는 접근성 서비스.
 *
 * 접근성 서비스는 TYPE_ACCESSIBILITY_OVERLAY 윈도우를 쓸 수 있어서
 * '다른 앱 위에 표시' 권한 없이도 화면 위에 터치 영역을 올릴 수 있다.
 *
 * 사용자가 접근성 설정에서 켜두면 재부팅 후에도 시스템이 자동으로 다시 연결해준다.
 */
class NotchAccessibilityService : AccessibilityService(), NotchTouchView.Callbacks {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: SettingsStore
    private lateinit var windowManager: WindowManager
    private var executor: ActionExecutor? = null
    private var vibrator: Vibrator? = null

    private var touchView: NotchTouchView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentProfile: NotchProfile? = null

    /** 편집(내 노치 찾기) 모드 대상 — null 이면 편집 중 아님 */
    private var editTarget: ScreenProfileKey? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { applyProfile() }
    }

    // ---------------- 라이프사이클 ----------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = SettingsStore(this)
        windowManager = getSystemService(WindowManager::class.java)
        executor = ActionExecutor(this, store)
        vibrator = resolveVibrator()
        store.registerListener(prefListener)
        store.lastConnectedAt = System.currentTimeMillis()
        ServiceStatusNotifier.cancel(this)
        instance = this
        notifyStateChanged()
        applyProfile()
        Log.i(TAG, "접근성 서비스 연결됨")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (instance === this) instance = null
        if (::store.isInitialized) runCatching { store.unregisterListener(prefListener) }
        removeOverlay()
        executor?.release()
        executor = null
        handler.removeCallbacksAndMessages(null)
        notifyStateChanged()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 폴드 펼침/접힘, 화면 회전, 화면 크기 변경 모두 여기로 들어온다
        handler.post {
            applyProfile()
            notifyStateChanged()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    // ---------------- 오버레이 ----------------

    fun currentScreenKey(): ScreenProfileKey = ScreenDetector.screenKey(this, store)

    private fun applyProfile() {
        if (!::store.isInitialized) return
        val screenKey = currentScreenKey()
        val profile = store.profile(screenKey)
        currentProfile = profile

        val editing = editTarget == screenKey
        if (!profile.enabled && !editing) {
            removeOverlay()
            return
        }

        val bounds = displayBounds()
        val density = resources.displayMetrics.density
        val widthPx = (profile.widthDp * density).roundToInt()
            .coerceIn(1, bounds.width().coerceAtLeast(1))
        val heightPx = (profile.heightDp * density).roundToInt()
            .coerceIn(1, bounds.height().coerceAtLeast(1))
        val yPx = (profile.verticalDp * density).roundToInt()
            .coerceIn(0, (bounds.height() - heightPx).coerceAtLeast(0))
        val xPx = horizontalOffsetPx(profile.horizontalPercent, bounds.width(), widthPx)

        val params = layoutParams ?: newLayoutParams().also { layoutParams = it }
        params.width = widthPx
        params.height = heightPx
        params.x = xPx
        params.y = yPx

        val view = touchView ?: NotchTouchView(this, this).also { touchView = it }
        view.doubleTapAssigned = profile.actionFor(Gesture.DOUBLE_TAP) != NotchAction.NONE
        view.editMode = editing

        if (view.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(view, params) }
                .onFailure { Log.e(TAG, "오버레이 갱신 실패", it) }
        } else {
            runCatching { windowManager.addView(view, params) }
                .onFailure { Log.e(TAG, "오버레이 추가 실패", it) }
        }
    }

    private fun newLayoutParams() = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    private fun removeOverlay() {
        touchView?.let { v ->
            if (v.isAttachedToWindow) runCatching { windowManager.removeView(v) }
        }
        touchView = null
        layoutParams = null
    }

    private fun displayBounds(): Rect = Rect(windowManager.currentWindowMetrics.bounds)

    private fun horizontalOffsetPx(percent: Int, screenWidthPx: Int, viewWidthPx: Int): Int {
        val maxOffset = ((screenWidthPx - viewWidthPx) / 2f).coerceAtLeast(0f)
        return (maxOffset * percent / 100f).roundToInt()
    }

    // ---------------- 제스처 콜백 ----------------

    override fun onGesture(gesture: Gesture) {
        val profile = currentProfile ?: return
        val action = profile.actionFor(gesture)
        if (action == NotchAction.NONE) return
        if (profile.haptic) vibrate(profile.hapticMs)
        executor?.perform(action)
    }

    override fun onDrag(dxPx: Float, dyPx: Float) {
        val params = layoutParams ?: return
        val view = touchView ?: return
        val bounds = displayBounds()
        val maxOffset = ((bounds.width() - params.width) / 2f).coerceAtLeast(0f).roundToInt()
        params.x = (params.x + dxPx.roundToInt()).coerceIn(-maxOffset, maxOffset)
        params.y = (params.y + dyPx.roundToInt())
            .coerceIn(0, (bounds.height() - params.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    override fun onDragFinished() {
        val params = layoutParams ?: return
        val target = editTarget ?: return
        val bounds = displayBounds()
        val density = resources.displayMetrics.density
        val maxOffset = ((bounds.width() - params.width) / 2f).coerceAtLeast(1f)
        val percent = (params.x / maxOffset * 100f).roundToInt().coerceIn(-100, 100)
        val verticalDp = (params.y / density).roundToInt().coerceAtLeast(0)
        store.savePosition(target, percent, verticalDp)
        vibrate(15)
    }

    private fun vibrate(ms: Int) {
        val v = vibrator ?: return
        if (ms <= 0 || !v.hasVibrator()) return
        runCatching {
            v.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun resolveVibrator(): Vibrator? =
        runCatching { getSystemService(VibratorManager::class.java)?.defaultVibrator }.getOrNull()
            ?: getSystemService(Vibrator::class.java)

    // ---------------- 액티비티에서 호출하는 명령 ----------------

    /** 편집 모드 설정. null 이면 편집 종료 */
    fun setEditTarget(key: ScreenProfileKey?) {
        editTarget = key
        handler.post { applyProfile() }
    }

    fun previewHaptic(ms: Int) = vibrate(ms)

    /** 실제 디스플레이 컷아웃(노치/펀치홀)을 읽는다. 없으면 null */
    fun detectCutout(): DetectedCutout? {
        val display = runCatching {
            getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        }.getOrNull() ?: return null
        val cutout = display.cutout ?: return null
        // 화면을 돌리면 컷아웃도 회전된 좌표로 보고되므로, 방향별로 그대로 쓰면 된다.
        val rect = cutout.boundingRects.firstOrNull { !it.isEmpty } ?: return null
        val bounds = displayBounds()
        return DetectedCutout(
            leftPx = rect.left,
            topPx = rect.top,
            rightPx = rect.right,
            bottomPx = rect.bottom,
            screenWidthPx = bounds.width(),
            screenHeightPx = bounds.height(),
            density = resources.displayMetrics.density,
        )
    }

    /**
     * 감지한 컷아웃에 맞춰 해당 프로필의 크기/위치를 자동 설정한다.
     * @return 감지 성공 여부
     */
    fun autoFitToCutout(key: ScreenProfileKey, paddingDp: Int = 6): Boolean {
        val cutout = detectCutout() ?: return false
        val density = cutout.density
        val widthDp = ((cutout.widthPx / density).roundToInt() + paddingDp * 2)
            .coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)
        val heightDp = ((cutout.heightPx / density).roundToInt() + paddingDp * 2)
            .coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
        val widthPx = widthDp * density
        val centerX = (cutout.leftPx + cutout.rightPx) / 2f
        val offsetFromCenter = centerX - cutout.screenWidthPx / 2f
        val maxOffset = ((cutout.screenWidthPx - widthPx) / 2f).coerceAtLeast(1f)
        val percent = (offsetFromCenter / maxOffset * 100f).roundToInt().coerceIn(-100, 100)
        val verticalDp = ((cutout.topPx / density).roundToInt() - paddingDp)
            .coerceIn(0, MAX_VERTICAL_DP)

        store.save(
            store.profile(key).copy(
                widthDp = widthDp,
                heightDp = heightDp,
                horizontalPercent = percent,
                verticalDp = verticalDp,
            )
        )
        return true
    }

    private fun notifyStateChanged() {
        stateListeners.forEach { runCatching { it() } }
    }

    companion object {
        private const val TAG = "NotchService"

        const val MIN_WIDTH_DP = 24
        const val MAX_WIDTH_DP = 320
        const val MIN_HEIGHT_DP = 12
        const val MAX_HEIGHT_DP = 320
        const val MAX_VERTICAL_DP = 900

        @Volatile
        var instance: NotchAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null

        private val stateListeners = CopyOnWriteArrayList<() -> Unit>()

        /** 서비스 연결/해제/화면 상태 변화 시 UI 갱신용 */
        fun addStateListener(listener: () -> Unit) {
            stateListeners.add(listener)
        }

        fun removeStateListener(listener: () -> Unit) {
            stateListeners.remove(listener)
        }
    }
}
