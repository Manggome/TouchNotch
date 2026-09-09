package kr.manggome.touchnotch.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kr.manggome.touchnotch.data.SettingsStore
import kr.manggome.touchnotch.model.FoldState
import kr.manggome.touchnotch.model.Gesture as NotchGesture
import kr.manggome.touchnotch.model.NotchAction
import kr.manggome.touchnotch.model.NotchProfile
import kr.manggome.touchnotch.service.NotchAccessibilityService
import kr.manggome.touchnotch.service.NotchAccessibilityService.Companion.MAX_HEIGHT_DP
import kr.manggome.touchnotch.service.NotchAccessibilityService.Companion.MAX_VERTICAL_DP
import kr.manggome.touchnotch.service.NotchAccessibilityService.Companion.MAX_WIDTH_DP
import kr.manggome.touchnotch.service.NotchAccessibilityService.Companion.MIN_HEIGHT_DP
import kr.manggome.touchnotch.service.NotchAccessibilityService.Companion.MIN_WIDTH_DP

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onRequestRuntimePermissions: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val configuration = LocalConfiguration.current

    var serviceRunning by remember { mutableStateOf(NotchAccessibilityService.isRunning) }
    var refreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val listener: () -> Unit = {
            serviceRunning = NotchAccessibilityService.isRunning
            refreshTick += 1
        }
        NotchAccessibilityService.addStateListener(listener)
        onDispose { NotchAccessibilityService.removeStateListener(listener) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceRunning = NotchAccessibilityService.isRunning
                refreshTick += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var threshold by remember { mutableIntStateOf(store.foldThresholdDp) }
    val smallestWidthDp = configuration.smallestScreenWidthDp
    val currentFold =
        if (smallestWidthDp >= threshold) FoldState.UNFOLDED else FoldState.FOLDED

    var selected by remember { mutableStateOf(currentFold) }
    LaunchedEffect(currentFold) { selected = currentFold }

    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(editing, selected, serviceRunning) {
        NotchAccessibilityService.instance?.setEditTarget(if (editing) selected else null)
    }
    DisposableEffect(Unit) {
        onDispose { NotchAccessibilityService.instance?.setEditTarget(null) }
    }

    val profileState = rememberProfile(store, selected)
    val profile = profileState.value
    fun update(transform: (NotchProfile) -> NotchProfile) {
        val next = transform(profileState.value)
        profileState.value = next
        store.save(next)
    }

    var cutoutText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(serviceRunning, refreshTick, smallestWidthDp) {
        val cutout = NotchAccessibilityService.instance?.detectCutout()
        cutoutText = cutout?.let {
            val w = (it.widthPx / it.density).toInt()
            val h = (it.heightPx / it.density).toInt()
            "감지됨 · ${w}×${h}dp · 화면 상단 ${(it.topPx / it.density).toInt()}dp 지점"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("액션노치", fontWeight = FontWeight.Bold)
                    Text(
                        "노치 터치로 빠른 동작",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------- 1. 서비스 상태 ----------
            ServiceStatusCard(
                running = serviceRunning,
                onOpenSettings = {
                    context.openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                },
            )

            // ---------- 2. 현재 화면 상태 ----------
            SectionCard(
                title = "현재 화면",
                subtitle = "폴드 상태에 따라 설정이 자동으로 바뀝니다",
                icon = Icons.Filled.PhoneAndroid,
            ) {
                InfoRow("지금 상태", currentFold.label)
                InfoRow("화면 최소 너비", "${smallestWidthDp}dp")
                Spacer(Modifier.height(6.dp))
                IntSlider(
                    label = "펼침 판정 기준",
                    value = threshold,
                    range = 200..800,
                    valueText = "${threshold}dp 이상이면 펼침",
                    onValueChange = {
                        threshold = it
                        store.foldThresholdDp = it
                    },
                )
                Text(
                    "지금 화면의 최소 너비(${smallestWidthDp}dp)가 기준값보다 크면 ‘펼쳤을 때’ 설정을 씁니다. " +
                        "접었을 때와 펼쳤을 때 각각 이 화면을 열어 값을 확인하면 정확한 기준을 찾을 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------- 3. 프로필 선택 ----------
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                FoldState.entries.forEachIndexed { index, state ->
                    SegmentedButton(
                        selected = selected == state,
                        onClick = { selected = state },
                        shape = SegmentedButtonDefaults.itemShape(index, FoldState.entries.size),
                        label = {
                            Text(
                                if (state == currentFold) "${state.shortLabel} ●" else state.shortLabel
                            )
                        },
                    )
                }
            }
            if (selected != currentFold) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "지금은 ‘${currentFold.shortLabel}’ 상태예요. 크기·위치는 해당 화면에서 조정하면 정확합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------- 4. 내 노치 찾기 ----------
            SectionCard(
                title = "내 노치 찾기",
                subtitle = selected.label,
                icon = Icons.Filled.CenterFocusStrong,
            ) {
                Text(
                    cutoutText ?: "이 화면에서 노치(펀치홀)를 자동으로 찾지 못했습니다. 아래 ‘위치 직접 조정’으로 맞춰주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cutoutText != null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val service = NotchAccessibilityService.instance
                            val ok = service?.autoFitToCutout(selected) == true
                            context.toast(
                                if (ok) "노치 위치에 맞췄습니다" else "노치를 자동으로 찾지 못했습니다"
                            )
                            refreshTick += 1
                        },
                        enabled = serviceRunning && selected == currentFold,
                        modifier = Modifier.weight(1f),
                    ) { Text("자동으로 맞추기") }

                    OutlinedButton(
                        onClick = { editing = !editing },
                        enabled = serviceRunning && selected == currentFold,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (editing) "조정 끝내기" else "위치 직접 조정") }
                }
                if (editing) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "화면 위에 파란 영역이 표시됩니다. 손가락으로 끌어서 노치에 딱 맞춘 뒤 ‘조정 끝내기’를 눌러주세요. " +
                            "조정 중에는 제스처가 동작하지 않습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (!serviceRunning) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "먼저 접근성 서비스를 켜주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ---------- 5. 터치 영역 ----------
            SectionCard(
                title = "터치 영역",
                subtitle = "${selected.shortLabel} 설정",
                icon = Icons.Filled.Tune,
            ) {
                SwitchRow(
                    title = "이 화면에서 사용",
                    subtitle = if (profile.enabled) "노치 터치가 동작합니다" else "노치 터치를 끕니다",
                    checked = profile.enabled,
                    onCheckedChange = { checked -> update { it.copy(enabled = checked) } },
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                IntSlider(
                    label = "노치 크기 · 가로",
                    value = profile.widthDp,
                    range = MIN_WIDTH_DP..MAX_WIDTH_DP,
                    valueText = "${profile.widthDp}dp",
                    onValueChange = { v -> update { it.copy(widthDp = v) } },
                )
                IntSlider(
                    label = "노치 크기 · 세로",
                    value = profile.heightDp,
                    range = MIN_HEIGHT_DP..MAX_HEIGHT_DP,
                    valueText = "${profile.heightDp}dp",
                    onValueChange = { v -> update { it.copy(heightDp = v) } },
                )
                IntSlider(
                    label = "수평 위치",
                    value = profile.horizontalPercent,
                    range = -100..100,
                    valueText = when {
                        profile.horizontalPercent == 0 -> "가운데"
                        profile.horizontalPercent < 0 -> "왼쪽 ${-profile.horizontalPercent}%"
                        else -> "오른쪽 ${profile.horizontalPercent}%"
                    },
                    onValueChange = { v -> update { it.copy(horizontalPercent = v) } },
                )
                IntSlider(
                    label = "수직 위치",
                    value = profile.verticalDp,
                    range = 0..MAX_VERTICAL_DP,
                    valueText = "위에서 ${profile.verticalDp}dp",
                    onValueChange = { v -> update { it.copy(verticalDp = v) } },
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchRow(
                    title = "촉각 피드백",
                    subtitle = "누르면 진동으로 알려줍니다",
                    checked = profile.haptic,
                    onCheckedChange = { checked ->
                        update { it.copy(haptic = checked) }
                        if (checked) NotchAccessibilityService.instance?.previewHaptic(profile.hapticMs)
                    },
                )
                if (profile.haptic) {
                    IntSlider(
                        label = "진동 세기",
                        value = profile.hapticMs,
                        range = 5..80,
                        valueText = "${profile.hapticMs}ms",
                        onValueChange = { v -> update { it.copy(hapticMs = v) } },
                        onReleased = {
                            NotchAccessibilityService.instance?.previewHaptic(profileState.value.hapticMs)
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    store.resetProfile(selected)
                    profileState.value = store.profile(selected)
                    context.toast("${selected.shortLabel} 설정을 초기화했습니다")
                }) { Text("이 화면 설정 초기화") }
            }

            // ---------- 6. 제스처 → 동작 ----------
            SectionCard(
                title = "제스처 동작",
                subtitle = "${selected.shortLabel} 설정",
                icon = Icons.Filled.Gesture,
            ) {
                NotchGesture.entries.forEach { gesture ->
                    LabeledDropdown(
                        label = gesture.label,
                        options = NotchAction.entries,
                        selected = profile.actionFor(gesture),
                        optionLabel = { it.label },
                        optionHint = { it.hint },
                        onSelect = { action ->
                            update { it.copy(actions = it.actions + (gesture to action)) }
                        },
                    )
                }
                Text(
                    "‘더블 터치’에 동작을 지정하지 않으면 싱글 터치가 더 빠르게 반응합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------- 7. 권한 ----------
            PermissionsCard(
                refreshTick = refreshTick,
                store = store,
                onRequestRuntimePermissions = onRequestRuntimePermissions,
            )

            // ---------- 8. 업데이트 ----------
            UpdateCard(store = store)

            // ---------- 9. 도움말 ----------
            SectionCard(
                title = "알아두면 좋아요",
                icon = Icons.Filled.Info,
            ) {
                listOf(
                    "노치 영역을 너무 크게 잡으면 상태바를 내리기 어려워집니다. 실제 펀치홀보다 살짝 크게만 잡아주세요.",
                    "‘화면 녹화’와 ‘카메라’는 백그라운드에서 화면을 띄워야 하므로 ‘다른 앱 위에 표시’ 권한이 필요합니다.",
                    "‘소리/진동 토글’은 ‘방해 금지 접근’ 권한이 필요합니다.",
                    "‘맨 위로 스크롤’은 현재 화면에서 가장 큰 스크롤 영역을 위로 올립니다.",
                ).forEach { line ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("· ", color = MaterialTheme.colorScheme.primary)
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ServiceStatusCard(running: Boolean, onOpenSettings: () -> Unit) {
    val accent = if (running) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
    SectionCard(
        title = if (running) "동작 중" else "접근성 서비스 꺼짐",
        subtitle = if (running) {
            "노치를 터치해보세요"
        } else {
            "설정 › 접근성 › 설치된 앱 › 액션노치 를 켜주세요"
        },
        icon = if (running) Icons.Filled.CheckCircle else Icons.Filled.Warning,
        accent = accent,
    ) {
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.Accessibility, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (running) "접근성 설정 열기" else "접근성 설정에서 켜기")
        }
    }
}

@Composable
private fun PermissionsCard(
    refreshTick: Int,
    store: SettingsStore,
    onRequestRuntimePermissions: () -> Unit,
) {
    val context = LocalContext.current
    val dndGranted = remember(refreshTick) {
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true
    }
    val overlayGranted = remember(refreshTick) { Settings.canDrawOverlays(context) }
    val installGranted = remember(refreshTick) {
        context.packageManager.canRequestPackageInstalls()
    }
    val notifyGranted = remember(refreshTick) { context.hasPermission(Manifest.permission.POST_NOTIFICATIONS) }
    val micGranted = remember(refreshTick) { context.hasPermission(Manifest.permission.RECORD_AUDIO) }
    var recordAudio by remember { mutableStateOf(store.recordAudio) }

    SectionCard(
        title = "권한",
        subtitle = "필요한 동작에만 켜주세요",
        icon = Icons.Filled.Shield,
    ) {
        PermissionRow(
            title = "방해 금지 접근",
            hint = "소리/진동 토글에 필요",
            granted = dndGranted,
            onClick = { context.openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
        )
        PermissionRow(
            title = "다른 앱 위에 표시",
            hint = "카메라 · 화면 녹화 실행에 필요",
            granted = overlayGranted,
            onClick = {
                context.openSettings(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
            },
        )
        PermissionRow(
            title = "알 수 없는 앱 설치",
            hint = "앱 내 업데이트 설치에 필요",
            granted = installGranted,
            onClick = {
                context.openSettings(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                )
            },
        )
        PermissionRow(
            title = "알림",
            hint = "화면 녹화 중 알림 표시",
            granted = notifyGranted,
            onClick = onRequestRuntimePermissions,
        )
        PermissionRow(
            title = "마이크",
            hint = "화면 녹화에 소리 포함",
            granted = micGranted,
            onClick = onRequestRuntimePermissions,
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SwitchRow(
            title = "녹화에 마이크 소리 포함",
            checked = recordAudio,
            enabled = micGranted,
            onCheckedChange = {
                recordAudio = it
                store.recordAudio = it
            },
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    hint: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (granted) {
            AssistChip(onClick = onClick, label = { Text("허용됨") })
        } else {
            Button(onClick = onClick, shape = RoundedCornerShape(12.dp)) { Text("허용") }
        }
    }
}

@Composable
private fun rememberProfile(
    store: SettingsStore,
    state: FoldState,
): androidx.compose.runtime.MutableState<NotchProfile> {
    val profileState = remember(state) { mutableStateOf(store.profile(state)) }
    DisposableEffect(state) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            profileState.value = store.profile(state)
        }
        store.registerListener(listener)
        onDispose { store.unregisterListener(listener) }
    }
    return profileState
}

// ---------------- 확장 함수 ----------------

internal fun Context.openSettings(action: String, data: Uri? = null) {
    val intent = Intent(action).apply {
        if (data != null) this.data = data
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
        .onFailure { toast("설정 화면을 열 수 없습니다") }
}

internal fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

internal fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

