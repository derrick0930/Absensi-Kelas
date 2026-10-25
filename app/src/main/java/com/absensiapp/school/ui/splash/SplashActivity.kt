package com.absensiapp.school.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.absensiapp.school.data.repository.AppRepository
import com.absensiapp.school.databinding.ActivitySplashBinding
import com.absensiapp.school.ui.dashboard.DashboardActivity
import com.absensiapp.school.ui.login.LoginActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AppRepository.getInstance(applicationContext)

        lifecycleScope.launch {
            delay(1200)
            val currentUser = repository.getCurrentSession()
            val targetIntent = if (currentUser != null) {
                Intent(this@SplashActivity, DashboardActivity::class.java)
            } else {
                Intent(this@SplashActivity, LoginActivity::class.java)
            }
            startActivity(targetIntent)
            finish()
        }
    }
}
