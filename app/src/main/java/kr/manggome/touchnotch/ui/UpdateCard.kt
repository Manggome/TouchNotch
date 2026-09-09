package kr.manggome.touchnotch.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.manggome.touchnotch.data.SettingsStore
import kr.manggome.touchnotch.update.UpdateState
import kr.manggome.touchnotch.update.Updater

@Composable
fun UpdateCard(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var autoCheck by remember { mutableStateOf(store.autoCheckUpdate) }

    fun check(showUpToDate: Boolean) {
        state = UpdateState.Checking
        scope.launch {
            Updater.fetchLatest().fold(
                onSuccess = { release ->
                    state = when {
                        Updater.isNewer(release.version) -> UpdateState.Available(release)
                        showUpToDate -> UpdateState.UpToDate(Updater.currentVersion)
                        else -> UpdateState.Idle
                    }
                },
                onFailure = { error ->
                    state = if (showUpToDate) {
                        UpdateState.Failed(error.message ?: "업데이트를 확인할 수 없습니다")
                    } else {
                        UpdateState.Idle
                    }
                },
            )
        }
    }

    // 앱을 열 때 한 번 조용히 확인한다
    LaunchedEffect(Unit) {
        if (store.autoCheckUpdate) check(showUpToDate = false)
    }

    SectionCard(
        title = "업데이트",
        subtitle = "GitHub · ${Updater.repo}",
        icon = Icons.Filled.CloudDownload,
    ) {
        InfoRow("설치된 버전", Updater.currentVersion)

        when (val s = state) {
            is UpdateState.Available -> {
                InfoRow("최신 버전", s.release.version)
                if (s.release.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        s.release.notes.take(600),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is UpdateState.ReadyToInstall -> InfoRow("내려받은 버전", s.release.version)
            is UpdateState.UpToDate -> InfoRow("상태", "최신 버전입니다")
            is UpdateState.Failed -> Text(
                s.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            is UpdateState.Downloading -> {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { s.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "내려받는 중 ${s.percent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> Unit
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (val s = state) {
                is UpdateState.Available -> {
                    Button(
                        onClick = {
                            scope.launch {
                                state = UpdateState.Downloading(0)
                                Updater.download(context, s.release) { percent ->
                                    state = UpdateState.Downloading(percent)
                                }.fold(
                                    onSuccess = { file ->
                                        state = UpdateState.ReadyToInstall(file, s.release)
                                        Updater.install(context, file).onFailure {
                                            state = UpdateState.Failed(
                                                "설치 화면을 열 수 없습니다. ‘알 수 없는 앱 설치’ 권한을 허용해주세요"
                                            )
                                        }
                                    },
                                    onFailure = { error ->
                                        state = UpdateState.Failed(
                                            error.message ?: "다운로드에 실패했습니다"
                                        )
                                    },
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("업데이트 (${s.release.version})") }
                }

                is UpdateState.ReadyToInstall -> {
                    Button(
                        onClick = { Updater.install(context, s.file) },
                        modifier = Modifier.weight(1f),
                    ) { Text("설치하기") }
                }

                is UpdateState.Checking -> {
                    CircularProgressIndicator(Modifier.height(22.dp))
                    Spacer(Modifier.height(0.dp))
                    Text("확인 중…", style = MaterialTheme.typography.bodySmall)
                }

                is UpdateState.Downloading -> {
                    Text("잠시만 기다려주세요…", style = MaterialTheme.typography.bodySmall)
                }

                else -> {
                    Button(
                        onClick = { check(showUpToDate = true) },
                        modifier = Modifier.weight(1f),
                    ) { Text("업데이트 확인") }
                }
            }

            if (state !is UpdateState.Checking && state !is UpdateState.Downloading) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(Updater.releasesPageUrl))
                        )
                    }.onFailure { context.toast("브라우저를 열 수 없습니다") }
                }) { Text("릴리스") }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        SwitchRow(
            title = "앱 실행 시 자동 확인",
            checked = autoCheck,
            onCheckedChange = {
                autoCheck = it
                store.autoCheckUpdate = it
            },
        )
        Column {
            Text(
                "GitHub 저장소에 코드를 올리면 자동으로 새 APK가 릴리스되고, 여기서 바로 받아 설치할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${Updater.repo}"))
                    )
                }
            }) { Text("저장소 열기", fontWeight = FontWeight.Medium) }
        }
    }
}
