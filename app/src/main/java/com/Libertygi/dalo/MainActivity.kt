package com.Libertygi.dalo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlin.math.floor
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat


class MainActivity : AppCompatActivity() {
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    companion object {
        @Volatile var isInForeground: Boolean = false
    }

    // UI
    private lateinit var buttonMusic: Button
    private lateinit var buttonVideo: Button
    private lateinit var buttonDownload: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var inputText: EditText
    private lateinit var btnClear: ImageButton
    private lateinit var circleProgress: CircularProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var progressCheck: ImageView

    private var settingsMenuItem: MenuItem? = null

    // 기본: 음악 선택
    private var selectedMode: Int = 0 // 0=audio, 1=video
    private var uiLocked: Boolean = false

    // ---------------------------
    // 진행률 브로드캐스트 수신
    // ---------------------------
    private val downloadProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val p = intent?.getIntExtra(DownloadService.EXTRA_PROGRESS, 0) ?: 0
            setDownloadProgress(p.toFloat())
        }
    }

    // ---------------------------
    // 완료 브로드캐스트 수신
    // ---------------------------
    private val downloadDoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val result = intent?.getStringExtra(DownloadService.EXTRA_RESULT) ?: "ERR:unknown"
            setUiLocked(false)
            if (result.startsWith("ERR:")) {
                showErrorDialog(result)
                setDownloadProgress(0f)
            } else {
                // 완료: 체크만 보이게 처리됨(100% 텍스트 숨김)
                setDownloadProgress(100f)
            }

            // ✅ 완료 후에도 이전 선택 유지 + 알파(1/0.4) 유지
            applySelectionUI()

            // ✅ 완료 시점에 백그라운드였다면 앱(태스크) 종료
            if (!isInForeground) {
                finishAndRemoveTask()
            }
        }
    }

    // ---------------------------
    // ?si= 제거
    // ---------------------------
    private fun sanitizeYoutubeUrl(raw: String): String {
        val s = raw.trim()
        val idx = s.lastIndexOf("?si=") // 뒤에서부터 탐색
        return if (idx >= 0) s.substring(0, idx) else s
    }

    // ---------------------------
    // 메뉴
    // ---------------------------
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        settingsMenuItem = menu.findItem(R.id.action_settings)
        settingsMenuItem?.isEnabled = !uiLocked
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        settingsMenuItem = menu.findItem(R.id.action_settings)
        settingsMenuItem?.isEnabled = !uiLocked
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_settings -> {
                if (!uiLocked) startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    // ---------------------------
    // 라이프사이클
    // ---------------------------
    override fun onStart() {
        super.onStart()
        isInForeground = true
    }

    override fun onStop() {
        super.onStop()
        isInForeground = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ensureNotificationPermission()
        // 브로드캐스트 등록
        ContextCompat.registerReceiver(
            this,
            downloadProgressReceiver,
            IntentFilter(DownloadService.ACTION_PROGRESS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        ContextCompat.registerReceiver(
            this,
            downloadDoneReceiver,
            IntentFilter(DownloadService.ACTION_DONE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Python 초기화(기존 유지)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // bind views
        buttonMusic = findViewById(R.id.button1)
        buttonVideo = findViewById(R.id.button2)
        buttonDownload = findViewById(R.id.button3)
        btnSettings = findViewById(R.id.btnSettings)
        inputText = findViewById(R.id.inputText)
        btnClear = findViewById(R.id.btnClear)
        circleProgress = findViewById(R.id.circleProgress)
        progressText = findViewById(R.id.progressText)
        progressCheck = findViewById(R.id.progressCheck)

        btnClear.setOnClickListener {
            if (uiLocked) return@setOnClickListener
            inputText.setText("")
        }

        // 초기 UI 상태
        applySelectionUI()
        setDownloadProgress(0f)

        buttonMusic.setOnClickListener {
            if (uiLocked) return@setOnClickListener
            selectedMode = 0
            applySelectionUI()
        }

        buttonVideo.setOnClickListener {
            if (uiLocked) return@setOnClickListener
            selectedMode = 1
            applySelectionUI()
        }

        btnSettings.setOnClickListener {
            if (uiLocked) return@setOnClickListener
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        buttonDownload.setOnClickListener {
            if (uiLocked) return@setOnClickListener

            val url = sanitizeYoutubeUrl(inputText.text.toString())
            if (url.isBlank()) {
                showErrorDialog("URL을 입력하세요.")
                return@setOnClickListener
            }

            setUiLocked(true)
            setDownloadProgress(0f)

            // ✅ ForegroundService 시작(백그라운드에서도 다운로드)
            val svc = Intent(this, DownloadService::class.java).apply {
                putExtra(DownloadService.EXTRA_URL, url)
                putExtra(DownloadService.EXTRA_MODE, selectedMode)
            }
            ContextCompat.startForegroundService(this, svc)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(downloadProgressReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(downloadDoneReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    // ---------------------------
    // UI helpers
    // ---------------------------

    /**
     * ✅ 선택된 버튼은 alpha=1.0, 선택되지 않은 버튼은 alpha=0.4
     * ✅ 다운로드 전/후 선택 유지
     */
    private fun applySelectionUI() {
        val selectedAlpha = 1.0f
        val unselectedAlpha = 0.4f

        // uiLocked=true일 때는 setUiLocked에서 모두 0.4로 눌러두고,
        // uiLocked=false로 풀릴 때만 이 규칙이 적용되도록 합니다.
        if (!uiLocked) {
            buttonMusic.alpha = if (selectedMode == 0) selectedAlpha else unselectedAlpha
            buttonVideo.alpha = if (selectedMode == 1) selectedAlpha else unselectedAlpha
        }
    }

    /**
     * ✅ 다운로드 중에는 전체 비활성 + 알파 0.4
     * ✅ 다운로드 종료 후에는 선택 버튼 1.0 / 비선택 0.4 복구
     */
    private fun setUiLocked(locked: Boolean) {
        uiLocked = locked
        invalidateOptionsMenu()

        buttonMusic.isEnabled = !locked
        buttonVideo.isEnabled = !locked
        buttonDownload.isEnabled = !locked
        btnSettings.isEnabled = !locked
        inputText.isEnabled = !locked
        btnClear.isEnabled = !locked
        if (locked) {
            val alpha = 0.4f
            buttonMusic.alpha = alpha
            buttonVideo.alpha = alpha
            buttonDownload.alpha = alpha
            btnSettings.alpha = alpha
            inputText.alpha = alpha
            btnClear.alpha = alpha
        } else {
            // 다운로드 버튼/설정/입력은 정상 알파
            buttonDownload.alpha = 1f
            btnSettings.alpha = 1f
            inputText.alpha = 1f
            btnClear.alpha = 1f

            // 음악/동영상 버튼은 선택 규칙 적용
            applySelectionUI()
        }
    }

    /**
     * ✅ 다운로드 진행도 원형바 실시간 업데이트
     * ✅ 완료 시 체크만 표시하고 "100%" 텍스트는 숨김
     */
    private fun setDownloadProgress(p: Float) {
        val clamped = p.coerceIn(0f, 100f)
        val intP = floor(clamped).toInt()

        circleProgress.progress = intP

        val done = (intP >= 100)

        // 완료면 체크만
        progressCheck.visibility = if (done) View.VISIBLE else View.INVISIBLE
        progressText.visibility = if (done) View.INVISIBLE else View.VISIBLE

        if (!done) {
            progressText.text = "${intP}%"
        }
    }

    private fun showErrorDialog(message: String) {
        try {
            ErrorDialogFragment.newInstance(message)
                .show(supportFragmentManager, "error_dialog")
        } catch (_: Exception) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}
