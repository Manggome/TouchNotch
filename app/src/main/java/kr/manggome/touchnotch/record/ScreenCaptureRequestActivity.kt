package kr.manggome.touchnotch.record

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 화면 녹화 권한(MediaProjection)만 받아오는 투명 액티비티.
 * 안드로이드는 녹화를 시작할 때마다 사용자 동의를 요구하므로 매번 이 화면을 거친다.
 */
class ScreenCaptureRequestActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenRecordService.start(this, result.resultCode, data)
        } else {
            Toast.makeText(this, "화면 녹화를 취소했습니다", Toast.LENGTH_SHORT).show()
        }
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            Toast.makeText(this, "이 기기에서 화면 녹화를 쓸 수 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        launcher.launch(manager.createScreenCaptureIntent())
    }
}
