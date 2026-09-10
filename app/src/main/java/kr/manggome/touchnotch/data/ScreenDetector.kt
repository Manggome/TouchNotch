package kr.manggome.touchnotch.data

import android.content.Context
import android.content.res.Configuration
import kr.manggome.touchnotch.model.FoldState
import kr.manggome.touchnotch.model.Orientation
import kr.manggome.touchnotch.model.ScreenProfileKey

/**
 * 지금 어떤 화면 상태인지 판별한다.
 *
 * 폴드 상태는 화면 최소 너비(smallestScreenWidthDp)로 본다. 이 값은 회전과 무관하게
 * 그 화면의 짧은 변을 dp 로 알려주므로 커버 화면 / 메인 화면을 구분하는 데 가장 안정적이다.
 * 방향은 Configuration.orientation 을 그대로 쓴다.
 */
object ScreenDetector {

    fun smallestWidthDp(context: Context): Int =
        context.resources.configuration.smallestScreenWidthDp

    fun foldState(context: Context, thresholdDp: Int): FoldState =
        if (smallestWidthDp(context) >= thresholdDp) FoldState.UNFOLDED else FoldState.FOLDED

    fun foldState(context: Context, store: SettingsStore): FoldState =
        foldState(context, store.foldThresholdDp)

    fun orientation(context: Context): Orientation =
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Orientation.LANDSCAPE
        } else {
            Orientation.PORTRAIT
        }

    fun screenKey(context: Context, thresholdDp: Int): ScreenProfileKey =
        ScreenProfileKey(foldState(context, thresholdDp), orientation(context))

    fun screenKey(context: Context, store: SettingsStore): ScreenProfileKey =
        screenKey(context, store.foldThresholdDp)
}
