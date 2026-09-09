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

/** 폴더블 상태 — 접었을 때 / 펼쳤을 때 프로필을 따로 저장한다 */
enum class FoldState(val key: String, val label: String, val shortLabel: String) {
    FOLDED("folded", "접었을 때 (커버 화면)", "접었을 때"),
    UNFOLDED("unfolded", "펼쳤을 때 (메인 화면)", "펼쳤을 때");

    companion object {
        fun from(key: String?): FoldState = entries.firstOrNull { it.key == key } ?: UNFOLDED
    }
}

/** 한쪽 화면 상태에 대한 전체 설정 */
data class NotchProfile(
    val state: FoldState,
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
        fun default(state: FoldState): NotchProfile = when (state) {
            FoldState.FOLDED -> NotchProfile(
                state = state,
                enabled = true,
                widthDp = 96,
                heightDp = 30,
                horizontalPercent = 0,
                verticalDp = 0,
                haptic = true,
                hapticMs = 22,
                actions = mapOf(
                    Gesture.SINGLE_TAP to NotchAction.NONE,
                    Gesture.DOUBLE_TAP to NotchAction.FLASHLIGHT,
                    Gesture.LONG_PRESS to NotchAction.POWER_MENU,
                    Gesture.SWIPE_LEFT to NotchAction.SOUND_TOGGLE,
                    Gesture.SWIPE_RIGHT to NotchAction.SCREENSHOT,
                ),
            )

            FoldState.UNFOLDED -> NotchProfile(
                state = state,
                enabled = true,
                widthDp = 110,
                heightDp = 34,
                horizontalPercent = 72,
                verticalDp = 0,
                haptic = true,
                hapticMs = 22,
                actions = mapOf(
                    Gesture.SINGLE_TAP to NotchAction.NONE,
                    Gesture.DOUBLE_TAP to NotchAction.FLASHLIGHT,
                    Gesture.LONG_PRESS to NotchAction.POWER_MENU,
                    Gesture.SWIPE_LEFT to NotchAction.SCROLL_TOP,
                    Gesture.SWIPE_RIGHT to NotchAction.SCREENSHOT,
                ),
            )
        }
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
