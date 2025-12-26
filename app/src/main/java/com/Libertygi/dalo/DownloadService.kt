package com.Libertygi.dalo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

class DownloadService : Service() {

    companion object {
        // MainActivity에서 서비스로 전달하는 데이터 키
        const val EXTRA_URL = "extra_url"
        const val EXTRA_MODE = "extra_mode"

        // 서비스에서 MainActivity로 보내는 완료 브로드캐스트 액션 및 키
        const val ACTION_DONE = "com.Libertygi.dalo.DOWNLOAD_DONE"
        const val EXTRA_RESULT = "extra_result"

        // 서비스에서 MainActivity로 보내는 진행률 브로드캐스트 액션 및 키
        const val ACTION_PROGRESS = "com.Libertygi.dalo.DOWNLOAD_PROGRESS"
        const val EXTRA_PROGRESS = "extra_progress"

        // 알림 채널 및 ID 상수
        private const val CHANNEL_ID = "ytdlp_download"
        private const val NOTI_ID = 1001
    }

    /**
     * 파이썬 스크립트(main.py)로부터 진행률 콜백을 받기 위한 인터페이스
     * Chaquopy를 통해 파이썬 코드에서 이 인터페이스의 메소드를 호출합니다.
     */
    interface ProgressCallback {
        fun onProgress(p: Int)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 알림 채널 생성 (Android 8.0 이상 필수)
        ensureChannel()

        // 서비스가 앱의 진입점이 될 수도 있으므로 여기서도 Python 엔진을 초기화합니다.
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: ""
        val mode = intent?.getIntExtra(EXTRA_MODE, 0) ?: 0

        // URL이 없으면 서비스 종료
        if (url.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 포그라운드 서비스 시작: 시스템에 의해 프로세스가 종료되는 것을 방지합니다.
        startForeground(NOTI_ID, buildProgressNotification(0, "다운로드 준비 중..."))

        // 네트워크 작업 및 파일 처리는 메인 스레드(UI 스레드)를 차단하지 않도록 별도 스레드에서 수행합니다.
        Thread {
            val result = runYtDlpAndSave(url, mode)

            val success = !result.startsWith("ERR:")
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 작업 완료 후 결과 알림 표시
            nm.notify(
                NOTI_ID,
                buildDoneNotification(
                    if (success) "다운로드 완료" else "다운로드 실패",
                    success
                )
            )

            // MainActivity 등에 완료 사실을 브로드캐스트로 알림
            sendBroadcast(Intent(ACTION_DONE).apply {
                setPackage(packageName)
                putExtra(EXTRA_RESULT, result)
            })

            // 포그라운드 상태 해제
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }.start()

        return START_NOT_STICKY
    }

    // ----------------------------
    // 알림(Notification) 관리
    // ----------------------------
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Dalo 다운로드",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
    }

    // 진행 중 상태 알림 생성 (진행 바 포함, 삭제 불가)
    private fun buildProgressNotification(progress: Int, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Dalo 다운로드")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .build()
    }

    // 완료 상태 알림 생성 (진행 바 제거, 클릭 시 삭제 가능)
    private fun buildDoneNotification(text: String, success: Boolean): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Dalo 다운로드")
            .setContentText(text)
            .setOnlyAlertOnce(false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .build()
    }

