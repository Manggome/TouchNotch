package kr.manggome.touchnotch.action

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.util.Log

/** 손전등(토치) 제어. CameraManager.setTorchMode 는 별도 권한이 필요 없다. */
class TorchController(context: Context, handler: Handler) {

    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var torchOn = false
    private var cameraId: String? = null

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == resolveCameraId()) torchOn = enabled
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == resolveCameraId()) torchOn = false
        }
    }

    init {
        runCatching { cameraManager.registerTorchCallback(callback, handler) }
    }

    fun hasFlash(): Boolean = resolveCameraId() != null

    /** 토글. 성공하면 true */
    fun toggle(): Boolean {
        val id = resolveCameraId() ?: return false
        return runCatching {
            val next = !torchOn
            cameraManager.setTorchMode(id, next)
            torchOn = next
            true
        }.getOrElse {
            Log.w(TAG, "setTorchMode 실패", it)
            false
        }
    }

    fun release() {
        runCatching { cameraManager.unregisterTorchCallback(callback) }
    }

    private fun resolveCameraId(): String? {
        cameraId?.let { return it }
        val found = runCatching {
            val ids = cameraManager.cameraIdList
            ids.firstOrNull { id ->
                val c = cameraManager.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: ids.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
        cameraId = found
        return found
    }

    private companion object {
        const val TAG = "TorchController"
    }
}
