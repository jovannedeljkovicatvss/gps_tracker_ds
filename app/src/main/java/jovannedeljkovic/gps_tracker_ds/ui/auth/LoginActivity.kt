package jovannedeljkovic.gps_tracker_ds.ui.auth

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import jovannedeljkovic.gps_tracker_ds.App
import jovannedeljkovic.gps_tracker_ds.utils.PasswordHasher
import jovannedeljkovic.gps_tracker_ds.databinding.ActivityLoginBinding
import jovannedeljkovic.gps_tracker_ds.ui.main.MainActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Login dugme
        binding.btnLogin.setOnClickListener {
            loginUser()
        }

        // Registracija link
        binding.tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        // Zaboravljena lozinka
        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Funkcionalnost u izradi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loginUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        // Validacija
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Unesite email i lozinku", Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Unesite validan email", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val app = application as App
                val hashedPassword = PasswordHasher.hashPassword(password)

                val user = app.userRepository.loginUser(email, hashedPassword)

                runOnUiThread {
                    if (user != null) {
                        // SAČUVAJTE KORISNIČKI EMAIL U SHARED PREFERENCES
                        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
                        sharedPreferences.edit().putString("user_email", email).apply()

                        Toast.makeText(
                            this@LoginActivity,
                            "Uspešno ste se prijavili!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Prebaci na MainActivity
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(
                            this@LoginActivity,
                            "Pogrešan email ili lozinka!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@LoginActivity,
                        "Greška pri prijavi: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}