package kr.manggome.touchnotch.model

import android.view.Surface

/** 노치 영역에서 인식하는 제스처 */
enum class Gesture(val key: String, val label: String) {
    SINGLE_TAP("single_tap", "싱글 터치"),
    DOUBLE_TAP("double_tap", "더블 터치"),
    LONG_PRESS("long_press", "롱 터치"),
    SWIPE_LEFT("swipe_left", "왼쪽으로 스와이프"),
    SWIPE_RIGHT("swipe_right", "오른쪽으로 스와이프");

    companion object {
        fun from(key: String?): Gesture? = entries.firstOrNull { it.key == key }
    }
}

/** 제스처에 연결할 수 있는 동작 */
enum class NotchAction(val key: String, val label: String, val hint: String = "") {
    NONE("none", "사용 안 함"),
    FLASHLIGHT("flashlight", "손전등 켜기", "다시 실행하면 꺼집니다"),
    POWER_MENU("power_menu", "전원 메뉴 열기"),
    SOUND_TOGGLE("sound_toggle", "소리/진동 토글", "방해 금지 접근 권한 필요"),
    SCROLL_TOP("scroll_top", "맨 위로 스크롤"),
    SCREENSHOT("screenshot", "스크린샷 찍기"),
    SCREEN_RECORD("screen_record", "화면 녹화 시작", "다시 실행하면 녹화가 멈춥니다"),
    CAMERA("camera", "카메라 켜기");

    companion object {
        fun from(key: String?): NotchAction = entries.firstOrNull { it.key == key } ?: NONE
    }
}

/** 폴더블 상태 */
enum class FoldState(val key: String, val label: String, val shortLabel: String) {
    FOLDED("folded", "접었을 때 (커버 화면)", "접었을 때"),
    UNFOLDED("unfolded", "펼쳤을 때 (메인 화면)", "펼쳤을 때");

    companion object {
        fun from(key: String?): FoldState = entries.firstOrNull { it.key == key } ?: UNFOLDED
    }
}

/**
 * 화면 회전 4방향.
 *
 * 노치는 기기에 물리적으로 붙어 있어서 회전할 때마다 화면 좌표계에서 다른 변으로 옮겨간다.
 * 그래서 가로/세로만 나누면 부족하고, 0°/90°/180°/270° 를 모두 따로 저장해야 한다.
 *
 * 어느 회전에서 노치가 어느 변으로 가는지는 기기마다 다를 수 있으므로
 * 화면에는 각도와 함께 "지금 이 방향" 표시를 띄워, 기기를 돌려가며 맞추게 한다.
 */
enum class ScreenRotation(
    val key: String,
    val surfaceRotation: Int,
    val label: String,
    val shortLabel: String,
    val isLandscape: Boolean,
) {
    ROTATION_0("rot0", Surface.ROTATION_0, "세로 (0°)", "0°", false),
    ROTATION_90("rot90", Surface.ROTATION_90, "가로 (90°)", "90°", true),
    ROTATION_180("rot180", Surface.ROTATION_180, "세로 뒤집힘 (180°)", "180°", false),
    ROTATION_270("rot270", Surface.ROTATION_270, "가로 반대쪽 (270°)", "270°", true);

    companion object {
        fun from(key: String?): ScreenRotation = entries.firstOrNull { it.key == key } ?: ROTATION_0

        fun fromSurfaceRotation(rotation: Int): ScreenRotation =
            entries.firstOrNull { it.surfaceRotation == rotation } ?: ROTATION_0
    }
}

/**
 * 설정을 나누는 기준. 폴드 상태(2) × 회전(4) = 8개의 독립 프로필.
 */
data class ScreenProfileKey(
    val fold: FoldState,
    val rotation: ScreenRotation,
) {
    val prefix: String get() = "${fold.key}_${rotation.key}"

    /** "접었을 때 · 가로 (90°)" */
    val label: String get() = "${fold.shortLabel} · ${rotation.label}"

    /** "접었을 때 90°" */
    val shortLabel: String get() = "${fold.shortLabel} ${rotation.shortLabel}"

    companion object {
        val ALL: List<ScreenProfileKey> = FoldState.entries.flatMap { fold ->
            ScreenRotation.entries.map { rotation -> ScreenProfileKey(fold, rotation) }
        }
    }
}

