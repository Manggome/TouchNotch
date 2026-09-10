package kr.manggome.touchnotch.model

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

/** 화면 방향 */
enum class Orientation(val key: String, val label: String, val shortLabel: String) {
    PORTRAIT("portrait", "세로 모드", "세로"),
    LANDSCAPE("landscape", "가로 모드", "가로");

    companion object {
        fun from(key: String?): Orientation = entries.firstOrNull { it.key == key } ?: PORTRAIT
    }
}

/**
 * 설정을 나누는 기준. 폴드 상태 × 화면 방향 = 4개의 독립 프로필.
 *
 * 노치는 기기에 물리적으로 고정돼 있어서 화면을 돌리면 화면 좌표계에서의 위치가
 * 완전히 달라진다. 그래서 방향별로 크기·위치를 따로 저장해야 한다.
 */
data class ScreenProfileKey(
    val fold: FoldState,
    val orientation: Orientation,
) {
    val prefix: String get() = "${fold.key}_${orientation.key}"

    /** "접었을 때 · 세로 모드" */
    val label: String get() = "${fold.shortLabel} · ${orientation.label}"

    /** "접었을 때 세로" */
    val shortLabel: String get() = "${fold.shortLabel} ${orientation.shortLabel}"

    companion object {
        val ALL: List<ScreenProfileKey> = FoldState.entries.flatMap { fold ->
            Orientation.entries.map { orientation -> ScreenProfileKey(fold, orientation) }
        }
    }
}

/** 한 화면 상태(폴드 × 방향)에 대한 전체 설정 */
data class NotchProfile(
    val key: ScreenProfileKey,
    val enabled: Boolean,
    val widthDp: Int,
    val heightDp: Int,
    /** -100(왼쪽 끝) ~ 0(중앙) ~ 100(오른쪽 끝) */
    val horizontalPercent: Int,
    /** 화면 최상단에서의 거리(dp) */
    val verticalDp: Int,
    val haptic: Boolean,
    val hapticMs: Int,
    val actions: Map<Gesture, NotchAction>,
) {
    fun actionFor(gesture: Gesture): NotchAction = actions[gesture] ?: NotchAction.NONE

    companion object {
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

        fun default(key: ScreenProfileKey): NotchProfile {
            // 세로에서는 노치가 화면 위쪽 가로로 눕고, 가로에서는 화면 옆쪽 세로로 선다
            val (width, height, horizontal, vertical) = when (key.orientation) {
                Orientation.PORTRAIT -> when (key.fold) {
                    FoldState.FOLDED -> Geometry(96, 30, 0, 0)
                    FoldState.UNFOLDED -> Geometry(110, 34, 72, 0)
                }

                Orientation.LANDSCAPE -> when (key.fold) {
                    FoldState.FOLDED -> Geometry(30, 96, -100, 60)
                    FoldState.UNFOLDED -> Geometry(34, 110, -100, 60)
                }
            }
            return NotchProfile(
                key = key,
                enabled = true,
                widthDp = width,
                heightDp = height,
                horizontalPercent = horizontal,
                verticalDp = vertical,
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
}
