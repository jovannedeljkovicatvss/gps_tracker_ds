package jovannedeljkovic.gps_tracker_pro.ui.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import jovannedeljkovic.gps_tracker_pro.App
import jovannedeljkovic.gps_tracker_pro.data.entities.User
import jovannedeljkovic.gps_tracker_pro.databinding.ActivityAdminBinding
import jovannedeljkovic.gps_tracker_pro.ui.auth.LoginActivity
import jovannedeljkovic.gps_tracker_pro.utils.FeatureManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private lateinit var usersAdapter: UsersAdapter
    private val usersList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadUsers()
        setupClickListeners()
    }

    // ✅ ISPRAVNO: Ne pozivamo super odmah, već kada korisnik potvrdi
    override fun onBackPressed() {
        // Prvo pozovite super, ali dodajte svoju logiku pre ili posle
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Admin Panel")
            .setMessage("Da li želite da izađete iz admin moda?")
            .setPositiveButton("DA, izloguj me") { _, _ ->
                // Ova logika će se izvršiti kada korisnik potvrdi
                logoutAndExit()
            }
            .setNegativeButton("NE, ostani", null)
            .setOnCancelListener {
                // Kada korisnik otkaže dijalog, dozvoli normalan back
                super.onBackPressed()
            }
            .show()
    }

    private fun showExitConfirmationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Admin Panel")
            .setMessage("Da li želite da izađete iz admin moda?")
            .setPositiveButton("DA, izloguj me") { _, _ ->
                logoutAndExit()
            }
            .setNegativeButton("NE, ostani", null)
            .show()
    }

    private fun logoutAndExit() {
        // 1. Očisti admin pristup
        getSharedPreferences("admin_prefs", MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        // 2. Kreiraj intent za LoginActivity
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        // 3. Pokreni LoginActivity
        startActivity(intent)

        // 4. Zatvori ovu aktivnost
        finish()

        Toast.makeText(this, "Admin mod isključen", Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        usersAdapter = UsersAdapter(usersList) { user ->
            showUserOptionsDialog(user)
        }
        binding.rvUsers.apply {
            layoutManager = LinearLayoutManager(this@AdminActivity)
            adapter = usersAdapter
        }
    }

    private fun loadUsers() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val users = app.userRepository.getAllUsers()

                usersList.clear()
                usersList.addAll(users)

                runOnUiThread {
                    usersAdapter.notifyDataSetChanged()
                    binding.tvUserCount.text = "Ukupno korisnika: ${users.size}"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@AdminActivity, "Greška pri učitavanju korisnika: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUserOptionsDialog(user: User) {
        val options = arrayOf(
            "Postavi kao ADMIN",
            "Postavi kao PREMIUM",
            "Postavi kao BASIC",
            "Obriši korisnika",
            "Podaci o korisniku"
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Upravljanje korisnikom")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> setUserRole(user, "ADMIN")
                    1 -> setUserRole(user, "PREMIUM")
                    2 -> setUserRole(user, "BASIC")
                    3 -> deleteUser(user)
                    4 -> showUserDetails(user)
                }
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun setUserRole(user: User, newRole: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val success = when (newRole) {
                    "ADMIN" -> app.userRepository.setUserAsAdmin(user.id)
                    "PREMIUM" -> app.userRepository.upgradeToPremium(user.id, 30)
                    "BASIC" -> app.userRepository.downgradeToBasic(user.id)
                    else -> false
                }

                runOnUiThread {
                    if (success) {
                        Toast.makeText(this@AdminActivity, "Korisnik ${user.email} sada je $newRole", Toast.LENGTH_LONG).show()
                        loadUsers()
                    } else {
                        Toast.makeText(this@AdminActivity, "Greška pri promeni uloge", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@AdminActivity, "Greška: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteUser(user: User) {
        Toast.makeText(this, "Brisanje korisnika će biti implementirano kasnije", Toast.LENGTH_SHORT).show()
    }

    private fun showUserDetails(user: User) {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val createdDate = dateFormat.format(Date(user.createdAt))

        val premiumInfo = if (user.role == "PREMIUM" && user.premiumExpiry > 0) {
            val expiryDate = dateFormat.format(Date(user.premiumExpiry))
            "Premium ističe: $expiryDate"
        } else {
            "Nema premium"
        }

        val message = """
            📧 Email: ${user.email}
            👤 Ime: ${user.name}
            📞 Telefon: ${user.phone}
            👑 Uloga: ${FeatureManager.getUserRoleDisplayName(user)}
            📅 Kreiran: $createdDate
            💎 $premiumInfo
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Detalji korisnika")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            // Pozovi istu metodu kao za back dugme
            showExitConfirmationDialog()
        }

        binding.btnRefresh.setOnClickListener {
            loadUsers()
        }
    }
}