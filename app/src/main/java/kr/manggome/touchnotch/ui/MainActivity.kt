package kr.manggome.touchnotch.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kr.manggome.touchnotch.service.NotchAccessibilityService

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TouchNotchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(onRequestRuntimePermissions = ::requestRuntimePermissions)
                }
            }
        }
        requestRuntimePermissions()
    }

    override fun onStop() {
        super.onStop()
        // 앱을 벗어나면 '위치 조정' 표시가 화면에 남지 않도록 편집 모드를 끈다
        NotchAccessibilityService.instance?.setEditTarget(null)
    }

    private fun requestRuntimePermissions() {
        val needed = listOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO)
            .filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}
