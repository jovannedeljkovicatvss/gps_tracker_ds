// app\src\main\java\jovannedeljkovic\gps_tracker_pro\data\database\dao\UserDao.kt
package jovannedeljkovic.gps_tracker_pro.data.database.dao

import androidx.room.*
import jovannedeljkovic.gps_tracker_pro.data.entities.User

@Dao
interface UserDao {
    @Insert
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE email = :email AND password = :password")
    suspend fun loginUser(email: String, password: String): User?

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT COUNT(*) FROM users WHERE email = :email")
    suspend fun isEmailExists(email: String): Int

    // NOVE METODE:
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): User?

    @Query("UPDATE users SET role = :role WHERE id = :userId")
    suspend fun updateUserRole(userId: String, role: String)

    @Query("UPDATE users SET premiumExpiry = :expiry WHERE id = :userId")
    suspend fun updatePremiumExpiry(userId: String, expiry: Long)

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User> // Za admin panel

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: String)
}