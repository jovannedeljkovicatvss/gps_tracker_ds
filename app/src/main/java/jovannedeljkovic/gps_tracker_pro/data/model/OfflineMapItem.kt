package jovannedeljkovic.gps_tracker_pro.data.model

import java.io.File

data class OfflineMapItem(
    val name: String,
    val tileCount: Int,
    val isSatellite: Boolean,
    val file: File,
    val icon: String
)