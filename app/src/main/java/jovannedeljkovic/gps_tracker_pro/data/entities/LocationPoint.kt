package jovannedeljkovic.gps_tracker_pro.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "location_points")
data class LocationPoint(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val routeId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val accuracy: Float = 0f
)