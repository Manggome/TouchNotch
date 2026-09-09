package kr.manggome.touchnotch.data

import android.content.Context
import android.content.SharedPreferences
import kr.manggome.touchnotch.model.FoldState
import kr.manggome.touchnotch.model.Gesture
import kr.manggome.touchnotch.model.NotchAction
import kr.manggome.touchnotch.model.NotchProfile

/**
 * 설정 저장소. SharedPreferences 하나에 프로필별 접두어(folded_/unfolded_)로 저장한다.
 * 액티비티에서 값을 바꾸면 접근성 서비스가 리스너로 즉시 반영한다.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------- 프로필 ----------------

    fun profile(state: FoldState): NotchProfile {
        val d = NotchProfile.default(state)
        val p = state.key
        return NotchProfile(
            state = state,
            enabled = prefs.getBoolean("${p}_enabled", d.enabled),
            widthDp = prefs.getInt("${p}_width", d.widthDp),
            heightDp = prefs.getInt("${p}_height", d.heightDp),
            horizontalPercent = prefs.getInt("${p}_hpos", d.horizontalPercent),
            verticalDp = prefs.getInt("${p}_vpos", d.verticalDp),
            haptic = prefs.getBoolean("${p}_haptic", d.haptic),
            hapticMs = prefs.getInt("${p}_haptic_ms", d.hapticMs),
            actions = Gesture.entries.associateWith { g ->
                NotchAction.from(prefs.getString("${p}_action_${g.key}", d.actionFor(g).key))
            },
        )
    }

    fun save(profile: NotchProfile) {
        val p = profile.state.key
        prefs.edit().apply {
            putBoolean("${p}_enabled", profile.enabled)
            putInt("${p}_width", profile.widthDp)
            putInt("${p}_height", profile.heightDp)
            putInt("${p}_hpos", profile.horizontalPercent)
            putInt("${p}_vpos", profile.verticalDp)
            putBoolean("${p}_haptic", profile.haptic)
            putInt("${p}_haptic_ms", profile.hapticMs)
            profile.actions.forEach { (g, a) -> putString("${p}_action_${g.key}", a.key) }
        }.apply()
    }

    fun resetProfile(state: FoldState) = save(NotchProfile.default(state))

    /** 드래그로 위치만 갱신할 때 쓰는 경량 저장 */
    fun savePosition(state: FoldState, horizontalPercent: Int, verticalDp: Int) {
        val p = state.key
        prefs.edit()
            .putInt("${p}_hpos", horizontalPercent)
            .putInt("${p}_vpos", verticalDp)
            .apply()
    }

    // ---------------- 전역 설정 ----------------

    /** 이 값보다 화면 최소 너비(dp)가 크면 "펼쳤을 때"로 판단 */
    var foldThresholdDp: Int
        get() = prefs.getInt(KEY_FOLD_THRESHOLD, DEFAULT_FOLD_THRESHOLD)
        set(v) = prefs.edit().putInt(KEY_FOLD_THRESHOLD, v).apply()

    /** 화면 녹화 시 마이크 소리도 함께 녹음 */
    var recordAudio: Boolean
        get() = prefs.getBoolean(KEY_RECORD_AUDIO, true)
        set(v) = prefs.edit().putBoolean(KEY_RECORD_AUDIO, v).apply()

    /** 업데이트 자동 확인 */
    var autoCheckUpdate: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_UPDATE, v).apply()

    var skippedVersion: String?
        get() = prefs.getString(KEY_SKIPPED_VERSION, null)
        set(v) = prefs.edit().putString(KEY_SKIPPED_VERSION, v).apply()

    // ---------------- 변경 알림 ----------------

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val PREFS = "touchnotch_settings"
        private const val KEY_FOLD_THRESHOLD = "fold_threshold_dp"
        private const val KEY_RECORD_AUDIO = "record_audio"
        private const val KEY_AUTO_UPDATE = "auto_check_update"
        private const val KEY_SKIPPED_VERSION = "skipped_version"

        /** 갤럭시 Z 폴드: 커버 ≈ 344dp, 메인 ≈ 690dp → 480dp 기준이면 안전하게 갈린다 */
        const val DEFAULT_FOLD_THRESHOLD = 480
    }
}
