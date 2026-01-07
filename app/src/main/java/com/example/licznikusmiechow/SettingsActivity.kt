package com.example.licznikusmiechow

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "smile_settings"
        private const val KEY_THRESHOLD = "smile_threshold"

        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val MIN_THRESHOLD = -2.0f
        private const val MAX_THRESHOLD = 2.0f
        private const val STEP = 0.04f
    }

    private lateinit var seekBar: SeekBar
    private lateinit var valueText: TextView

    private val stepsCount = ((MAX_THRESHOLD - MIN_THRESHOLD) / STEP).roundToInt() // 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        seekBar = findViewById(R.id.thresholdSeekBar)
        valueText = findViewById(R.id.thresholdValueText)

        val btnMinus: Button = findViewById(R.id.btnMinus)
        val btnPlus: Button = findViewById(R.id.btnPlus)
        val btnReset: Button = findViewById(R.id.btnReset)

        val soundSwitch: android.widget.Switch = findViewById(R.id.soundSwitch)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedThreshold = prefs.getFloat(KEY_THRESHOLD, 0.0f)

        seekBar.max = stepsCount
        seekBar.progress = thresholdToProgress(savedThreshold)
        updateText(progressToThreshold(seekBar.progress))

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progressToThreshold(progress)
                updateText(value)
                prefs.edit().putFloat(KEY_THRESHOLD, value).apply()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnMinus.setOnClickListener {
            seekBar.progress = (seekBar.progress - 1).coerceAtLeast(0)
        }

        btnPlus.setOnClickListener {
            seekBar.progress = (seekBar.progress + 1).coerceAtMost(stepsCount)
        }

        btnReset.setOnClickListener {
            seekBar.progress = thresholdToProgress(0.0f)
        }
        // ---- DŹWIĘKI ON/OFF ----
        soundSwitch.isChecked =
            prefs.getBoolean(KEY_SOUND_ENABLED, true)

        soundSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(KEY_SOUND_ENABLED, isChecked)
                .apply()
        }
    }

    private fun progressToThreshold(progress: Int): Float {
        return MIN_THRESHOLD + progress * STEP
    }

    private fun thresholdToProgress(value: Float): Int {
        return ((value - MIN_THRESHOLD) / STEP).roundToInt().coerceIn(0, stepsCount)
    }

    private fun updateText(value: Float) {
        valueText.text = String.format("Próg: %.2f", value)
    }
}
