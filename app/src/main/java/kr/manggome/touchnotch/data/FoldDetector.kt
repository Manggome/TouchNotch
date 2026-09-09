package kr.manggome.touchnotch.data

import android.content.Context
import kr.manggome.touchnotch.model.FoldState

/**
 * 폴더블 상태 판별.
 *
 * 화면 최소 너비(smallestScreenWidthDp)는 회전과 무관하게 그 화면의 짧은 변을 dp로 알려주므로
 * 커버 화면 / 메인 화면을 구분하는 가장 안정적인 값이다.
 */
object FoldDetector {

    fun smallestWidthDp(context: Context): Int =
        context.resources.configuration.smallestScreenWidthDp

    fun detect(context: Context, thresholdDp: Int): FoldState =
        if (smallestWidthDp(context) >= thresholdDp) FoldState.UNFOLDED else FoldState.FOLDED

    fun detect(context: Context, store: SettingsStore): FoldState =
        detect(context, store.foldThresholdDp)
}
