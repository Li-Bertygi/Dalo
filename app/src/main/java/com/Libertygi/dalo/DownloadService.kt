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
        // MainActivity -> Service
        const val EXTRA_URL = "extra_url"
        const val EXTRA_MODE = "extra_mode"

        // Service -> MainActivity (완료)
        const val ACTION_DONE = "com.Libertygi.dalo.DOWNLOAD_DONE"
        const val EXTRA_RESULT = "extra_result"

        // Service -> MainActivity (진행률)
        const val ACTION_PROGRESS = "com.Libertygi.dalo.DOWNLOAD_PROGRESS"
        const val EXTRA_PROGRESS = "extra_progress"

        private const val CHANNEL_ID = "ytdlp_download"
        private const val NOTI_ID = 1001
    }

    /**
     * Chaquopy 쪽에서 콜백으로 호출될 인터페이스.
     * main.py에서 cb.onProgress(0~100) 형태로 호출해야 함.
     */
    interface ProgressCallback {
        fun onProgress(p: Int)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()

        // Activity가 백그라운드여도 Service는 단독으로 뜰 수 있으므로 여기서도 Python 초기화
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: ""
        val mode = intent?.getIntExtra(EXTRA_MODE, 0) ?: 0

        if (url.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // ✅ Foreground는 가능한 빨리 시작해야 OS가 kill 안 함
        startForeground(NOTI_ID, buildProgressNotification(0, "다운로드 준비 중..."))

        Thread {
            val result = runYtDlpAndSave(url, mode)

            val success = !result.startsWith("ERR:")
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                NOTI_ID,
                buildDoneNotification(
                    if (success) "다운로드 완료" else "다운로드 실패",
                    success
                )
            )

            // ✅ 완료 브로드캐스트
            sendBroadcast(Intent(ACTION_DONE).apply {
                setPackage(packageName)
                putExtra(EXTRA_RESULT, result)
            })

            // stopForeground 호환
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
    // Notification
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

    private fun buildProgressNotification(progress: Int, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Dalo 다운로드")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true) // ✅ 진행 중: 고정
            .setProgress(100, progress.coerceIn(0, 100), false) // ✅ 진행바 표시
            .build()
    }

    private fun buildDoneNotification(text: String, success: Boolean): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Dalo 다운로드")
            .setContentText(text)
            .setOnlyAlertOnce(false)
            .setOngoing(false)     // ✅ 완료: 고정 해제
            .setAutoCancel(true)   // ✅ 탭하면 사라짐
            .setProgress(0, 0, false) // ✅ 진행바 제거
            .build()
    }


    private fun updateProgressNotification(progress: Int, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTI_ID, buildProgressNotification(progress, text))
    }

    private fun broadcastProgress(p: Int) {
        sendBroadcast(Intent(ACTION_PROGRESS).apply {
            setPackage(packageName)
            putExtra(EXTRA_PROGRESS, p.coerceIn(0, 100))
        })
    }

    // ----------------------------
    // Download + Save
    // ----------------------------
    private fun runYtDlpAndSave(url: String, mode: Int): String {
        return try {
            val workDir = File(filesDir, "ytdlp_work").apply { mkdirs() }

            val prefs = getSharedPreferences("ytdlp_settings", MODE_PRIVATE)
            val audioQuality = prefs.getString("audio_quality", "high") ?: "high"
            val videoRes = prefs.getInt("video_resolution", 1080)
            val videoFps = prefs.getInt("video_fps", 60)

            val py = Python.getInstance()
            val module = py.getModule("main")

            val cb = object : ProgressCallback {
                override fun onProgress(p: Int) {
                    val pp = p.coerceIn(0, 100)
                    updateProgressNotification(pp, "다운로드 중... ($pp%)")
                    broadcastProgress(pp)
                }
            }

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

            // ✅ main.py 결과 포맷: 줄 단위 KEY=VALUE
            if (mode == 0) {
                // AUDIO
                val path = getLineValue(result, "FILE")
                val title = getLineValue(result, "FINAL_TITLE").ifBlank { "music" }
                val src = File(path)
                if (!src.exists()) return "ERR:audio file not found"

                saveToDownloads(src, title, isAudio = true)
                src.delete()

                // UI도 100% 보장(혹시 cb가 100 못 보냈을 때)
                broadcastProgress(100)
                return result
            }

            if (result.startsWith("OK_SINGLE")) {
                // VIDEO SINGLE
                val path = getLineValue(result, "FILE")
                val title = getLineValue(result, "FINAL_TITLE").ifBlank { "video" }
                val src = File(path)
                if (!src.exists()) return "ERR:video file not found"

                saveToDownloads(src, title, isAudio = false)
                src.delete()

                broadcastProgress(100)
                return result
            }

            // VIDEO SPLIT (OK_SPLIT)
            val videoPath = getLineValue(result, "VIDEO")
            val audioPath = getLineValue(result, "AUDIO")
            val title = getLineValue(result, "FINAL_TITLE").ifBlank { "video" }

            val vFile = File(videoPath)
            val aFile = File(audioPath)
            if (!vFile.exists() || !aFile.exists()) return "ERR:split files not found"

            val merged = File(workDir, "$title.mp4")
            muxMp4(videoPath, audioPath, merged.absolutePath)

            saveToDownloads(merged, title, isAudio = false)

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
    // Parse: KEY=VALUE per line
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
    // Save to Downloads (MediaStore)
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
    // Mux MP4
    // ----------------------------
    private fun muxMp4(videoPath: String, audioPath: String, outPath: String) {
        val muxer = MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val vEx = MediaExtractor().apply { setDataSource(videoPath) }
        val aEx = MediaExtractor().apply { setDataSource(audioPath) }

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

        copy(vEx, outV)
        copy(aEx, outA)

        muxer.stop()
        muxer.release()
        vEx.release()
        aEx.release()
    }
}
