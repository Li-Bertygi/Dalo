package com.Libertygi.dalo

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.radiobutton.MaterialRadioButton

/**
 * 설정 화면 액티비티
 * 사용자가 다운로드할 오디오 품질, 비디오 해상도, 프레임 레이트(FPS)를 설정합니다.
 * 설정값은 SharedPreferences에 즉시 저장되어 다음 다운로드 시 반영됩니다.
 */
class SettingsActivity : AppCompatActivity() {

    // 설정을 저장할 SharedPreferences 파일 이름
    private val PREF_NAME = "ytdlp_settings"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // 툴바 설정 (뒤로가기 버튼 활성화)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // SharedPreferences 인스턴스 가져오기
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // -------------------------------------------------------
        // 1. 오디오 품질 설정 (Low / Mid / High)
        // -------------------------------------------------------
        val rbAudioLow  = findViewById<MaterialRadioButton>(R.id.rbAudioLow)
        val rbAudioMid  = findViewById<MaterialRadioButton>(R.id.rbAudioMid)
        val rbAudioHigh = findViewById<MaterialRadioButton>(R.id.rbAudioHigh)

        // 저장된 값 불러오기 (기본값: high)
        // low: 64kbps 이하, mid: 128kbps 이하, high: 원본 품질
        when (prefs.getString("audio_quality", "high")) {
            "low"  -> rbAudioLow.isChecked = true
            "mid"  -> rbAudioMid.isChecked = true
            else   -> rbAudioHigh.isChecked = true
        }

        // 변경 리스너 등록: 선택 시 즉시 저장
        rbAudioLow.setOnCheckedChangeListener { _, c ->
            if (c) prefs.edit().putString("audio_quality", "low").apply()
        }
        rbAudioMid.setOnCheckedChangeListener { _, c ->
            if (c) prefs.edit().putString("audio_quality", "mid").apply()
        }
        rbAudioHigh.setOnCheckedChangeListener { _, c ->
            if (c) prefs.edit().putString("audio_quality", "high").apply()
        }

        // -------------------------------------------------------
        // 2. 비디오 해상도 설정 (360p ~ 2160p/4K)
        // -------------------------------------------------------
        // 해상도 값(Int)과 라디오 버튼 ID(Int)를 매핑하여 관리
        val resolutionMap = mapOf(
            360  to R.id.rb360,
            480  to R.id.rb480,
            720  to R.id.rb720,
            1080 to R.id.rb1080,
            1440 to R.id.rb1440,
            2160 to R.id.rb2160
        )

        // 저장된 해상도 불러오기 (기본값: 1080p)
        val savedRes = prefs.getInt("video_resolution", 1080)

        // 저장된 값에 해당하는 버튼이 UI에 존재하면 체크, 없으면 기본값(1080) 체크
        findViewById<MaterialRadioButton>(
            resolutionMap[savedRes] ?: R.id.rb1080
        ).isChecked = true

        // 모든 해상도 버튼에 대해 리스너 일괄 등록
        resolutionMap.forEach { (res, id) ->
            findViewById<MaterialRadioButton>(id)
                .setOnCheckedChangeListener { _, c ->
                    if (c) prefs.edit().putInt("video_resolution", res).apply()
                }
        }

        // -------------------------------------------------------
        // 3. 비디오 프레임 레이트 (FPS) 설정
        // -------------------------------------------------------
        val rbFps30 = findViewById<MaterialRadioButton>(R.id.rbFps30)
        val rbFps60 = findViewById<MaterialRadioButton>(R.id.rbFps60)

        // 저장된 FPS 불러오기 (기본값: 60fps)
        when (prefs.getInt("video_fps", 60)) {
            30 -> rbFps30.isChecked = true
            else -> rbFps60.isChecked = true
        }

        // 변경 리스너 등록
        rbFps30.setOnCheckedChangeListener { _, c ->
            if (c) prefs.edit().putInt("video_fps", 30).apply()
        }
        rbFps60.setOnCheckedChangeListener { _, c ->
            if (c) prefs.edit().putInt("video_fps", 60).apply()
        }
    }
}