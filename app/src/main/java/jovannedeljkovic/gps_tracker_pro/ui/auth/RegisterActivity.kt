package jovannedeljkovic.gps_tracker_pro.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import jovannedeljkovic.gps_tracker_pro.App
import jovannedeljkovic.gps_tracker_pro.data.entities.User
import jovannedeljkovic.gps_tracker_pro.databinding.ActivityRegisterBinding
import jovannedeljkovic.gps_tracker_pro.utils.PasswordHasher
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    // U deklaracijama varijabli dodaj:

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Registracija dugme
        binding.btnRegister.setOnClickListener {
            registerUser()
        }

        // Link ka Login
        binding.tvLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()

        // Validacija
        if (!validateInput(email, password, confirmPassword, name, phone)) {
            return
        }

        lifecycleScope.launch {
            try {
                val app = application as App
                val hashedPassword = PasswordHasher.hashPassword(password)

                val user = User(
                    email = email,
                    password = hashedPassword,
                    name = name,
                    phone = phone
                )

                val success = app.userRepository.registerUser(user)

                runOnUiThread {
                    if (success) {
                        // SAČUVAJTE KORISNIČKI EMAIL U SHARED PREFERENCES
                        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
                        sharedPreferences.edit().putString("user_email", email).apply()

                        Toast.makeText(
                            this@RegisterActivity,
                            "Uspešno ste se registrovali!",
                            Toast.LENGTH_LONG
                        ).show()

                        // Prebaci na Login
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(
                            this@RegisterActivity,
                            "Email već postoji!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Greška pri registraciji: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun validateInput(
        email: String,
        password: String,
        confirmPassword: String,
        name: String,
        phone: String
    ): Boolean {
        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Popunite sva obavezna polja", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Unesite validan email", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.length < 6) {
            Toast.makeText(this, "Lozinka mora imati najmanje 6 karaktera", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Lozinke se ne poklapaju", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }
}