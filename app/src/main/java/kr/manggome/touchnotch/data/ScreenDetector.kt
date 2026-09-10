package kr.manggome.touchnotch.data

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.view.Display
import kr.manggome.touchnotch.model.FoldState
import kr.manggome.touchnotch.model.ScreenProfileKey
import kr.manggome.touchnotch.model.ScreenRotation

/**
 * 지금 어떤 화면 상태인지 판별한다.
 *
 * 폴드 상태는 화면 최소 너비(smallestScreenWidthDp)로 본다. 이 값은 회전과 무관하게
 * 그 화면의 짧은 변을 dp 로 알려주므로 커버 화면 / 메인 화면을 구분하는 데 가장 안정적이다.
 *
 * 회전은 Display.getRotation() 을 쓴다. Configuration 에는 회전 정보가 없어서
 * 0°↔180° 처럼 화면 크기가 그대로인 회전은 onConfigurationChanged 가 오지 않는다.
 * 그런 변화까지 잡으려면 DisplayManager.DisplayListener 로 관찰해야 한다.
 */
object ScreenDetector {

    fun smallestWidthDp(context: Context): Int =
        context.resources.configuration.smallestScreenWidthDp

    fun foldState(context: Context, thresholdDp: Int): FoldState =
        if (smallestWidthDp(context) >= thresholdDp) FoldState.UNFOLDED else FoldState.FOLDED

    fun foldState(context: Context, store: SettingsStore): FoldState =
        foldState(context, store.foldThresholdDp)

    fun rotation(context: Context): ScreenRotation {
        val display = defaultDisplay(context) ?: return ScreenRotation.ROTATION_0
        return ScreenRotation.fromSurfaceRotation(display.rotation)
    }

    fun screenKey(context: Context, thresholdDp: Int): ScreenProfileKey =
        ScreenProfileKey(foldState(context, thresholdDp), rotation(context))

    fun screenKey(context: Context, store: SettingsStore): ScreenProfileKey =
        screenKey(context, store.foldThresholdDp)

    fun defaultDisplay(context: Context): Display? = runCatching {
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    }.getOrNull()

    /**
     * 기본 디스플레이의 변화(회전 포함)를 관찰한다.
     * @return 해제 함수
     */
    fun observeDisplayChanges(
        context: Context,
        handler: Handler,
        onChanged: () -> Unit,
    ): () -> Unit {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return {}
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) onChanged()
            }
        }
        runCatching { manager.registerDisplayListener(listener, handler) }
        return { runCatching { manager.unregisterDisplayListener(listener) } }
    }
}
