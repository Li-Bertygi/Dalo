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

    /**
     * 알림 권한 요청 (Android 13/Tiramisu 이상)
     * 포그라운드 서비스를 위해 알림 권한이 필수적입니다.
     */
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
        // 앱이 포그라운드 상태인지 추적하는 변수
        // 다운로드 완료 시 앱이 백그라운드라면 종료하기 위해 사용됩니다.
        @Volatile var isInForeground: Boolean = false
    }

    // UI 컴포넌트 변수 선언
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

    // 현재 선택된 모드 (0: 오디오, 1: 비디오)
    private var selectedMode: Int = 0
    // 다운로드 진행 중 UI 잠금 상태
    private var uiLocked: Boolean = false

    // ---------------------------
    // 브로드캐스트 리시버: 진행률 업데이트
    // ---------------------------
    private val downloadProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val p = intent?.getIntExtra(DownloadService.EXTRA_PROGRESS, 0) ?: 0
            setDownloadProgress(p.toFloat())
        }
    }

    // ---------------------------
    // 브로드캐스트 리시버: 다운로드 완료 처리
    // ---------------------------
    private val downloadDoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val result = intent?.getStringExtra(DownloadService.EXTRA_RESULT) ?: "ERR:unknown"

            // UI 잠금 해제
            setUiLocked(false)

            if (result.startsWith("ERR:")) {
                // 에러 발생 시 다이얼로그 표시 및 진행률 초기화
                showErrorDialog(result)
                setDownloadProgress(0f)
            } else {
                // 성공 시 체크 아이콘 표시 (진행률 100% 처리)
                setDownloadProgress(100f)
            }

            // 완료 후 버튼 선택 상태(알파값 등) 복구
            applySelectionUI()

            // 앱이 백그라운드에 있다면 작업 완료 후 프로세스 완전 종료
            if (!isInForeground) {
                finishAndRemoveTask()
            }
        }
    }

    // ---------------------------
    // 유틸리티: URL 정리
    // ---------------------------
    private fun sanitizeYoutubeUrl(raw: String): String {
        // 유튜브 공유 링크에 붙는 트래킹 파라미터(?si=...) 제거
        val s = raw.trim()
        val idx = s.lastIndexOf("?si=") // 뒤에서부터 탐색
        return if (idx >= 0) s.substring(0, idx) else s
    }

    // ---------------------------
    // 메뉴 구성
    // ---------------------------
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        settingsMenuItem = menu.findItem(R.id.action_settings)
        // 다운로드 중에는 설정 메뉴 접근 불가
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
    // 액티비티 생명주기
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

        // 권한 체크
        ensureNotificationPermission()

        // 서비스로부터 오는 브로드캐스트 리시버 등록
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

        // Chaquopy 파이썬 환경 초기화
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // 뷰 바인딩
        buttonMusic = findViewById(R.id.button1)
        buttonVideo = findViewById(R.id.button2)
        buttonDownload = findViewById(R.id.button3)
        btnSettings = findViewById(R.id.btnSettings)
        inputText = findViewById(R.id.inputText)
        btnClear = findViewById(R.id.btnClear)
        circleProgress = findViewById(R.id.circleProgress)
        progressText = findViewById(R.id.progressText)
        progressCheck = findViewById(R.id.progressCheck)

        // 입력창 초기화 버튼
        btnClear.setOnClickListener {
            if (uiLocked) return@setOnClickListener
            inputText.setText("")
        }

        // 초기 UI 상태 설정
        applySelectionUI()
        setDownloadProgress(0f)

        // 음악(오디오) 모드 선택
        buttonMusic.setOnClickListener {
            if (uiLocked) return@setOnClickListener
            selectedMode = 0
            applySelectionUI()
        }

        // 동영상 모드 선택
        buttonVideo.setOnClickListener {
            if (uiLocked) return@setOnClickListener
            selectedMode = 1
            applySelectionUI()
        }

        // 설정 화면 이동
        btnSettings.setOnClickListener {
            if (uiLocked) return@setOnClickListener
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 다운로드 버튼 클릭
        buttonDownload.setOnClickListener {
            if (uiLocked) return@setOnClickListener

            val url = sanitizeYoutubeUrl(inputText.text.toString())
            if (url.isBlank()) {
                showErrorDialog("URL을 입력하세요.")
                return@setOnClickListener
            }

            // 다운로드 시작: UI 잠금 및 초기화
            setUiLocked(true)
            setDownloadProgress(0f)

            // 포그라운드 서비스 시작 (백그라운드 다운로드 보장)
            val svc = Intent(this, DownloadService::class.java).apply {
                putExtra(DownloadService.EXTRA_URL, url)
                putExtra(DownloadService.EXTRA_MODE, selectedMode)
            }
            ContextCompat.startForegroundService(this, svc)
        }
    }

    override fun onDestroy() {
        // 리시버 해제 (예외 처리 포함)
        try { unregisterReceiver(downloadProgressReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(downloadDoneReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    // ---------------------------
    // UI 헬퍼 메소드
    // ---------------------------

    /**
     * 버튼 선택 상태 UI 적용
     * 선택된 버튼은 불투명(1.0), 선택되지 않은 버튼은 반투명(0.4) 처리합니다.
     * UI가 잠겨있지 않을 때만 변경됩니다.
     */
    private fun applySelectionUI() {
        val selectedAlpha = 1.0f
        val unselectedAlpha = 0.4f

        if (!uiLocked) {
            buttonMusic.alpha = if (selectedMode == 0) selectedAlpha else unselectedAlpha
            buttonVideo.alpha = if (selectedMode == 1) selectedAlpha else unselectedAlpha
        }
    }

    /**
     * 다운로드 중 UI 잠금/해제
     * locked가 true면 모든 입력을 차단하고 버튼을 반투명하게 만듭니다.
     * false면 입력을 활성화하고 버튼 상태를 복구합니다.
     */
    private fun setUiLocked(locked: Boolean) {
        uiLocked = locked
        // 옵션 메뉴(설정 등) 갱신 트리거
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
            // 잠금 해제 시 기본 버튼들은 완전 불투명
            buttonDownload.alpha = 1f
            btnSettings.alpha = 1f
            inputText.alpha = 1f
            btnClear.alpha = 1f

            // 모드 선택 버튼은 현재 선택 상태에 따라 알파값 적용
            applySelectionUI()
        }
    }

    /**
     * 다운로드 진행률 업데이트 (원형 인디케이터)
     * 100% 도달 시 숫자를 숨기고 완료 체크 아이콘을 표시합니다.
     */
    private fun setDownloadProgress(p: Float) {
        val clamped = p.coerceIn(0f, 100f)
        val intP = floor(clamped).toInt()

        circleProgress.progress = intP

        val done = (intP >= 100)

        // 완료 여부에 따라 체크 아이콘/텍스트 전환
        progressCheck.visibility = if (done) View.VISIBLE else View.INVISIBLE
        progressText.visibility = if (done) View.INVISIBLE else View.VISIBLE

        if (!done) {
            progressText.text = "${intP}%"
        }
    }

    /**
     * 에러 다이얼로그 표시
     * ErrorDialogFragment 사용을 우선하되 실패 시 토스트 메시지로 대체합니다.
     */
    private fun showErrorDialog(message: String) {
        try {
            ErrorDialogFragment.newInstance(message)
                .show(supportFragmentManager, "error_dialog")
        } catch (_: Exception) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}