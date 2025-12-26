package com.Libertygi.dalo

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.radiobutton.MaterialRadioButton

class SettingsActivity : AppCompatActivity() {

    private val PREF_NAME = "ytdlp_settings"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        /* =======================
           🎵 음악 – 음질
           ======================= */
        val rbAudioLow  = findViewById<MaterialRadioButton>(R.id.rbAudioLow)
        val rbAudioMid  = findViewById<MaterialRadioButton>(R.id.rbAudioMid)
        val rbAudioHigh = findViewById<MaterialRadioButton>(R.id.rbAudioHigh)

        when (prefs.getString("audio_quality", "high")) {
            "low"  -> rbAudioLow.isChecked = true
            "mid"  -> rbAudioMid.isChecked = true
            else   -> rbAudioHigh.isChecked = true   // ✅ 기본값
        }

        rbAudioLow.setOnCheckedChangeListener { _, c ->
            if (c) prefs.edit().putString("audio_quality", "low").apply()
        }
        rbAudioMid.setOnCheckedChangeListener { _, c ->
            if (c) prefs.edit().putString("audio_quality", "mid").apply()
        }
        rbAudioHigh.setOnCheckedChangeListener { _, c ->
            if (c) prefs.edit().putString("audio_quality", "high").apply()
        }

        /* =======================
           🎬 동영상 – 해상도
           ======================= */
        val resolutionMap = mapOf(
            360  to R.id.rb360,
            480  to R.id.rb480,
            720  to R.id.rb720,
            1080 to R.id.rb1080,
            1440 to R.id.rb1440,
            2160 to R.id.rb2160
        )

        val savedRes = prefs.getInt("video_resolution", 1080) // ✅ 기본값
        findViewById<MaterialRadioButton>(
            resolutionMap[savedRes] ?: R.id.rb1080
        ).isChecked = true

        resolutionMap.forEach { (res, id) ->
            findViewById<MaterialRadioButton>(id)
                .setOnCheckedChangeListener { _, c ->
                    if (c) prefs.edit().putInt("video_resolution", res).apply()
                }
        }

        /* =======================
           🎞 FPS
           ======================= */
        val rbFps30 = findViewById<MaterialRadioButton>(R.id.rbFps30)
        val rbFps60 = findViewById<MaterialRadioButton>(R.id.rbFps60)

        when (prefs.getInt("video_fps", 60)) {
            30 -> rbFps30.isChecked = true
            else -> rbFps60.isChecked = true   // ✅ 기본값
        }

        rbFps30.setOnCheckedChangeListener { _, c ->
            if (c) prefs.edit().putInt("video_fps", 30).apply()
        }
        rbFps60.setOnCheckedChangeListener { _, c ->
            if (c) prefs.edit().putInt("video_fps", 60).apply()
        }
    }
}
