package kr.manggome.touchnotch.record

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kr.manggome.touchnotch.R
import kr.manggome.touchnotch.data.SettingsStore

/** MediaProjection 으로 화면을 녹화하는 포그라운드 서비스 */
class ScreenRecordService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var outputUri: Uri? = null
    private var pfd: ParcelFileDescriptor? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            handler.post { stopRecording(notifyUser = false); stopSelf() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording(notifyUser = true)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
                val data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                if (resultCode == Int.MIN_VALUE || data == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                // Android 14+: MediaProjection 을 얻기 전에 포그라운드로 올라가 있어야 한다
                startForegroundNotification()
                if (!startRecording(resultCode, data)) {
                    stopRecording(notifyUser = false)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRecording(notifyUser = false)
        super.onDestroy()
    }

    // ---------------- 녹화 ----------------

    private fun startRecording(resultCode: Int, data: Intent): Boolean {
        val manager = getSystemService(MediaProjectionManager::class.java) ?: return false
        val mp = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull()
        if (mp == null) {
            toast("화면 녹화 권한을 받지 못했습니다")
            return false
        }
        projection = mp
        mp.registerCallback(projectionCallback, handler)

        val (width, height) = captureSize()
        val dpi = resources.displayMetrics.densityDpi
        val withAudio = SettingsStore(this).recordAudio && hasAudioPermission()

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName())
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/$FOLDER_NAME")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = runCatching {
            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()
        if (uri == null) {
            toast("저장할 파일을 만들 수 없습니다")
            return false
        }
        outputUri = uri

        val descriptor = runCatching { contentResolver.openFileDescriptor(uri, "w") }.getOrNull()
        if (descriptor == null) {
            toast("저장할 파일을 열 수 없습니다")
            return false
        }
        pfd = descriptor

        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder = rec

        val prepared = runCatching {
            if (withAudio) rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setOutputFile(descriptor.fileDescriptor)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            rec.setVideoSize(width, height)
            rec.setVideoFrameRate(FRAME_RATE)
            rec.setVideoEncodingBitRate(bitrateFor(width, height))
            if (withAudio) {
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(128_000)
                rec.setAudioSamplingRate(44_100)
            }
            rec.prepare()
            true
        }.getOrElse {
            Log.e(TAG, "MediaRecorder 준비 실패", it)
            toast("녹화를 준비할 수 없습니다")
            false
        }
        if (!prepared) return false

        val vd = runCatching {
            mp.createVirtualDisplay(
                "TouchNotchRecord",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                rec.surface,
                null,
                handler,
            )
        }.getOrNull()
        if (vd == null) {
            toast("화면을 캡처할 수 없습니다")
            return false
        }
        virtualDisplay = vd

        return runCatching {
            rec.start()
            isRecording = true
            toast("화면 녹화를 시작합니다")
            true
        }.getOrElse {
            Log.e(TAG, "녹화 시작 실패", it)
            toast("녹화를 시작할 수 없습니다")
            false
        }
    }

    private fun stopRecording(notifyUser: Boolean) {
        val wasRecording = isRecording
        isRecording = false

        recorder?.let { rec ->
            runCatching { if (wasRecording) rec.stop() }
                .onFailure { Log.w(TAG, "recorder.stop 실패", it) }
            runCatching { rec.reset() }
            runCatching { rec.release() }
        }
        recorder = null

        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        projection?.let { mp ->
            runCatching { mp.unregisterCallback(projectionCallback) }
            runCatching { mp.stop() }
        }
        projection = null

        runCatching { pfd?.close() }
        pfd = null

        outputUri?.let { uri ->
            if (wasRecording) {
                runCatching {
                    contentResolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                        null,
                        null,
                    )
                }
                if (notifyUser) toast("녹화를 저장했습니다 · Movies/$FOLDER_NAME")
            } else {
                runCatching { contentResolver.delete(uri, null, null) }
            }
        }
        outputUri = null

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // ---------------- 알림 ----------------

    private fun startForegroundNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "화면 녹화", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
            )
        }
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle("화면 녹화 중")
            .setContentText("노치를 다시 터치하거나 아래 버튼으로 멈출 수 있어요")
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_record, "녹화 중지", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---------------- 도우미 ----------------

    private fun captureSize(): Pair<Int, Int> {
        val bounds = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        var w = bounds.width()
        var h = bounds.height()
        val longSide = maxOf(w, h)
        if (longSide > MAX_LONG_SIDE) {
            val scale = MAX_LONG_SIDE.toFloat() / longSide
            w = (w * scale).toInt()
            h = (h * scale).toInt()
        }
        // 인코더는 짝수 해상도를 요구한다
        return Pair(w - w % 2, h - h % 2)
    }

    private fun bitrateFor(width: Int, height: Int): Int =
        (width.toLong() * height * FRAME_RATE * 0.16).toInt().coerceIn(3_000_000, 20_000_000)

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun fileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
        return "TouchNotch_$stamp.mp4"
    }

    private fun toast(message: String) {
        handler.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val TAG = "ScreenRecordService"
        private const val CHANNEL_ID = "screen_record"
        private const val NOTIFICATION_ID = 4211
        private const val FOLDER_NAME = "TouchNotch"
        private const val FRAME_RATE = 30
        private const val MAX_LONG_SIDE = 1920

        const val ACTION_START = "kr.manggome.touchnotch.START_RECORD"
        const val ACTION_STOP = "kr.manggome.touchnotch.STOP_RECORD"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        @Volatile
        var isRecording: Boolean = false
            private set

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenRecordService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenRecordService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
