package com.rytm.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rytm.app.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    private val quotes = listOf(
        "Consistency is what transforms average into excellence.",
        "Small steps every day lead to big results.",
        "Your future is created by what you do today, not tomorrow.",
        "Motivation gets you started. Habit keeps you going.",
        "Success is the sum of small efforts, repeated day in and day out.",
        "Don't stop until you're proud.",
        "Focus on the step in front of you, not the whole staircase.",
        "The secret of your future is hidden in your daily routine.",
        "Discipline is choosing between what you want now and what you want most.",
        "Believe you can and you're halfway there."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show a random motivational quote
        binding.tvQuote.text = quotes.random()

        // Transition to MainActivity after a short delay
        lifecycleScope.launch {
            delay(2500) // 2.5 seconds
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}

