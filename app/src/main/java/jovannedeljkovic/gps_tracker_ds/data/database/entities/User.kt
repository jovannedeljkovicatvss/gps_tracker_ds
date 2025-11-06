package jovannedeljkovic.gps_tracker_ds.data.entities

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
    val createdAt: Long = System.currentTimeMillis()
)