/** 한 화면 상태(폴드 × 회전)에 대한 전체 설정 */
data class NotchProfile(
    val key: ScreenProfileKey,
    val enabled: Boolean,
    val widthDp: Int,
    val heightDp: Int,
    /** -100(왼쪽 끝) ~ 0(중앙) ~ 100(오른쪽 끝) */
    val horizontalPercent: Int,
    /** 화면 최상단에서의 거리(dp). 화면 밖으로 나가는 값은 표시할 때 잘린다 */
    val verticalDp: Int,
    val haptic: Boolean,
    val hapticMs: Int,
    val actions: Map<Gesture, NotchAction>,
) {
    fun actionFor(gesture: Gesture): NotchAction = actions[gesture] ?: NotchAction.NONE

    companion object {
        /** 화면 아래쪽에 붙이고 싶을 때 쓰는 값. 표시할 때 화면 높이에 맞춰 잘린다 */
        const val VERTICAL_BOTTOM = 9_999

        /** 제스처 기본 매핑은 폴드 상태에 따라서만 달라진다 */
        private fun defaultActions(fold: FoldState): Map<Gesture, NotchAction> = when (fold) {
            FoldState.FOLDED -> mapOf(
                Gesture.SINGLE_TAP to NotchAction.NONE,
                Gesture.DOUBLE_TAP to NotchAction.FLASHLIGHT,
                Gesture.LONG_PRESS to NotchAction.POWER_MENU,
                Gesture.SWIPE_LEFT to NotchAction.SOUND_TOGGLE,
                Gesture.SWIPE_RIGHT to NotchAction.SCREENSHOT,
            )

            FoldState.UNFOLDED -> mapOf(
                Gesture.SINGLE_TAP to NotchAction.NONE,
                Gesture.DOUBLE_TAP to NotchAction.FLASHLIGHT,
                Gesture.LONG_PRESS to NotchAction.POWER_MENU,
                Gesture.SWIPE_LEFT to NotchAction.SCROLL_TOP,
                Gesture.SWIPE_RIGHT to NotchAction.SCREENSHOT,
            )
        }

        /**
         * 기본 크기·위치.
         *
         * 실제로는 서비스가 이 프로필을 처음 쓸 때 디스플레이 컷아웃을 읽어 자동으로 맞춘다.
         * 여기 값은 컷아웃이 감지되지 않는 화면(폴드 메인 화면의 언더 디스플레이 카메라 등)에서
         * 사용자가 끌어서 맞출 때의 출발점이다.
         */
        private fun defaultGeometry(key: ScreenProfileKey): Geometry {
            val long = if (key.fold == FoldState.FOLDED) 96 else 110
            val short = if (key.fold == FoldState.FOLDED) 30 else 34
            // 0° 에서 노치가 가로로 치우친 정도 (폴드 메인 화면은 오른쪽 위 카메라)
            val bias = if (key.fold == FoldState.FOLDED) 0 else 72
            return when (key.rotation) {
                // 위쪽 변
                ScreenRotation.ROTATION_0 -> Geometry(long, short, bias, 0)
                // 아래쪽 변 (좌우가 뒤집힌다)
                ScreenRotation.ROTATION_180 -> Geometry(long, short, -bias, VERTICAL_BOTTOM)
                // 옆쪽 변 — 가로/세로가 바뀌므로 크기를 뒤집는다
                ScreenRotation.ROTATION_90 -> Geometry(short, long, -100, 0)
                ScreenRotation.ROTATION_270 -> Geometry(short, long, 100, 0)
            }
        }

        fun default(key: ScreenProfileKey): NotchProfile {
            val g = defaultGeometry(key)
            return NotchProfile(
                key = key,
                enabled = true,
                widthDp = g.widthDp,
                heightDp = g.heightDp,
                horizontalPercent = g.horizontalPercent,
                verticalDp = g.verticalDp,
                haptic = true,
                hapticMs = 22,
                actions = defaultActions(key.fold),
            )
        }

        private data class Geometry(
            val widthDp: Int,
            val heightDp: Int,
            val horizontalPercent: Int,
            val verticalDp: Int,
        )
    }
}

/** 화면에서 감지한 실제 노치(디스플레이 컷아웃) 정보 */
data class DetectedCutout(
    val leftPx: Int,
    val topPx: Int,
    val rightPx: Int,
    val bottomPx: Int,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val density: Float,
) {
    val widthPx: Int get() = rightPx - leftPx
    val heightPx: Int get() = bottomPx - topPx

    /** 노치가 화면의 어느 변에 붙어 있는지 — UI 안내용 */
    val side: String
        get() {
            val centerX = (leftPx + rightPx) / 2f
            val centerY = (topPx + bottomPx) / 2f
            val fromLeft = centerX
            val fromRight = screenWidthPx - centerX
            val fromTop = centerY
            val fromBottom = screenHeightPx - centerY
            return when (minOf(fromLeft, fromRight, fromTop, fromBottom)) {
                fromTop -> "위쪽"
                fromBottom -> "아래쪽"
                fromLeft -> "왼쪽"
                else -> "오른쪽"
            }
        }
}
