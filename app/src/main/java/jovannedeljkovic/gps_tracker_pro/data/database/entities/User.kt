// app\src\main\java\jovannedeljkovic\gps_tracker_pro\data\entities\User.kt
package jovannedeljkovic.gps_tracker_pro.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val email: String,
    val password: String,
    val name: String = "",
    val phone: String = "",
    val role: String = "BASIC", // NOVO: BASIC, PREMIUM, ADMIN
    val premiumExpiry: Long = 0L, // NOVO: datum isteka premiuma
    val createdAt: Long = System.currentTimeMillis()
)