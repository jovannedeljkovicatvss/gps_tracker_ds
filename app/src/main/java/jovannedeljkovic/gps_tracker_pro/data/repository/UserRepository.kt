package jovannedeljkovic.gps_tracker_pro.data.repository

import jovannedeljkovic.gps_tracker_pro.data.database.dao.UserDao
import jovannedeljkovic.gps_tracker_pro.data.entities.User
import jovannedeljkovic.gps_tracker_pro.utils.AdminManager
class UserRepository(private val userDao: UserDao) {

    suspend fun registerUser(user: User): Boolean {
        return try {
            // Proveri da li email već postoji
            val existingUser = userDao.getUserByEmail(user.email)
            if (existingUser != null) {
                return false // Email već postoji
            }

            // PROVERA DA LI JE MASTER ADMIN
            val finalUser = if (AdminManager.isMasterAdmin(user.email)) {
                user.copy(role = "ADMIN") // Automatski postavi kao ADMIN
            } else {
                user // Ostavi BASIC ulogu
            }

            userDao.insertUser(finalUser)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun loginUser(email: String, password: String): User? {
        val user = userDao.loginUser(email, password)

        // PROVERA: ako je master admin, osiguraj da je role ADMIN
        user?.let { existingUser ->
            if (AdminManager.isMasterAdmin(email) && existingUser.role != "ADMIN") {
                // Ako je master admin ali nema ADMIN role, popravi to
                setUserAsAdmin(existingUser.id)
                return userDao.getUserByEmail(email) // Vrati korigovanog usera
            }
        }

        return user
    }

    suspend fun isEmailExists(email: String): Boolean {
        return userDao.getUserByEmail(email) != null
    }

    // OSTALE METODE:
    suspend fun getUserById(userId: String): User? {
        return userDao.getUserById(userId)
    }

    suspend fun upgradeToPremium(userId: String, days: Int = 30): Boolean {
        return try {
            val expiry = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
            userDao.updateUserRole(userId, "PREMIUM")
            userDao.updatePremiumExpiry(userId, expiry)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downgradeToBasic(userId: String): Boolean {
        return try {
            userDao.updateUserRole(userId, "BASIC")
            userDao.updatePremiumExpiry(userId, 0L)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setUserAsAdmin(userId: String): Boolean {
        return try {
            userDao.updateUserRole(userId, "ADMIN")
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getAllUsers(): List<User> {
        return userDao.getAllUsers()
    }

    suspend fun isUserPremium(userId: String): Boolean {
        val user = userDao.getUserById(userId)
        return user?.role == "PREMIUM" && (user.premiumExpiry > System.currentTimeMillis() || user.premiumExpiry == 0L)
    }

    suspend fun isUserAdmin(userId: String): Boolean {
        val user = userDao.getUserById(userId)
        return user?.role == "ADMIN"
    }

    suspend fun getUserRole(userId: String): String {
        val user = userDao.getUserById(userId)
        return user?.role ?: "BASIC"
    }

    suspend fun getUserByEmail(email: String): User? {
        return userDao.getUserByEmail(email)
    }
}