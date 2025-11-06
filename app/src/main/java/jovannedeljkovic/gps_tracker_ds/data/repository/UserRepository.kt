package jovannedeljkovic.gps_tracker_ds.data.repository

import jovannedeljkovic.gps_tracker_ds.data.database.dao.UserDao
import jovannedeljkovic.gps_tracker_ds.data.entities.User

class UserRepository(private val userDao: UserDao) {

    suspend fun registerUser(user: User): Boolean {
        return try {
            // Proveri da li email već postoji
            val existingUser = userDao.getUserByEmail(user.email)
            if (existingUser != null) {
                return false // Email već postoji
            }
            userDao.insertUser(user)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun loginUser(email: String, password: String): User? {
        return userDao.loginUser(email, password)
    }

    suspend fun isEmailExists(email: String): Boolean {
        return userDao.getUserByEmail(email) != null
    }
}