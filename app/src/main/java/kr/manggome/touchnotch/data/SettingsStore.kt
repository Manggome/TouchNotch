package kr.manggome.touchnotch.data

import android.content.Context
import android.content.SharedPreferences
import kr.manggome.touchnotch.model.FoldState
import kr.manggome.touchnotch.model.Gesture
import kr.manggome.touchnotch.model.NotchAction
import kr.manggome.touchnotch.model.NotchProfile
import kr.manggome.touchnotch.model.ScreenProfileKey
import kr.manggome.touchnotch.model.ScreenRotation

/**
 * 설정 저장소. SharedPreferences 하나에 프로필별 접두어로 저장한다.
 * 접두어는 `<폴드상태>_<회전>_` 형태다. (예: `folded_rot90_width`)
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

    /** 이 프로필이 한 번이라도 저장된 적 있는지 (없으면 서비스가 컷아웃에 자동으로 맞춘다) */
    fun hasProfile(key: ScreenProfileKey): Boolean =
        prefs.contains("${key.prefix}_width")

    /** 드래그로 위치만 갱신할 때 쓰는 경량 저장 */
    fun savePosition(key: ScreenProfileKey, horizontalPercent: Int, verticalDp: Int) {
        val p = key.prefix
        prefs.edit()
            .putInt("${p}_hpos", horizontalPercent)
            .putInt("${p}_vpos", verticalDp)
            .apply()
    }

    /**
     * 한 프로필의 제스처 동작·촉각 피드백을 나머지 프로필 전체에 복사한다.
     * 크기·위치는 화면·회전마다 달라야 하므로 건드리지 않는다.
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
     * 저장 스키마 변경 이력.
     *
     * v1 — 폴드 상태만 구분: `folded_*`, `unfolded_*`
     * v2 — 방향 추가: `folded_portrait_*`, `folded_landscape_*`
     * v3 — 회전 4방향으로 확장: `folded_rot0_*` … `unfolded_rot270_*`
     *
     * 회전마다 노치가 화면의 다른 변으로 가므로 크기·위치는 물려줄 수 없다.
     * 대응되는 회전(세로→0°, 가로→90°)에만 그대로 옮기고, 나머지 회전에는
     * 동작·진동 설정만 복사한 뒤 크기·위치는 기본값에서 다시 잡게 한다.
     */
    private fun migrateIfNeeded() {
        var schema = prefs.getInt(KEY_SCHEMA, 0)
        if (schema >= SCHEMA_VERSION) return

        if (schema < 2) {
            migrateV1ToV2()
            schema = 2
        }
        if (schema < 3) {
            migrateV2ToV3()
            schema = 3
        }
        prefs.edit().putInt(KEY_SCHEMA, schema).apply()
    }

    /** `folded_width` → `folded_portrait_width` */
    private fun migrateV1ToV2() {
        val snapshot = prefs.all
        val editor = prefs.edit()
        for (fold in FoldState.entries) {
            val oldPrefix = "${fold.key}_"
            for ((oldKey, value) in snapshot) {
                if (!oldKey.startsWith(oldPrefix)) continue
                val suffix = oldKey.removePrefix(oldPrefix)
                if (suffix.startsWith("portrait_") || suffix.startsWith("landscape_")) continue
                put(editor, "${fold.key}_portrait_$suffix", value)
                if (isSharableSetting(suffix)) {
                    put(editor, "${fold.key}_landscape_$suffix", value)
                }
                editor.remove(oldKey)
            }
        }
        editor.apply()
    }

    /** `folded_portrait_width` → `folded_rot0_width` (가로는 90°로) */
    private fun migrateV2ToV3() {
        val snapshot = prefs.all
        val editor = prefs.edit()
        val moves = mapOf(
            "portrait" to ScreenRotation.ROTATION_0,
            "landscape" to ScreenRotation.ROTATION_90,
        )
        for (fold in FoldState.entries) {
            for ((oldName, rotation) in moves) {
                val oldPrefix = "${fold.key}_${oldName}_"
                for ((oldKey, value) in snapshot) {
                    if (!oldKey.startsWith(oldPrefix)) continue
                    val suffix = oldKey.removePrefix(oldPrefix)
                    put(editor, "${fold.key}_${rotation.key}_$suffix", value)
                    if (isSharableSetting(suffix)) {
                        // 반대쪽 회전(180°/270°)에는 동작·진동만 물려준다
                        val counterpart = when (rotation) {
                            ScreenRotation.ROTATION_0 -> ScreenRotation.ROTATION_180
                            else -> ScreenRotation.ROTATION_270
                        }
                        put(editor, "${fold.key}_${counterpart.key}_$suffix", value)
                    }
                    editor.remove(oldKey)
                }
            }
        }
        editor.apply()
    }

    /** 크기·위치와 달리 화면 방향에 상관없이 그대로 써도 되는 설정 */
    private fun isSharableSetting(suffix: String): Boolean =
        suffix == "enabled" || suffix.startsWith("haptic") || suffix.startsWith("action_")

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
        private const val SCHEMA_VERSION = 3
        private const val KEY_FOLD_THRESHOLD = "fold_threshold_dp"
        private const val KEY_RECORD_AUDIO = "record_audio"
        private const val KEY_AUTO_UPDATE = "auto_check_update"
        private const val KEY_BOOT_NOTIFY = "boot_notify"
        private const val KEY_LAST_CONNECTED = "last_connected_at"

        /** 갤럭시 Z 폴드: 커버 ≈ 344dp, 메인 ≈ 690dp → 480dp 기준이면 안전하게 갈린다 */
        const val DEFAULT_FOLD_THRESHOLD = 480
    }
}
