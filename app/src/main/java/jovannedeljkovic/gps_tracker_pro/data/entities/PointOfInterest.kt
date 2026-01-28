package jovannedeljkovic.gps_tracker_pro.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "points_of_interest")
data class PointOfInterest(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val name: String = "Nova tačka",
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long = System.currentTimeMillis()
)