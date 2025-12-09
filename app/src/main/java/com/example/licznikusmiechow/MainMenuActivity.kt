package com.example.licznikusmiechow

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button

class MainMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        val startButton: Button = findViewById(R.id.startButton)
        val exitButton: Button = findViewById(R.id.exitButton)
        val settingsButton: Button = findViewById(R.id.settingsButton) // NOWY

        startButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        exitButton.setOnClickListener {
            finishAffinity()
        }
    }
}
