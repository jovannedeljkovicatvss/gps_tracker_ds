package jovannedeljkovic.gps_tracker_pro

import android.app.Application
import android.util.Log
import jovannedeljkovic.gps_tracker_pro.data.database.AppDatabase
import jovannedeljkovic.gps_tracker_pro.data.repository.PointRepository
import jovannedeljkovic.gps_tracker_pro.data.repository.RouteRepository
import jovannedeljkovic.gps_tracker_pro.data.repository.UserRepository
import jovannedeljkovic.gps_tracker_pro.utils.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class App : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val userRepository by lazy { UserRepository(database.userDao()) }
    val routeRepository by lazy { RouteRepository(database.routeDao()) }
    val pointRepository by lazy { PointRepository(database.pointOfInterestDao()) }

    override fun onCreate() {
        super.onCreate()
        ensureBackupAdminExists()
    }

    private fun ensureBackupAdminExists() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val backupAdminEmail = "backupadmin@localhost"
                val existingUser = userRepository.getUserByEmail(backupAdminEmail)

                if (existingUser == null) {
                    // Kreiraj backup admina
                    val backupAdmin = jovannedeljkovic.gps_tracker_pro.data.entities.User(
                        email = backupAdminEmail,
                        password = PasswordHasher.hashPassword("backup123"),
                        name = "Backup Admin",
                        role = "ADMIN"
                    )
                    userRepository.registerUser(backupAdmin)
                    Log.d("App", "Backup admin kreiran")
                }
            } catch (e: Exception) {
                Log.e("App", "Greška pri kreiranju backup admina: ${e.message}")
            }
        }
    }
}