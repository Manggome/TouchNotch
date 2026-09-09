package kr.manggome.touchnotch.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import kr.manggome.touchnotch.model.Gesture

/**
 * 노치 영역 전용 제스처 인식기.
 *
 * 노치는 폭이 좁아서 기본 GestureDetector 의 fling 최소 속도를 못 넘기는 경우가 많다.
 * 그래서 "이동 거리" 기준으로 스와이프를 직접 판정한다.
 */
class NotchGestureDetector(
    context: Context,
    /** 더블 터치에 동작이 할당돼 있는지 — 아니라면 싱글 터치를 즉시 처리해 반응 속도를 높인다 */
    private val doubleTapAssigned: () -> Boolean,
    private val onGesture: (Gesture) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val vc = ViewConfiguration.get(context)
    private val touchSlop = vc.scaledTouchSlop
    private val density = context.resources.displayMetrics.density

    private val swipeThresholdPx = SWIPE_THRESHOLD_DP * density

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var longPressFired = false
    private var swipeFired = false
    private var moved = false

    private var pendingSingleTap = false
    private var lastTapUpTime = 0L

    private val longPressRunnable = Runnable {
        if (!moved && !swipeFired) {
            longPressFired = true
            onGesture(Gesture.LONG_PRESS)
        }
    }

    private val singleTapRunnable = Runnable {
        if (pendingSingleTap) {
            pendingSingleTap = false
            onGesture(Gesture.SINGLE_TAP)
        }
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downTime = event.eventTime
                longPressFired = false
                swipeFired = false
                moved = false
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!moved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    moved = true
                    handler.removeCallbacks(longPressRunnable)
                }
                // 스와이프는 손가락을 떼기 전에 판정해 즉각 반응하게 한다
                if (!swipeFired && !longPressFired && abs(dx) >= swipeThresholdPx && abs(dx) > abs(dy) * 1.2f) {
                    swipeFired = true
                    handler.removeCallbacks(longPressRunnable)
                    cancelPendingSingleTap()
                    onGesture(if (dx < 0) Gesture.SWIPE_LEFT else Gesture.SWIPE_RIGHT)
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                val duration = event.eventTime - downTime
                if (!swipeFired && !longPressFired && !moved && duration <= TAP_TIMEOUT_MS) {
                    val now = event.eventTime
                    if (pendingSingleTap && now - lastTapUpTime <= DOUBLE_TAP_MS) {
                        cancelPendingSingleTap()
                        onGesture(Gesture.DOUBLE_TAP)
                    } else if (doubleTapAssigned()) {
                        pendingSingleTap = true
                        lastTapUpTime = now
                        handler.postDelayed(singleTapRunnable, DOUBLE_TAP_MS)
                    } else {
                        onGesture(Gesture.SINGLE_TAP)
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
            }
        }
        return true
    }

    fun reset() {
        handler.removeCallbacks(longPressRunnable)
        cancelPendingSingleTap()
    }

    private fun cancelPendingSingleTap() {
        pendingSingleTap = false
        handler.removeCallbacks(singleTapRunnable)
    }

    companion object {
        private const val LONG_PRESS_MS = 380L
        private const val DOUBLE_TAP_MS = 230L
        private const val TAP_TIMEOUT_MS = 350L
        private const val SWIPE_THRESHOLD_DP = 26f
    }
}
