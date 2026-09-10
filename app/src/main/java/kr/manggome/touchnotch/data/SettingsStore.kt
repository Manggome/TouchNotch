package kr.manggome.touchnotch.data

import android.content.Context
import android.content.SharedPreferences
import kr.manggome.touchnotch.model.FoldState
import kr.manggome.touchnotch.model.Gesture
import kr.manggome.touchnotch.model.NotchAction
import kr.manggome.touchnotch.model.NotchProfile
import kr.manggome.touchnotch.model.Orientation
import kr.manggome.touchnotch.model.ScreenProfileKey

/**
 * 설정 저장소. SharedPreferences 하나에 프로필별 접두어로 저장한다.
 * 접두어는 `<폴드상태>_<방향>_` 형태다. (예: `folded_portrait_width`)
 *
 * 액티비티에서 값을 바꾸면 접근성 서비스가 리스너로 즉시 반영한다.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        migrateIfNeeded()
    }

    // ---------------- 프로필 ----------------

    fun profile(key: ScreenProfileKey): NotchProfile {
        val d = NotchProfile.default(key)
        val p = key.prefix
        return NotchProfile(
            key = key,
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
        val p = profile.key.prefix
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

    fun resetProfile(key: ScreenProfileKey) = save(NotchProfile.default(key))

    /** 드래그로 위치만 갱신할 때 쓰는 경량 저장 */
    fun savePosition(key: ScreenProfileKey, horizontalPercent: Int, verticalDp: Int) {
        val p = key.prefix
        prefs.edit()
            .putInt("${p}_hpos", horizontalPercent)
            .putInt("${p}_vpos", verticalDp)
            .apply()
    }

    /**
     * 한 프로필의 제스처 동작·촉각 피드백을 나머지 세 프로필에 복사한다.
     * 크기·위치는 화면마다 달라야 하므로 건드리지 않는다.
     */
    fun copyActionsToOthers(from: ScreenProfileKey) {
        val source = profile(from)
        ScreenProfileKey.ALL.filter { it != from }.forEach { target ->
            save(
                profile(target).copy(
                    actions = source.actions,
                    haptic = source.haptic,
                    hapticMs = source.hapticMs,
                )
            )
        }
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

    /** 재부팅 후 서비스가 살아있지 않으면 알림으로 알려준다 */
    var notifyIfNotRunning: Boolean
        get() = prefs.getBoolean(KEY_BOOT_NOTIFY, true)
        set(v) = prefs.edit().putBoolean(KEY_BOOT_NOTIFY, v).apply()

    /** 접근성 서비스가 마지막으로 연결된 시각 (재부팅 확인용) */
    var lastConnectedAt: Long
        get() = prefs.getLong(KEY_LAST_CONNECTED, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_CONNECTED, v).apply()

    // ---------------- 변경 알림 ----------------

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(l)

    // ---------------- 마이그레이션 ----------------

    /**
     * v1 은 폴드 상태만 구분해서 `folded_*` / `unfolded_*` 로 저장했다.
     * v2 부터 방향까지 나누므로 기존 값을 세로 프로필로 옮긴다.
     * 가로 프로필에는 크기·위치를 빼고 동작·진동만 물려준다 (화면이 돌아가면 크기가 뒤집히므로).
     */
    private fun migrateIfNeeded() {
        if (prefs.getInt(KEY_SCHEMA, 0) >= SCHEMA_VERSION) return

        val snapshot = prefs.all
        val editor = prefs.edit()

        for (fold in FoldState.entries) {
            val oldPrefix = "${fold.key}_"
            for ((oldKey, value) in snapshot) {
                if (!oldKey.startsWith(oldPrefix)) continue
                val suffix = oldKey.removePrefix(oldPrefix)
                // 이미 새 형식이면 건너뛴다
                if (Orientation.entries.any { suffix.startsWith("${it.key}_") }) continue

                put(editor, "${fold.key}_${Orientation.PORTRAIT.key}_$suffix", value)
                if (suffix == "enabled" || suffix.startsWith("haptic") || suffix.startsWith("action_")) {
                    put(editor, "${fold.key}_${Orientation.LANDSCAPE.key}_$suffix", value)
                }
                editor.remove(oldKey)
            }
        }

        editor.putInt(KEY_SCHEMA, SCHEMA_VERSION).apply()
    }

    private fun put(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            else -> Unit
        }
    }

    companion object {
        const val PREFS = "touchnotch_settings"
        private const val KEY_SCHEMA = "schema_version"
        private const val SCHEMA_VERSION = 2
        private const val KEY_FOLD_THRESHOLD = "fold_threshold_dp"
        private const val KEY_RECORD_AUDIO = "record_audio"
        private const val KEY_AUTO_UPDATE = "auto_check_update"
        private const val KEY_BOOT_NOTIFY = "boot_notify"
        private const val KEY_LAST_CONNECTED = "last_connected_at"

        /** 갤럭시 Z 폴드: 커버 ≈ 344dp, 메인 ≈ 690dp → 480dp 기준이면 안전하게 갈린다 */
        const val DEFAULT_FOLD_THRESHOLD = 480
    }
}
