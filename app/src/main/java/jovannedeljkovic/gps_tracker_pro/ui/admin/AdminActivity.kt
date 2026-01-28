package jovannedeljkovic.gps_tracker_pro.ui.admin

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import jovannedeljkovic.gps_tracker_pro.App
import jovannedeljkovic.gps_tracker_pro.data.entities.User
import jovannedeljkovic.gps_tracker_pro.databinding.ActivityAdminBinding
import jovannedeljkovic.gps_tracker_pro.ui.auth.LoginActivity
import jovannedeljkovic.gps_tracker_pro.utils.FeatureManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
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

        // Dodajte dugme za statistiku ako postoji u layout-u
        binding.btnStatistics?.setOnClickListener {
            showUserStatistics()
        }
    }

    // ISPRAVNO: Ne pozivamo super odmah, već kada korisnik potvrdi
    override fun onBackPressed() {
        showExitConfirmationDialog()
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

                withContext(Dispatchers.Main) {
                    usersAdapter.notifyDataSetChanged()
                    binding.tvUserCount.text = "Ukupno korisnika: ${users.size}"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AdminActivity,
                        "Greška pri učitavanju korisnika: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showUserOptionsDialog(user: User) {
        val options = arrayOf(
            "Postavi kao ADMIN",
            "Postavi kao PREMIUM",
            "Postavi kao BASIC",
            "🗑️ Obriši korisnika",
            "👁️ Podaci o korisniku",
            "📊 Statistika korisnika"
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
                    5 -> showUserStatistics()
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

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(
                            this@AdminActivity,
                            "Korisnik ${user.email} sada je $newRole",
                            Toast.LENGTH_LONG
                        ).show()
                        loadUsers()
                    } else {
                        Toast.makeText(
                            this@AdminActivity,
                            "Greška pri promeni uloge",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AdminActivity,
                        "Greška: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun deleteUser(user: User) {
        AlertDialog.Builder(this)
            .setTitle("❌ Brisanje korisnika")
            .setMessage("Da li ste sigurni da želite da obrišete korisnika ${user.email}?\n\n" +
                    "Ova akcija će obrisati:\n" +
                    "• Sve rute korisnika\n" +
                    "• Sve tačke interesa\n" +
                    "• Korisnički nalog\n\n" +
                    "Ova akcija se NE MOŽE poništiti!")
            .setPositiveButton("✅ Obriši") { dialog, which ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val app = application as App

                        // Prvo obrišite rute korisnika
                        val userRoutes = app.routeRepository.getUserRoutes(user.id)
                        userRoutes.forEach { route ->
                            app.routeRepository.deleteRoute(route)
                        }

                        // Zatim obrišite tačke korisnika
                        val userPoints = app.pointRepository.getUserPoints(user.id)
                        userPoints.forEach { point ->
                            app.pointRepository.deletePoint(point)
                        }

                        // Na kraju obrišite korisnika
                        // Dodajte ovu metodu u UserDao:
                        // @Query("DELETE FROM users WHERE id = :userId")
                        // suspend fun deleteUserById(userId: String)

                        // Za sada, možete koristiti workaround:
                        // Ovde možete dodati logiku za brisanje korisnika

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AdminActivity,
                                "⚠️ Brisanje korisnika će biti implementirano u narednoj verziji",
                                Toast.LENGTH_LONG
                            ).show()
                            // loadUsers() // Osveži listu kada implementirate brisanje
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AdminActivity,
                                "❌ Greška: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
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
            📱 Telefon: ${user.phone}
            🎭 Uloga: ${FeatureManager.getUserRoleDisplayName(user)}
            📅 Kreiran: $createdDate
            ⭐ $premiumInfo
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Detalji korisnika")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showUserStatistics() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val allUsers = app.userRepository.getAllUsers()

                // Izračunaj statistike
                val totalUsers = allUsers.size
                val adminCount = allUsers.count { it.role == "ADMIN" }
                val premiumCount = allUsers.count { it.role == "PREMIUM" }
                val basicCount = allUsers.count { it.role == "BASIC" }

                // Prosečno vreme od kreiranja naloga
                val averageAccountAge = allUsers.map {
                    System.currentTimeMillis() - it.createdAt
                }.average() / (1000 * 60 * 60 * 24) // u danima

                // Aktivni premium korisnici (još nije istekla pretplata)
                val activePremium = allUsers.count {
                    it.role == "PREMIUM" && it.premiumExpiry > System.currentTimeMillis()
                }

                withContext(Dispatchers.Main) {
                    val statsMessage = """
                        📊 STATISTIKA KORISNIKA
                        
                        👥 Ukupno korisnika: $totalUsers
                        
                        🎭 Distribucija uloga:
                           👑 Admin: $adminCount
                           ⭐ Premium: $premiumCount (od toga aktivnih: $activePremium)
                           🔵 Basic: $basicCount
                        
                        📅 Prosečna starost naloga: ${String.format("%.1f", averageAccountAge)} dana
                        
                        ${if (premiumCount > 0) "📈 Aktivni premium: ${String.format("%.1f", (activePremium.toDouble() / premiumCount * 100))}%" else "📈 Nema premium korisnika"}
                        
                        🕒 Poslednji pregled: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
                    """.trimIndent()

                    AlertDialog.Builder(this@AdminActivity)
                        .setTitle("📈 Statistika korisnika")
                        .setMessage(statsMessage)
                        .setPositiveButton("💾 Eksportuj CSV") { dialog, which ->
                            exportUserStatisticsToCSV(allUsers)
                        }
                        .setNegativeButton("❌ Zatvori", null)
                        .show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AdminActivity,
                        "❌ Greška pri učitavanju statistike: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun exportUserStatisticsToCSV(users: List<User>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = "korisnici_statistika_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val csvFile = File(downloadsDir, fileName)

                FileWriter(csvFile).use { writer ->
                    // Header
                    writer.append("Email,Ime,Telefon,Uloga,Datum kreiranja,Premium ističe,Starost naloga (dana)\n")

                    // Podaci
                    users.forEach { user ->
                        val accountAgeDays = (System.currentTimeMillis() - user.createdAt) / (1000 * 60 * 60 * 24)
                        val premiumExpiry = if (user.premiumExpiry > 0) {
                            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(user.premiumExpiry))
                        } else "Nema premium"

                        writer.append("${user.email},${user.name},${user.phone},${user.role},")
                        writer.append("${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(user.createdAt))},")
                        writer.append("$premiumExpiry,$accountAgeDays\n")
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AdminActivity,
                        "✅ Statistika eksportovana u CSV: $fileName",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AdminActivity,
                        "❌ Greška pri eksportu: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            showExitConfirmationDialog()
        }

        binding.btnRefresh.setOnClickListener {
            loadUsers()
        }
    }
}