package kr.manggome.touchnotch.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kr.manggome.touchnotch.model.Gesture

/**
 * 노치 위에 얹히는 투명 터치 영역.
 *
 * - 일반 모드: 완전히 투명하고 제스처만 인식한다.
 * - 편집(내 노치 찾기) 모드: 영역이 눈에 보이고, 드래그해서 위치를 옮길 수 있다.
 */
@SuppressLint("ViewConstructor")
class NotchTouchView(
    context: Context,
    private val callbacks: Callbacks,
) : View(context) {

    interface Callbacks {
        fun onGesture(gesture: Gesture)
        /** 편집 모드에서 드래그 중 — 화면 기준 이동량(px) */
        fun onDrag(dxPx: Float, dyPx: Float)
        fun onDragFinished()
    }

    var editMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                detector.reset()
                invalidate()
            }
        }

    var doubleTapAssigned: Boolean = true

    private val detector = NotchGestureDetector(
        context = context,
        doubleTapAssigned = { doubleTapAssigned },
        onGesture = { callbacks.onGesture(it) },
    )

    private val density = context.resources.displayMetrics.density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D38BDF8")
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    private val rect = RectF()

    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragging = false

    override fun onDraw(canvas: Canvas) {
        if (!editMode) return
        val inset = strokePaint.strokeWidth / 2f
        rect.set(inset, inset, width - inset, height - inset)
        val radius = minOf(width, height) / 2f
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        val cx = width / 2f
        val cy = height / 2f
        val arm = minOf(width, height) * 0.22f
        canvas.drawLine(cx - arm, cy, cx + arm, cy, crossPaint)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, crossPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editMode) return detector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.rawX
                dragStartY = event.rawY
                dragging = true
            }

            MotionEvent.ACTION_MOVE -> if (dragging) {
                val dx = event.rawX - dragStartX
                val dy = event.rawY - dragStartY
                dragStartX = event.rawX
                dragStartY = event.rawY
                callbacks.onDrag(dx, dy)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                callbacks.onDragFinished()
            }
        }
        return true
    }
}
