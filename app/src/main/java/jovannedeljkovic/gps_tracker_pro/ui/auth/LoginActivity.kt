package jovannedeljkovic.gps_tracker_pro.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import jovannedeljkovic.gps_tracker_pro.App
import jovannedeljkovic.gps_tracker_pro.databinding.ActivityLoginBinding
import jovannedeljkovic.gps_tracker_pro.ui.admin.AdminActivity
import jovannedeljkovic.gps_tracker_pro.ui.main.MainActivity
import jovannedeljkovic.gps_tracker_pro.utils.PasswordHasher
import kotlinx.coroutines.launch
import jovannedeljkovic.gps_tracker_pro.utils.AdminManager
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        checkForSecretAdminAccess()
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

        // SECRET CODE: dugo klik na "Registruj se" za admin access
        binding.tvRegisterLink.setOnLongClickListener {
            showSecretAdminAccess()
            true
        }

        // SECRET CODE: dugo klik na email polje
        binding.etEmail.setOnLongClickListener {
            showSecretAdminAccess()
            true
        }

        // SECRET CODE: dugo klik na "Prijavi se" dugme
        binding.btnLogin.setOnLongClickListener {
            showSecretAdminAccess()
            true
        }
    }

    private fun checkForSecretAdminAccess() {
        val sharedPreferences = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        val hasAdminAccess = sharedPreferences.getBoolean("has_admin_access", false)

        if (hasAdminAccess) {
            // Ako već ima admin pristup, otvori admin panel direktno
            val intent = Intent(this, AdminActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun showSecretAdminAccess() {
        val editText = EditText(this).apply {
            hint = "Unesi secret admin kod"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🔐 Secret Admin Access")
            .setMessage("Unesi secret kod za admin pristup:")
            .setView(editText)
            .setPositiveButton("Unesi") { dialog, _ ->
                val code = editText.text.toString().trim()
                if (AdminManager.isValidSecretCode(code)) {
                    enableAdminFeatures()
                } else {
                    Toast.makeText(this, "❌ Pogrešan kod!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun enableAdminFeatures() {
        val sharedPreferences = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("has_admin_access", true).apply()

        Toast.makeText(this, "✅ Admin pristup omogućen!", Toast.LENGTH_LONG).show()

        // Otvori Admin panel direktno
        val intent = Intent(this, AdminActivity::class.java)
        startActivity(intent)
        finish()
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

                // KORISTI ORIGINALNU METODU (sada je modifikovana sa admin proverom)
                val user = app.userRepository.loginUser(email, hashedPassword)

                runOnUiThread {
                    if (user != null) {
                        // SAČUVAJ KORISNIČKI EMAIL U SHARED PREFERENCES
                        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
                        sharedPreferences.edit().putString("user_email", email).apply()

                        Toast.makeText(
                            this@LoginActivity,
                            "Uspešno ste se prijavili!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Proveri da li je admin i prebaci na odgovarajuću aktivnost
                        if (user.role == "ADMIN" || AdminManager.isMasterAdmin(email)) {
                            // Admin ide direktno u Admin panel
                            val intent = Intent(this@LoginActivity, AdminActivity::class.java)
                            startActivity(intent)
                        } else {
                            // Običan korisnik ide u MainActivity
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            startActivity(intent)
                        }
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
} // OVO JE ZATVARAJUĆA ZAGRADA KOJA JE NEDOSTAJALA