package com.example.licznikusmiechow

import android.content.Context
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "smile_settings"
        private const val KEY_THRESHOLD = "smile_threshold"

        // zakres progu: [-2.0, +2.0]
        private const val MIN_THRESHOLD = -2.0f
        private const val MAX_THRESHOLD = 2.0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val seekBar: SeekBar = findViewById(R.id.thresholdSeekBar)
        val valueText: TextView = findViewById(R.id.thresholdValueText)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getFloat(KEY_THRESHOLD, 0.0f)

        // zamiana progu (-2..2) na progress (0..100)
        fun thresholdToProgress(th: Float): Int {
            val ratio = (th - MIN_THRESHOLD) / (MAX_THRESHOLD - MIN_THRESHOLD)
            return (ratio * 100f).toInt().coerceIn(0, 100)
        }

        fun progressToThreshold(progress: Int): Float {
            val ratio = progress / 100f
            return MIN_THRESHOLD + ratio * (MAX_THRESHOLD - MIN_THRESHOLD)
        }

        seekBar.max = 100
        seekBar.progress = thresholdToProgress(current)
        valueText.text = String.format("Próg: %.2f", current)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val th = progressToThreshold(progress)
                valueText.text = String.format("Próg: %.2f", th)

                prefs.edit().putFloat(KEY_THRESHOLD, th).apply()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }
}
