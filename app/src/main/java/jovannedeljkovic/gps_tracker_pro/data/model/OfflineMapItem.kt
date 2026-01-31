package jovannedeljkovic.gps_tracker_pro.data.model

import java.io.File

data class OfflineMapItem(
    val name: String,
    val lookupName: String,
    val tileCount: Int,
    val isSatellite: Boolean,
    val mapType: String,
    val file: File,
    val icon: String,
    val additionalInfo: String = "",

    val downloadDate: String = "",
    val minZoom: Int = 0,
    val maxZoom: Int = 0,

    // Dodajte bounding box za razlikovanje Custom regiona
    val bboxNorth: Double = 0.0,
    val bboxSouth: Double = 0.0,
    val bboxEast: Double = 0.0,
    val bboxWest: Double = 0.0
)