package com.absensiapp.school.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.absensiapp.school.R
import com.absensiapp.school.data.repository.AppRepository
import com.absensiapp.school.databinding.ActivityLoginBinding
import com.absensiapp.school.ui.dashboard.DashboardActivity
import com.absensiapp.school.ui.scanner.CardScannerActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = AppRepository.getInstance(this)

        // Ensure user is not automatically logged out when exiting and re-entering the app
        val activeSession = repository.getCurrentSession()
        if (activeSession != null) {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        binding.btnNavigateScanner.text = android.text.Html.fromHtml(
            getString(R.string.register_prompt),
            android.text.Html.FROM_HTML_MODE_LEGACY
        )
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            handleLogin()
        }

        binding.btnNavigateScanner.setOnClickListener {
            val intent = Intent(this, CardScannerActivity::class.java)
            startActivity(intent)
        }

        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Silakan hubungi administrator sekolah untuk reset password.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleLogin() {
        val id = binding.etUserId.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()

        if (id.isEmpty()) {
            binding.tilUserId.error = "Mohon masukkan username atau NIS"
            return
        }
        binding.tilUserId.error = null

        if (password.isEmpty()) {
            binding.tilPassword.error = "Mohon masukkan kata sandi"
            return
        }
        binding.tilPassword.error = null

        binding.btnLogin.isEnabled = false
        binding.tvErrorMessage.visibility = View.GONE

        lifecycleScope.launch {
            val result = repository.login(id, password)
            binding.btnLogin.isEnabled = true

            if (result.isSuccess) {
                val user = result.getOrNull()
                Toast.makeText(
                    this@LoginActivity,
                    "Selamat datang, ${user?.name} (${user?.role?.displayName})",
                    Toast.LENGTH_SHORT
                ).show()

                val intent = Intent(this@LoginActivity, DashboardActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                binding.tvErrorMessage.text = result.exceptionOrNull()?.message ?: "Gagal masuk ke sistem."
                binding.tvErrorMessage.visibility = View.VISIBLE
            }
        }
    }
}
