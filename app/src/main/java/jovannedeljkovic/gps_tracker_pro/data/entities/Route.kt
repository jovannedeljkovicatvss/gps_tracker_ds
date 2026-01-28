package jovannedeljkovic.gps_tracker_pro.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "routes")
data class Route(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val name: String = "Nova ruta",
    val distance: Double = 0.0, // u metrima
    val duration: Long = 0, // u milisekundama
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = 0,
    val isCompleted: Boolean = false
)