    // 진행률 알림 업데이트
    private fun updateProgressNotification(progress: Int, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTI_ID, buildProgressNotification(progress, text))
    }

    // 진행률 변경 사항을 브로드캐스트로 전송 (UI 업데이트용)
    private fun broadcastProgress(p: Int) {
        sendBroadcast(Intent(ACTION_PROGRESS).apply {
            setPackage(packageName)
            putExtra(EXTRA_PROGRESS, p.coerceIn(0, 100))
        })
    }

    // ----------------------------
    // 다운로드 및 저장 로직
    // ----------------------------
    private fun runYtDlpAndSave(url: String, mode: Int): String {
        return try {
            // 작업용 임시 디렉토리 생성
            val workDir = File(filesDir, "ytdlp_work").apply { mkdirs() }

            // 설정값 불러오기
            val prefs = getSharedPreferences("ytdlp_settings", MODE_PRIVATE)
            val audioQuality = prefs.getString("audio_quality", "high") ?: "high"
            val videoRes = prefs.getInt("video_resolution", 1080)
            val videoFps = prefs.getInt("video_fps", 60)

            val py = Python.getInstance()
            val module = py.getModule("main")

            // 파이썬으로 전달할 콜백 객체
            val cb = object : ProgressCallback {
                override fun onProgress(p: Int) {
                    val pp = p.coerceIn(0, 100)
                    updateProgressNotification(pp, "다운로드 중... ($pp%)")
                    broadcastProgress(pp)
                }
            }

            // main.py의 run_ytdlp 함수 호출
            val result = module.callAttr(
                "run_ytdlp",
                url,
                mode,
                workDir.absolutePath,
                cb,
                audioQuality,
                videoRes,
                videoFps,
                null
            ).toString()

            if (result.startsWith("ERR:")) return result

            // 파이썬 실행 결과(문자열)를 파싱하여 처리
            // 1. 오디오 모드
            if (mode == 0) {
                val path = getLineValue(result, "FILE")
                val title = getLineValue(result, "FINAL_TITLE").ifBlank { "music" }
                val src = File(path)
                if (!src.exists()) return "ERR:audio file not found"

                // MediaStore를 통해 공용 다운로드 폴더로 이동
                saveToDownloads(src, title, isAudio = true)
                src.delete()

                broadcastProgress(100)
                return result
            }

            // 2. 비디오 단일 파일 모드 (병합 불필요)
            if (result.startsWith("OK_SINGLE")) {
                val path = getLineValue(result, "FILE")
                val title = getLineValue(result, "FINAL_TITLE").ifBlank { "video" }
                val src = File(path)
                if (!src.exists()) return "ERR:video file not found"

                saveToDownloads(src, title, isAudio = false)
                src.delete()

                broadcastProgress(100)
                return result
            }

            // 3. 비디오 분할 파일 모드 (병합 필요)
            // 고화질 비디오와 오디오가 따로 다운로드된 경우
            val videoPath = getLineValue(result, "VIDEO")
            val audioPath = getLineValue(result, "AUDIO")
            val title = getLineValue(result, "FINAL_TITLE").ifBlank { "video" }

            val vFile = File(videoPath)
            val aFile = File(audioPath)
            if (!vFile.exists() || !aFile.exists()) return "ERR:split files not found"

            val merged = File(workDir, "$title.mp4")
            // Android Native Muxer를 사용하여 병합 수행
            muxMp4(videoPath, audioPath, merged.absolutePath)

            saveToDownloads(merged, title, isAudio = false)

            // 임시 파일 정리
            vFile.delete()
            aFile.delete()
            merged.delete()

            broadcastProgress(100)
            result
        } catch (e: Exception) {
            "ERR:${e.message}"
        }
    }

    // ----------------------------
    // 문자열 파싱 헬퍼 (KEY=VALUE 형태)
    // ----------------------------
    private fun getLineValue(result: String, key: String): String {
        val lines = result.split('\n')
        for (line in lines) {
            if (line.startsWith("$key=")) {
                return line.substring(key.length + 1).trim()
            }
        }
        return ""
    }

    // ----------------------------
    // 파일 저장 (MediaStore)
    // ----------------------------
    private fun saveToDownloads(src: File, title: String, isAudio: Boolean) {
        val ext = src.extension.lowercase(Locale.US).let { if (it.isBlank()) "" else ".$it" }
        val fileName = title + ext
        val mime = guessMime(src.extension, isAudio)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "Download/Dalo/" + if (isAudio) "Music" else "Video"
            )
        }

        // MediaStore에 항목 삽입 및 데이터 복사
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw RuntimeException("MediaStore insert failed")

        contentResolver.openOutputStream(uri).use { out ->
            if (out == null) throw RuntimeException("OutputStream null")
            src.inputStream().use { it.copyTo(out) }
        }
    }

    private fun guessMime(extRaw: String, isAudio: Boolean): String {
        val ext = extRaw.lowercase(Locale.US)
        return when (ext) {
            "mp4" -> "video/mp4"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "webm" -> if (isAudio) "audio/webm" else "video/webm"
            "wav" -> "audio/wav"
            else -> if (isAudio) "audio/*" else "video/*"
        }
    }

    // ----------------------------
    // 미디어 병합 (Muxing)
    // ----------------------------
    private fun muxMp4(videoPath: String, audioPath: String, outPath: String) {
        // MediaMuxer: 비디오와 오디오 스트림을 하나의 MP4 컨테이너로 합칩니다.
        val muxer = MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val vEx = MediaExtractor().apply { setDataSource(videoPath) }
        val aEx = MediaExtractor().apply { setDataSource(audioPath) }

        // 트랙 선택 함수: 비디오 또는 오디오 트랙을 찾아 인덱스와 포맷을 반환
        fun select(ex: MediaExtractor, wantVideo: Boolean): Pair<Int, MediaFormat> {
            for (i in 0 until ex.trackCount) {
                val fmt = ex.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (wantVideo && mime.startsWith("video/")) return Pair(i, fmt)
                if (!wantVideo && mime.startsWith("audio/")) return Pair(i, fmt)
            }
            throw RuntimeException("Track not found")
        }

        val (vTrack, vFmt) = select(vEx, true)
        val (aTrack, aFmt) = select(aEx, false)

        vEx.selectTrack(vTrack)
        aEx.selectTrack(aTrack)

        val outV = muxer.addTrack(vFmt)
        val outA = muxer.addTrack(aFmt)

        muxer.start()

        val buf = ByteBuffer.allocate(512 * 1024)
        val info = android.media.MediaCodec.BufferInfo()

        // 데이터를 읽어서 Muxer에 쓰는 함수
        fun copy(ex: MediaExtractor, outTrack: Int) {
            while (true) {
                info.offset = 0
                info.size = ex.readSampleData(buf, 0)
                if (info.size < 0) break
                info.presentationTimeUs = ex.sampleTime
                info.flags = ex.sampleFlags
                muxer.writeSampleData(outTrack, buf, info)
                ex.advance()
            }
        }

        // 비디오 트랙 복사
        copy(vEx, outV)
        // 오디오 트랙 복사
        copy(aEx, outA)

        muxer.stop()
        muxer.release()
        vEx.release()
        aEx.release()
    }
}