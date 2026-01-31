package jovannedeljkovic.gps_tracker_pro.ui.main


import android.content.ClipboardManager
import android.content.ClipData
import android.location.Location
import androidx.annotation.RequiresApi
import jovannedeljkovic.gps_tracker_pro.data.model.OfflineMapItem
import org.osmdroid.util.MapTileIndex
import android.view.Gravity
import android.view.ViewGroup
import jovannedeljkovic.gps_tracker_pro.utils.AdminManager
import jovannedeljkovic.gps_tracker_pro.ui.admin.AdminActivity
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import org.osmdroid.views.overlay.Polyline
import android.os.Handler
import android.graphics.Canvas
import android.graphics.Paint
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.config.Configuration
import java.util.*
import kotlinx.coroutines.launch
import androidx.activity.OnBackPressedCallback
import android.os.PowerManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import jovannedeljkovic.gps_tracker_pro.R
import jovannedeljkovic.gps_tracker_pro.data.entities.LocationPoint
import jovannedeljkovic.gps_tracker_pro.data.entities.PointOfInterest
import jovannedeljkovic.gps_tracker_pro.data.entities.Route
import jovannedeljkovic.gps_tracker_pro.databinding.ActivityMainBinding
import jovannedeljkovic.gps_tracker_pro.utils.NotificationHelper
import android.os.Build
import jovannedeljkovic.gps_tracker_pro.service.TrackingService
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import jovannedeljkovic.gps_tracker_pro.App
import jovannedeljkovic.gps_tracker_pro.data.entities.User
import jovannedeljkovic.gps_tracker_pro.utils.FeatureManager
import android.widget.LinearLayout
import android.widget.Button
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.BroadcastReceiver
import android.app.Activity
import android.content.ContentUris
import android.provider.MediaStore
import android.text.InputType
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.lang.reflect.Type
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.app.Dialog
import jovannedeljkovic.gps_tracker_pro.ui.adapter.PointsAdapter
import jovannedeljkovic.gps_tracker_pro.ui.adapter.RoutesAdapter
import jovannedeljkovic.gps_tracker_pro.ui.adapter.OfflineMapsAdapter
import kotlin.math.*
import android.widget.SeekBar
import com.google.android.material.snackbar.Snackbar
import android.view.Menu
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import com.google.android.material.button.MaterialButton
import org.osmdroid.tileprovider.MapTileProviderBase
import android.provider.Settings
import android.graphics.drawable.BitmapDrawable
import java.util.*
import android.content.ActivityNotFoundException
import java.nio.charset.Charset
import org.osmdroid.util.BoundingBox
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


data class GpxFileInfo(
    val id: Long,
    val name: String,
    val size: Long,
    val dateModified: Long
)

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: org.osmdroid.views.MapView


    private var currentOfflineMapName: String? = null
    private var currentOfflineMapMaxZoom: Int = 21
    private var currentOfflineMapIsSatellite: Boolean = false


    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var trackingTime: Long = 0L
    // Tracking variables
    private var isTracking = false
    private var trackingStartTime: Long = 0
    private var totalDistance = 0.0
    private var currentRoute: Route? = null
    private val routePoints = mutableListOf<GeoPoint>()
    private var lastLocation: Location? = null
    private var stationaryPoints = 0
    private val stationaryThreshold = 3 // Broj tačaka dok stojimo
    private var isActivityVisible = false
    private var backgroundPointsBuffer = mutableListOf<GeoPoint>()

    private var lastCompassBearing: Float = 0f
    private lateinit var backgroundLocationReceiver: BroadcastReceiver
    private var isReceivingBackgroundUpdates = false
    private var trackingSeconds = 0
    private var isFollowingLocation = false
    private val pointsOfInterest = mutableListOf<PointOfInterest>()
    private var isPointMode = false
    private var pointMarkers = mutableMapOf<String, Marker>()
    private var isMapCentered = false
    private var currentBearing: Float = 0f
    private var myLocationMarker: Marker? = null
    private var isMapOrientationNorth = true  // true = North, false = Follow direction
    private var isAutoFollowEnabled = true     // Auto-follow location
    private val polylines = mutableListOf<Polyline>()
    private lateinit var locationOverlay: MyLocationNewOverlay
    //Kompas
    private var isCompassVisible = false
    private var currentAzimuth: Float = 0f
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val accelerometer by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    private val magnetometer by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    private var wasInBackground = false
    private var lastBackgroundTime: Long = 0L
    private val backgroundThreshold = 10000L // 10 sekundi

    // Lista segmenta rute (umesto jedne rute)
    private val routeSegments = mutableListOf<MutableList<GeoPoint>>()
    private var currentSegment = mutableListOf<GeoPoint>()
    private var gravity = FloatArray(3)
    private var geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    // Additional tracking variables
    private var currentSpeed: Double = 0.0
    private var currentAltitude: Double = 0.0
    private var isStationary = false
    private var stationaryStartTime: Long = 0
    private var trackingMode = "self"
    private var savedRoutes = mutableListOf<Route>()

    //private lateinit var btnTrackingModeBottom: Button
    //private lateinit var progressBarBottom: ProgressBar
    //private lateinit var tvBottomTime: TextView
    //private lateinit var tvBottomDistance: TextView
    //private lateinit var tvBottomSpeed: TextView
    // Notification helper
    private lateinit var notificationHelper: NotificationHelper
    private var isSatelliteMode = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val IMPORT_GPX_REQUEST_CODE = 2001
        private const val IMPORT_CSV_REQUEST_CODE = 2002
        private const val IMPORT_BACKUP_REQUEST_CODE = 2003
        private const val IMPORT_GPX_POINTS_REQUEST_CODE = 2004
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private const val RECEIVER_NOT_EXPORTED = Context.RECEIVER_NOT_EXPORTED
        private const val REQUEST_CODE_STORAGE_PERMISSION = 1002
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private const val RECEIVER_EXPORTED = Context.RECEIVER_EXPORTED

    }
    private fun showImportFallbackOptions() {
        AlertDialog.Builder(this)
            .setTitle("❌ Problem sa File Picker-om")
            .setMessage("File Picker nije dostupan. Želite li da:\n\n• Pristupite Download folderu direktno?\n• Proverite dozvole za skladištenje?")
            .setPositiveButton("📁 Download Folder") { dialog, which ->
                importGpxFromDownloads()
            }
            .setNeutralButton("🔐 Proveri Dozvole") { dialog, which ->
                requestStoragePermissions()
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }
    private fun importGpxRoute(uri: Uri) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("📥 Uvoz rute...")
            .setMessage("Učitavam GPX fajl...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val gpxContent = inputStream.bufferedReader().use { it.readText() }

                    // KORISTI NAPREDNI PARSER
                    val allRoutes = parseGpxContentAdvanced(gpxContent)

                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()

                        if (allRoutes.isNotEmpty()) {
                            if (allRoutes.size == 1) {
                                // Samo jedna ruta - prikaži direktno
                                showImportedRoutePreview(allRoutes.first())
                            } else {
                                // Više ruta - pitaј korisnika koju da uveze
                                showRouteSelectionDialog(allRoutes)
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "❌ Nema tačaka u GPX fajlu", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "❌ Greška pri uvozu GPX: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun showRouteSelectionDialog(routes: List<List<GeoPoint>>) {
        val routeNames = routes.mapIndexed { index, routePoints ->
            "Ruta ${index + 1} (${routePoints.size} tačaka)"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("🔍 Izaberite rutu za uvoz")
            .setMessage("Pronađeno ${routes.size} ruta u GPX fajlu. Koju želite da uvezete?")
            .setItems(routeNames) { dialog, which ->
                val selectedRoute = routes[which]
                showImportedRoutePreview(selectedRoute, "Uvežena ruta ${which + 1}")
            }
            .setPositiveButton("📥 Uvezi prvu rutu") { dialog, which ->
                showImportedRoutePreview(routes.first(), "Uvežena ruta 1")
            }
            .setNeutralButton("📦 Uvezi SVE rute") { dialog, which ->  // 👈 NOVO DUGME
                importAllRoutesFromGpx(routes)
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }
    private fun importAllRoutesFromGpx(allRoutes: List<List<GeoPoint>>) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("📦 Uvoz svih ruta...")
            .setMessage("Uvozim ${allRoutes.size} ruta...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var importedCount = 0

                allRoutes.forEachIndexed { index, routePoints ->
                    if (routePoints.isNotEmpty()) {
                        saveImportedRoute(routePoints, "Uvežena ruta ${index + 1}")
                        importedCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        "✅ Uspešno uveženo $importedCount ruta!",
                        Toast.LENGTH_LONG
                    ).show()

                    // Osveži prikaz ruta na mapi
                    loadSavedRoutes()
                    refreshMapAndRoute()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        "❌ Greška pri uvozu ruta: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private suspend fun parseGpxContent(gpxContent: String): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        val routes = mutableListOf<List<GeoPoint>>()

        try {
            Log.d("GPXImport", "🔄 Počinjem parsiranje GPX fajla")

            // 1. PARSIRAJ SVE TRKSEG (TRACK SEGMENTS)
            val trksegPattern = """<trkseg>([\s\S]*?)</trkseg>""".toRegex()
            val trksegMatches = trksegPattern.findAll(gpxContent)

            trksegMatches.forEachIndexed { segmentIndex, segmentMatch ->
                val segmentContent = segmentMatch.groupValues[1]
                val segmentPoints = mutableListOf<GeoPoint>()

                // Parsiraj tačke unutar ovog segmenta
                val pointPattern = """<trkpt lat="([^"]+)" lon="([^"]+)">""".toRegex()
                val pointMatches = pointPattern.findAll(segmentContent)

                pointMatches.forEach { pointMatch ->
                    val lat = pointMatch.groupValues[1].toDoubleOrNull()
                    val lon = pointMatch.groupValues[2].toDoubleOrNull()
                    if (lat != null && lon != null) {
                        segmentPoints.add(GeoPoint(lat, lon))
                    }
                }

                if (segmentPoints.isNotEmpty()) {
                    routes.add(segmentPoints)
                    Log.d("GPXImport", "📈 Segment $segmentIndex: ${segmentPoints.size} tačaka")
                }
            }

            // 2. AKO NEMA TRKSEG, POKUŠAJ SA TRKPT DIRECTLY
            if (routes.isEmpty()) {
                Log.d("GPXImport", "ℹ️ Nema trkseg, pokušavam sa trkpt direktno")
                val pointPattern = """<trkpt lat="([^"]+)" lon="([^"]+)">""".toRegex()
                val pointMatches = pointPattern.findAll(gpxContent)

                pointMatches.forEach { pointMatch ->
                    val lat = pointMatch.groupValues[1].toDoubleOrNull()
                    val lon = pointMatch.groupValues[2].toDoubleOrNull()
                    if (lat != null && lon != null) {
                        points.add(GeoPoint(lat, lon))
                    }
                }
                Log.d("GPXImport", "📍 Pronađeno ${points.size} tačaka direktno")
            } else {
                // 3. KORISTI SAMO PRVU RUTU ILI KOMBINUJ SVE
                Log.d("GPXImport", "📊 Pronađeno ${routes.size} ruta/segmenata")

                // Opcija A: Koristi samo prvu rutu (najčešći scenario)
                points.addAll(routes.first())

                // Opcija B: Kombinuj sve rute u jednu (ako želiš)
                // routes.forEach { routePoints -> points.addAll(routePoints) }
            }

            Log.d("GPXImport", "✅ Parsiranje završeno: ${points.size} tačaka")

        } catch (e: Exception) {
            Log.e("GPXImport", "💥 Greška pri parsiranju GPX: ${e.message}")
        }

        return points
    }
    private suspend fun parseGpxContentAdvanced(gpxContent: String): List<List<GeoPoint>> {
        val allRoutes = mutableListOf<List<GeoPoint>>()

        try {
            Log.d("GPXImport", "🎯 Napredno parsiranje GPX fajla")

            // 1. PRONAĐI SVE TRK (TRACKS)
            val trkPattern = """<trk>([\s\S]*?)</trk>""".toRegex()
            val trkMatches = trkPattern.findAll(gpxContent)

            trkMatches.forEachIndexed { trackIndex, trackMatch ->
                val trackContent = trackMatch.groupValues[1]
                val trackPoints = mutableListOf<GeoPoint>()

                // Pronađi ime rute
                val namePattern = """<name>([^<]+)</name>""".toRegex()
                val nameMatch = namePattern.find(trackContent)
                val trackName = nameMatch?.groupValues?.get(1) ?: "Ruta ${trackIndex + 1}"

                Log.d("GPXImport", "🔍 Parsiram rutu: $trackName")

                // Pronađi sve tačke u ovoj rutí
                val pointPattern = """<trkpt lat="([^"]+)" lon="([^"]+)">""".toRegex()
                val pointMatches = pointPattern.findAll(trackContent)

                pointMatches.forEach { pointMatch ->
                    val lat = pointMatch.groupValues[1].toDoubleOrNull()
                    val lon = pointMatch.groupValues[2].toDoubleOrNull()
                    if (lat != null && lon != null) {
                        trackPoints.add(GeoPoint(lat, lon))
                    }
                }

                if (trackPoints.isNotEmpty()) {
                    allRoutes.add(trackPoints)
                    Log.d("GPXImport", "✅ Ruta '$trackName': ${trackPoints.size} tačaka")
                }
            }

            // 2. AKO NEMA TRK, POKUŠAJ SA TRKSEG
            if (allRoutes.isEmpty()) {
                Log.d("GPXImport", "ℹ️ Nema trk, pokušavam sa trkseg")
                val trksegPattern = """<trkseg>([\s\S]*?)</trkseg>""".toRegex()
                val trksegMatches = trksegPattern.findAll(gpxContent)

                trksegMatches.forEachIndexed { segmentIndex, segmentMatch ->
                    val segmentContent = segmentMatch.groupValues[1]
                    val segmentPoints = mutableListOf<GeoPoint>()

                    val pointPattern = """<trkpt lat="([^"]+)" lon="([^"]+)">""".toRegex()
                    val pointMatches = pointPattern.findAll(segmentContent)

                    pointMatches.forEach { pointMatch ->
                        val lat = pointMatch.groupValues[1].toDoubleOrNull()
                        val lon = pointMatch.groupValues[2].toDoubleOrNull()
                        if (lat != null && lon != null) {
                            segmentPoints.add(GeoPoint(lat, lon))
                        }
                    }

                    if (segmentPoints.isNotEmpty()) {
                        allRoutes.add(segmentPoints)
                        Log.d("GPXImport", "✅ Segment $segmentIndex: ${segmentPoints.size} tačaka")
                    }
                }
            }

            Log.d("GPXImport", "🎉 Pronađeno ${allRoutes.size} ruta/segmenata")

        } catch (e: Exception) {
            Log.e("GPXImport", "💥 Greška pri naprednom parsiranju: ${e.message}")
        }

        return allRoutes
    }
    private fun importCsvPoints(uri: Uri) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("📥 Uvoz tačaka...")
            .setMessage("Učitavam CSV fajl...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = inputStream.bufferedReader()
                    val points = mutableListOf<PointOfInterest>()

                    reader.readLine() // Preskoči header
                    var line: String?
                    var lineNumber = 1

                    while (reader.readLine().also { line = it } != null) {
                        lineNumber++
                        line?.let { csvLine ->
                            val fields = csvLine.split(",")
                            if (fields.size >= 3) {
                                try {
                                    val name = fields[0].trim()
                                    val lat = fields[1].trim().toDouble()
                                    val lon = fields[2].trim().toDouble()

                                    val point = PointOfInterest(
                                        userId = getCurrentUserId(),
                                        name = name,
                                        latitude = lat,
                                        longitude = lon,
                                        createdAt = System.currentTimeMillis()
                                    )
                                    points.add(point)
                                } catch (e: NumberFormatException) {
                                    Log.w("CSVImport", "Greška u liniji $lineNumber: $csvLine")
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        if (points.isNotEmpty()) {
                            showImportedPointsPreview(points)
                        } else {
                            Toast.makeText(this@MainActivity, "❌ Nema validnih tačaka u CSV fajlu", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "❌ Greška pri uvozu CSV: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun showImportedRoutePreview(points: List<GeoPoint>, suggestedName: String = "Uvežena ruta") {
        val message = """
    🗺️ Pronađena ruta sa ${points.size} tačaka
    
    Prva tačka: ${String.format("%.6f", points.first().latitude)}, ${String.format("%.6f", points.first().longitude)}
    Poslednja tačka: ${String.format("%.6f", points.last().latitude)}, ${String.format("%.6f", points.last().longitude)}
    
    Želite li da sačuvate ovu rutu?
""".trimIndent()

        // Dodaj EditText za ime rute
        val input = EditText(this).apply {
            setText(suggestedName)
            hint = "Unesite ime rute"
        }

        AlertDialog.Builder(this)
            .setTitle("👁️ Pregled uvezene rute")
            .setMessage(message)
            .setView(input)
            .setPositiveButton("✅ Sačuvaj rutu") { dialog, which ->
                val routeName = input.text.toString().takeIf { it.isNotBlank() } ?: suggestedName
                saveImportedRoute(points, routeName)
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }
    private fun showImportedPointsPreview(points: List<PointOfInterest>) {
        AlertDialog.Builder(this)
            .setTitle("👁️ Pregled uvezenih tačaka")
            .setMessage("Pronađeno ${points.size} tačaka. Želite li da ih sačuvate?")
            .setPositiveButton("✅ Sačuvaj sve tačke") { dialog, which ->
                saveImportedPoints(points)
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }
    private fun saveImportedRoute(points: List<GeoPoint>, routeName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val route = Route(
                    userId = getCurrentUserId(),
                    name = routeName,
                    startTime = System.currentTimeMillis(),
                    distance = calculateRouteDistance(points),
                    duration = 0L,
                    isCompleted = true
                )

                val routeId = app.routeRepository.createRoute(route)

                // Sačuvaj sve tačke rute
                points.forEach { point ->
                    val locationPoint = LocationPoint(
                        routeId = routeId,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        timestamp = System.currentTimeMillis()
                    )
                    app.routeRepository.addLocationPoint(locationPoint)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "✅ Ruta '$routeName' uspešno uvežena!", Toast.LENGTH_LONG).show()
                    // Prikaži rutu na mapi
                    showImportedRouteOnMap(points, routeName)
                    // Osveži listu sačuvanih ruta
                    loadSavedRoutes()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "❌ Greška pri čuvanju rute: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun showRouteDeletionOptions() {
        if (polylines.isEmpty()) {
            Toast.makeText(this, "❌ Nema prikazanih ruta za brisanje", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf(
            "🗑️ Obriši sve prikazane rute sa mape",
            "🔍 Izaberi rutu za brisanje",
            "❌ Otkaži"
        )

        AlertDialog.Builder(this)
            .setTitle("🗑️ Brisanje ruta sa mape")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> clearAllDisplayedRoutes()  // Obriši sve
                    1 -> showRouteSelectionForDeletion()  // Izaberi pojedinačno
                    // 2 -> Otkaži
                }
            }
            .setNegativeButton("❌ Zatvori", null)
            .show()
    }
    private fun clearAllDisplayedRoutes() {
        polylines.forEach { polyline ->
            binding.mapView.overlays.remove(polyline)
        }
        polylines.clear()
        binding.mapView.invalidate()

        Toast.makeText(this, "🗑️ Sve prikazane rute obrisane sa mape", Toast.LENGTH_SHORT).show()
    }
    private fun showRouteSelectionForDeletion() {
        if (polylines.isEmpty()) {
            Toast.makeText(this, "❌ Nema prikazanih ruta", Toast.LENGTH_SHORT).show()
            return
        }

        val routeNames = polylines.mapIndexed { index, _ ->
            "Prikazana ruta ${index + 1}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("🔍 Izaberite rutu za brisanje")
            .setItems(routeNames) { dialog, which ->
                removeRouteFromMap(which)
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun removeRouteFromMap(index: Int) {
        if (index < polylines.size) {
            val polyline = polylines[index]
            binding.mapView.overlays.remove(polyline)
            polylines.removeAt(index)
            binding.mapView.invalidate()

            Toast.makeText(this, "🗑️ Ruta obrisana sa mape", Toast.LENGTH_SHORT).show()
        }
    }
    private fun saveImportedPoints(points: List<PointOfInterest>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App

                points.forEach { point ->
                    app.pointRepository.addPoint(point)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "✅ ${points.size} tačaka uspešno uveženo!", Toast.LENGTH_LONG).show()
                    loadPointsOfInterest() // Osveži prikaz tačaka
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "❌ Greška pri čuvanju tačaka: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun showImportedRouteOnMap(points: List<GeoPoint>, routeName: String? = null) {
        try {
            if (points.isNotEmpty()) {
                binding.mapView.controller.animateTo(points.first())
                binding.mapView.controller.setZoom(16.0)
            }

            val polyline = Polyline().apply {
                setPoints(points)

                // Različite boje za različite tipove ruta
                outlinePaint.color = when {
                    routeName?.contains("uvezen", ignoreCase = true) == true -> Color.parseColor("#FF4CAF50") // Zelena
                    routeName?.contains("snimljena", ignoreCase = true) == true -> Color.parseColor("#FF2196F3") // Plava
                    else -> Color.parseColor("#FFFF9800") // Narandžasta
                }

                outlinePaint.strokeWidth = 10.0f
                outlinePaint.style = Paint.Style.STROKE
            }

            binding.mapView.overlays.add(polyline)
            polylines.add(polyline)
            binding.mapView.invalidate()

            val message = if (routeName != null) {
                "🗺️ Ruta '$routeName' prikazana na mapi"
            } else {
                "🗺️ Ruta prikazana na mapi"
            }

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("RouteDisplay", "Greška pri prikazu rute: ${e.message}")
        }
    }
    private fun calculateRouteDistance(points: List<GeoPoint>): Double {
        var totalDistance = 0.0
        for (i in 1 until points.size) {
            totalDistance += points[i-1].distanceToAsDouble(points[i])
        }
        return totalDistance
    }

    private fun importBackupData(uri: Uri) {
        Toast.makeText(this, "📂 Funkcionalnost za backup u izradi...", Toast.LENGTH_LONG).show()

        // Za sada samo prikažite poruku
        AlertDialog.Builder(this)
            .setTitle("🔄 Uvoz Backup Podataka")
            .setMessage("Ova funkcionalnost će biti dostupna u narednoj verziji aplikacije.\n\n" +
                    "Planirane mogućnosti:\n" +
                    "• Uvoz ruta iz GPX/JSON\n" +
                    "• Uvoz tačaka interesa\n" +
                    "• Restauracija celokupnih podataka")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showBackupImportDialog(jsonObject: JSONObject) {
        val backupInfo = """
        📊 Podaci u backup fajlu:
        
        📅 Datum backup-a: ${jsonObject.optString("backupDate", "Nepoznato")}
        📱 Verzija aplikacije: ${jsonObject.optString("appVersion", "Nepoznato")}
        
        Izaberite šta želite da uvezete:
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("📂 Opcije uvoza backup-a")
            .setMessage(backupInfo)
            .setPositiveButton("🗺️ Samo moje rute i tačke") { dialog, which ->
                importUserDataFromBackup(jsonObject)
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }
    private fun importUserDataFromBackup(jsonObject: JSONObject) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("🔄 Uvoz podataka...")
            .setMessage("Uvozim rute i tačke...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val gson = Gson()

                // Uvezi rute
                if (jsonObject.has("routes")) {
                    val routesJson = jsonObject.getJSONArray("routes").toString()
                    val routesType: Type = object : TypeToken<List<Route>>() {}.type
                    val routes: List<Route> = gson.fromJson(routesJson, routesType)

                    routes.forEach { route ->
                        // Ažuriraj userId na trenutnog korisnika
                        val updatedRoute = route.copy(userId = getCurrentUserId())
                        app.routeRepository.createRoute(updatedRoute)
                    }
                }

                // Uvezi tačke
                if (jsonObject.has("points")) {
                    val pointsJson = jsonObject.getJSONArray("points").toString()
                    val pointsType: Type = object : TypeToken<List<PointOfInterest>>() {}.type
                    val points: List<PointOfInterest> = gson.fromJson(pointsJson, pointsType)

                    points.forEach { point ->
                        // Ažuriraj userId na trenutnog korisnika
                        val updatedPoint = point.copy(userId = getCurrentUserId())
                        app.pointRepository.addPoint(updatedPoint)
                    }
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        "✅ Podaci uspešno uvezeni!",
                        Toast.LENGTH_LONG
                    ).show()

                    // Osveži prikaz
                    loadPointsOfInterest()
                    loadSavedRoutes()
                    refreshMapAndRoute()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        "❌ Greška pri uvozu: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    private fun showBackupImportOptions(backupData: BackupData) {
        val message = """
        📊 Pronađeni podaci u backup-u:
        
        👥 Korisnici: ${backupData.users.size}
        🗺️ Rute: ${backupData.routes.size}
        📍 Tačke: ${backupData.points.size}
        📅 Datum backup-a: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(backupData.backupDate))}
        📱 Verzija aplikacije: ${backupData.appVersion}
        
        ⚠️ Pažnja: Ovim ćete zameniti trenutne podatke!
        
        Izaberite opciju uvoza:
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("📂 Opcije uvoza backup-a")
            .setMessage(message)
            .setPositiveButton("✅ Uvezi SVE podatke") { dialog, which ->
                importAllBackupData(backupData)
            }
            /*.setNeutralButton("👥 Samo korisnike") { dialog, which ->
                importUsersFromBackup(backupData.users)
            }*/
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    // Dodajte BackupData klase:
    data class BackupData(
        val users: List<User>,
        val routes: List<Route>,
        val points: List<PointOfInterest>,
        val backupDate: Long,
        val appVersion: String
    )


    private fun importAllBackupData(backupData: BackupData) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("🔄 Uvoz svih podataka...")
            .setMessage("Molimo sačekajte...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App

                // 1. Obriši sve postojeće podatke
                val currentUserId = getCurrentUserId()

                // 2. Uvezi korisnike
                backupData.users.forEach { user ->
                    app.userRepository.registerUser(user)
                }

                // 3. Uvezi rute
                backupData.routes.forEach { route ->
                    app.routeRepository.createRoute(route)
                }

                // 4. Uvezi tačke
                backupData.points.forEach { point ->
                    app.pointRepository.addPoint(point)
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        "✅ Uspešno uvezeno ${backupData.users.size} korisnika, ${backupData.routes.size} ruta i ${backupData.points.size} tačaka!",
                        Toast.LENGTH_LONG
                    ).show()

                    // Osveži prikaz
                    loadPointsOfInterest()
                    loadSavedRoutes()
                    refreshMapAndRoute()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        "❌ Greška pri uvozu: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    private fun showDeleteAllDataDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Brisanje svih podataka")
            .setMessage("Da li ste sigurni da želite da obrišete SVE rute i tačke? Ova akcija se ne može poništiti!")
            .setPositiveButton("🗑️ Obriši sve") { dialog, which ->
                deleteAllData()
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun deleteAllData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App

                // Obriši sve rute i tačke
                val userRoutes = app.routeRepository.getUserRoutes(getCurrentUserId())
                userRoutes.forEach { route ->
                    app.routeRepository.deleteRoute(route)
                }

                val userPoints = app.pointRepository.getUserPoints(getCurrentUserId())
                userPoints.forEach { point ->
                    app.pointRepository.deletePoint(point)
                }

                withContext(Dispatchers.Main) {
                    // Očisti mapu
                    binding.mapView.overlays.clear()
                    binding.mapView.invalidate()

                    Toast.makeText(this@MainActivity, "✅ Svi podaci obrisani", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "❌ Greška pri brisanju: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
      private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d("Battery", "🔋 Aplikacija nije izuzeta iz optimizacije baterije")
                // Opciono: možete pokazati dialog korisniku kasnije
            } else {
                Log.d("Battery", "✅ Aplikacija je izuzeta iz optimizacije baterije")
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapView = findViewById(R.id.mapView)

        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)

        // Ili ako koristite DEFAULT, možda je ograničenje
        // mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)

        // Postavi zoom ograničenje na veću vrednost
        mapView.maxZoomLevel = 23.0
        mapView.minZoomLevel = 3.0



        if (!::binding.isInitialized) {
            Log.e("MainActivity", "Binding nije uspešno inicijalizovan!")
            finish()
            return
        }

        notificationHelper = NotificationHelper(this)

        // DODAJTE OVO - postavka receivera
        setupBackgroundLocationReceiver()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })

        initializeMap()
        isAutoFollowEnabled = true
        locationOverlay.enableFollowLocation()
        setupEnhancedMapConfiguration()

        binding.btnToggleHybrid.setOnClickListener {
            toggleHybridMode()
        }
        // Postavite dugme kao sakriveno na početku
        binding.btnToggleHybrid.visibility = View.GONE

        initializeLocationClient()
        setupClickListeners()  // ILI safeButtonSetup() ako koristiš tu metodu
        setupPointOfInterestMode()
        loadPointsOfInterest()
        loadSavedRoutes()
        checkLocationPermissions()
        disableAccuracyCircle()
        checkBatteryOptimization()
        checkUserFeatures()

        fixExistingSatelliteMaps()
        // DODAJ OVDE - POSLEDNJE PRE HANDLER-A:
        setupCompass()
        val filter = IntentFilter("LOCATION_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(locationReceiver, filter)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            getBestLocation()
        }, 2000)
    }

        private fun addStamenTonerOverlay() {
        try {
            Log.d("HybridDebug", "Dodajem Stamen Toner overlay (tamniji)...")

            removeStreetOverlay()

            // STAMEN TONER - tamnije labele, bolje za satelit
            val tonerSource = object : org.osmdroid.tileprovider.tilesource.XYTileSource(
                "Stamen_Toner",
                0,
                18,
                256,
                ".png",
                arrayOf(
                    "https://stamen-tiles.a.ssl.fastly.net/toner/{z}/{x}/{y}.png"
                )
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String {
                    val zoom = MapTileIndex.getZoom(pMapTileIndex)
                    val x = MapTileIndex.getX(pMapTileIndex)
                    val y = MapTileIndex.getY(pMapTileIndex)
                    return "https://stamen-tiles.a.ssl.fastly.net/toner/$zoom/$x/$y.png"
                }
            }

            streetOverlay = org.osmdroid.views.overlay.TilesOverlay(
                org.osmdroid.tileprovider.MapTileProviderBasic(
                    applicationContext,
                    tonerSource
                ),
                applicationContext
            )

            // Postavi VEĆU TRANSPARENTNOST za Toner (jer je tamniji)
            try {
                val alphaField = streetOverlay!!::class.java.getDeclaredField("mAlpha")
                alphaField.isAccessible = true
                alphaField.set(streetOverlay, 0.4f) // 60% prozirno - Toner je tamniji
            } catch (e: Exception) {
                Log.w("HybridDebug", "Ne mogu postaviti alpha")
            }

            binding.mapView.overlays.add(streetOverlay)
            binding.mapView.invalidate()

            Log.d("HybridMode", "✅ Stamen Toner overlay dodat")
            Toast.makeText(this, "🗺️ Tamne labele", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("HybridMode", "❌ Stamen Toner greška: ${e.message}", e)

            // Fallback na Toner Lite
            addStamenTonerLiteOverlay()
        }
    }
    private fun toggleHybridMode() {
        try {
            if (isHybridMode) {
                // ISKLJUČI hibridni mod
                removeStreetOverlay()
                isHybridMode = false
                binding.btnToggleHybrid.setBackgroundResource(R.drawable.button_transparent_round)
                Toast.makeText(this, "🛰️ Samo satelitski prikaz", Toast.LENGTH_SHORT).show()
            } else {


                // Probajte redom:

                //addSimpleDarkLabelsOverlay() //labele crne, dobre, ali sloj mnogo osvetljen

                // addCustomLabelOverlay() // nije dobra
                 addBlackLabelsOnlyOverlay() // Druga opcija
                // addOSMCartoLabelsOverlay() // Treća opcija
                //addStamenTonerOverlay() // Prvo probajte tamniji

                // Ako ne radi, probajte drugu opciju
                //addOSMCartoLabelsOverlay()
                // UKLJUČI hibridni mod - PROBAJTE REDOM
                // Prvo probajte Stamen Toner Lite (najbolje za samo labele)
                //addStamenTonerLiteOverlay()

                // Ako ne radi, probajte poboljšanu verziju
                // addStreetLabelsOverlay()

                // Ako ne radi ni to, probajte bele labele
                // addWhiteLabelsOnBlackOverlay()

                isHybridMode = true
                binding.btnToggleHybrid.setBackgroundResource(R.drawable.button_modern_accent)
            }

            binding.mapView.invalidate()

        } catch (e: Exception) {
            Log.e("HybridMode", "Greška pri prebacivanju: ${e.message}", e)
            Toast.makeText(this, "Greška: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private var isHybridMode = false
    private var streetOverlay: org.osmdroid.views.overlay.TilesOverlay? = null
    private fun getMapView(): MapView? {
        return if (::binding.isInitialized && binding.mapView != null) {
            binding.mapView
        } else {
            Log.w("MapView", "MapView nije dostupan")
            null
        }
    }

    private fun setupZoomLimiter() {
        mapView.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                return false
            }

            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                event?.let {
                    val currentZoom = mapView.zoomLevelDouble

                    // Proveri da li smo u offline modu
                    if (!mapView.useDataConnection() && currentOfflineMapMaxZoom > 0) {
                        if (currentZoom > currentOfflineMapMaxZoom) {
                            // Blokiraj zoom iznad max
                            Handler(Looper.getMainLooper()).postDelayed({
                                mapView.controller.setZoom(currentOfflineMapMaxZoom.toDouble())
                                // Prikaži Toast umesto Snackbar
                                Toast.makeText(
                                    this@MainActivity,
                                    "📏 Max zoom za offline: $currentOfflineMapMaxZoom",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }, 100)
                            return true
                        }
                    }
                }
                return false
            }
        })
    }



    private fun enableOnlineMode() {
        // Vrati na online mod
        binding.mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        binding.mapView.setUseDataConnection(true)

        // Resetuj offline varijable
        currentOfflineMapName = null
        currentOfflineMapMaxZoom = 21
        currentOfflineMapIsSatellite = false

        // Očisti cache
        binding.mapView.tileProvider.clearTileCache()

        // Sakrij dugme za hibridni mod
        binding.btnToggleHybrid.visibility = View.GONE
        isHybridMode = false
        removeStreetOverlay()

        // Osveži prikaz
        binding.mapView.invalidate()

        Toast.makeText(this, "🌐 Online mode", Toast.LENGTH_SHORT).show()
    }


    private fun setupBackgroundLocationReceiver() {
        backgroundLocationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "BACKGROUND_LOCATION_UPDATE") {
                    val latitude = intent.getDoubleExtra("latitude", 0.0)
                    val longitude = intent.getDoubleExtra("longitude", 0.0)
                    val accuracy = intent.getFloatExtra("accuracy", 0f)
                    val speed = intent.getFloatExtra("speed", 0f)

                    val location = Location("background_service").apply {
                        this.latitude = latitude
                        this.longitude = longitude
                        this.accuracy = accuracy
                        this.speed = speed
                    }

                    // AŽURIRAJTE RUTU SA POZADINSKOM LOKACIJOM
                    if (isTracking) {
                        val geoPoint = GeoPoint(latitude, longitude)

                        // DODAJ U GLAVNE LISTE TAČAKA
                        routePoints.add(geoPoint)
                        currentSegment.add(geoPoint)

                        // AŽURIRAJ STATISTIKE
                        updateDistanceAndSpeedAccurate(location)

                        // SAČUVAJ U BAZU
                        savePointToDatabase(geoPoint)

                        // OVDE JE KLJUČNO: OSVEŽI MAPU AKO JE APLIKACIJA VIDLJIVA
                        if (isActivityVisible) {
                            runOnUiThread {
                                drawSmoothRouteOnMap()
                                Log.d("BackgroundTracking", "?? Map osvežena sa pozadinskom tačkom")
                            }
                        } else {
                            Log.d("BackgroundTracking", "?? Tačka sačuvana, mapa će se osvežiti pri povratku")
                        }

                        Log.d("BackgroundTracking", "?? Pozadinska tačka dodata: ${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}")
                    }
                }
            }
        }
    }

    private fun setupCompass() {
        binding.btnCompass.setOnClickListener {
            toggleCompassVisibility()
        }

        // Pokreni ažuriranje kompasa
        startCompassUpdates()
    }


    private fun toggleCompass() {
        isCompassVisible = !isCompassVisible

        if (isCompassVisible) {
            startCompass()
            //binding.compassView.visibility = View.VISIBLE
            binding.compassNeedle.visibility = View.VISIBLE
            Toast.makeText(this, "🧭 Kompas uključen", Toast.LENGTH_SHORT).show()
        } else {
            stopCompass()
            //binding.compassView.visibility = View.GONE
            binding.compassNeedle.visibility = View.GONE
            Toast.makeText(this, "🧭 Kompas isključen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCompass() {
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    private fun stopCompass() {
        sensorManager.unregisterListener(sensorListener)
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> gravity = event.values.clone()
                Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = event.values.clone()
            }

            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                SensorManager.getOrientation(rotationMatrix, orientation)
                val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()

                updateCompass(azimuth)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun updateCompass(azimuth: Float) {
        currentAzimuth = azimuth

        // Rotiraj celu osnovu kompasa (pokazuje sever)
        //binding.compassView.rotation = -azimuth

        // Kombinuj sa smerom kretanja ako se krećeš
        val finalRotation = if (lastLocation?.hasBearing() == true) {
            -azimuth // Samo kompas (pokazuje sever)
        } else {
            -azimuth // Samo kompas
        }

        //binding.compassView.rotation = finalRotation
        binding.compassNeedle.rotation = finalRotation
    }

    private fun toggleCompassVisibility() {
        isCompassVisible = !isCompassVisible
        binding.btnCompass.visibility = if (isCompassVisible) View.VISIBLE else View.GONE

        Toast.makeText(this,
            if (isCompassVisible) "🧭 Kompas uključen" else "🧭 Kompas isključen",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun startCompassUpdates() {
        val handler = Handler(Looper.getMainLooper())

        val compassRunnable = object : Runnable {
            override fun run() {
                if (isCompassVisible && lastLocation != null && lastLocation!!.hasBearing()) {
                    updateCompassRotation(lastLocation!!.bearing)
                }
                handler.postDelayed(this, 100) // Ažuriraj svakih 100ms
            }
        }
        handler.post(compassRunnable)
    }

    private fun updateCompassRotation(bearing: Float) {
        if (bearing != lastCompassBearing) {
            binding.btnCompass.rotation = -bearing // Kompas se rotira suprotno od smera
            lastCompassBearing = bearing
        }
    }

    private fun checkOfflineTiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val satelliteDir = File(Configuration.getInstance().osmdroidBasePath, "tiles/World_Imagery")
                val tileCount = countTilesInDirectory(satelliteDir)

                withContext(Dispatchers.Main) {
                    Log.d("TileCheck", "📊 Ukupno satelitskih tile-ova: $tileCount")
                    if (tileCount > 0) {
                        Toast.makeText(this@MainActivity, "✅ Ima $tileCount tile-ova", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Nema tile-ova!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("TileCheck", "Greška: ${e.message}")
            }
        }
    }
// DODAJ OVE METODE ISPOD onCreate:
private fun checkButtonAvailability() {
    val buttons = listOf(
        "btnStartTracking" to binding.btnStartTracking,
        "btnStopTracking" to binding.btnStopTracking,
        "btnMyLocation" to binding.btnMyLocation,
        "btnMenu" to binding.btnMenu,
        "btnSavedRoutes" to binding.btnSavedRoutes,
        "btnZoomIn" to binding.btnZoomIn,
        "btnZoomOut" to binding.btnZoomOut,
        "fabAddPoint" to binding.fabAddPoint,
        "btnTrackingMode" to binding.btnTrackingMode,
        "btnNavigation" to binding.btnNavigation,
        "btnMapType" to binding.btnMapType,
        "btnExport" to binding.btnExport,
        "btnReset" to binding.btnReset
    )

    buttons.forEach { (name, button) ->
        if (button == null) {
            Log.w("ButtonCheck", "Dugme $name je NULL")
        } else {
            Log.d("ButtonCheck", "Dugme $name je dostupno")
        }
    }
}
    private fun setupEnhancedMapConfiguration() {
        try {
            // POBOLJŠANA KONFIGURACIJA ZA BOLJI QUALITY
            Configuration.getInstance().apply {
                userAgentValue = packageName
                cacheMapTileCount = 3000  // Povećan cache
                tileDownloadThreads = 4   // Više threadova za download
                tileFileSystemThreads = 3
                tileDownloadMaxQueueSize = 200
            }

            // PODEŠAVANJA ZA BOLJU OŠTRINU
            binding.mapView.apply {
                setMultiTouchControls(true)
                minZoomLevel = 3.0
                maxZoomLevel = 23.0  // Povećan max zoom za više detalja
                setTilesScaledToDpi(true)
                setUseDataConnection(true)
                isHorizontalMapRepetitionEnabled = true
                isVerticalMapRepetitionEnabled = true
                isTilesScaledToDpi = true

                // BITNO: Omogući hardware acceleration za bolju performance
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }

            Log.d("MapConfig", "Enhanced map configuration uspešno postavljena")

        } catch (e: Exception) {
            Log.e("MapConfig", "Greška pri map konfiguraciji: ${e.message}")
            Toast.makeText(this, "Upozorenje: Neke map opcije možda neće raditi optimalno", Toast.LENGTH_SHORT).show()
        }
    }




    private fun optimizeTileCacheForZoom(zoom: Double) {
        val config = Configuration.getInstance()
        when {
            zoom > 20.0 -> {
                config.cacheMapTileCount = 6000
                config.tileDownloadThreads = 4
                Log.d("ZoomOptimize", "Ultra high zoom optimization")
            }
            zoom > 18.0 -> {
                config.cacheMapTileCount = 4000
                config.tileDownloadThreads = 3
                Log.d("ZoomOptimize", "High zoom optimization")
            }
            else -> {
                config.cacheMapTileCount = 2000
                config.tileDownloadThreads = 2
            }
        }
    }


    private fun showZoomLevel(zoom: Double) {
        // Prikazuj zoom level samo ako je veći od 15 (kad postane bitan)
        if (zoom >= 15.0) {
            val zoomText = when {
                zoom > 20.0 -> "🔍 Ultra Zoom: ${String.format("%.1f", zoom)}x"
                zoom > 18.0 -> "🔍 High Zoom: ${String.format("%.1f", zoom)}x"
                else -> "🔍 Zoom: ${String.format("%.1f", zoom)}x"
            }

            // Koristi kratki toast samo za visoke zoom levele
            if (zoom > 18.0) {
                Toast.makeText(this, zoomText, Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun showZoomInfo(zoom: Double) {
        // Prikazuj zoom level samo ako je veći od 16 (kad postane bitan)
        if (zoom >= 16.0) {
            val zoomText = when {
                zoom > 20.0 -> "🔍 Ultra Zoom: ${String.format("%.1f", zoom)}x"
                zoom > 18.0 -> "🔍 High Zoom: ${String.format("%.1f", zoom)}x"
                else -> "🔍 Zoom: ${String.format("%.1f", zoom)}x"
            }

            // Koristi kratki toast samo za visoke zoom levele
            if (zoom > 18.0) {
                Toast.makeText(this, zoomText, Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun setHighQualityStandardMap() {
        try {
            binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
            binding.btnMapType.setBackgroundResource(R.drawable.button_dark_blue_accent)
            binding.mapView.maxZoomLevel = 22.0

            // PODEŠAVANJA ZA BOLJU OŠTRINU
            binding.mapView.setTilesScaledToDpi(true)
            binding.mapView.setBackgroundColor(Color.WHITE)

            Toast.makeText(this, "🗺️ OSM Standard (High Quality)", Toast.LENGTH_LONG).show()
            binding.mapView.invalidate()
        } catch (e: Exception) {
            Log.e("MapType", "Standard mapa nije dostupna: ${e.message}")
            setStandardMap()
        }
    }

    private fun initializeLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        )
            .setMinUpdateIntervalMillis(1000L)
            .setWaitForAccurateLocation(true)
            .setMinUpdateDistanceMeters(2.0f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return

                if (location.accuracy < 20.0f && location.accuracy > 0 && location.speed >= 0) {
                    val currentLocation = GeoPoint(location.latitude, location.longitude)

                    // AŽURIRAJ PODATKE
                    currentSpeed = location.speed * 3.6
                    currentAltitude = location.altitude
                    currentBearing = location.bearing

                    // ✅ AŽURIRAJ ORIJENTACIJU MAPE AKO JE OMOGUĆENO
                    if (location.hasBearing() && !isMapOrientationNorth) {
                        updateMapOrientation(location.bearing)
                    }
                    if (isCompassVisible && location.hasBearing()) {
                        updateCompassRotation(location.bearing)
                    }

                    // ✅ AUTO-CENTRIRANJE AKO JE OMOGUĆENO
                    if (isAutoFollowEnabled) {
                        // PROVERI DA LI JE MAPA INICIJALIZOVANA
                        if (::binding.isInitialized && binding.mapView != null) {
                            binding.mapView.controller.animateTo(currentLocation)
                        }
                    }

                    // PRIKAŽI SAMO JEDNU TAČNU LOKACIJU - SA PROVEROM
                    if (::binding.isInitialized && binding.mapView != null) {
                        showAccurateLocationMarker(currentLocation, location)
                    }

                    // TRACKING FUNKCIONALNOST
                    if (isTracking) {
                        addPointToRouteSmoothly(currentLocation, location)
                        updateDistanceAndSpeedAccurate(location)
                        updateTrackingNotification()
                        updateAccuracyProgress()
                    }

                    lastLocation = location
                }
            }
        }
    }
    private fun updateDistanceAndSpeedAccurate(newLocation: Location) {
        lastLocation?.let { lastLoc ->
            // KORISTI ANDROID-OVU distanceTo METODU - NAJPRECIZNIJA
            val distance = lastLoc.distanceTo(newLocation).toDouble()

            // KORISTI GPS BRZINU - NAJPRECIZNIJA
            val speed = if (newLocation.hasSpeed() && newLocation.speed > 0) {
                newLocation.speed * 3.6 // konvertuj u km/h
            } else {
                0.0
            }

            // JEDNOSTAVNO FILTRIRANJE - samo očigledne greške
            if (distance > 1.0 && distance < 500.0) {
                totalDistance += distance
                currentSpeed = speed

                Log.d("Tracking", "📏 +${String.format("%.1f", distance)}m | Ukupno: ${formatDistance(totalDistance)} | 🚀 Brzina: ${String.format("%.1f", speed)} km/h")
            } else {
                currentSpeed = speed
                Log.d("Tracking", "⏭️ Preskočeno: ${String.format("%.1f", distance)}m (GPS greška)")
            }

            updateTrackingStats(totalDistance, currentSpeed)

        } ?: run {
            // PRVA LOKACIJA
            currentSpeed = if (newLocation.hasSpeed() && newLocation.speed > 0) {
                newLocation.speed * 3.6
            } else {
                0.0
            }
            updateTrackingStats(totalDistance, currentSpeed)
            Log.d("Tracking", "📍 Prva lokacija | Brzina: ${String.format("%.1f", currentSpeed)} km/h")
        }

        lastLocation = newLocation
    }
    private fun enableSatelliteOfflineMode(regionName: String) {
        try {
            // KORISTITE generateUniqueFileName DA PRONAĐETE FAJL
            val fileName = generateUniqueFileName(regionName, true)
            val metadataDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "offline_regions")

            // PRONAĐITE FAJL - KORISTITE var ZA metadataFile
            var metadataFile = File(metadataDir, fileName)

            // ALTERNATIVNO: Tražite bilo koji fajl koji sadrži regionName
            if (!metadataFile.exists()) {
                // Fallback: pronađite fajl po regionName-u
                val matchingFiles = metadataDir.listFiles { file ->
                    file.name.contains(regionName.replace(" ", "_"), ignoreCase = true) &&
                            file.name.endsWith(".json")
                }

                if (!matchingFiles.isNullOrEmpty()) {
                    metadataFile = matchingFiles.first()  // 👈 OVO JE OK SADA JER JE var
                    Log.d("OfflineDebug", "Pronađen fajl po imenu: ${metadataFile.name}")
                }
            }

            if (metadataFile.exists()) {
                val metadata = Gson().fromJson(metadataFile.readText(), Map::class.java)
                val isSatellite = metadata["isSatellite"] as? Boolean ?: false

                if (!isSatellite) {
                    Toast.makeText(this, "❌ Ova mapa nije satelitska", Toast.LENGTH_SHORT).show()
                    return
                }

                Log.d("SatelliteOffline", "🛰️ Aktiviranje satelitske offline mape: $regionName")

                // Postavi satelitski tile source
                binding.mapView.setTileSource(createSatelliteTileSource())
                binding.mapView.setUseDataConnection(false)
                binding.mapView.maxZoomLevel = 23.0

                // Očisti cache za sigurnost
                binding.mapView.tileProvider.clearTileCache()
                binding.mapView.invalidate()

                // Sačuvaj informacije o aktivnoj mapi
                currentOfflineMapName = regionName
                currentOfflineMapIsSatellite = true
                currentOfflineMapMaxZoom = (metadata["maxZoom"] as? Number)?.toInt() ?: 19

                // PRIKAŽI DUGME ZA HIBRIDNI MOD
                binding.btnToggleHybrid.visibility = View.VISIBLE


                // Resetuj hibridni mod
                isHybridMode = false
                removeStreetOverlay()

                Toast.makeText(this, "🛰️ Satelitska offline: $regionName", Toast.LENGTH_SHORT).show()

            } else {
                Toast.makeText(this, "❌ Metadata fajl ne postoji: $fileName", Toast.LENGTH_SHORT).show()
                Log.e("OfflineDebug", "Traženi fajl: $fileName, Postojeći fajlovi:")
                metadataDir.listFiles()?.forEach { file ->
                    Log.e("OfflineDebug", "  - ${file.name}")
                }
            }

        } catch (e: Exception) {
            Toast.makeText(this, "❌ Greška: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun debugSatelliteTiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val satelliteDir = File(Configuration.getInstance().osmdroidBasePath, "tiles/World_Imagery")

                Log.d("TileDebug", "=== 🛰️ SATELLITE TILE DEBUG ===")
                Log.d("TileDebug", "📁 Cache dir exists: ${satelliteDir.exists()}")
                Log.d("TileDebug", "📁 Cache dir path: ${satelliteDir.absolutePath}")

                if (satelliteDir.exists()) {
                    var tileCount = 0
                    satelliteDir.walk().forEach { file ->
                        if (file.isFile && file.name.endsWith(".png")) {
                            tileCount++
                            if (tileCount <= 10) {
                                Log.d("TileDebug", "📄 Found tile: ${file.absolutePath} (${file.length()} bytes)")
                            }
                        }
                    }
                    Log.d("TileDebug", "📊 Total tiles found: $tileCount")

                    withContext(Dispatchers.Main) {
                        if (tileCount == 0) {
                            Toast.makeText(this@MainActivity, "❌ Nema tile-ova u cache-u!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainActivity, "✅ Pronađeno $tileCount tile-ova", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "❌ Cache folder ne postoji!", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("TileDebug", "💥 Debug error: ${e.message}")
            }
        }
    }
    private fun testSatelliteDownload() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("TestDownload", "🧪 POČINJEM TEST SATELITSKOG PREUZIMANJA...")

                // Preuzmite jedan tile za Niš
                val zoom = 14
                val x = 8800  // Koordinate za Niš
                val y = 5700

                Log.d("TestDownload", "📍 Preuzimam tile za Niš: $zoom/$x/$y")
                downloadSatelliteSingleTile(x, y, zoom)

                // Proverite da li je sačuvan
                val tileFile = File(Configuration.getInstance().osmdroidBasePath, "tiles/World_Imagery/$zoom/$x/$y.png")

                withContext(Dispatchers.Main) {
                    if (tileFile.exists()) {
                        Toast.makeText(this@MainActivity, "✅ Test tile uspešno preuzet!", Toast.LENGTH_LONG).show()
                        debugSatelliteTiles()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Test tile nije sačuvan!", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "💥 Greška: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    /*private fun debugSatelliteTiles(regionName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val satelliteDir = File(Configuration.getInstance().osmdroidBasePath, "tiles/World_Imagery")

                Log.d("TileDebug", "=== SATELLITE TILE DEBUG ===")
                Log.d("TileDebug", "Cache dir exists: ${satelliteDir.exists()}")
                Log.d("TileDebug", "Cache dir path: ${satelliteDir.absolutePath}")

                if (satelliteDir.exists()) {
                    var tileCount = 0
                    satelliteDir.walk().forEach { file ->
                        if (file.isFile && file.name.endsWith(".png")) {
                            tileCount++
                            if (tileCount <= 5) { // Prikaži prvih 5
                                Log.d("TileDebug", "Found tile: ${file.absolutePath}")
                            }
                        }
                    }
                    Log.d("TileDebug", "Total tiles found: $tileCount")

                    withContext(Dispatchers.Main) {
                        if (tileCount == 0) {
                            Toast.makeText(this@MainActivity, "⚠️ Nema tile-ova u cache-u!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainActivity, "✅ Pronađeno $tileCount tile-ova", Toast.LENGTH_LONG).show()
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("TileDebug", "Debug error: ${e.message}")
            }
        }
    }*/
    private fun checkSatelliteTilesAvailable(regionName: String): Int {
        return try {
            val cacheDir = File(filesDir, "osmdroid/tiles/World_Imagery")
            val tileCount = countTilesInDirectory(cacheDir)

            Log.d("TileCheck", "Satelitski tile-ovi dostupni: $tileCount, putanja: ${cacheDir.absolutePath}")

            tileCount
        } catch (e: Exception) {
            Log.e("TileCheck", "Greška pri proveri tile-ova: ${e.message}")
            0
        }
    }
    private fun showSatelliteMapsList() {
        val metadataDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "offline_regions")
        val regionFiles = metadataDir.listFiles { file ->
            file.name.endsWith(".json") && file.readText().contains("isSatellite")
        }

        if (!metadataDir.exists() || regionFiles.isNullOrEmpty()) {
            Toast.makeText(this, "?? Nema preuzetih satelitskih mapa", Toast.LENGTH_LONG).show()
            downloadOfflineMap() // Ponudi preuzimanje
            return
        }

        val regionNames = regionFiles.map { file ->
            try {
                val metadata = Gson().fromJson(file.readText(), Map::class.java)
                "??? ${metadata["regionName"]} (${metadata["tileCount"]} tile-ova)"
            } catch (e: Exception) {
                "? Nevažeća satelitska mapa"
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("?? Preuzete Satelitske Mape")
            .setItems(regionNames) { dialog, which ->
                val selectedFile = regionFiles[which]
                try {
                    val metadata = Gson().fromJson(selectedFile.readText(), Map::class.java)
                    val regionName = metadata["regionName"] as String
                    enableSatelliteOfflineMode(regionName)
                } catch (e: Exception) {
                    Toast.makeText(this, "? Greška pri učitavanju", Toast.LENGTH_SHORT).show()
                }
            }
            .setPositiveButton("? Preuzmi Novu") { dialog, which ->
                downloadOfflineMap()
            }
            .setNegativeButton("? Zatvori", null)
            .show()
    }
    private fun checkOfflineTilesAvailable(regionName: String): Boolean {
        return try {
            val isSatellite = regionName.contains("satelit", ignoreCase = true) ||
                    regionName.contains("satellite", ignoreCase = true)

            val cacheDir = if (isSatellite) {
                File(Configuration.getInstance().osmdroidBasePath, "tiles/World_Imagery")
            } else {
                File(Configuration.getInstance().osmdroidBasePath, "tiles/OpenStreetMap")
            }

            cacheDir.exists() && cacheDir.listFiles()?.isNotEmpty() == true
        } catch (e: Exception) {
            false
        }
    }

    /*private fun deleteUserWithAllData(user: User) {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Brisanje korisnika")
            .setMessage("Da li ste sigurni da želite da obrišete korisnika ${user.email}?\n\n" +
                    "Ova akcija će obrisati:\n" +
                    "📊 Sve rute korisnika\n" +
                    "📍 Sve tačke interesa\n" +
                    "👤 Korisnički nalog\n\n" +
                    "⚠️ Ova akcija se NE MOŽE poništiti!")
            .setPositiveButton("🗑️ Obriši") { dialog, which ->
                performUserDeletion(user)
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun performUserDeletion(user: User) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("🔄 Brisanje u toku...")
            .setMessage("Brišem korisnika i sve podatke...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App

                // 1. Obriši sve rute korisnika
                val userRoutes = app.routeRepository.getUserRoutes(user.id)
                userRoutes.forEach { route ->
                    // Prvo obriši sve tačke rute
                    app.routeRepository.deleteRoutePoints(route.id)
                    // Onda obriši rutu
                    app.routeRepository.deleteRoute(route)
                }

                // 2. Obriši sve tačke interesa
                val userPoints = app.pointRepository.getUserPoints(user.id)
                userPoints.forEach { point ->
                    app.pointRepository.deletePoint(point)
                }

                // 3. Obriši korisnika
                app.userRepository.deleteUser(user.id)

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@AdminActivity,  // ✅ ISPRAVNO: Dodajte @
                        "✅ Korisnik ${user.email} uspešno obrisan sa svim podacima!",
                        Toast.LENGTH_LONG
                    ).show()
                    loadUsers() // Osveži listu
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@AdminActivity,
                        "❌ Greška pri brisanju: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }*/
    private fun showNavigationMenu() {
        val options = arrayOf(
            "🗺️ Offline Mape",
            "🛠️ Alatke",
            "🧭 Kompas",
            "🗺️ Sačuvane rute",
            "🔄 Režim praćenja",
            "⚙️ Podešavanja",
            "👤 Admin Panel",
            "🚪 Odjava"
        )

        AlertDialog.Builder(this)
            .setTitle("🧭 Navigacioni Meni")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showOfflineMapsDialog()
                    1 -> showToolsDialog()
                    2 -> toggleCompass()
                    3 -> showRoutesRecyclerViewDialog()
                    4 -> toggleTrackingMode()
                    5 -> showSettings()
                    6 -> showAdminLoginDialog()
                    7 -> logout()
                }
            }
            .setNegativeButton("✖ Zatvori", null)
            .show()
    }

    private fun showRoutesRecyclerViewDialog() {
        if (savedRoutes.isEmpty()) {
            Toast.makeText(this, "❌ Nema sačuvanih ruta", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this).apply {
            setContentView(R.layout.dialog_routes_list)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val recyclerView = dialog.findViewById<RecyclerView>(R.id.recyclerViewRoutes)
        val tvRoutesCount = dialog.findViewById<TextView>(R.id.tvRoutesCount)
        val statisticsContainer = dialog.findViewById<LinearLayout>(R.id.statisticsContainer)
        val selectionContainer = dialog.findViewById<LinearLayout>(R.id.selectionContainer)
        val tvTotalDistance = dialog.findViewById<TextView>(R.id.tvTotalDistance)
        val tvTotalTime = dialog.findViewById<TextView>(R.id.tvTotalTime)
        val btnMultiSelect = dialog.findViewById<Button>(R.id.btnMultiSelect)
        val btnSelectAllRoutes = dialog.findViewById<Button>(R.id.btnSelectAllRoutes)
        val btnDeleteSelectedRoutes = dialog.findViewById<Button>(R.id.btnDeleteSelectedRoutes)
        val btnShowStats = dialog.findViewById<Button>(R.id.btnShowStats)
        val btnExport = dialog.findViewById<Button>(R.id.btnExport)
        val btnClose = dialog.findViewById<Button>(R.id.btnClose)

        recyclerView.layoutManager = LinearLayoutManager(this)

        lateinit var adapter: RoutesAdapter

        adapter = RoutesAdapter(
            routes = savedRoutes.sortedByDescending { it.startTime },
            onRouteClick = { route: Route ->
                if (!adapter.isMultiSelectMode()) {
                    dialog.dismiss()
                    showAdvancedRouteOptions(route)
                }
            },
            onShowOnMap = { route: Route ->
                dialog.dismiss()
                displayRouteOnMap(route)
            },
            onExportRoute = { route: Route ->
                dialog.dismiss()
                Toast.makeText(this, "📤 Izvoz rute: ${route.name}", Toast.LENGTH_SHORT).show()
            },
            onDeleteRoute = { route: Route ->
                if (!adapter.isMultiSelectMode()) {
                    dialog.dismiss()
                    deleteRoute(route)
                }
            }
        )

        recyclerView.adapter = adapter
        tvRoutesCount.text = "${savedRoutes.size} ruta"

        // Multi-select toggle
        var multiSelectMode = false
        btnMultiSelect.setOnClickListener {
            multiSelectMode = !multiSelectMode
            adapter.setMultiSelectMode(multiSelectMode)
            selectionContainer.visibility = if (multiSelectMode) View.VISIBLE else View.GONE
            btnMultiSelect.text = if (multiSelectMode) "✗ Završi izbor" else "✓ Višestruki izbor"
            btnMultiSelect.setBackgroundColor(
                if (multiSelectMode) Color.parseColor("#FFEBEE") else Color.parseColor("#E3F2FD")
            )
        }

        // Select all routes
        var allRoutesSelected = false
        btnSelectAllRoutes.setOnClickListener {
            allRoutesSelected = !allRoutesSelected
            adapter.selectAll(allRoutesSelected)
            btnSelectAllRoutes.text = if (allRoutesSelected) "✗ Deselektuj sve" else "☑️ Selektuj sve"
        }

        // Delete selected routes
        btnDeleteSelectedRoutes.setOnClickListener {
            val selectedRoutes = adapter.getSelectedRoutes()
            if (selectedRoutes.isNotEmpty()) {
                dialog.dismiss()
                showBulkRoutesDeleteConfirmation(selectedRoutes)
            } else {
                Toast.makeText(this, "⚠️ Nijedna ruta nije selektovana", Toast.LENGTH_SHORT).show()
            }
        }

        // Statistics
        var statsVisible = false
        btnShowStats.setOnClickListener {
            statsVisible = !statsVisible
            statisticsContainer.visibility = if (statsVisible) View.VISIBLE else View.GONE
            btnShowStats.text = if (statsVisible) "📊 Sakrij" else "📊 Statistika"

            if (statsVisible) {
                val totalDistance = savedRoutes.sumOf { it.distance }
                val totalTime = savedRoutes.sumOf { it.duration } / 1000 / 60

                tvTotalDistance.text = "Ukupna udaljenost: ${formatDistance(totalDistance)}"
                tvTotalTime.text = "Ukupno vreme: ${totalTime} min"
            }
        }

        // Export all
        btnExport.setOnClickListener {
            dialog.dismiss()
            showExportRoutesOptions()
        }

        // Close
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun showAdminLoginDialog() {
        val editText = EditText(this).apply {
            hint = "🔐 Unesite Admin secret key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("👑 Admin Panel Pristup")
            .setMessage("Za pristup Admin panelu unesite secret key:")
            .setView(editText)
            .setPositiveButton("🔓 Uloguj se") { dialog, _ ->
                val code = editText.text.toString().trim()
                if (code == "ADMIN123") {
                    // Sačuvaj admin pristup
                    val sharedPreferences = getSharedPreferences("admin_prefs", MODE_PRIVATE)
                    sharedPreferences.edit().putBoolean("has_admin_access", true).apply()

                    // Otvori Admin panel
                    val intent = Intent(this, AdminActivity::class.java)
                    startActivity(intent)

                    Toast.makeText(this@MainActivity, "✅ Admin pristup odobren!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "❌ Pogrešan kod!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }
    private fun openAdminPanel() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val userEmail = getCurrentUserFromPrefs()
                val user = app.userRepository.getUserByEmail(userEmail)

                // PROVERA SECRET ADMIN PRISTUPA
                val sharedPreferences = getSharedPreferences("admin_prefs", MODE_PRIVATE)
                val hasSecretAdminAccess = sharedPreferences.getBoolean("has_admin_access", false)

                runOnUiThread {
                    val isAdmin = user?.role == "ADMIN" ||
                            AdminManager.isMasterAdmin(userEmail) ||
                            hasSecretAdminAccess

                    if (isAdmin) {
                        val intent = Intent(this@MainActivity, AdminActivity::class.java)
                        startActivity(intent)
                    } else {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("🚫 Admin pristup")
                            .setMessage("Samo administratori mogu pristupiti Admin panelu.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun setupClickListeners() {
        try {
            Log.d("ClickSetup", "Postavljanje click listener-a...")

            // 1. TRACKING DUGMIĆI
            binding.btnStartTracking.setOnClickListener {
                Log.d("Click", "Start Tracking kliknut")
                startTracking()
            }
            binding.btnStopTracking.setOnClickListener {
                Log.d("Click", "Stop Tracking kliknut")
                stopTracking()
            }

            // 2. MOJA LOKACIJA - POPRAVLJENO
            binding.btnMyLocation.setOnClickListener {
                Log.d("Click", "Moja Lokacija kliknut")
                centerOnMyLocationSilent()
            }

            // DUGO ZADRŽAVANJE "Moja lokacija" za osvežavanje
            binding.btnMyLocation.setOnLongClickListener {
                refreshMapAndRoute()
                Toast.makeText(this, "?? Mapa i ruta osvežene", Toast.LENGTH_SHORT).show()
                true
            }

            // 3. MENI DUGME
            binding.btnMenu.setOnClickListener {
                Log.d("Click", "Meni kliknut")
                showNavigationMenu()
            }

            // 4. SAČUVANE RUTE
            binding.btnSavedRoutes.setOnClickListener {
                Log.d("Click", "Sačuvane rute kliknut")
                showRoutesRecyclerViewDialog()
            }

            // 5. ZOOM KONTROLE
            binding.btnZoomIn.setOnClickListener {
                Log.d("Click", "Zoom In kliknut")
                zoomIn()
            }

            binding.btnZoomOut.setOnClickListener {
                Log.d("Click", "Zoom Out kliknut")
                zoomOut()
            }

            // 6. DODAVANJE TAČKE (FAB)
            binding.fabAddPoint.setOnClickListener {
                Log.d("Click", "Dodaj tačku kliknut")
                togglePointMode()
            }

            // 7. TRACKING MODE (TEKST DUGME) - PROVERITE OVO
            binding.btnTrackingMode.setOnClickListener {
                Log.d("Click", "Tracking Mode kliknut")
                toggleTrackingMode()
            }

            // 8. NAVIGACIJA (GOOGLE MAPS)
            binding.btnNavigation.setOnClickListener {
                Log.d("Click", "Navigacija kliknut")
                openGoogleMaps()
            }

            // 9. TIP MAPE
            binding.btnMapType.setOnClickListener {
                Log.d("Click", "Tip mape kliknut")
                showMapTypeDialog()
            }

            // 10. EXPORT I RESET
            binding.btnExport?.setOnClickListener {
                Log.d("Click", "Export kliknut")
                exportRouteData()
            }
            binding.btnMyLocation?.setOnLongClickListener {
                refreshMap()
                Toast.makeText(this, "🔄 Mapa osvežena", Toast.LENGTH_SHORT).show()
                true
            }
            binding.btnReset?.setOnClickListener {
                Log.d("Click", "Reset kliknut")
                resetCurrentRoute()
            }
            binding.btnMapOrientation.setOnClickListener {
                toggleMapOrientation()
            }

            binding.btnFollowLocation.setOnClickListener {
                toggleAutoFollow()
            }


            Log.d("ClickSetup", "Svi click listener-i postavljeni uspešno")

        } catch (e: Exception) {
            Log.e("ClickSetup", "Greška pri postavljanju click listener-a: ${e.message}")
            e.printStackTrace()
        }
    }
    // ============================================
// MAP ORIENTATION & AUTO-FOLLOW FUNCTIONS
// ============================================

    private fun toggleMapOrientation() {
        isMapOrientationNorth = !isMapOrientationNorth

        if (isMapOrientationNorth) {
            // FIKSNA ORIJENTACIJA - SEVER GORE
            binding.mapView.mapOrientation = 0f
            binding.btnMapOrientation.setBackgroundResource(R.drawable.button_transparent_round)
            Toast.makeText(this, "🧭 Mapa fiksirana - Sever gore", Toast.LENGTH_SHORT).show()
            Log.d("MapOrientation", "Switched to North orientation")
        } else {
            // PRATI SMER KRETANJA
            binding.btnMapOrientation.setBackgroundResource(R.drawable.button_modern_accent)
            Toast.makeText(this, "🔄 Mapa prati smer kretanja", Toast.LENGTH_SHORT).show()
            Log.d("MapOrientation", "Switched to Follow direction")

            // Postavi trenutni bearing ako postoji
            lastLocation?.let { location ->
                if (location.hasBearing()) {
                    binding.mapView.mapOrientation = -location.bearing
                }
            }
        }
    }

    private fun toggleAutoFollow() {
        isAutoFollowEnabled = !isAutoFollowEnabled

        if (isAutoFollowEnabled) {
            // UKLJUĆI AUTO-PRAĆENJE
            locationOverlay.enableFollowLocation()
            binding.btnFollowLocation.setBackgroundResource(R.drawable.button_modern_accent)
            Toast.makeText(this, "📍 Auto-praćenje UKLJUČENO", Toast.LENGTH_SHORT).show()
            Log.d("AutoFollow", "Auto-follow enabled")

            // Odmah centruj na lokaciju
            centerOnMyLocationSilent()
        } else {
            // ISKLJUČI AUTO-PRAĆENJE
            locationOverlay.disableFollowLocation()
            binding.btnFollowLocation.setBackgroundResource(R.drawable.button_transparent_round)
            Toast.makeText(this, "📍 Auto-praćenje ISKLJUČENO", Toast.LENGTH_SHORT).show()
            Log.d("AutoFollow", "Auto-follow disabled")
        }
    }

    private fun updateMapOrientation(bearing: Float) {
        if (!::binding.isInitialized || binding.mapView == null) return

        if (!isMapOrientationNorth && isAutoFollowEnabled) {
            // Rotiraj mapu prema smeru kretanja
            binding.mapView.mapOrientation = -bearing
        }
    }
    private fun centerOnMyLocationSilent() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissions()
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    if (it.accuracy < 50.0f) {
                        val currentLocation = GeoPoint(it.latitude, it.longitude)
                        binding.mapView.controller.animateTo(currentLocation)
                        binding.mapView.controller.setZoom(18.0)

                        // ✅ PRIKAŽI MARKER ALI BEZ DIJALOGA
                        showAccurateLocationMarker(currentLocation, it)

                        lastLocation = it
                        Log.d("Location", "Centriranje uspešno: ${it.accuracy}m")
                    } else {
                        val currentLocation = GeoPoint(it.latitude, it.longitude)
                        binding.mapView.controller.animateTo(currentLocation)
                        binding.mapView.controller.setZoom(16.0)
                        showAccurateLocationMarker(currentLocation, it)
                    }
                } ?: run {
                    Toast.makeText(this, "🔍 Tražim lokaciju...", Toast.LENGTH_SHORT).show()
                    startQuickLocationUpdate()
                }
            }
            .addOnFailureListener { e ->
                Log.e("Location", "Greška pri dobijanju lokacije: ${e.message}")
                Toast.makeText(this, "🔍 Tražim lokaciju...", Toast.LENGTH_SHORT).show()
                startQuickLocationUpdate()
            }
    }

    // POMOĆNE FUNKCIJE ZA KONVERZIJU TILE -> COORDINATES
    private fun tile2lat(y: Int, zoom: Int): Double {
        val n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, zoom.toDouble())
        return Math.toDegrees(Math.atan(Math.sinh(n)))
    }

    private fun tile2lon(x: Int, zoom: Int): Double {
        return (x / Math.pow(2.0, zoom.toDouble()) * 360.0) - 180.0
    }
    private fun resetZoomToLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissions()
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLocation = GeoPoint(it.latitude, it.longitude)

                    // RESET ZOOM NA PODRAŽAJNU VREDNOST
                    binding.mapView.controller.animateTo(currentLocation)
                    binding.mapView.controller.setZoom(16.0) // Standardni zoom level

                    Toast.makeText(this, "🎯 Zoom resetovan na lokaciju", Toast.LENGTH_SHORT).show()
                    Log.d("ZoomReset", "Zoom resetovan na lokaciju: ${it.latitude}, ${it.longitude}")
                } ?: run {
                    Toast.makeText(this, "📍 Lokacija nije dostupna", Toast.LENGTH_SHORT).show()
                    startQuickLocationUpdate()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ZoomReset", "Greška pri resetovanju zooma: ${e.message}")
                Toast.makeText(this, "❌ Greška pri resetovanju", Toast.LENGTH_SHORT).show()
            }
    }

    private fun drawRouteOnMap() {
        polylines.forEach { binding.mapView.overlays.remove(it) }
        polylines.clear()

        if (routePoints.size < 2) {
            Log.d("Tracking", "Premalo tačaka za crtanje: ${routePoints.size}")
            binding.mapView.invalidate()
            return
        }

        try {
            val polyline = Polyline()
            polyline.setPoints(routePoints)

            polyline.outlinePaint.color = Color.parseColor("#FF3498DB")
            polyline.outlinePaint.strokeWidth = 8.0f
            polyline.outlinePaint.style = android.graphics.Paint.Style.STROKE

            binding.mapView.overlays.add(polyline)
            polylines.add(polyline)
            binding.mapView.invalidate()

            Log.d("Tracking", "Ruta nacrtana sa ${routePoints.size} tačaka")
        } catch (e: Exception) {
            Log.e("Tracking", "Greška pri crtanju rute: ${e.message}")
        }
    }

    private fun addPointToRouteSmoothly(location: GeoPoint, androidLocation: Location) {
        if (!isTracking) return

        if (!isValidLocationForTracking(location, androidLocation)) {
            return
        }

        // DODAJ U TEKUĆI SEGMENT
        currentSegment.add(location)
        routePoints.add(location)

        // ČEŠĆE OSVEŽAVANJE ZA BOLJI PRIKAZ
        val shouldRedraw = currentSegment.size % 2 == 0 ||
                System.currentTimeMillis() - lastDrawTime > 3000 ||
                !isActivityVisible // Redraw when coming from background

        if (shouldRedraw && isActivityVisible) {
            runOnUiThread {
                drawSmoothRouteOnMap()
            }
            lastDrawTime = System.currentTimeMillis()
        }

        savePointToDatabase(location)
        Log.d("Tracking", "?? Dodata tačka ${currentSegment.size}: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}")
    }
    private fun resetMapOverlays() {
        try {
            runOnUiThread {
                // Privremeno ukloni sve overlay-e
                binding.mapView.overlays.clear()

                // Ponovo dodaj kompas overlay
                val compassOverlay = CompassOverlay(this, InternalCompassOrientationProvider(this), binding.mapView)
                compassOverlay.enableCompass()
                binding.mapView.overlays.add(compassOverlay)

                // Ponovo dodaj location overlay
                locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView)
                locationOverlay.enableMyLocation()
                locationOverlay.isDrawAccuracyEnabled = false
                binding.mapView.overlays.add(locationOverlay)

                // Ponovo dodaj click listener overlay
                binding.mapView.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
                    override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: org.osmdroid.views.MapView?): Boolean {
                        // ... postojeći click handler kod ...
                        return false
                    }
                })

                // Ponovo iscrtaj rutu
                if (isTracking && routePoints.isNotEmpty()) {
                    drawSmoothRouteOnMap()
                }

                // Ponovo prikaži tačke interesa
                refreshPointsOfInterest()

                binding.mapView.invalidate()
                Log.d("MapReset", "?? Svi overlay-i resetovani")
            }
        } catch (e: Exception) {
            Log.e("MapReset", "?? Greška pri resetovanju overlay-a: ${e.message}")
        }
    }
    private fun refreshPointsOfInterest() {
        try {
            // Ukloni sve postojeće markere tačaka
            pointMarkers.values.forEach { marker ->
                binding.mapView.overlays.remove(marker)
            }
            pointMarkers.clear()

            // Ponovo dodaj sve tačke interesa
            pointsOfInterest.forEach { point ->
                val location = GeoPoint(point.latitude, point.longitude)
                addMarkerToMap(location, point.name, point.id)
            }

            binding.mapView.invalidate()
            Log.d("PointsRefresh", "?? Tačke interesa osvežene: ${pointsOfInterest.size} tačaka")
        } catch (e: Exception) {
            Log.e("PointsRefresh", "?? Greška pri osvežavanju tačaka: ${e.message}")
        }
    }

    private fun drawSmoothRouteOnMap() {
        // PROVERI DA LI JE MAPA INICIJALIZOVANA
        if (!::binding.isInitialized || binding.mapView == null) {
            Log.w("RouteDrawing", "MapView nije inicijalizovan, preskačem crtanje rute")
            return
        }

        try {
            // UKLONI SVE PRETHODNE LINIJE
            polylines.forEach { polyline ->
                binding.mapView.overlays.remove(polyline)
            }
            polylines.clear()

            // CRTANJE SVIH SEGMENATA
            routeSegments.forEachIndexed { index, segment ->
                if (segment.size >= 2) {
                    val polyline = Polyline().apply {
                        setPoints(segment)
                        outlinePaint.color = Color.parseColor("#FF2196F3")
                        outlinePaint.strokeWidth = 12.0f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        outlinePaint.style = Paint.Style.STROKE
                    }
                    binding.mapView.overlays.add(polyline)
                    polylines.add(polyline)
                    Log.d("RouteDrawing", "?? Segment $index iscrtan sa ${segment.size} tačaka")
                }
            }

            // CRTANJE TEKUĆEG SEGMENTA
            if (currentSegment.size >= 2) {
                val currentPolyline = Polyline().apply {
                    setPoints(currentSegment)
                    outlinePaint.color = Color.parseColor("#FF2196F3")
                    outlinePaint.strokeWidth = 12.0f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.style = Paint.Style.STROKE
                }
                binding.mapView.overlays.add(currentPolyline)
                polylines.add(currentPolyline)
                Log.d("RouteDrawing", "?? Tekući segment iscrtan sa ${currentSegment.size} tačaka")
            }

            // FORSIRAJ OSVEŽAVANJE
            binding.mapView.invalidate()
            binding.mapView.postInvalidate()

        } catch (e: Exception) {
            Log.e("RouteDrawing", "?? Greška pri crtanju rute: ${e.message}")
        }
    }

    private var lastDrawTime: Long = 0

    private fun isValidLocationForTracking(location: GeoPoint, androidLocation: Location): Boolean {
        // PROVERA GEOGRAFSKIH GRANICA (Srbija)
        if (location.latitude < 40.0 || location.latitude > 47.0 ||
            location.longitude < 18.0 || location.longitude > 23.0) {
            return false
        }

        // PROVERA AKURATNOSTI
        if (androidLocation.accuracy > 25.0f) {
            return false
        }

        // PROVERA RAZMAKA OD PRETHODNE TAČKE
        if (routePoints.isNotEmpty()) {
            val lastPoint = routePoints.last()
            val distance = calculateDistance(
                lastPoint.latitude, lastPoint.longitude,
                location.latitude, location.longitude
            )

            // FILTRIRAJ PREVELIKE I PREMANJE RAZMAKE
            if (distance < 3.0 || distance > 100.0) {
                return false
            }
        }

        return true
    }
    private fun showAccurateLocationMarker(location: GeoPoint, androidLocation: Location) {
        // PROVERI DA LI JE MAPVIEW INICIJALIZOVAN
        if (!::binding.isInitialized || binding.mapView == null) {
            Log.w("LocationMarker", "MapView nije inicijalizovan, preskačem prikaz markera")
            return
        }

        // UKLONI STARI MARKER
        myLocationMarker?.let { marker ->
            binding.mapView.overlays.remove(marker)
        }

        // BOLJE IKONE - koristi uvek istu, jasnu ikonu
        val iconRes = R.drawable.ic_my_location

        // KREIRAJ NOVI MARKER
        val marker = Marker(binding.mapView).apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            // BOLJI TITLE
            title = when {
                currentSpeed > 5.0 -> "🚗 Vožnja - ${String.format("%.1f", currentSpeed)} km/h"
                currentSpeed > 1.0 -> "🚶 Kretanje - ${String.format("%.1f", currentSpeed)} km/h"
                else -> "📍 Moja lokacija"
            }

            // JASNA IKONA
            val iconDrawable = ContextCompat.getDrawable(this@MainActivity, iconRes)
            setIcon(iconDrawable)

            if (androidLocation.hasBearing()) {
                rotation = androidLocation.bearing
            }

            // CLICK LISTENER
            setOnMarkerClickListener { marker, mapView ->
                showMyLocationDetails()
                true
            }
        }

        binding.mapView.overlays.add(marker)
        myLocationMarker = marker

        // OSVEŽI PRIKAZ SAMO AKO JE MAPA VIDLJIVA
        if (isActivityVisible) {
            binding.mapView.invalidate()
        }
    }
    // PAMETNO FILTRIRANJE POKRETA
    private fun isValidMovement(distance: Double, lastLoc: Location, newLoc: Location): Boolean {
        val timeDiff = (newLoc.time - lastLoc.time) / 1000.0

        // SAMO OČIGLEDNE GREŠKE
        if (distance < 1.0) return false  // Premali pokret
        if (distance > 500.0) return false // Preveliki skok
        if (timeDiff < 0.5) return false   // Prebrzo ažuriranje

        return true
    }
    private fun getBestLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // POKUŠAJTE DA DOBIJETE POSLEDNJU LOKACIJU
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    // OPUSTITE USLOVE ZA PRIHVATANJE LOKACIJE
                    if (it.accuracy < 100.0f) { // SA 25 NA 100m
                        Log.d("Location", "Pronađena dobra poslednja lokacija: ${it.accuracy}m")
                        val currentLocation = GeoPoint(it.latitude, it.longitude)
                        binding.mapView.controller.animateTo(currentLocation)
                        binding.mapView.controller.setZoom(17.0)
                        isMapCentered = true
                        lastLocation = it

                        // PRIKAŽI MARKER
                        showAccurateLocationMarker(currentLocation, it)
                    } else {
                        Log.d("Location", "Poslednja lokacija nije idealna: ${it.accuracy}m, ali je prihvatljiva")
                        // IPAK JE KORISTITE DOK SE NE DOBIJE BOLJA
                        val currentLocation = GeoPoint(it.latitude, it.longitude)
                        binding.mapView.controller.animateTo(currentLocation)
                        binding.mapView.controller.setZoom(16.0)
                        showAccurateLocationMarker(currentLocation, it)
                    }
                } ?: run {
                    Log.d("Location", "Nema poslednje lokacije - pokrećem traženje")
                    startLocationUpdates()
                }
            }
            .addOnFailureListener { e ->
                Log.e("Location", "Greška pri dobijanju poslednje lokacije: ${e.message}")
                startLocationUpdates()
            }
    }

    // BRZINA IZ UDALJENOSTI I VREMENA
    private fun calculateSpeedFromDistance(distance: Double, startTime: Long, endTime: Long): Double {
        val timeDiff = (endTime - startTime) / 1000.0 // u sekundama
        return if (timeDiff > 0) (distance / timeDiff) * 3.6 else 0.0 // km/h
    }
    // HAVERSINE FORMULA - NAJTAČNIJA METODA
    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // metri

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        val distance = earthRadius * c

        // DEBUG LOG
        Log.d("DistanceCalc", "📍 ($lat1, $lon1) -> ($lat2, $lon2) = ${String.format("%.2f", distance)}m")

        return distance
    }
    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Izlazak iz aplikacije")
            .setMessage("Da li ste sigurni da želite da izađete?")
            .setPositiveButton("Da") { dialog, which ->
                finish()
            }
            .setNegativeButton("Ne", null)
            .show()
    }
    private fun toggleLocationFollowing() {
        isFollowingLocation = !isFollowingLocation

        if (isFollowingLocation) {
            locationOverlay.enableFollowLocation()
            binding.btnMyLocation.setBackgroundResource(R.drawable.button_dark_blue_accent)
            Toast.makeText(this, "🗺️ Automatsko praćenje UKLJUČENO", Toast.LENGTH_SHORT).show()
            Log.d("Location", "Automatsko praćenje ručno uključeno")
        } else {
            locationOverlay.disableFollowLocation()
            binding.btnMyLocation.setBackgroundResource(R.drawable.button_dark_blue)
            Toast.makeText(this, "🗺️ Automatsko praćenje ISKLJUČENO", Toast.LENGTH_SHORT).show()
            Log.d("Location", "Automatsko praćenje ručno isključeno")
        }
    }

    private fun showMyLocationDetails() {
        val location = locationOverlay.myLocation ?: run {
            Toast.makeText(this, "📍 Lokacija nije dostupna", Toast.LENGTH_SHORT).show()
            return
        }

        val coordinates = "📍 ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}"
        val speed = "🚀 Brzina: ${String.format("%.1f", currentSpeed)} km/h"
        val altitude = "⛰️ Visina: ${String.format("%.0f", currentAltitude)} m"
        val battery = "🔋 Baterija: ${getBatteryLevel()}%"
        val accuracy = lastLocation?.accuracy ?: 0f
        val accuracyText = "🎯 Tačnost: ${String.format("%.0f", accuracy)} m"

        // DODAJ SMER KRETANJA
        val bearing = lastLocation?.bearing ?: 0f
        val bearingText = if (lastLocation?.hasBearing() == true) {
            "🧭 Smer: ${getBearingDirection(bearing)} (${String.format("%.0f", bearing)}°)"
        } else {
            "🧭 Smer: Nepoznat"
        }

        val message = """
$coordinates
$accuracyText
$speed
$altitude  
$bearingText
$battery

🌐 Akcije:
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("📍 Moja Lokacija")
            .setMessage(message)
            .setPositiveButton("🗺️ Google Mape") { dialog, which ->
                openGoogleMapsForCurrentLocation()
            }
            .setNeutralButton("📋 Kopiraj koordinate") { dialog, which ->
                copyCoordinatesToClipboard(location.latitude, location.longitude)
            }
            .setNegativeButton("❌ Zatvori", null)
            .show()
    }
    private fun openGoogleMapsForCurrentLocation() {
        val location = locationOverlay.myLocation ?: return

        try {
            val uri = "geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            intent.setPackage("com.google.android.apps.maps")

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                val webUri = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri))
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Greška pri otvaranju Google Maps", Toast.LENGTH_SHORT).show()
        }
    }


    private fun copyCoordinatesToClipboard(lat: Double, lon: Double) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("coordinates", "$lat, $lon")
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "✅ Koordinate kopirane", Toast.LENGTH_SHORT).show()
    }


    private fun getBearingFromLastLocation(): String {
        val currentLocation = locationOverlay.myLocation ?: return "Nepoznato"
        val lastLoc = lastLocation ?: return "Nepoznato"

        val bearing = lastLoc.bearingTo(android.location.Location("").apply {
            latitude = currentLocation.latitude
            longitude = currentLocation.longitude
        })

        return getBearingDirection(bearing)
    }

    private fun initializeMap() {
        try {
            Configuration.getInstance().cacheMapTileCount = 1000
            Configuration.getInstance().tileDownloadThreads = 3
            Configuration.getInstance().tileFileSystemThreads = 2
            Configuration.getInstance().tileDownloadMaxQueueSize = 100

            binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
            binding.mapView.setMultiTouchControls(true)
            binding.mapView.minZoomLevel = 3.0
            binding.mapView.maxZoomLevel = 19.0
            binding.mapView.setTilesScaledToDpi(true)

            val compassOverlay = CompassOverlay(this, InternalCompassOrientationProvider(this), binding.mapView)
            compassOverlay.enableCompass()
            binding.mapView.overlays.add(compassOverlay)

            // ISPRAVLJENA INICIJALIZACIJA LOCATION OVERLAY
            locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView)
            locationOverlay.enableMyLocation()

            // 👇 OVO JE KLJUČNO: ISKLJUČI ACCURACY CIRCLE
            locationOverlay.isDrawAccuracyEnabled = false

            // 👇 OPCIONALNO: ISKLJUČI I DEFAULT PERSON ICON (ako želiš samo naš marker)
            // locationOverlay.setPersonIcon(null)

            binding.mapView.overlays.add(locationOverlay)

            // Overlay za klikove ostaje isti
            binding.mapView.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
                override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: org.osmdroid.views.MapView?): Boolean {
                    try {
                        if (mapView != null && e != null) {
                            val tappedPoint = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint

                            if (isPointMode) {
                                showAddPointDialog(tappedPoint)
                                return true
                            }

                            val myLocation = locationOverlay.myLocation
                            if (myLocation != null) {
                                val distance = calculateDistance(
                                    myLocation.latitude, myLocation.longitude,
                                    tappedPoint.latitude, tappedPoint.longitude
                                )
                                if (distance < 5.0) {
                                    showMyLocationDetails()
                                    return true
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        Log.e("MainActivity", "Greška u click handler: ${ex.message}")
                    }
                    return false
                }
            })

            val nis = GeoPoint(43.3209, 21.8958)
            binding.mapView.controller.setZoom(13.0)
            binding.mapView.controller.setCenter(nis)

            Log.d("MainActivity", "Mapa uspešno inicijalizovana")

        } catch (e: Exception) {
            Log.e("MainActivity", "Greška pri inicijalizaciji mape: ${e.message}")
            Toast.makeText(this, "Greška pri učitavanju mape", Toast.LENGTH_SHORT).show()
        }
    }
    private fun disableAccuracyCircle() {
        try {
            // PRONAĐI SVE OVERLAYE I ISKLJUČI IM ACCURACY
            binding.mapView.overlays.forEach { overlay ->
                if (overlay is MyLocationNewOverlay) {
                    overlay.isDrawAccuracyEnabled = false
                }
            }

            // OSVEŽI PRIKAZ
            binding.mapView.invalidate()

        } catch (e: Exception) {
            Log.e("AccuracyCircle", "Greška pri isključivanju accuracy circle: ${e.message}")
        }
    }
    private fun startQuickLocationUpdate() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // KORISTITE BRZI LocationRequest ZA HITNO DOBIJANJE LOKACIJE
        val fastLocationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        ).setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(false)
            .setMaxUpdateDelayMillis(2000L)
            .build()

        val fastLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    Log.d("Location", "Brza lokacija pronađena: ${location.accuracy}m")

                    // CENTRIRAJTE SE ČAK I AKO LOKACIJA NIJE SAVRŠENO TAČNA
                    val currentLocation = GeoPoint(location.latitude, location.longitude)
                    binding.mapView.controller.animateTo(currentLocation)
                    binding.mapView.controller.setZoom(18.0)
                    showAccurateLocationMarker(currentLocation, location)

                    // Pređite na normalan režim nakon što dobijete lokaciju
                    fusedLocationClient.removeLocationUpdates(this)
                    startLocationUpdates()
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                fastLocationRequest,
                fastLocationCallback,
                Looper.getMainLooper()
            )
            Log.d("Location", "Brzo pokretanje lokacije za centriranje")
        } catch (e: SecurityException) {
            Log.e("Location", "Security exception pri brzom pokretanju: ${e.message}")
        }
    }
    private fun showAddPointDialog(location: GeoPoint) {
        val editText = EditText(this).apply {
            hint = "Ime tačke"
        }

        AlertDialog.Builder(this)
            .setTitle("Dodaj tačku")
            .setView(editText)
            .setPositiveButton("Dodaj") { dialog, _ ->
                val name = editText.text?.toString()?.takeIf { it.isNotBlank() } ?: "Nova tačka"
                addPointOfInterest(name, location)
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun addPointOfInterest(name: String, location: GeoPoint) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val userEmail = getCurrentUserFromPrefs()
                val user = app.userRepository.getUserByEmail(userEmail)

                if (user != null && !FeatureManager.canCreateUnlimitedPoints(user)) {
                    val currentPoints = app.pointRepository.getUserPoints(user.id)
                    val maxPoints = FeatureManager.getMaxPoints(user)

                    if (currentPoints.size >= maxPoints) {
                        runOnUiThread {
                            showPointsLimitReachedDialog(maxPoints)
                        }
                        return@launch
                    }
                }

                // NASTAVAK POSTOJEĆEG KODA...
                val existingPoints = app.pointRepository.getUserPoints(getCurrentUserId())
                val duplicatePoint = existingPoints.find { it.name.equals(name, ignoreCase = true) }

                if (duplicatePoint != null) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "❌ Tačka sa imenom '$name' već postoji!", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val point = PointOfInterest(
                    userId = getCurrentUserId(),
                    name = name,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    createdAt = System.currentTimeMillis()
                )

                app.pointRepository.addPoint(point)

                runOnUiThread {
                    // 1. OSVEŽI LISTU TAČAKA
                    loadPointsOfInterest() // Ovo će ponovo učitati sve tačke iz baze

                    // 2. DODAJ MARKER NA MAPU
                    addMarkerToMap(location, name, point.id)

                    // 3. PRIKAŽI POTVRDU
                    notificationHelper.showPointAdded(name)

                    val message = """
                ✅ Tačka '$name' dodata!
                
                📍 ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}
                
                Klikni na tačku za više opcija.
            """.trimIndent()

                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()

                    // 4. OSVEŽI MAPU
                    binding.mapView.invalidate()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška pri čuvanju tačke: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addMarkerToMap(location: GeoPoint, title: String, pointId: String) {
        // Kreirajte custom marker sa tekstom
        val marker = object : Marker(binding.mapView) {
            override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
                super.draw(canvas, mapView, shadow)

                // Nacrtajte tekst ispod markera
                if (!shadow) {
                    val project = mapView.projection
                    val point = project.toPixels(position, null)

                    val textPaint = android.graphics.Paint().apply {
                        color = Color.WHITE
                        textSize = 36f  // Veličina teksta
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        setShadowLayer(3f, 0f, 0f, Color.BLACK)
                    }

                    val backgroundPaint = android.graphics.Paint().apply {
                        color = Color.argb(180, 255, 0, 0)  // Crvena pozadina
                        style = android.graphics.Paint.Style.FILL
                    }

                    // Izmerite tekst
                    val textBounds = android.graphics.Rect()
                    textPaint.getTextBounds(title, 0, title.length, textBounds)

                    val padding = 8f  // PROMENJENO: 8f umesto 8
                    val backgroundRect = android.graphics.RectF(
                        point.x - textBounds.width() / 2 - padding,  // ISPRAVLJENO: point.x je Int
                        point.y + 40f,  // Pozicija ispod markera
                        point.x + textBounds.width() / 2 + padding,  // ISPRAVLJENO: point.x je Int
                        point.y + 40f + textBounds.height() + padding * 2
                    )

                    // Nacrtajte pozadinu
                    canvas.drawRoundRect(backgroundRect, 8f, 8f, backgroundPaint)

                    // Nacrtajte tekst
                    canvas.drawText(
                        title,
                        point.x.toFloat(),  // ISPRAVLJENO: konvertujte u Float
                        point.y + 40f + textBounds.height() + padding,
                        textPaint
                    )
                }
            }
        }

        marker.position = location
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "📍 $title"
        marker.snippet = "Klikni za opcije"
        marker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_point_blue))

        marker.setOnMarkerClickListener { marker, mapView ->
            marker.showInfoWindow()
            showPointOptionsDialog(pointId, title, location)
            true
        }

        binding.mapView.overlays.add(marker)
        pointMarkers[pointId] = marker
        binding.mapView.invalidate()
    }

    private fun showPointOptionsDialog(pointId: String, pointName: String, location: GeoPoint) {
        val options = arrayOf(
            "Prikaži detalje",
            "Navigiraj do tačke",
            "Preimenuj tačku",
            "Obriši tačku",
            "Otkaži"
        )

        AlertDialog.Builder(this)
            .setTitle("Tačka: $pointName")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showPointDetails(pointName, location)
                    1 -> navigateToPoint(location)
                    2 -> renamePoint(pointId, pointName)
                    3 -> deletePoint(pointId, pointName)
                }
            }
            .show()
    }

    private fun renamePoint(pointId: String, currentName: String) {
        val editText = EditText(this).apply {
            setText(currentName)
            hint = "Unesite novo ime tačke"
            setSelectAllOnFocus(true)
        }

        AlertDialog.Builder(this)
            .setTitle("Preimenuj tačku")
            .setMessage("Trenutno ime: $currentName")
            .setView(editText)
            .setPositiveButton("Sačuvaj") { dialog, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isBlank()) {
                    Toast.makeText(this, "Ime ne može biti prazno!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newName == currentName) {
                    Toast.makeText(this, "Ime nije promenjeno", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (isPointNameDuplicate(newName, pointId)) {
                    Toast.makeText(this, "Tačka sa imenom '$newName' već postoji!", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                updatePointName(pointId, currentName, newName)
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun isPointNameDuplicate(newName: String, currentPointId: String): Boolean {
        return pointsOfInterest.any {
            it.name.equals(newName, ignoreCase = true) && it.id != currentPointId
        }
    }

    private fun updatePointName(pointId: String, oldName: String, newName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as jovannedeljkovic.gps_tracker_pro.App
                val point = app.pointRepository.getPointById(pointId)
                point?.let { existingPoint ->
                    val updatedPoint = existingPoint.copy(name = newName)
                    app.pointRepository.updatePoint(updatedPoint)
                    runOnUiThread {
                        val index = pointsOfInterest.indexOfFirst { it.id == pointId }
                        if (index != -1) {
                            pointsOfInterest[index] = updatedPoint
                        }
                        updateMarkerName(pointId, newName)
                        notificationHelper.showPointRenamed(oldName, newName)
                        Toast.makeText(
                            this@MainActivity,
                            "Tačka '$oldName' preimenovana u '$newName'",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } ?: run {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Greška: Tačka nije pronađena!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Greška pri preimenovanju: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun updateMarkerName(pointId: String, newName: String) {
        pointMarkers[pointId]?.let { marker ->
            marker.title = newName
            if (marker.isInfoWindowShown) {
                marker.closeInfoWindow()
                marker.showInfoWindow()
            }
            binding.mapView.invalidate()
        }
    }

    private fun showPointDetails(pointName: String, location: GeoPoint) {
        val distance = calculateDistanceToPoint(PointOfInterest(
            id = "",
            userId = getCurrentUserId(),
            name = pointName,
            latitude = location.latitude,
            longitude = location.longitude,
            createdAt = System.currentTimeMillis()
        ))

        val currentUser = getUserName()
        val details = """
        📍 $pointName
        
        👤 Kreirao: $currentUser
        📅 ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}
        
        Koordinate:
        ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}
        
        Udaljenost od vas:
        ${formatDistance(distance)}
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Detalji tačke")
            .setMessage(details)
            .setPositiveButton("Podeli tačku") { dialog, which ->
                sharePoint(pointName, location)
            }
            .setNegativeButton("Zatvori", null)
            .show()
    }

    private fun sharePoint(pointName: String, location: GeoPoint) {
        val shareText = """
        📍 $pointName
        
        Lokacija: 
        ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}
        
        Google Maps:
        https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}
        
        Deljeno iz GPS Tracker aplikacije
    """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Tačka: $pointName")
        }
        startActivity(Intent.createChooser(intent, "Podeli tačku"))
    }

    private fun deletePoint(pointId: String, pointName: String) {
        AlertDialog.Builder(this)
            .setTitle("Brisanje tačke")
            .setMessage("Da li ste sigurni da želite da obrišete tačku '$pointName'?")
            .setPositiveButton("Obriši") { dialog, which ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val app = application as jovannedeljkovic.gps_tracker_pro.App
                        val point = app.pointRepository.getPointById(pointId)
                        point?.let {
                            app.pointRepository.deletePoint(it)
                            runOnUiThread {
                                pointMarkers[pointId]?.let { marker ->
                                    binding.mapView.overlays.remove(marker)
                                }
                                pointMarkers.remove(pointId)
                                binding.mapView.invalidate()
                                Toast.makeText(this@MainActivity, "Tačka '$pointName' obrisana", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Greška pri brisanju: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun navigateToPoint(location: GeoPoint) {
        try {
            val uri = "google.navigation:q=${location.latitude},${location.longitude}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            intent.setPackage("com.google.android.apps.maps")
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                val mapsUri = "https://www.google.com/maps/dir/?api=1&destination=${location.latitude},${location.longitude}"
                val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUri))
                startActivity(mapsIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Greška pri navigaciji: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
// DODAJTE OVE METODE U MAIN ACTIVITY:

    private fun startBackgroundTracking() {
        if (isTracking) {
            // REGISTRUJ RECEIVER PRVO
            val filter = IntentFilter("BACKGROUND_LOCATION_UPDATE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(backgroundLocationReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(backgroundLocationReceiver, filter)
            }
            isReceivingBackgroundUpdates = true

            // POKRENI SERVIS
            val intent = Intent(this, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START_TRACKING
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            Log.d("Tracking", "?? Pozadinsko snimanje pokrenuto")
        }
    }
    private fun stopBackgroundTracking() {
        // UKLONI RECEIVER
        if (isReceivingBackgroundUpdates) {
            try {
                unregisterReceiver(backgroundLocationReceiver)
                isReceivingBackgroundUpdates = false
            } catch (e: Exception) {
                Log.e("Tracking", "Greška pri uklanjanju receivera: ${e.message}")
            }
        }

        // ZAUSTAVI SERVIS
        val intent = Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP_TRACKING
        }
        stopService(intent)
        Log.d("Tracking", "?? Pozadinsko snimanje zaustavljeno")
    }

    // TRACKING FUNCTIONALITY
    private fun startTracking() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val userEmail = getCurrentUserFromPrefs()
                val user = app.userRepository.getUserByEmail(userEmail)

                if (user != null && !FeatureManager.canCreateUnlimitedRoutes(user)) {
                    val dailyRoutes = getTodayRouteCount(user.id)
                    val maxRoutes = FeatureManager.getMaxDailyRoutes(user)

                    if (dailyRoutes >= maxRoutes) {
                        runOnUiThread {
                            showDailyLimitReachedDialog(maxRoutes)
                        }
                        return@launch
                    }
                }

                runOnUiThread {
                    Log.d("Tracking", "=== START TRACKING ===")
                    isTracking = true
                    trackingStartTime = System.currentTimeMillis()
                    trackingSeconds = 0
                    totalDistance = 0.0
                    routePoints.clear()
                    lastLocation = null

                    // OVO JE KLJUČNO - PRIKAŽI TRACKING PANEL
                    binding.trackingPanel.visibility = View.VISIBLE

                    // INICIJALIZUJ SEGMENTE
                    routeSegments.clear()
                    currentSegment = mutableListOf()

                    // ODMH AŽURIRAJ UI SA POČETNIM VREDNOSTIMA
                    updateTrackingStats(0.0, 0.0)
                    updateTrackingTime()

                    // POKRENI TIMER ODMH
                    startTrackingTimer()

                    // POKRENI POZADINSKI SERVIS PRVO!
                    startBackgroundTracking()

                    lifecycleScope.launch(Dispatchers.IO) {
                        val app = application as App
                        currentRoute = Route(
                            userId = getCurrentUserId(),
                            name = "Ruta ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}",
                            startTime = System.currentTimeMillis(),
                            distance = 0.0,
                            duration = 0L,
                            isCompleted = false
                        )
                        val routeId = app.routeRepository.createRoute(currentRoute!!)
                        currentRoute = currentRoute!!.copy(id = routeId)

                        withContext(Dispatchers.Main) {
                            if (!locationOverlay.isFollowLocationEnabled) {
                                locationOverlay.enableFollowLocation()
                                isFollowingLocation = true
                            }

                            centerOnMyLocationSilent()
                            startLocationUpdates()
                            startTrackingNotification()
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                            binding.btnStartTracking.isEnabled = false
                            binding.btnStopTracking.isEnabled = true
                            binding.btnStartTracking.alpha = 0.5f
                            binding.btnStopTracking.alpha = 1.0f

                            Log.d("Tracking", "Snimanje potpuno pokrenuto")
                        }
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private suspend fun getTodayRouteCount(userId: String): Int {
        return try {
            val app = application as App
            val allRoutes = app.routeRepository.getUserRoutes(userId)

            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            allRoutes.count { route ->
                route.startTime >= today
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun showDailyLimitReachedDialog(maxRoutes: Int) {
        AlertDialog.Builder(this)
            .setTitle("🚫 Dnevni limit dostignut")
            .setMessage(
                """
            Dostigli ste dnevni limit od $maxRoutes ruta za BASIC nalog.
            
            🌟 Nadogradi na PREMIUM za:
            • Neograničeno praćenje ruta
            • Neograničene tačke interesa
            • Sve vrste mapa
            • Offline mape
            
            Želite li da nadogradite na PREMIUM?
            """.trimIndent()
            )
            .setPositiveButton("🌟 Nadogradi") { dialog, which ->
                showPremiumUpgradeDialog()
            }
            .setNegativeButton("Nastavi BASIC", null)
            .show()
    }
    private fun showPointsLimitReachedDialog(maxPoints: Int) {
        AlertDialog.Builder(this)
            .setTitle("🚫 Limit tačaka dostignut")
            .setMessage(
                """
            Dostigli ste limit od $maxPoints tačaka za BASIC nalog.
            
            🌟 Nadogradi na PREMIUM za:
            • Neograničene tačke interesa
            • Neograničeno praćenje ruta  
            • Sve vrste mapa
            • Offline mape
            
            Želite li da nadogradite na PREMIUM?
            """.trimIndent()
            )
            .setPositiveButton("🌟 Nadogradi") { dialog, which ->
                showPremiumUpgradeDialog()
            }
            .setNegativeButton("Nastavi BASIC", null)
            .show()
    }
    private fun showModernTrackingDialog() {
        val dialog = AlertDialog.Builder(this, R.style.TransparentDialogTheme).apply {
            setCancelable(false)
        }.create()

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_tracking_modern, null)
        dialog.setView(view)

        // JAKA TRANSPARENTNOST
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(0f)

        // Postavi poziciju
        val params = dialog.window?.attributes
        params?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params?.y = 200
        dialog.window?.attributes = params
        // ... ostatak metode ostaje isti ...
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val tvDialogTime = view.findViewById<TextView>(R.id.tvDialogTime)
        val tvDialogDistance = view.findViewById<TextView>(R.id.tvDialogDistance)
        val tvDialogSpeed = view.findViewById<TextView>(R.id.tvDialogSpeed)
        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)

        progressBar?.isIndeterminate = true

        val handler = Handler(Looper.getMainLooper())
        var dialogSeconds = 0

        val dialogRunnable = object : Runnable {
            override fun run() {
                if (dialog.isShowing) {
                    dialogSeconds++

                    val minutes = dialogSeconds / 60
                    val seconds = dialogSeconds % 60
                    tvDialogTime?.text = String.format("%02d:%02d", minutes, seconds)
                    tvDialogDistance?.text = formatDistance(totalDistance)
                    tvDialogSpeed?.text = "${String.format("%.1f", currentSpeed)} km/h"

                    val status = when {
                        currentSpeed > 1.0 -> "🎯 Pratimo kretanje..."
                        lastLocation != null -> "✅ GPS signal stabilan"
                        else -> "📡 Tražim GPS signal..."
                    }
                    tvStatus?.text = status

                    handler.postDelayed(this, 1000)
                }
            }
        }

        dialog.show()
        handler.post(dialogRunnable)

        handler.postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
                handler.removeCallbacks(dialogRunnable)
                showTrackingToast()
            }
        }, 3000)
    }

    private fun showTrackingToast() {
        Toast.makeText(this, "🎯 GPS aktiviran - snimamo rutu!", Toast.LENGTH_SHORT).show()
    }
    private val trackingHandler = Handler(Looper.getMainLooper())

    private fun startTrackingTimer() {
        trackingHandler.postDelayed(object : Runnable {
            override fun run() {
                if (isTracking) {
                    trackingSeconds++
                    updateTrackingTime()
                    trackingHandler.postDelayed(this, 1000)
                }
            }
        }, 1000)
    }

    private fun showTrackingStartedDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_tracking_modern, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Postavite prozirnu pozadinu
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.7f)

        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        //val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)
        val tvDialogTime = dialogView.findViewById<TextView>(R.id.tvDialogTime)
        val tvDialogDistance = dialogView.findViewById<TextView>(R.id.tvDialogDistance)
        val tvDialogSpeed = dialogView.findViewById<TextView>(R.id.tvDialogSpeed)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvStatus)

        // Animirajte progress bar
        progressBar.isIndeterminate = true

        // Timer za ažuriranje dijaloga
        val dialogHandler = Handler(Looper.getMainLooper())
        var dialogSeconds = 0

        val dialogRunnable = object : Runnable {
            override fun run() {
                if (dialog.isShowing) {
                    dialogSeconds++

                    // Ažuriraj vreme u dijalogu
                    val minutes = dialogSeconds / 60
                    val seconds = dialogSeconds % 60
                    tvDialogTime.text = String.format("%02d:%02d", minutes, seconds)

                    // Ažuriraj udaljenost i brzinu iz glavnih podataka
                    tvDialogDistance.text = formatDistance(totalDistance)
                    tvDialogSpeed.text = "${String.format("%.1f", currentSpeed)} km/h"

                    // Ažuriraj status na osnovu GPS signala
                    val status = when {
                        currentSpeed > 1.0 -> "🎯 Pratimo kretanje..."
                        lastLocation != null -> "✅ GPS signal stabilan"
                        else -> "🔍 Tražim GPS signal..."
                    }
                    tvStatus.text = status

                    dialogHandler.postDelayed(this, 1000)
                }
            }
        }

        dialog.show()
        dialogHandler.post(dialogRunnable)

        // Automatski zatvori dijalog nakon 3 sekunde
        dialogHandler.postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
                dialogHandler.removeCallbacks(dialogRunnable)
                Toast.makeText(this, "🎯 GPS aktiviran - snimamo rutu!", Toast.LENGTH_SHORT).show()
            }
        }, 3000)
    }
    private fun startTrackingService() {
        val intent = Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_TRACKING
            // Prosledi tekuću rutu ako postoji
            putExtra("ROUTE_ID", currentRoute?.id)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    private fun stopTrackingService() {
        val intent = Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP_TRACKING
        }
        stopService(intent)
    }


    private fun stopTracking() {
        // ZAUSTAVI POZADINSKI SERVIS PRVO!
        stopBackgroundTracking()

        Log.d("Tracking", "=== STOP TRACKING ===")
        isTracking = false

        // SAKRIJ TRACKING PANEL
        binding.trackingPanel.visibility = View.GONE

        // UKLONI SVE CALLBACKS
        trackingHandler.removeCallbacksAndMessages(null)

        binding.btnStartTracking.isEnabled = true
        binding.btnStopTracking.isEnabled = false
        binding.btnStartTracking.alpha = 1.0f
        binding.btnStopTracking.alpha = 0.5f

        currentRoute?.let { route ->
            val duration = trackingSeconds * 1000
            val completedRoute = route.copy(
                isCompleted = true,
                endTime = System.currentTimeMillis(),
                distance = totalDistance,
                duration = duration.toLong()
            )
            lifecycleScope.launch(Dispatchers.IO) {
                val app = application as App
                app.routeRepository.updateRoute(completedRoute)
            }
        }

        stopTrackingNotification()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val minutes = trackingSeconds / 60
        val seconds = trackingSeconds % 60

        Toast.makeText(
            this,
            "Snimanje zaustavljeno!\nUdaljenost: ${formatDistance(totalDistance)}\nVreme: ${minutes}m ${seconds}s",
            Toast.LENGTH_LONG
        ).show()

        Log.d("Tracking", "Snimanje zaustavljeno")
    }
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "LOCATION_UPDATE") {
                val latitude = intent.getDoubleExtra("latitude", 0.0)
                val longitude = intent.getDoubleExtra("longitude", 0.0)
                val accuracy = intent.getFloatExtra("accuracy", 0f)
                val speed = intent.getFloatExtra("speed", 0f)

                val location = Location("service").apply {
                    this.latitude = latitude
                    this.longitude = longitude
                    this.accuracy = accuracy
                    this.speed = speed
                }

                val geoPoint = GeoPoint(latitude, longitude)

                // AŽURIRAJTE RUTU SA NOVOM LOKACIJOM
                if (isTracking) {
                    addPointToRouteSmoothly(geoPoint, location)
                    updateDistanceAndSpeedAccurate(location)
                }
            }
        }
    }
    private fun addPointToRoute(location: GeoPoint, androidLocation: Location) {
        if (!isTracking) return

        // POBOLJŠANO FILTRIRANJE - IZBEGAVAJTE OŠTRE UGLOVE
        if (routePoints.isNotEmpty()) {
            val lastPoint = routePoints.last()
            val distance = calculateDistance(
                lastPoint.latitude, lastPoint.longitude,
                location.latitude, location.longitude
            )

            // FILTRIRAJ PREVELIKE I PREMANJE RAZMAKE
            if (distance < 3.0 || distance > 50.0) {
                Log.d("Tracking", "Preskočena tačka: ${String.format("%.1f", distance)}m")
                return
            }

            // PROVERA ZA OŠTRE UGLOVE - koristite bearing za detekciju naglih skretanja
            lastLocation?.let { lastLoc ->
                if (lastLoc.hasBearing() && androidLocation.hasBearing()) {
                    val bearingDiff = Math.abs(lastLoc.bearing - androidLocation.bearing)
                    if (bearingDiff > 90 && bearingDiff < 270) { // Nagli zaokret
                        Log.d("Tracking", "Nagli zaokret detektovan: ${String.format("%.1f", bearingDiff)}°")
                        // Možete dodati dodatnu logiku ovde ako želite
                    }
                }
            }
        }

        routePoints.add(location)
        runOnUiThread {
            drawSmoothRouteOnMap()
        }

        savePointToDatabase(location)
        Log.d("Tracking", "Dodata tačka ${routePoints.size}: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}")
    }
    private fun savePointToDatabase(location: GeoPoint) {
        currentRoute?.let { route ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val app = application as jovannedeljkovic.gps_tracker_pro.App
                    val point = LocationPoint(
                        routeId = route.id,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = System.currentTimeMillis()
                    )
                    app.routeRepository.addLocationPoint(point)
                    Log.d("Tracking", "Tačka sačuvana u bazi: ${point.id}")
                } catch (e: Exception) {
                    Log.e("Tracking", "Greška pri čuvanju tačke: ${e.message}")
                }
            }
        }
    }
    private fun getUserName(): String {
        return try {
            val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
            val userEmail = sharedPreferences.getString("user_email", "Korisnik")
            userEmail?.substringBefore("@") ?: "Korisnik"
        } catch (e: Exception) {
            "Korisnik"
        }
    }
    private fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            String.format("%.0f m", meters)
        } else {
            String.format("%.2f km", meters / 1000)
        }
    }
    private fun updateTrackingTime() {
        val minutes = trackingSeconds / 60
        val seconds = trackingSeconds % 60
        binding.tvTime.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateTrackingStats(distance: Double, speed: Double) {
        runOnUiThread {
            // Brzina sa bojama za bezbednost
            val speedColor = when {
                speed > 120 -> "#FFFF5252" // Crvena - prebrzo
                speed > 80 -> "#FFFF9800"  // Narandžasta - brzo
                else -> "#FF4CAF50"        // Zelena - normalno
            }

            binding.tvSpeed.text = String.format("%.1f", speed)
            binding.tvSpeed.setTextColor(Color.parseColor(speedColor))

            // Udaljenost
            binding.tvDistance.text = formatDistance(distance)

            // Ažuriraj progres tačnosti
            updateAccuracyProgress()
        }
    }
    private fun updateAccuracyProgress() {
        lastLocation?.let { location ->
            val accuracy = location.accuracy
            val progress = when {
                accuracy < 10 -> 100
                accuracy < 20 -> 80
                accuracy < 30 -> 60
                accuracy < 50 -> 40
                else -> 20
            }

            // Ako imate progress bar u tracking panelu, ažurirajte ga ovde
            // binding.accuracyProgress?.progress = progress
            // binding.tvAccuracy?.text = when {
            //     accuracy < 10 -> "Odlično"
            //     accuracy < 20 -> "Dobro"
            //     accuracy < 30 -> "Zadovoljava"
            //     else -> "Slabo"
            // }

            Log.d("Accuracy", "GPS tačnost: ${accuracy}m, progress: $progress")
        }
    }
    private var isMapRotating = false

    private fun toggleMapRotation() {
        isMapRotating = !isMapRotating

        if (isMapRotating) {
            // Omogući rotaciju prema smeru kretanja
            locationOverlay.enableFollowLocation()
            locationOverlay.isDrawAccuracyEnabled = true


            Toast.makeText(this, "🎯 Auto-rotate ON - prati smer kretanja", Toast.LENGTH_SHORT).show()
        } else {
            // Onemogući rotaciju - fiksna orijentacija
            locationOverlay.disableFollowLocation()

            Toast.makeText(this, "🧭 Auto-rotate OFF - fiksna orijentacija", Toast.LENGTH_SHORT).show()
        }

        binding.mapView.invalidate()
    }
    private fun showMapTypeDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val userEmail = getCurrentUserFromPrefs()
                val user = app.userRepository.getUserByEmail(userEmail)

                runOnUiThread {
                    val mapTypes = mutableListOf(
                        "🗺️ OSM Standard (Preporučeno)"
                    )

                    // ZAMENJENO: Satelitski sa Pregled Offline mapa
                    if (user == null || FeatureManager.canUseSatelliteMaps(user)) {
                        mapTypes.add("📂 Pregled Offline Mapa")
                    } else {
                        mapTypes.add("📂 Pregled Offline Mapa (PREMIUM)")
                    }

                    if (user == null || FeatureManager.canUseTopoMaps(user)) {
                        mapTypes.add("⛰️ OSM Topo Planine")
                    } else {
                        mapTypes.add("⛰️ OSM Topo Planine (PREMIUM)")
                    }

                    // ZAMENJENO: Offline Mape sa Upravljaj offline mapama
                    if (user == null || FeatureManager.canUseOfflineMaps(user)) {
                        mapTypes.add("⚙️ Upravljaj Offline Mapama")
                    } else {
                        mapTypes.add("⚙️ Upravljaj Offline Mapama (PREMIUM)")
                    }

                    mapTypes.add("🎨 OSM Art Style")

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("🗺️ Dostupni Tipovi Mapa")
                        .setItems(mapTypes.toTypedArray()) { dialog, which ->
                            when (which) {
                                0 -> setHighQualityStandardMap()
                                1 -> {
                                    if (user == null || FeatureManager.canUseSatelliteMaps(user)) {
                                        showOfflineMapsList() // PROMENJENO: umesto setSatelliteMap()
                                    } else {
                                        showPremiumRequiredDialog("pregled offline mapa")
                                    }
                                }
                                2 -> {
                                    if (user == null || FeatureManager.canUseTopoMaps(user)) {
                                        setOsmTopoMap()
                                    } else {
                                        showPremiumRequiredDialog("topo mape")
                                    }
                                }
                                3 -> {
                                    if (user == null || FeatureManager.canUseOfflineMaps(user)) {
                                        showOfflineMapsDialog() // PROMENJENO: ostaje isto ali je preimenovano u meniju
                                    } else {
                                        showPremiumRequiredDialog("upravljanje offline mapama")
                                    }
                                }
                                4 -> setOsmArtMap()
                            }
                        }
                        .setNegativeButton("❌ Zatvori", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
// DODAJ OVE METODE NA KRAJ MAIN ACTIVITY:
private fun showPremiumRequiredDialog(featureName: String) {
    AlertDialog.Builder(this)
        .setTitle("🌟 Premium Funkcionalnost")
        .setMessage(
            """
            $featureName su dostupne samo PREMIUM korisnicima.
            
            🌟 Nadogradi na PREMIUM za:
            • Neograničene rute i tačke
            • Sve vrste mapa (satelitske, topo, offline)
            • Napredni eksport (GPX, KML)
            • Cloud backup
            
            Želite li da nadogradite na PREMIUM?
            """.trimIndent()
        )
        .setPositiveButton("🌟 Nadogradi") { dialog, which ->
            showPremiumUpgradeDialog()
        }
        .setNegativeButton("Nastavi BASIC", null)
        .show()
}
    private fun showPremiumUpgradeDialog() {
        val benefits = listOf(
            "✅ Neograničeno praćenje ruta",
            "✅ Neograničene tačke interesa",
            "✅ Sve vrste mapa (satelitske, topo, offline)",
            "✅ Napredni eksport (GPX, KML, PDF)",
            "✅ Cloud backup podataka",
            "✅ Detaljne statistike i analize",
            "✅ Prioritetna podrška"
        )

        val benefitsText = benefits.joinToString("\n")

        AlertDialog.Builder(this)
            .setTitle("🌟 Nadogradnja na PREMIUM")
            .setMessage(
                """
            Premium funkcionalnosti:
            
            $benefitsText
            
            💰 Cenovnik:
            • Mesečna pretplata: $2.99
            • Godišnja pretplata: $24.99 (30% jeftinije)
            • Doživotni pristup: $49.99
            
            Odaberite opciju:
            """.trimIndent()
            )
            .setPositiveButton("💰 Mesečna ($2.99)") { dialog, which ->
                initiatePremiumPurchase("premium_monthly")
            }
            .setNeutralButton("💰 Godišnja ($24.99)") { dialog, which ->
                initiatePremiumPurchase("premium_yearly")
            }
            .setNegativeButton("❌ Kasnije", null)
            .show()
    }

    private fun initiatePremiumPurchase(productId: String) {
        // Ovo ćemo implementirati kada dodamo Google Play Billing
        Toast.makeText(this, "Premium kupovina će biti dostupna uskoro!", Toast.LENGTH_LONG).show()

        // Za sada, simuliraj uspešnu kupovinu
        simulatePremiumUpgrade()
    }

    private fun simulatePremiumUpgrade() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val userEmail = getCurrentUserFromPrefs()
                val user = app.userRepository.getUserByEmail(userEmail)

                if (user != null) {
                    val success = app.userRepository.upgradeToPremium(user.id, 30) // 30 dana besplatno za test

                    runOnUiThread {
                        if (success) {
                            Toast.makeText(
                                this@MainActivity,
                                "🎉 Sada ste PREMIUM korisnik! Uživajte u svim funkcionalnostima!",
                                Toast.LENGTH_LONG
                            ).show()

                            // Osveži UI
                            checkUserFeatures()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showOfflineMapsDialog() {
        val options = arrayOf(
            "📥 Preuzmi Offline Mapu (Srbija)",
            "📂 Pregled Offline Mapa",
            "🗑️ Obriši Offline Mape",
            "ℹ️ Informacije o Offline Mapama"
        )

        AlertDialog.Builder(this)
            .setTitle("⚙️ Upravljaj Offline Mapama")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> downloadOfflineMap()
                    1 -> showOfflineMapsList()
                    2 -> deleteOfflineMaps()
                    3 -> showOfflineMapsInfo()
                }
            }
            .setNegativeButton("❌ Zatvori", null)
            .show()
    }

    private fun downloadOfflineMap() {
        val regions = arrayOf(
            "🗺️ Srbija (standardna)",
            "🛰️ Srbija (satelitska)",
            "🏙️ Beograd (standardna)",
            "🛰️ Beograd (satelitska)",
            "🎯 Custom Region - izaberi sam",
            "📏 Trenutno vidljivo područje"
        )

        AlertDialog.Builder(this)
            .setTitle("🗺️ Preuzmi Offline Mapu")
            .setItems(regions) { dialog, which ->
                when (which) {
                    0 -> downloadRegionSerbia()
                    1 -> downloadRegionSerbiaSatellite()
                    2 -> downloadRegionBelgrade()
                    3 -> downloadRegionBelgradeSatellite()
                    4 -> selectCustomRegion()  // OVO TREBA DA OTvori NOVI DIALOG
                    5 -> downloadCurrentViewport()
                }
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }
    private fun downloadRegionSerbiaSatellite() {
        val bbox = org.osmdroid.util.BoundingBox(45.5, 21.0, 43.0, 19.0)

        // KREIRAJTE PROGRESS DIALOG
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("🗺️ Preuzimanje: Srbija Satelitska")
            .setMessage("Priprema tile-ova...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var totalTiles = 0
                var downloadedTiles = 0
                val minZoom = 8
                val maxZoom = 14

                // IZRAČUNAJ UKUPAN BROJ TILE-OVA
                for (zoom in minZoom..maxZoom) {
                    totalTiles += calculateTilesForZoom(bbox, zoom)
                }

                withContext(Dispatchers.Main) {
                    progressDialog.setMessage("Preuzimanje: Srbija Satelitska\n0/$totalTiles")
                }

                // PREUZIMANJE TILE-OVA
                for (zoom in minZoom..maxZoom) {
                    val downloaded = downloadSatelliteZoomLevel(bbox, zoom, "Srbija Satelitska", progressDialog, downloadedTiles, totalTiles)
                    downloadedTiles += downloaded
                }

                // SAČUVAJ METAPODATKE
                saveSatelliteRegionMetadata(bbox, minZoom, maxZoom, "Srbija Satelitska", downloadedTiles)

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        "✅ Preuzeto: Srbija Satelitska\n$downloadedTiles/$totalTiles tile-ova",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "❌ Greška: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun downloadRegionBelgradeSatellite() {
        val bbox = org.osmdroid.util.BoundingBox(44.9, 20.6, 44.7, 20.3)
        downloadSatelliteTilesForRegion(bbox, 10, 16, "Beograd Satelitska")
    }

    private fun downloadRegionNisSatellite() {
        val bbox = org.osmdroid.util.BoundingBox(43.35, 21.95, 43.3, 21.85)
        downloadSatelliteTilesForRegion(bbox, 12, 18, "Niš Satelitska")
    }

    private fun downloadSatelliteTilesForRegion(bbox: org.osmdroid.util.BoundingBox, minZoom: Int, maxZoom: Int, regionName: String) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("🗺️ Preuzimanje: $regionName")
            .setMessage("Priprema satelitskih tile-ova...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var totalTiles = 0
                var downloadedTiles = 0

                // IZRAČUNAJ UKUPAN BROJ TILE-OVA
                for (zoom in minZoom..maxZoom) {
                    totalTiles += calculateTilesForZoom(bbox, zoom)
                }

                withContext(Dispatchers.Main) {
                    progressDialog.setMessage("Preuzimanje: $regionName\nSatelitski tile-ovi\n0/$totalTiles")
                }

                // PREUZIMANJE SATELITSKIH TILE-OVA
                for (zoom in minZoom..maxZoom) {
                    val downloaded = downloadSatelliteZoomLevel(bbox, zoom, regionName, progressDialog, downloadedTiles, totalTiles)
                    downloadedTiles += downloaded
                }

                // SAČUVAJ METAPODATKE SA OZNAKOM DA JE SATELITSKA
                saveSatelliteRegionMetadata(bbox, minZoom, maxZoom, regionName, downloadedTiles)

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    showSatelliteDownloadSuccess(regionName, downloadedTiles)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "? Greška: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun downloadSatelliteZoomLevel(
        bbox: org.osmdroid.util.BoundingBox,
        zoom: Int,
        regionName: String,
        progressDialog: AlertDialog,
        alreadyDownloaded: Int,
        totalTiles: Int
    ): Int {
        var downloaded = 0
        val northTile = lat2tile(bbox.latNorth, zoom)
        val southTile = lat2tile(bbox.latSouth, zoom)
        val eastTile = lon2tile(bbox.lonEast, zoom)
        val westTile = lon2tile(bbox.lonWest, zoom)

        for (x in westTile..eastTile) {
            for (y in northTile..southTile) {
                try {
                    downloadSatelliteSingleTile(x, y, zoom)
                    downloaded++

                    // AŽURIRAJ PROGRESS
                    if (downloaded % 5 == 0) {
                        val currentTotal = alreadyDownloaded + downloaded
                        withContext(Dispatchers.Main) {
                            progressDialog.setMessage(
                                "Preuzimanje: $regionName\n" +
                                        "Satelitski tile-ovi\n" +
                                        "$currentTotal/$totalTiles tile-ova\n" +
                                        "Progress: ${(currentTotal * 100 / totalTiles)}%"
                            )
                        }
                    }

                } catch (e: Exception) {
                    Log.e("SatelliteTile", "Greška pri satelitskom tile $zoom/$x/$y: ${e.message}")
                }
            }
        }

        return downloaded
    }

    private fun downloadSatelliteSingleTile(x: Int, y: Int, zoom: Int) {
        Log.d("DownloadDebug", "🛰️ POČINJEM preuzimanje satelitskog tila: zoom=$zoom, x=$x, y=$y")

        // ArcGIS World Imagery servis
        val tileUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x"

        Log.d("DownloadDebug", "🔗 URL: $tileUrl")

        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(tileUrl)
            .header("User-Agent", "Mozilla/5.0 (Android GPS Tracker)")
            .build()

        try {
            val response = client.newCall(request).execute()
            Log.d("DownloadDebug", "📡 HTTP Status: ${response.code}")

            if (response.isSuccessful) {
                val tileData = response.body?.bytes()
                if (tileData != null && tileData.isNotEmpty()) {
                    Log.d("DownloadDebug", "✅ Primljeno ${tileData.size} bajtova")
                    saveSatelliteTileToCache(x, y, zoom, tileData)
                    Log.d("DownloadDebug", "💾 Sačuvan tile: $zoom/$x/$y")
                } else {
                    Log.e("DownloadDebug", "❌ Tile data je NULL ili prazna")
                }
            } else {
                Log.e("DownloadDebug", "❌ HTTP greška: ${response.code} - ${response.message}")
            }
            response.close()
        } catch (e: Exception) {
            Log.e("DownloadDebug", "💥 Greška pri preuzimanju: ${e.message}")
            e.printStackTrace()
        }
    }
    /*private fun debugTileCache() {
        try {
            val satelliteDir = File(filesDir, "osmdroid/tiles/World_Imagery")
            val tileCount = countTilesInDirectory(satelliteDir)

            // Proveri da li folder postoji
            val folderExists = satelliteDir.exists()
            val folderPath = satelliteDir.absolutePath

            // Proveri permisije
            val canWrite = satelliteDir.canWrite()
            val canRead = satelliteDir.canRead()

            val message = """
            🗂️ DEBUG CACHE INFORMACIJE:
            
            📁 Folder postoji: ${if (folderExists) "✅" else "❌"}
            📍 Putanja: $folderPath
            ✏️ Može da piše: ${if (canWrite) "✅" else "❌"}
            👁️ Može da čita: ${if (canRead) "✅" else "❌"}
            
            📡 Satelitski tile-ovi: $tileCount
            
            ℹ️ Ako je 0 tile-ova:
            • Tile-ovi se ne preuzimaju ILI
            • Nema dozvola za pisanje ILI  
            • Folder ne postoji
        """.trimIndent()

            Log.d("TileDebug", message)

            AlertDialog.Builder(this)
                .setTitle("🗂️ Debug Cache")
                .setMessage(message)
                .setPositiveButton("Test Preuzimanje") { dialog, which ->
                    testSimpleDownload()
                }
                .setNegativeButton("OK", null)
                .show()

        } catch (e: Exception) {
            Log.e("TileDebug", "Greška: ${e.message}")
            Toast.makeText(this, "❌ Greška pri debug-u: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }*/

    private fun checkCurrentTileSource() {
        val tileSource = mapView.tileProvider.tileSource
        val sourceName = tileSource.name()
        val minZoom = tileSource.minimumZoomLevel
        val maxZoom = tileSource.maximumZoomLevel

        Toast.makeText(
            this,
            "Tile Source: $sourceName\nMin Zoom: $minZoom\nMax Zoom: $maxZoom",
            Toast.LENGTH_LONG
        ).show()

        Log.d("TileSourceDebug", "Source: $sourceName, Min: $minZoom, Max: $maxZoom")
    }
    private fun createSatelliteTileSource(): org.osmdroid.tileprovider.tilesource.ITileSource {
        return object : org.osmdroid.tileprovider.tilesource.XYTileSource(
            "World_Imagery",
            0,     // Min zoom - postavite na 0 ili 3
            23,    // Max zoom - POVEĆAJTE OVO NA 21 ili 23!
            256,   // Tile size
            ".png",
            arrayOf(
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
            )
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                return "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x"
            }
        }
    }
    private fun saveSatelliteTileToCache(x: Int, y: Int, zoom: Int, tileData: ByteArray) {
        try {
            // KORISTITE OSMdroid cache putanju
            val cacheDir = File(Configuration.getInstance().osmdroidBasePath, "tiles/World_Imagery")

            if (!cacheDir.exists()) {
                val created = cacheDir.mkdirs()
                Log.d("TileSave", "📁 Kreiran cache folder: ${cacheDir.absolutePath} - $created")
            }

            val tileFile = File(cacheDir, "$zoom/$x/$y.png")
            tileFile.parentFile?.mkdirs()

            Log.d("TileSave", "💾 Čuvam tile: ${tileFile.absolutePath}")

            // Proverite da li možete da pišete u fajl
            if (tileFile.parentFile?.canWrite() != true) {
                Log.e("TileSave", "❌ Nema dozvola za pisanje u: ${tileFile.parentFile?.absolutePath}")
                return
            }

            tileFile.writeBytes(tileData)

            // Proverite da li je fajl zaista sačuvan
            val saved = tileFile.exists()
            val savedSize = tileFile.length()

            Log.d("TileSave", "✅ Tile SAČUVAN: $saved, veličina: $savedSize bajtova")
            Log.d("TileSave", "📁 Putanja: ${tileFile.absolutePath}")

        } catch (e: Exception) {
            Log.e("TileSave", "💥 GREŠKA pri čuvanju tila: ${e.message}")
            e.printStackTrace()
        }
    }
    private fun checkStoragePermissions(): Boolean {
        // Za sada vraćamo true jer koristimo samo interni storage
        Log.d("Permissions", "Koristim interni storage - dozvole nisu potrebne")
        return true
    }

    private fun requestStoragePermissions() {
        // SAMO ZA ANDROID 9 I NIŽE
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                AlertDialog.Builder(this)
                    .setTitle("🔐 Dozvole za skladištenje")
                    .setMessage("Aplikaciji su potrebne dozvole za pristup Download folderu (samo za Android 9 i niže).")
                    .setPositiveButton("Dozvoli") { dialog, which ->
                        requestPermissions(
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                            LOCATION_PERMISSION_REQUEST_CODE + 100
                        )
                    }
                    .setNegativeButton("Kasnije", null)
                    .show()
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    LOCATION_PERMISSION_REQUEST_CODE + 100
                )
            }
        } else {
            // ANDROID 10+ - KORISTI MediaStore BEZ DOZVOLA
            queryGpxFilesWithMediaStoreAndroid10()
        }
    }
    private fun showNoGpxFilesFound() {
        AlertDialog.Builder(this)
            .setTitle("📭 Nema GPX fajlova")
            .setMessage("""
        Nema GPX fajlova u Download folderu.
        
        Kako dodati GPX fajlove:
        
        1. 📥 Preuzmite GPX fajl sa interneta
        2. 💾 Sačuvajte ga u Download folder
        3. 🔄 Vratite se ovde i osvežite listu
        
        Preporučeno: Koristite "File Picker" opciju za bolju kompatibilnost.
        """.trimIndent())
            .setPositiveButton("📁 File Picker") { dialog, which ->
                importRouteFromFile()
            }
            .setNeutralButton("🔄 Osveži") { dialog, which ->
                importGpxFromDownloads()
            }
            .setNegativeButton("❌ Zatvori", null)
            .show()
    }
    private fun importRouteFromFile() {
        Log.d("ImportDebug", "🎯 POZIVAM importRouteFromFile()")
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/gpx+xml",
                    "application/xml",
                    "text/xml",
                    "text/plain",
                    "application/octet-stream"
                ))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }

            try {
                startActivityForResult(intent, IMPORT_GPX_REQUEST_CODE)
                Toast.makeText(this, "🔍 Izaberite GPX fajl sa rutom", Toast.LENGTH_SHORT).show()
                Log.d("ImportDebug", "✅ USPEO: Pokrenut File Picker za rutu")
            } catch (e: Exception) {
                Log.w("ImportDebug", "OPEN_DOCUMENT nije dostupan, koristim GET_CONTENT: ${e.message}")
                // FALLBACK: Koristi stari intent za stare Androide
                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                        "application/gpx+xml",
                        "application/xml",
                        "text/xml",
                        "text/plain"
                    ))
                }
                startActivityForResult(fallbackIntent, IMPORT_GPX_REQUEST_CODE)
                Toast.makeText(this, "🔍 Izaberite GPX fajl", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e("ImportDebug", "❌ GREŠKA pri otvaranju file pickera: ${e.message}")
            Toast.makeText(this, "❌ Greška: ${e.message}", Toast.LENGTH_SHORT).show()

            // FALLBACK: Pokaži opciju za direktan pristup Download folderu
            showImportFallbackOptions()
        }
    }
    private fun showImportFallbackForPoints() {
        AlertDialog.Builder(this)
            .setTitle("❌ File Picker nije dostupan")
            .setMessage("Želite li da pokušate sa Download folderom za tačke?")
            .setPositiveButton("📁 Da, Download folder") { dialog, which ->
                // OVDE MOŽEŠ DODATI LOGIKU ZA UVOZ TAČAKA IZ DOWNLOAD FOLDERA
                Toast.makeText(this, "📁 Uvoz tačaka iz Download foldera će biti dostupan uskoro", Toast.LENGTH_LONG).show()

                // Za sada, pokaži obaveštenje
                AlertDialog.Builder(this)
                    .setTitle("ℹ️ Funkcionalnost u izradi")
                    .setMessage("Uvoz tačaka iz Download foldera će biti dostupan u narednoj verziji aplikacije.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun countTilesInDirectory(dir: File): Int {
        if (!dir.exists()) return 0

        var count = 0
        dir.walk().forEach { file ->
            if (file.isFile && file.name.endsWith(".png")) {
                count++
            }
        }
        return count
    }

    private fun saveSatelliteRegionMetadata(bbox: org.osmdroid.util.BoundingBox, minZoom: Int, maxZoom: Int, regionName: String, tileCount: Int) {
        // KORISTITE generateUniqueFileName
        val fileName = generateUniqueFileName(regionName, true)

        val safeMaxZoom = if (maxZoom > 19) 19 else maxZoom

        val metadata = mapOf(
            "regionName" to regionName,
            "displayName" to regionName,
            "north" to bbox.latNorth,
            "south" to bbox.latSouth,
            "east" to bbox.lonEast,
            "west" to bbox.lonWest,
            "minZoom" to minZoom,
            "maxZoom" to safeMaxZoom,
            "tileCount" to tileCount,
            "downloadDate" to System.currentTimeMillis(),  // 👈 DODAJTE OVO
            "downloadDateFormatted" to SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()), // 👈 I OVO
            "isSatellite" to true,
            "type" to "satellite"
        )

        val metadataDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "offline_regions")
        if (!metadataDir.exists()) {
            metadataDir.mkdirs()
        }

        val metadataFile = File(metadataDir, fileName)
        metadataFile.writeText(Gson().toJson(metadata))

        Log.d("MetadataSave", "✅ Sačuvana satelitska mapa: $fileName, datum: ${metadata["downloadDateFormatted"]}")
    }

    private fun generateUniqueFileName(baseName: String, isSatellite: Boolean): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val prefix = if (isSatellite) "SAT" else "STD"
        val cleanName = baseName.replace("[^a-zA-Z0-9]".toRegex(), "_")

        return "${prefix}_${cleanName}_${timestamp}.json"
    }

    // Koristite ovako:



    private fun enableHybridMode(regionName: String) {
        try {
            // Učitaj satelitsku mapu
            binding.mapView.setTileSource(createSatelliteTileSource())
            binding.mapView.setUseDataConnection(false)

            // DODAJTE OVERLAY ZA LABELE (ulice, imena)
            addStreetLabelsOverlay()

            Toast.makeText(this, "🛰️🗺️ Hibridni prikaz: $regionName", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "❌ Greška: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addStreetLabelsOverlay() {
        try {
            // KORISTITE "LABELS ONLY" TILE SOURCE
            val labelsOnlyTileSource = object : org.osmdroid.tileprovider.tilesource.XYTileSource(
                "ArcGIS_LabelsOnly",
                0,
                19,
                256,
                ".png",
                arrayOf(
                    "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"
                )
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String {
                    val zoom = MapTileIndex.getZoom(pMapTileIndex)
                    val x = MapTileIndex.getX(pMapTileIndex)
                    val y = MapTileIndex.getY(pMapTileIndex)
                    return "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/$zoom/$y/$x"
                }
            }

            // Kreiraj overlay sa ovim source-om
            streetOverlay = org.osmdroid.views.overlay.TilesOverlay(
                org.osmdroid.tileprovider.MapTileProviderBasic(
                    applicationContext,
                    labelsOnlyTileSource
                ),
                applicationContext
            )

            // POSTAVI VISOKU TRANSPARENTNOST ZA POZADINU (ako ima)
            try {
                val alphaField = streetOverlay!!::class.java.getDeclaredField("mAlpha")
                alphaField.isAccessible = true
                alphaField.set(streetOverlay, 1.0f) // 👈 1.0 = potpuno neprozirne labele
            } catch (e: Exception) {
                // Ignoriši ako ne može
            }

            // Dodaj overlay
            binding.mapView.overlays.add(streetOverlay)
            binding.mapView.invalidate()

            Log.d("HybridMode", "✅ Labels-only overlay dodat")
            Toast.makeText(this, "🗺️ Samo imena ulica", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("HybridMode", "❌ Greška: ${e.message}", e)

            // FALLBACK: Probajte drugi servis
            tryFallbackLabelsOverlay()
        }
    }
    private fun addStamenTonerLiteOverlay() {
        try {
            Log.d("HybridDebug", "Dodajem Stamen overlay...")

            // Prvo uklonite postojeći ako postoji
            removeStreetOverlay()

            // STAMEN TONER LITE - crno-bele labele na transparentnoj pozadini
            val tonerLiteSource = object : org.osmdroid.tileprovider.tilesource.XYTileSource(
                "Stamen_Toner_Lite",
                0,
                18,
                256,
                ".png",
                arrayOf(
                    "https://stamen-tiles.a.ssl.fastly.net/toner-lite/{z}/{x}/{y}.png"
                )
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String {
                    val zoom = MapTileIndex.getZoom(pMapTileIndex)
                    val x = MapTileIndex.getX(pMapTileIndex)
                    val y = MapTileIndex.getY(pMapTileIndex)
                    return "https://stamen-tiles.a.ssl.fastly.net/toner-lite/$zoom/$x/$y.png"
                }
            }

            streetOverlay = org.osmdroid.views.overlay.TilesOverlay(
                org.osmdroid.tileprovider.MapTileProviderBasic(
                    applicationContext,
                    tonerLiteSource
                ),
                applicationContext
            )

            // Postavi transparentnost
            try {
                val alphaField = streetOverlay!!::class.java.getDeclaredField("mAlpha")
                alphaField.isAccessible = true
                alphaField.set(streetOverlay, 0.7f) // 30% prozirno
            } catch (e: Exception) {
                Log.w("HybridDebug", "Ne mogu postaviti alpha: ${e.message}")
            }

            // DODAJTE OVERLAY NA KRAJ LISTE (da bude iznad satelitskih tile-ova)
            binding.mapView.overlays.add(streetOverlay)

            Log.d("HybridDebug", "Overlay dodat. Ukupno overlay-a: ${binding.mapView.overlays.size}")

            // Invalirajte mapu
            binding.mapView.invalidate()

            Log.d("HybridMode", "✅ Stamen Toner Lite overlay dodat")
            Toast.makeText(this, "🗺️ Crno-bele labele", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("HybridMode", "❌ Stamen greška: ${e.message}", e)
            Toast.makeText(this, "❌ Stamen ne radi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addOSMCartoLabelsOverlay() {
        try {
            Log.d("HybridDebug", "Dodajem OSM Carto labels overlay...")

            removeStreetOverlay()

            // OpenStreetMap Carto
            val osmSource = org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK

            streetOverlay = object : org.osmdroid.views.overlay.TilesOverlay(
                org.osmdroid.tileprovider.MapTileProviderBasic(
                    applicationContext,
                    osmSource
                ),
                applicationContext
            ) {
                // ColorMatrix koji čuva samo CRNE (labele) a belu pozadinu čini prozirnom
                private val labelFilter = android.graphics.ColorMatrixColorFilter(
                    android.graphics.ColorMatrix().apply {
                        // OVAJ FILTER:
                        // 1. Bela pozadina (RGB ≈ 255,255,255) postaje potpuno prozirna
                        // 2. Crne labele (RGB ≈ 0,0,0) ostaju crne
                        // 3. Ostale boje (zelene, plave itd) se konvertuju u sive nijanse

                        // Prvo pretvorimo u crno-belo
                        setSaturation(0f)

                        // Zatim podesimo kontrast da naglasimo labele
                        val contrast = 2.0f
                        val scale = contrast
                        val translate = (-0.5f * contrast + 0.5f) * 255f

                        val contrastMatrix = floatArrayOf(
                            scale, 0f, 0f, 0f, translate,
                            0f, scale, 0f, 0f, translate,
                            0f, 0f, scale, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )

                        postConcat(android.graphics.ColorMatrix(contrastMatrix))

                        // Konačno: sve što je svetlo (pozadina) učini prozirnim
                        // Sve što je tamno (labele) ostavi neprozirno
                        val finalMatrix = floatArrayOf(
                            1f, 0f, 0f, 0f, 0f,
                            0f, 1f, 0f, 0f, 0f,
                            0f, 0f, 1f, 0f, 0f,
                            0f, 0f, 0f, 0.7f, 0f  // Alpha kanal: 0.7 = 30% prozirno
                        )

                        postConcat(android.graphics.ColorMatrix(finalMatrix))
                    }
                )

                override fun draw(canvas: Canvas, mapView: MapView?, shadow: Boolean) {
                    if (!shadow && mapView != null) {
                        val saveCount = canvas.save()

                        // Primeni filter koji čuva samo labele
                        val paint = Paint()
                        paint.colorFilter = labelFilter

                        canvas.saveLayer(null, paint, Canvas.ALL_SAVE_FLAG)
                        super.draw(canvas, mapView, shadow)
                        canvas.restoreToCount(saveCount)
                    }
                }
            }

            binding.mapView.overlays.add(streetOverlay)
            binding.mapView.invalidate()

            Log.d("HybridMode", "✅ OSM Carto labels overlay dodat (selektivna transparentnost)")
            Toast.makeText(this, "🗺️ Samo labele (bez pozadine)", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("HybridMode", "❌ OSM Carto greška: ${e.message}", e)
            Toast.makeText(this, "❌ Greška sa labelama", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addBlackLabelsOnlyOverlay() {
        try {
            Log.d("HybridDebug", "Dodajem Black Labels Only overlay...")

            removeStreetOverlay()

            val osmSource = org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK

            streetOverlay = object : org.osmdroid.views.overlay.TilesOverlay(
                org.osmdroid.tileprovider.MapTileProviderBasic(
                    applicationContext,
                    osmSource
                ),
                applicationContext
            ) {
                // Filter koji:
                // 1. Uklanja sve boje (čini crno-belim)
                // 2. Invertuje (crno->belo, belo->crno)
                // 3. Onda invertuje nazad (da bela pozadina postane prozirna)
                private val blackLabelsFilter = android.graphics.ColorMatrixColorFilter(
                    android.graphics.ColorMatrix().apply {
                        // Korak 1: Ukloni boje
                        setSaturation(0f)

                        // Korak 2: Invertuj (crno->belo, belo->crno)
                        val invertMatrix = floatArrayOf(
                            -1f, 0f, 0f, 0f, 255f,
                            0f, -1f, 0f, 0f, 255f,
                            0f, 0f, -1f, 0f, 255f,
                            0f, 0f, 0f, 1f, 0f
                        )
                        postConcat(android.graphics.ColorMatrix(invertMatrix))

                        // Korak 3: Postavi threshold - sve iznad 200 (bela) postaje prozirno
                        // Ovo je pojednostavljeni threshold filter
                        val threshold = 200f / 255f
                        val thresholdMatrix = floatArrayOf(
                            if (1f > threshold) 1f else 0f, 0f, 0f, 0f, 0f,
                            0f, if (1f > threshold) 1f else 0f, 0f, 0f, 0f,
                            0f, 0f, if (1f > threshold) 1f else 0f, 0f, 0f,
                            0f, 0f, 0f, if (1f > threshold) 0.3f else 1f, 0f  // Alpha: belo=30%, crno=100%
                        )
                        postConcat(android.graphics.ColorMatrix(thresholdMatrix))
                    }
                )

                override fun draw(canvas: Canvas, mapView: MapView?, shadow: Boolean) {
                    if (!shadow && mapView != null) {
                        val saveCount = canvas.save()

                        val paint = Paint()
                        paint.colorFilter = blackLabelsFilter

                        canvas.saveLayer(null, paint, Canvas.ALL_SAVE_FLAG)
                        super.draw(canvas, mapView, shadow)
                        canvas.restoreToCount(saveCount)
                    }
                }
            }

            binding.mapView.overlays.add(streetOverlay)
            binding.mapView.invalidate()

            Log.d("HybridMode", "✅ Black labels only overlay dodat")
            Toast.makeText(this, "🗺️ Crne labele", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("HybridMode", "❌ Black labels greška: ${e.message}", e)
            Toast.makeText(this, "❌ Greška", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addCustomLabelOverlay() {
        try {
            removeStreetOverlay()

            val osmSource = org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK

            streetOverlay = object : org.osmdroid.views.overlay.TilesOverlay(
                org.osmdroid.tileprovider.MapTileProviderBasic(
                    applicationContext,
                    osmSource
                ),
                applicationContext
            ) {
                private val shaderPaint = android.graphics.Paint().apply {
                    // Koristimo PorterDuff mode da zadržimo samo tamne piksele
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DARKEN)
                    alpha = 220 // 86% neprozirno - labele će biti malo prozirne
                }

                override fun draw(canvas: android.graphics.Canvas, mapView: MapView?, shadow: Boolean) {
                    if (!shadow && mapView != null) {
                        val saveCount = canvas.save()

                        // Primeni shader koji čuva samo tamne delove (labele)
                        canvas.saveLayer(null, shaderPaint, android.graphics.Canvas.ALL_SAVE_FLAG)
                        super.draw(canvas, mapView, shadow)
                        canvas.restoreToCount(saveCount)
                    }
                }
            }

            binding.mapView.overlays.add(streetOverlay)
            binding.mapView.invalidate()

            Toast.makeText(this, "🗺️ Labele (tamne)", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("HybridMode", "❌ Custom overlay greška: ${e.message}", e)
            Toast.makeText(this, "❌ Greška", Toast.LENGTH_SHORT).show()
        }
    }
    private fun addSimpleDarkLabelsOverlay() {
        try {
            removeStreetOverlay()

            val osmSource = org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK

            streetOverlay = object : org.osmdroid.views.overlay.TilesOverlay(
                org.osmdroid.tileprovider.MapTileProviderBasic(
                    applicationContext,
                    osmSource
                ),
                applicationContext
            ) {
                private val labelFilter = android.graphics.ColorMatrixColorFilter(
                    android.graphics.ColorMatrix().apply {
                        // 1. Ukloni boje
                        setSaturation(0f)

                        // 2. Povećaj kontrast
                        val contrast = 1.8f
                        val scale = contrast
                        val translate = (-0.5f * contrast + 0.5f) * 255f

                        val contrastMatrix = floatArrayOf(
                            scale, 0f, 0f, 0f, translate,
                            0f, scale, 0f, 0f, translate,
                            0f, 0f, scale, 0f, translate,
                            0f, 0f, 0f, 0.35f, 0f  // Alpha: 35% neprozirno
                        )

                        set(android.graphics.ColorMatrix(contrastMatrix))
                    }
                )

                private val labelPaint = android.graphics.Paint()

                init {
                    labelPaint.colorFilter = labelFilter
                    // Postavi transparentnost preko Paint-a
                    labelPaint.alpha = (255 * 0.35).toInt()
                }

                override fun draw(canvas: android.graphics.Canvas, mapView: MapView?, shadow: Boolean) {
                    if (!shadow && mapView != null) {
                        val saveCount = canvas.save()

                        canvas.saveLayer(null, labelPaint, android.graphics.Canvas.ALL_SAVE_FLAG)
                        super.draw(canvas, mapView, shadow)
                        canvas.restoreToCount(saveCount)
                    }
                }
            }

            binding.mapView.overlays.add(streetOverlay)

            // Za prioritet, dodajte na početak overlay-a (da bude ispred ostalih)
            binding.mapView.overlays.remove(streetOverlay)
            binding.mapView.overlays.add(0, streetOverlay)

            binding.mapView.invalidate()

            Toast.makeText(this, "🗺️ Tamne labele", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("HybridMode", "❌ Simple dark labels greška: ${e.message}", e)
            Toast.makeText(this, "❌ Greška", Toast.LENGTH_SHORT).show()
        }
    }
    private fun tryFallbackLabelsOverlay() {
        try {
            // ALTERNATIVNI SERVIS: OpenStreetMap samo labele
            val osmLabelsTileSource = object : org.osmdroid.tileprovider.tilesource.XYTileSource(
                "OSM_Labels",
                0,
                19,
                256,
                ".png",
                arrayOf(
                    "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                )
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String {
                    val zoom = MapTileIndex.getZoom(pMapTileIndex)
                    val x = MapTileIndex.getX(pMapTileIndex)
                    val y = MapTileIndex.getY(pMapTileIndex)
                    return "https://tile.openstreetmap.org/$zoom/$x/$y.png"
                }
            }

            streetOverlay = org.osmdroid.views.overlay.TilesOverlay(
                org.osmdroid.tileprovider.MapTileProviderBasic(
                    applicationContext,
                    osmLabelsTileSource
                ),
                applicationContext
            )

            // POKUŠAJTE DA FILTRIRATE BOJE - napravite labele belim
            // Ovo je malo komplikovanije, ali možemo probati
            val filterPaint = Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(
                    android.graphics.ColorMatrix().apply {
                        setSaturation(0f) // Ukloni boje
                    }
                )
                alpha = 200 // Malo prozirno
            }

            // Ovo možda neće raditi direktno, ali probajmo
            try {
                val paintField = streetOverlay!!::class.java.getDeclaredField("mPaint")
                paintField.isAccessible = true
                paintField.set(streetOverlay, filterPaint)
            } catch (e: Exception) {
                // Ignoriši
            }

            binding.mapView.overlays.add(streetOverlay)
            binding.mapView.invalidate()

            Log.d("HybridMode", "✅ OSM labels overlay dodat (fallback)")
            Toast.makeText(this, "🗺️ Ulice (OSM fallback)", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("HybridMode", "❌ Fallback takođe pao: ${e.message}")
            Toast.makeText(this, "❌ Nije moguće dodati labele", Toast.LENGTH_SHORT).show()
        }
    }



    private fun removeStreetOverlay() {
        streetOverlay?.let {
            binding.mapView.overlays.remove(it)
            streetOverlay = null
            Log.d("HybridMode", "Street overlay uklonjen")
        }
    }
    private fun showSatelliteDownloadSuccess(regionName: String, tileCount: Int) {
        AlertDialog.Builder(this)
            .setTitle("🎉 $regionName Preuzeta!")
            .setMessage("""
            Uspešno preuzeto $tileCount satelitskih tile-ova!
            
            🛰️ Sada možete koristiti satelitski prikaz BEZ interneta.
            
            Želite li odmah preći na satelitski offline mod?
        """.trimIndent())
            .setPositiveButton("✅ Koristi Satelitski") { dialog, which ->
                enableSatelliteOfflineMode(regionName)
            }
            .setNegativeButton("🌐 Ostani Online", null)
            .show()
    }

    private fun fixExistingMetadataFiles() {
        val metadataDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "offline_regions")
        if (!metadataDir.exists()) return

        metadataDir.listFiles { file -> file.name.endsWith(".json") }?.forEach { file ->
            try {
                val metadata = Gson().fromJson(file.readText(), Map::class.java)

                // Ako fajl ima underscore u imenu, popravi ga
                if (file.name.contains("_")) {
                    val newName = file.name.replace("_", " ")
                    val newFile = File(metadataDir, newName)

                    // Dodaj regionName ako ga nema
                    if (!metadata.containsKey("regionName")) {
                        val mutableMetadata = metadata.toMutableMap()
                        mutableMetadata["regionName"] = newName.replace(".json", "")
                        newFile.writeText(Gson().toJson(mutableMetadata))
                        file.delete() // Obriši stari fajl

                        Log.d("MetadataFix", "Popravljen fajl: ${file.name} -> $newName")
                    }
                }

            } catch (e: Exception) {
                Log.e("MetadataFix", "Greška pri popravci ${file.name}: ${e.message}")
            }
        }
    }
    private fun downloadRegionHighways() {
        // Autoputevi Srbije: A1, A2, A3, A4
        val bbox = org.osmdroid.util.BoundingBox(46.0, 22.0, 42.5, 18.5)
        downloadTilesForRegion(bbox, 10, 14, "Autoputevi Srbije")
    }

    private fun downloadRegionRivers() {
        // Glavne reke Srbije
        val bbox = org.osmdroid.util.BoundingBox(45.5, 21.5, 43.5, 19.0)
        downloadTilesForRegion(bbox, 10, 14, "Reke Srbije")
    }
    private fun downloadRegionSerbia() {
        // Centralna Srbija: Beograd, Niš, Novi Sad
        val bbox = org.osmdroid.util.BoundingBox(45.5, 21.0, 43.0, 19.0) // north, east, south, west
        downloadTilesForRegion(bbox, 8, 14, "Srbija Centralna")
    }

    private fun downloadRegionBelgrade() {
        // Beograd i okolina
        val bbox = org.osmdroid.util.BoundingBox(44.9, 20.6, 44.7, 20.3)
        downloadTilesForRegion(bbox, 10, 16, "Beograd")
    }

    private fun downloadRegionNis() {
        // Niš i okolina
        val bbox = org.osmdroid.util.BoundingBox(43.35, 21.95, 43.3, 21.85)
        downloadTilesForRegion(bbox, 12, 18, "Niš")
    }

    private fun downloadRegionNationalParks() {
        // Fruška gora + Tara
        val bbox = org.osmdroid.util.BoundingBox(45.2, 19.9, 44.0, 19.4)
        downloadTilesForRegion(bbox, 10, 16, "Nacionalni Parkovi")
    }

    private fun downloadCurrentViewport(minZoom: Int = 12, maxZoom: Int = 14) {
        val mapView = findViewById<org.osmdroid.views.MapView>(R.id.mapView)
        val bbox = mapView.boundingBox

        // Proverite da li je bbox validan
        if (bbox == null) {
            Toast.makeText(this, "❌ Nije moguće preuzeti prazno područje", Toast.LENGTH_SHORT).show()
            return
        }

        // Proverite veličinu bounding box-a
        val latDiff = Math.abs(bbox.latNorth - bbox.latSouth)
        val lonDiff = Math.abs(bbox.lonEast - bbox.lonWest)

        if (latDiff < 0.001 || lonDiff < 0.001) {  // Previše mali region
            Toast.makeText(this, "❌ Region je previše mali. Zumirajte malo.", Toast.LENGTH_SHORT).show()
            return
        }

        showRegionNameDialog(bbox, minZoom, maxZoom, isSatellite = false)
    }

    private fun showRegionNameDialog(bbox: org.osmdroid.util.BoundingBox, minZoom: Int, maxZoom: Int, isSatellite: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_region_name, null)

        val etRegionName = dialogView.findViewById<EditText>(R.id.etRegionName)
        val tvZoomInfo = dialogView.findViewById<TextView>(R.id.tvZoomInfo)
        val tvAreaInfo = dialogView.findViewById<TextView>(R.id.tvAreaInfo)

        // Podrazumevano ime
        val defaultName = if (isSatellite) "Custom Region Satelitski" else "Custom Region"
        etRegionName.setText(defaultName)

        // Prikaži informacije
        tvZoomInfo.text = "Zoom: $minZoom-$maxZoom"
        val areaKm = calculateAreaInKm2(bbox)
        tvAreaInfo.text = "Površina: ${"%.2f".format(areaKm)} km²"

        AlertDialog.Builder(this)
            .setTitle(if (isSatellite) "🛰️ Preuzmi satelitski region" else "🗺️ Preuzmi region")
            .setView(dialogView)
            .setPositiveButton("✅ Preuzmi") { dialog, which ->
                val regionName = etRegionName.text.toString().trim()
                if (regionName.isNotEmpty()) {
                    startDownloadWithProgress(bbox, minZoom, maxZoom, regionName, isSatellite)
                } else {
                    Toast.makeText(this, "❌ Unesite ime za region", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun startDownloadWithProgress(bbox: org.osmdroid.util.BoundingBox, minZoom: Int, maxZoom: Int, regionName: String, isSatellite: Boolean) {
        // PROVERA ZA SATELITSKE
        val actualMaxZoom = if (isSatellite) {
            if (maxZoom > 19) {
                Toast.makeText(this, "⚠️ Satelitske mape podržavaju max zoom 19. Smanjeno na 19.", Toast.LENGTH_LONG).show()
                19
            } else {
                maxZoom
            }
        } else {
            maxZoom  // Standardne mogu do 21
        }

        // Prikaži info
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("${if (isSatellite) "🛰️" else "🗺️"} Preuzimanje: $regionName")
            .setMessage("Zoom: $minZoom-$actualMaxZoom\n" +
                    "${if (isSatellite) "Satelitska (max 19)" else "Standardna (max 21)"}")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var totalTiles = 0
                var downloadedTiles = 0

                // IZRAČUNAJ UKUPAN BROJ TILE-OVA
                for (zoom in minZoom..maxZoom) {
                    totalTiles += calculateTilesForZoom(bbox, zoom)
                }

                withContext(Dispatchers.Main) {
                    progressDialog.setMessage("Preuzimanje: $regionName\nZoom: $minZoom-$maxZoom\n0/$totalTiles tile-ova")
                }

                if (isSatellite) {
                    // SATELITSKA MAPA
                    for (zoom in minZoom..maxZoom) {
                        val downloaded = downloadSatelliteZoomLevel(bbox, zoom, regionName, progressDialog, downloadedTiles, totalTiles)
                        downloadedTiles += downloaded
                    }

                    saveSatelliteRegionMetadata(bbox, minZoom, maxZoom, regionName, downloadedTiles)
                } else {
                    // STANDARDNA MAPA
                    for (zoom in minZoom..maxZoom) {
                        val downloaded = downloadStandardZoomLevel(bbox, zoom, regionName, progressDialog, downloadedTiles, totalTiles)
                        downloadedTiles += downloaded
                    }

                    saveStandardRegionMetadata(bbox, minZoom, maxZoom, regionName, downloadedTiles)
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    val successMessage = if (downloadedTiles > 0) {
                        "✅ Preuzeto: $regionName\n$downloadedTiles/$totalTiles tile-ova\nZoom: $minZoom-$maxZoom"
                    } else {
                        "⚠️ Nije preuzet nijedan tile. Proverite internet konekciju."
                    }
                    Toast.makeText(this@MainActivity, successMessage, Toast.LENGTH_LONG).show()

                    // Osveži listu offline mapa
                    showOfflineMapsList()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "❌ Greška: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private fun testZoomLevelsForSatellite() {
        val testCases = listOf(
            "Zoom 12-14 (osnovno)" to Pair(12, 14),
            "Zoom 15-17 (srednje)" to Pair(15, 17),
            "Zoom 18-19 (visoko)" to Pair(18, 19),
            "Zoom 20-21 (previsoko - NEĆE RADITI)" to Pair(20, 21)
        )

        val items = testCases.map { it.first }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("🧪 Test zoom nivoa za satelitske")
            .setItems(items) { dialog, which ->
                val (minZoom, maxZoom) = testCases[which].second

                if (maxZoom > 19) {
                    Toast.makeText(this, "❌ Zoom $maxZoom ne postoji za satelitske!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "✅ Zoom $minZoom-$maxZoom je podržan", Toast.LENGTH_SHORT).show()
                    // Testiraj preuzimanje
                    testDownloadRegion(minZoom, maxZoom)
                }
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun testDownloadRegion(minZoom: Int, maxZoom: Int) {
        // Mali test region (centar Beograda)
        val bbox = org.osmdroid.util.BoundingBox(44.82, 20.47, 44.80, 20.45)
        startDownloadWithProgress(bbox, minZoom, maxZoom, "TEST Satelitska", true)
    }

    private fun fixExistingSatelliteMaps() {
        val metadataDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "offline_regions")
        if (!metadataDir.exists()) return

        metadataDir.listFiles { file -> file.name.endsWith(".json") }?.forEach { file ->
            try {
                val metadata = Gson().fromJson(file.readText(), Map::class.java)
                val isSatellite = metadata["isSatellite"] as? Boolean ?: false
                val maxZoom = (metadata["maxZoom"] as? Number)?.toInt() ?: 0

                if (isSatellite && maxZoom > 19) {
                    Log.d("FixMaps", "Popravljam mapu: ${file.name}, maxZoom: $maxZoom -> 19")

                    // Ažuriraj metadata
                    val updatedMetadata = metadata.toMutableMap()
                    updatedMetadata["maxZoom"] = 19
                    updatedMetadata["fixed"] = true
                    updatedMetadata["originalMaxZoom"] = maxZoom

                    file.writeText(Gson().toJson(updatedMetadata))
                }
            } catch (e: Exception) {
                Log.e("FixMaps", "Greška pri popravci ${file.name}: ${e.message}")
            }
        }
    }
    private fun saveStandardRegionMetadata(bbox: org.osmdroid.util.BoundingBox, minZoom: Int, maxZoom: Int, regionName: String, tileCount: Int) {
        // KORISTITE generateUniqueFileName
        val fileName = generateUniqueFileName(regionName, false)

        val metadata = mapOf(
            "regionName" to regionName,
            "displayName" to regionName,
            "north" to bbox.latNorth,
            "south" to bbox.latSouth,
            "east" to bbox.lonEast,
            "west" to bbox.lonWest,
            "minZoom" to minZoom,
            "maxZoom" to maxZoom,
            "tileCount" to tileCount,
            "downloadDate" to System.currentTimeMillis(),  // 👈 DODAJTE OVO
            "downloadDateFormatted" to SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()), // 👈 I OVO
            "isSatellite" to false,
            "type" to "standard"
        )

        val metadataDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "offline_regions")
        if (!metadataDir.exists()) {
            metadataDir.mkdirs()
        }

        val metadataFile = File(metadataDir, fileName)
        metadataFile.writeText(Gson().toJson(metadata))

        Log.d("MetadataSave", "✅ Sačuvana standardna mapa: $fileName, datum: ${metadata["downloadDateFormatted"]}")
    }
    private suspend fun downloadStandardZoomLevel(
        bbox: org.osmdroid.util.BoundingBox,
        zoom: Int,
        regionName: String,
        progressDialog: AlertDialog,
        alreadyDownloaded: Int,
        totalTiles: Int
    ): Int {
        var downloaded = 0
        val northTile = lat2tile(bbox.latNorth, zoom)
        val southTile = lat2tile(bbox.latSouth, zoom)
        val eastTile = lon2tile(bbox.lonEast, zoom)
        val westTile = lon2tile(bbox.lonWest, zoom)

        for (x in westTile..eastTile) {
            for (y in northTile..southTile) {
                try {
                    downloadStandardSingleTile(x, y, zoom)
                    downloaded++

                    // AŽURIRAJ PROGRESS
                    if (downloaded % 5 == 0) {
                        val currentTotal = alreadyDownloaded + downloaded
                        withContext(Dispatchers.Main) {
                            progressDialog.setMessage(
                                "Preuzimanje: $regionName\n" +
                                        "Standardni tile-ovi\n" +
                                        "$currentTotal/$totalTiles tile-ova\n" +
                                        "Progress: ${(currentTotal * 100 / totalTiles)}%"
                            )
                        }
                    }

                } catch (e: Exception) {
                    Log.e("StandardTile", "Greška pri standardnom tile $zoom/$x/$y: ${e.message}")
                }
            }
        }

        return downloaded
    }

    private fun downloadStandardSingleTile(x: Int, y: Int, zoom: Int) {
        // OpenStreetMap tile URL
        val tileUrl = "https://tile.openstreetmap.org/$zoom/$x/$y.png"

        Log.d("DownloadDebug", "🗺️ Preuzimanje standardnog tila: zoom=$zoom, x=$x, y=$y")
        Log.d("DownloadDebug", "🔗 URL: $tileUrl")

        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(tileUrl)
            .header("User-Agent", "Mozilla/5.0 (Android GPS Tracker)")
            .build()

        try {
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val tileData = response.body?.bytes()
                if (tileData != null && tileData.isNotEmpty()) {
                    saveStandardTileToCache(x, y, zoom, tileData)
                    Log.d("DownloadDebug", "💾 Sačuvan standardni tile: $zoom/$x/$y")
                }
            }
            response.close()
        } catch (e: Exception) {
            Log.e("DownloadDebug", "💥 Greška pri preuzimanju standardnog tila: ${e.message}")
        }
    }

    private fun saveStandardTileToCache(x: Int, y: Int, zoom: Int, tileData: ByteArray) {
        try {
            // OSMdroid cache putanja za standardne mape
            val cacheDir = File(Configuration.getInstance().osmdroidBasePath, "tiles/Mapnik")

            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val tileFile = File(cacheDir, "$zoom/$x/$y.png")
            tileFile.parentFile?.mkdirs()

            tileFile.writeBytes(tileData)

            Log.d("TileSave", "✅ Standardni tile sačuvan: ${tileFile.absolutePath}")

        } catch (e: Exception) {
            Log.e("TileSave", "💥 GREŠKA pri čuvanju standardnog tila: ${e.message}")
        }
    }
    private fun downloadCurrentViewportSatellite(minZoom: Int = 12, maxZoom: Int = 14) {
        val mapView = findViewById<org.osmdroid.views.MapView>(R.id.mapView)
        val bbox = mapView.boundingBox

        if (bbox == null) {
            Toast.makeText(this, "❌ Nije moguće preuzeti prazno područje", Toast.LENGTH_SHORT).show()
            return
        }

        val latDiff = Math.abs(bbox.latNorth - bbox.latSouth)
        val lonDiff = Math.abs(bbox.lonEast - bbox.lonWest)

        if (latDiff < 0.001 || lonDiff < 0.001) {
            Toast.makeText(this, "❌ Region je previše mali. Zumirajte malo.", Toast.LENGTH_SHORT).show()
            return
        }

        showRegionNameDialog(bbox, minZoom, maxZoom, isSatellite = true)
    }
    private fun downloadTilesForRegion(bbox: org.osmdroid.util.BoundingBox, minZoom: Int, maxZoom: Int, regionName: String) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("📥 Preuzimanje: $regionName")
            .setMessage("Priprema...\nZoom: $minZoom-$maxZoom")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var totalTiles = 0

                // IZRAČUNAJ UKUPAN BROJ TILE-OVA
                for (zoom in minZoom..maxZoom) {
                    totalTiles += calculateTilesForZoom(bbox, zoom)
                }

                // JEDNOSTAVNA KALKULACIJA VELIČINE
                val estimatedSizeMB = (totalTiles * 35) / 1024.0
                val displaySize = "~${String.format("%.1f", estimatedSizeMB)} MB"

                withContext(Dispatchers.Main) {
                    progressDialog.setMessage("Preuzimanje: $regionName\nZoom: $minZoom-$maxZoom\nTile-ova: $totalTiles\nVeličina: $displaySize\n0%")
                }

                var downloadedTiles = 0

                // PREUZIMANJE TILE-OVA ZA SVAKI ZOOM LEVEL
                for (zoom in minZoom..maxZoom) {
                    val downloaded = downloadZoomLevel(bbox, zoom, regionName, progressDialog, downloadedTiles, totalTiles)
                    downloadedTiles += downloaded
                }

                // SAČUVAJ METAPODATKE
                saveRegionMetadata(bbox, minZoom, maxZoom, regionName, downloadedTiles)

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    showDownloadSuccess(regionName, downloadedTiles)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "❌ Greška: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }




    private suspend fun downloadZoomLevel(
        bbox: org.osmdroid.util.BoundingBox,
        zoom: Int,
        regionName: String,
        progressDialog: AlertDialog,
        alreadyDownloaded: Int,
        totalTiles: Int
    ): Int {
        var downloaded = 0
        val northTile = lat2tile(bbox.latNorth, zoom)
        val southTile = lat2tile(bbox.latSouth, zoom)
        val eastTile = lon2tile(bbox.lonEast, zoom)
        val westTile = lon2tile(bbox.lonWest, zoom)

        for (x in westTile..eastTile) {
            for (y in northTile..southTile) {
                try {
                    downloadSingleTile(x, y, zoom)
                    downloaded++

                    // AŽURIRAJ PROGRESS
                    if (downloaded % 10 == 0) { // Ažuriraj na svakih 10 tile-ova
                        val currentTotal = alreadyDownloaded + downloaded
                        withContext(Dispatchers.Main) {
                            progressDialog.setMessage(
                                "Preuzimanje: $regionName\n" +
                                        "Zoom: $zoom\n" +
                                        "$currentTotal/$totalTiles tile-ova\n" +
                                        "Progress: ${(currentTotal * 100 / totalTiles)}%"
                            )
                        }
                    }

                } catch (e: Exception) {
                    Log.e("TileDownload", "Greška pri preuzimanju tile $zoom/$x/$y: ${e.message}")
                }
            }
        }

        return downloaded
    }

    private fun downloadSingleTile(x: Int, y: Int, zoom: Int) {
        val tileUrl = "https://tile.openstreetmap.org/$zoom/$x/$y.png"
        val client = OkHttpClient()
        val request = Request.Builder().url(tileUrl).build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                // SAČUVAJ TILE U OSMdroid CACHE
                val tileData = response.body?.bytes()
                tileData?.let { data -> saveTileToCache(x, y, zoom, data) }
            }
        }
    }

    private fun saveTileToCache(x: Int, y: Int, zoom: Int, tileData: ByteArray) {
        val cacheDir = File(Configuration.getInstance().osmdroidBasePath, "tiles/OpenStreetMap")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val tileFile = File(cacheDir, "$zoom/$x/$y.png")
        tileFile.parentFile?.mkdirs()
        tileFile.writeBytes(tileData)
    }

    private fun saveRegionMetadata(
        bbox: org.osmdroid.util.BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        regionName: String,
        tileCount: Int  // <-- DODAJTE OVO
    ) {
        val metadataDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "offline_regions")
        if (!metadataDir.exists()) {
            metadataDir.mkdirs()
        }

        val metadataFile = File(metadataDir, "$regionName.json")

        val metadata = mapOf(
            "regionName" to regionName,
            "tileCount" to tileCount,  // <-- DODAJTE!
            "isSatellite" to false,    // <-- STANDARDNA NIJE SATELITSKA
            "bbox" to mapOf(
                "north" to bbox.latNorth,
                "south" to bbox.latSouth,
                "east" to bbox.lonEast,
                "west" to bbox.lonWest
            ),
            "minZoom" to minZoom,
            "maxZoom" to maxZoom,
            "createdAt" to System.currentTimeMillis()
        )

        metadataFile.writeText(Gson().toJson(metadata))
    }

    private fun showDownloadSuccess(regionName: String, tileCount: Int) {
        AlertDialog.Builder(this)
            .setTitle("🎉 $regionName Preuzeta!")
            .setMessage("""
            Uspešno preuzeto $tileCount tile-ova!
            
            🗺️ Sada možete koristiti ovaj region BEZ interneta.
            
            Želite li preći na offline mod?
        """.trimIndent())
            .setPositiveButton("✅ Koristi Offline") { dialog, which ->
                enableOfflineMode(regionName)
            }
            .setNegativeButton("🌐 Ostani Online", null)
            .show()
    }


    private fun enableOfflineMode(regionName: String) {
        try {
            val metadataDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "offline_regions")

            // PRONAĐITE FAJL - KORISTITE var
            var metadataFile = File(metadataDir, "$regionName.json")

            // Ako ne postoji sa .json, pokušajte sa generateUniqueFileName
            if (!metadataFile.exists()) {
                val satelliteFileName = generateUniqueFileName(regionName, true)
                val standardFileName = generateUniqueFileName(regionName, false)

                val satelliteFile = File(metadataDir, satelliteFileName)
                val standardFile = File(metadataDir, standardFileName)

                when {
                    satelliteFile.exists() -> metadataFile = satelliteFile
                    standardFile.exists() -> metadataFile = standardFile
                    else -> {
                        // Pokušajte da pronađete po imenu
                        val matchingFiles = metadataDir.listFiles { file ->
                            file.name.contains(regionName, ignoreCase = true) &&
                                    file.name.endsWith(".json")
                        }

                        if (!matchingFiles.isNullOrEmpty()) {
                            metadataFile = matchingFiles.first()
                        }
                    }
                }
            }

            if (metadataFile.exists()) {
                val metadata = Gson().fromJson(metadataFile.readText(), Map::class.java)
                val isSatellite = metadata["isSatellite"] as? Boolean ?: false

                if (isSatellite) {
                    Toast.makeText(this, "⚠️ Ova mapa je satelitska", Toast.LENGTH_SHORT).show()
                    return
                }

                Log.d("StandardOffline", "🗺️ Aktiviranje standardne offline mape: $regionName")

                // Postavi standardni tile source
                mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)

                // Isključi online tile-ove
                mapView.setUseDataConnection(false)

                // Očisti cache za sigurnost
                mapView.tileProvider.clearTileCache()

                // Osveži prikaz
                mapView.invalidate()

                // Sačuvaj informacije o aktivnoj mapi
                currentOfflineMapName = regionName
                currentOfflineMapIsSatellite = false
                currentOfflineMapMaxZoom = (metadata["maxZoom"] as? Number)?.toInt() ?: 19

                // SAKRI DUGME ZA HIBRIDNI MOD (samo za satelitske)
                binding.btnToggleHybrid.visibility = View.GONE
                isHybridMode = false
                removeStreetOverlay()

                Toast.makeText(this, "🗺️ Offline: $regionName", Toast.LENGTH_SHORT).show()

            } else {
                Toast.makeText(this, "❌ Metadata fajl ne postoji", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("StandardOffline", "Greška: ${e.message}", e)
            Toast.makeText(this, "❌ Greška: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun updateOfflineIndicator(regionName: String, maxZoom: Int) {
        // Samo prikažite Toast umesto TextView-a
        Toast.makeText(this, "📴 OFFLINE: $regionName (max zoom: $maxZoom)", Toast.LENGTH_LONG).show()

        // Ili dodajte Snackbar
        val snackbar = Snackbar.make(
            findViewById(android.R.id.content),
            "📴 OFFLINE: $regionName (max zoom: $maxZoom)",
            Snackbar.LENGTH_LONG
        )
        snackbar.setBackgroundTint(Color.parseColor("#4CAF50"))
        snackbar.show()
    }

    private fun calculateAreaInKm2(bbox: org.osmdroid.util.BoundingBox): Double {
        // Približna površina u km²
        val heightDegrees = bbox.latNorth - bbox.latSouth
        val widthDegrees = bbox.lonEast - bbox.lonWest

        // 1° latitude ≈ 111.32 km (konstantno)
        val heightKm = heightDegrees * 111.32

        // 1° longitude varira sa latitudom
        val avgLatRadians = Math.toRadians((bbox.latNorth + bbox.latSouth) / 2.0)
        val widthKm = widthDegrees * 111.32 * Math.cos(avgLatRadians)

        return heightKm * widthKm
    }

    private fun calculateTilesForZoom(bbox: org.osmdroid.util.BoundingBox, zoom: Int): Int {
        val northTile = lat2tile(bbox.latNorth, zoom)
        val southTile = lat2tile(bbox.latSouth, zoom)
        val eastTile = lon2tile(bbox.lonEast, zoom)
        val westTile = lon2tile(bbox.lonWest, zoom)

        return (eastTile - westTile + 1) * (southTile - northTile + 1)
    }
    // POMOĆNE FUNKCIJE ZA TILE KALKULACIJE
    private fun lat2tile(lat: Double, zoom: Int): Int {
        return Math.floor((1 - Math.log(Math.tan(lat * Math.PI / 180) + 1 / Math.cos(lat * Math.PI / 180)) / Math.PI) / 2 * (1 shl zoom)).toInt()
    }

    private fun lon2tile(lon: Double, zoom: Int): Int {
        return Math.floor((lon + 180) / 360 * (1 shl zoom)).toInt()
    }
    private fun showOfflineMapsList() {
        val metadataDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "offline_regions")

        // Debug log
        Log.d("OfflineMaps", "Proveravam folder: ${metadataDir.absolutePath}")

        if (!metadataDir.exists()) {
            Toast.makeText(this, "📭 Nema offline mapa", Toast.LENGTH_LONG).show()
            downloadOfflineMap()
            return
        }

        val regionFiles = metadataDir.listFiles { file ->
            file.name.endsWith(".json")
        }

        if (regionFiles.isNullOrEmpty()) {
            Toast.makeText(this, "📭 Nema offline mapa", Toast.LENGTH_LONG).show()
            downloadOfflineMap()
            return
        }

        // Kreiraj custom dijalog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_offline_maps, null)

        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerViewOfflineMaps)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val btnDownloadNew = dialogView.findViewById<Button>(R.id.btnDownloadNew)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)
        val tvNoMaps = dialogView.findViewById<TextView>(R.id.tvNoMaps)

        tvTitle.text = "🗺️ Offline Mape"

        // Kreiraj listu offline mapa
        val offlineMaps = mutableListOf<OfflineMapItem>()

        regionFiles.forEach { file ->
            try {
                val metadata = Gson().fromJson(file.readText(), Map::class.java)

                // U showOfflineMapsList(), u petlji gde učitavate metadata:
                val regionName = metadata["regionName"] as? String ?: file.nameWithoutExtension
                val displayName = metadata["displayName"] as? String ?: regionName
                val tileCount = (metadata["tileCount"] as? Number)?.toInt() ?: 0
                val isSatellite = metadata["isSatellite"] as? Boolean ?: false
                val downloadDate = metadata["downloadDateFormatted"] as? String ?: "Nepoznato"
                val mapType = if (isSatellite) "satellite" else "standard"

// Kreirajte ime za prikaz sa datumom
                val nameForDisplay = "$displayName\n📅 $downloadDate • 📊 $tileCount tile-ova"

                offlineMaps.add(
                    OfflineMapItem(
                        name = nameForDisplay,  // 👈 OVO ĆE PRIKAZATI DATUM
                        lookupName = regionName,
                        tileCount = tileCount,
                        isSatellite = isSatellite,
                        mapType = mapType,
                        file = file,
                        icon = if (isSatellite) "🛰️" else "🗺️",
                        downloadDate = downloadDate
                    )
                )

                Log.d("OfflineMaps", "Učitana: $nameForDisplay, tip: $mapType, tile-ova: $tileCount")

            } catch (e: Exception) {
                Log.e("OfflineMaps", "Greška pri učitavanju ${file.name}: ${e.message}")
            }
        }

        // Sortiraj po imenu
        val sortedMaps = offlineMaps.sortedBy { it.name }

        if (sortedMaps.isEmpty()) {
            Toast.makeText(this, "📭 Nema validnih offline mapa", Toast.LENGTH_LONG).show()
            downloadOfflineMap()
            return
        }

        // Postavi RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Kreiraj dijalog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Kreiraj adapter
        val adapter = OfflineMapsAdapter(sortedMaps,
            onMapClick = { offlineMap ->
                Log.d("MapClick", "Kliknuta: ${offlineMap.name}, tip: ${offlineMap.mapType}")

                when (offlineMap.mapType) {
                    "satellite" -> enableSatelliteOfflineMode(offlineMap.lookupName)
                    else -> enableOfflineMode(offlineMap.lookupName)
                }
                dialog.dismiss()
            },
            onDeleteClick = { offlineMap ->
                deleteOfflineMap(offlineMap, dialog)
            },
            onRenameClick = { offlineMap ->
                showRenameOfflineMapDialog(offlineMap, dialog)
            }
        )

        recyclerView.adapter = adapter

        // Dugmići
        btnDownloadNew.setOnClickListener {
            dialog.dismiss()
            downloadOfflineMap()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // Prikaži dijalog
        dialog.show()
    }
    private fun showRenameOfflineMapDialog(offlineMap: OfflineMapItem, parentDialog: AlertDialog) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rename_map, null)

        val etNewName = dialogView.findViewById<EditText>(R.id.etNewName)
        val tvCurrentName = dialogView.findViewById<TextView>(R.id.tvCurrentName)

        // Ekstrahuj samo osnovno ime (bez koordinata)
        val currentDisplayName = offlineMap.name
        val currentBaseName = if (currentDisplayName.contains("\n")) {
            currentDisplayName.substringBefore("\n").trim()
        } else {
            currentDisplayName
        }

        tvCurrentName.text = "Trenutno: $currentBaseName"
        etNewName.setText(currentBaseName)
        etNewName.selectAll()

        AlertDialog.Builder(this)
            .setTitle("✏️ Promeni ime mape")
            .setView(dialogView)
            .setPositiveButton("✅ Sačuvaj") { dialog, which ->
                val newName = etNewName.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentBaseName) {
                    renameOfflineMap(offlineMap, newName, parentDialog)
                } else if (newName.isEmpty()) {
                    Toast.makeText(this, "❌ Ime ne može biti prazno", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun renameOfflineMap(offlineMap: OfflineMapItem, newName: String, parentDialog: AlertDialog) {
        try {
            // Pročitaj postojeći metadata
            val metadata = Gson().fromJson(offlineMap.file.readText(), Map::class.java)

            // Ažuriraj regionName
            val updatedMetadata = metadata.toMutableMap()
            updatedMetadata["regionName"] = newName

            // Snimi novi metadata fajl
            val newFileName = newName.replace("/", "_").replace("\\", "_") + ".json"
            val newFile = File(offlineMap.file.parentFile, newFileName)

            newFile.writeText(Gson().toJson(updatedMetadata))

            // Obriši stari fajl ako je ime promenjeno
            if (newFile.absolutePath != offlineMap.file.absolutePath) {
                offlineMap.file.delete()
            }

            Toast.makeText(this, "✅ Ime promenjeno u: $newName", Toast.LENGTH_SHORT).show()

            // Osveži dijalog
            parentDialog.dismiss()
            showOfflineMapsList()

        } catch (e: Exception) {
            Toast.makeText(this, "❌ Greška: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun deleteOfflineMap(offlineMap: OfflineMapItem, parentDialog: AlertDialog) {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Brisanje mape")
            .setMessage("Da li ste sigurni da želite da obrišete:\n\n" +
                    "${offlineMap.name}\n" +
                    "${offlineMap.tileCount} tile-ova")
            .setPositiveButton("✅ Da, obriši") { dialog, which ->
                // Obriši metadata fajl
                val deleted = offlineMap.file.delete()

                if (deleted) {
                    Toast.makeText(this, "✅ Mapa obrisana", Toast.LENGTH_SHORT).show()
                    // Osveži dijalog
                    parentDialog.dismiss()
                    showOfflineMapsList()
                } else {
                    Toast.makeText(this, "❌ Greška pri brisanju", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }
    private fun deleteAllOfflineMaps() {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Brisanje Svih Offline Mapa")
            .setMessage("Da li ste sigurni? Ovo će obrisati sve preuzete tile-ove i region podatke.")
            .setPositiveButton("✅ Obriši Sve") { dialog, which ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        // OBRIŠI METADATA
                        val metadataDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "offline_regions")
                        if (metadataDir.exists()) {
                            metadataDir.deleteRecursively()
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "🗑️ Sve offline mape obrisane", Toast.LENGTH_LONG).show()
                            binding.btnMapType.setBackgroundResource(R.drawable.button_dark_blue) // Resetuj background
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "❌ Greška pri brisanju", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }
    private fun startOfflineMapDownload() {
        // POKAŽI PROGRESS DIALOG
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("📥 Preuzimanje mape...")
            .setMessage("Molimo sačekajte...\n\nSrbija ~50MB\n\nNe zatvarajte aplikaciju!")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // SIMULIRAJ DOWNLOAD (u realnoj app, ovde bi bio pravi download)
                for (i in 1..10) {
                    delay(1000) // 1 sekunda po koraku

                    withContext(Dispatchers.Main) {
                        val progress = i * 10
                        progressDialog.setMessage("Preuzimanje... $progress%\n\nSrbija ~50MB\n\nNe zatvarajte aplikaciju!")
                    }
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "✅ Offline mapa uspešno preuzeta!", Toast.LENGTH_LONG).show()
                    showOfflineMapReadyDialog()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "❌ Greška pri preuzimanju: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun selectCustomRegion() {
        val zoomLevels = arrayOf(
            "🗺️ Srednji detalji (zoom 12-14)",
            "🔍 Visoki detalji (zoom 15-17)",
            "🔬 Veoma visoki detalji (zoom 18-20)",
            "📐 Izaberi ručno zoom nivo"
        )

        AlertDialog.Builder(this)
            .setTitle("🔍 Nivo detalja")
            .setItems(zoomLevels) { dialog, which ->
                when (which) {
                    0 -> downloadCurrentViewportWithZoom(12, 14)  // Srednji
                    1 -> downloadCurrentViewportWithZoom(15, 17)  // Visoki
                    2 -> downloadCurrentViewportWithZoom(18, 20)  // Veoma visoki
                    3 -> selectManualZoomLevels()                 // Ručno
                }
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun selectCustomRegionWithZoom(minZoom: Int, maxZoom: Int) {
        // Ovo treba da otvori mapu za izbor regiona
        // Ako imate funkciju downloadCurrentViewport, promenite je da prihvata zoom parametre

        downloadCurrentViewportWithZoom(minZoom, maxZoom)
    }

    private fun selectManualZoomLevels() {
        // Koristite jednostavniji layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_simple_zoom, null)

        val seekBarMin = dialogView.findViewById<SeekBar>(R.id.seekBarMinZoom)
        val seekBarMax = dialogView.findViewById<SeekBar>(R.id.seekBarMaxZoom)
        val tvMinZoom = dialogView.findViewById<TextView>(R.id.tvMinZoom)
        val tvMaxZoom = dialogView.findViewById<TextView>(R.id.tvMaxZoom)

        // Osnovne postavke
        seekBarMin.max = 19 - 3  // Prilagodite max na raspon
        seekBarMin.progress = 12 - 3  // Progress = željena vrednost - minimum

        seekBarMax.max = 21 - 5  // Prilagodite max na raspon
        seekBarMax.progress = 16 - 5  // Progress = željena vrednost - minimum

// Ažurirajte tekstove
        tvMinZoom.text = "Min Zoom: 12"
        tvMaxZoom.text = "Max Zoom: 16"

        tvMinZoom.text = "Min Zoom: 12"
        tvMaxZoom.text = "Max Zoom: 16"

        // Listeners
        seekBarMin.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val actualValue = progress + 3  // Dodajte minimum nazad
                tvMinZoom.text = "Min Zoom: $actualValue"

                if (actualValue > (seekBarMax.progress + 5)) {
                    seekBarMax.progress = actualValue - 5
                    tvMaxZoom.text = "Max Zoom: ${seekBarMax.progress + 5}"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        seekBarMax.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val actualValue = progress + 5  // Dodajte minimum nazad
                tvMaxZoom.text = "Max Zoom: $actualValue"

                if (actualValue < (seekBarMin.progress + 3)) {
                    seekBarMin.progress = actualValue - 3
                    tvMinZoom.text = "Min Zoom: ${seekBarMin.progress + 3}"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        AlertDialog.Builder(this)
            .setTitle("📐 Izaberite zoom nivo")
            .setView(dialogView)
            .setPositiveButton("✅ Nastavi") { dialog, which ->
                val minZoom = seekBarMin.progress
                val maxZoom = seekBarMax.progress

                if (maxZoom - minZoom >= 2) {
                    showMapTypeSelection(minZoom, maxZoom)
                } else {
                    Toast.makeText(this, "❌ Razlika mora biti najmanje 2 nivoa", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun estimateTilesForZoomRange(minZoom: Int, maxZoom: Int): String {
        val tileCount = calculateMaxTilesForZoomRange(minZoom, maxZoom)
        val formattedCount = formatTileCount(tileCount)

        // Dodaj upozorenje ako je previše
        return when {
            tileCount > 10_000_000 -> "⚠️ PREVIŠE: $formattedCount tile-ova\n(Potrebno >1GB)"
            tileCount > 1_000_000 -> "🟡 Veliko: $formattedCount tile-ova\n(~100-500MB)"
            tileCount > 100_000 -> "🟢 Srednje: $formattedCount tile-ova\n(~10-100MB)"
            else -> "🟢 Mali: $formattedCount tile-ova\n(<10MB)"
        }
    }

    private fun calculateMaxTilesForZoomRange(minZoom: Int, maxZoom: Int): Long {
        // Broj tile-ova po zoom nivou za celu Zemlju (teorijski maksimum)
        val tilesPerLevel = mapOf(
            0 to 1L, 1 to 4L, 2 to 16L, 3 to 64L, 4 to 256L, 5 to 1024L,
            6 to 4096L, 7 to 16384L, 8 to 65536L, 9 to 262144L,
            10 to 1048576L, 11 to 4194304L, 12 to 16777216L,
            13 to 67108864L, 14 to 268435456L, 15 to 1073741824L,
            16 to 4294967296L, 17 to 17179869184L, 18 to 68719476736L,
            19 to 274877906944L, 20 to 1099511627776L, 21 to 4398046511104L
        )

        var totalTiles = 0L
        for (zoom in minZoom..maxZoom) {
            totalTiles += tilesPerLevel[zoom] ?: 0L
        }

        return totalTiles
    }

    private fun formatTileCount(count: Long): String {
        return when {
            count >= 1_000_000_000 -> "${"%.1f".format(count / 1_000_000_000.0)}B" // milijardi
            count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M" // miliona
            count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K" // hiljada
            else -> count.toString()
        }
    }
    private fun showMapTypeSelection(minZoom: Int, maxZoom: Int) {
        // Proveri da li je maxZoom > 19 za satelitske
        if (maxZoom > 19) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Upozorenje")
                .setMessage("Za satelitske mape, maksimalni zoom je 19.\n\n" +
                        "Željeni max zoom: $maxZoom\n" +
                        "Maksimum za satelitsku: 19\n\n" +
                        "Da li želite da:\n" +
                        "1. Smanjite max zoom na 19 (satelitska)\n" +
                        "2. Zadržite max zoom $maxZoom (standardna)")
                .setPositiveButton("🛰️ Satelitska (max 19)") { dialog, which ->
                    downloadCurrentViewportSatellite(minZoom, 19)
                }
                .setNeutralButton("🗺️ Standardna (max $maxZoom)") { dialog, which ->
                    downloadCurrentViewport(minZoom, maxZoom)
                }
                .setNegativeButton("❌ Otkaži", null)
                .show()
        } else {
            // Normalan dijalog ako je maxZoom <= 19
            AlertDialog.Builder(this)
                .setTitle("🗺️ Tip mape")
                .setItems(arrayOf("🛰️ Satelitska mapa", "🗺️ Standardna mapa")) { dialog, which ->
                    if (which == 0) {
                        downloadCurrentViewportSatellite(minZoom, maxZoom)
                    } else {
                        downloadCurrentViewport(minZoom, maxZoom)
                    }
                }
                .setNegativeButton("❌ Otkaži", null)
                .show()
        }
    }
    private fun downloadCurrentViewportWithZoom(minZoom: Int, maxZoom: Int) {
        // Pozovite postojeću funkciju sa zoom parametrima
        downloadCurrentViewport(minZoom, maxZoom)
    }

    private fun updateZoomTexts(
        tvMinZoom: TextView,
        tvMaxZoom: TextView,
        tvWarning: TextView,
        minZoom: Int,
        maxZoom: Int
    ) {
        tvMinZoom.text = "Min Zoom: $minZoom"
        tvMaxZoom.text = "Max Zoom: $maxZoom"

        // Izračunaj približan broj tile-ova
        val zoomDiff = maxZoom - minZoom
        val estimatedTiles = when {
            zoomDiff <= 2 -> "🟢 Mali broj tile-ova (~100-500)"
            zoomDiff <= 4 -> "🟡 Srednji broj tile-ova (~500-2000)"
            zoomDiff <= 6 -> "🟠 Veliki broj tile-ova (~2000-10000)"
            else -> "🔴 Ogroman broj tile-ova (10k+)"
        }

        tvWarning.text = "Razlika: $zoomDiff nivoa\n$estimatedTiles"
    }
    private fun downloadCurrentViewportCustom() {
        val bbox = binding.mapView.boundingBox
        val currentZoom = binding.mapView.zoomLevel.toInt()

        val regionInfo = """
        📍 Region: ${String.format("%.2f", bbox.latNorth)}°N - ${String.format("%.2f", bbox.latSouth)}°S
                  ${String.format("%.2f", bbox.lonWest)}°W - ${String.format("%.2f", bbox.lonEast)}°E
        🔍 Zoom: $currentZoom
        🏞️ Približna lokacija: ${getRegionName(bbox)}
    """.trimIndent()

        // DODAJ IZBOR TIPA MAPE
        val options = arrayOf("🛰️ Satelitska", "🗺️ Standardna")

        AlertDialog.Builder(this)
            .setTitle("📥 Preuzmi Custom Region")
            .setMessage(regionInfo)
            .setSingleChoiceItems(options, 0) { dialog, which ->
                val isSatellite = (which == 0)
            }
            .setPositiveButton("📥 Preuzmi") { dialog, which ->
                val isSatellite = (dialog as AlertDialog).listView.checkedItemPosition == 0
                if (isSatellite) {
                    downloadSatelliteTilesForRegion(bbox, currentZoom, currentZoom + 2, "Custom Region Satelitski")
                } else {
                    downloadTilesForRegion(bbox, currentZoom, currentZoom + 2, "Custom Region Standardni")
                }
            }
            .setNegativeButton("↩️ Promeni", null)
            .show()
    }
    private fun getRegionName(bbox: org.osmdroid.util.BoundingBox): String {
        // JEDNOSTAVNA DETEKCIJA LOKACIJE PO KOORDINATAMA
        val centerLat = (bbox.latNorth + bbox.latSouth) / 2
        val centerLon = (bbox.lonEast + bbox.lonWest) / 2

        return when {
            centerLat > 44.5 && centerLon > 20.0 -> "Severna Srbija (Beograd)"
            centerLat > 43.0 && centerLon > 21.0 -> "Južna Srbija (Niš)"
            centerLat > 45.0 -> "Vojvodina"
            centerLat < 43.0 -> "Južna Srbija/Kosovo"
            else -> "Centralna Srbija"
        }
    }
    private fun showCustomRegionSelector() {
        val zoomLevels = arrayOf(
            "🔍 Niski detalji (zoom 8-10) - Celokupna Srbija",
            "📐 Srednji detalji (zoom 10-13) - Gradovi",
            "📏 Visoki detalji (zoom 13-16) - Ulice",
            "🔬 Veoma visoki detalji (zoom 16-18) - Zgrade"
        )

        AlertDialog.Builder(this)
            .setTitle("🎚️ Nivo Detalja")
            .setItems(zoomLevels) { dialog, which ->
                val (minZoom, maxZoom) = when (which) {
                    0 -> 8 to 10
                    1 -> 10 to 13
                    2 -> 13 to 16
                    3 -> 16 to 18
                    else -> 12 to 15
                }

                // DODAJ IZBOR TIPA MAPE
                val mapTypes = arrayOf("🛰️ Satelitska", "🗺️ Standardna")

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("🗂️ Tip Mape")
                    .setSingleChoiceItems(mapTypes, 0, null)
                    .setPositiveButton("📥 Preuzmi") { innerDialog, innerWhich ->
                        val isSatellite = (innerDialog as AlertDialog).listView.checkedItemPosition == 0
                        val regionName = "Custom ${getRegionName(binding.mapView.boundingBox)}"

                        if (isSatellite) {
                            downloadSatelliteTilesForRegion(binding.mapView.boundingBox, minZoom, maxZoom, "$regionName Satelitski")
                        } else {
                            downloadTilesForRegion(binding.mapView.boundingBox, minZoom, maxZoom, "$regionName Standardni")
                        }
                    }
                    .setNegativeButton("❌ Otkaži", null)
                    .show()
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }
    private fun showOfflineMapReadyDialog() {
        AlertDialog.Builder(this)
            .setTitle("🎉 Offline Mapa Spremana!")
            .setMessage("""
            Srbija mapa je uspešno preuzeta!
            
            Sada možete koristiti mapu BEZ interneta.
            
            🌐 Online mape: bogatije informacije
            📱 Offline mape: brže, pouzdanije
            
            Želite li preći na offline mapu?
        """.trimIndent())
            .setPositiveButton("✅ Koristi Offline") { dialog, which ->
                switchToOfflineMap()
            }
            .setNegativeButton("🌐 Ostani Online") { dialog, which ->
                // Ostani na online mapi
            }
            .show()
    }

    private fun switchToOfflineMap() {
        try {
            // PROVERI DA LI POSTOJE OFFLINE TILE-OVI
            val metadataDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "offline_regions")
            val regionFiles = metadataDir.listFiles { file -> file.name.endsWith(".json") }

            if (!metadataDir.exists() || regionFiles.isNullOrEmpty()) {
                Toast.makeText(this, "?? Nema preuzetih offline mapa", Toast.LENGTH_LONG).show()
                downloadOfflineMap()
                return
            }

            // PRIKAŽI LISTU REGIONA SA PROVEROM DOSTUPNOSTI
            val availableRegions = mutableListOf<String>()
            val regionNames = regionFiles.mapNotNull { file ->
                try {
                    val metadata = Gson().fromJson(file.readText(), Map::class.java)
                    val regionName = metadata["regionName"] as String
                    if (checkOfflineTilesAvailable(regionName)) {
                        availableRegions.add("??? $regionName")
                        regionName
                    } else {
                        "❌ $regionName (nema tile-ova)"
                        null
                    }
                } catch (e: Exception) {
                    "? Nevažeća mapa"
                    null
                }
            }.toTypedArray()

            if (availableRegions.isEmpty()) {
                Toast.makeText(this, "?? Nema dostupnih offline tile-ova", Toast.LENGTH_LONG).show()
                downloadOfflineMap()
                return
            }

            AlertDialog.Builder(this)
                .setTitle("?? Dostupne Offline Mape")
                .setItems(regionNames) { dialog, which ->
                    val selectedFile = regionFiles[which]
                    try {
                        val metadata = Gson().fromJson(selectedFile.readText(), Map::class.java)
                        val regionName = metadata["regionName"] as String

                        if (regionName.contains("satelit", ignoreCase = true)) {
                            enableSatelliteOfflineMode(regionName)
                        } else {
                            enableOfflineMode(regionName)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "? Greška pri učitavanju", Toast.LENGTH_SHORT).show()
                    }
                }
                .setPositiveButton("? Preuzmi Novu") { dialog, which ->
                    downloadOfflineMap()
                }
                .setNegativeButton("? Zatvori", null)
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "? Greška: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun showOfflineStatus(regionName: String, maxZoom: Int, isSatellite: Boolean) {
        // Samo Toast, bez dodatnog koda
        val icon = if (isSatellite) "🛰️" else "🗺️"
        val type = if (isSatellite) "satelitska" else "standardna"

        Toast.makeText(
            this,
            "$icon $type offline: $regionName\nMax zoom: $maxZoom",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun deleteOfflineMaps() {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Brisanje Offline Mapa")
            .setMessage("Da li ste sigurni da želite da obrišete sve offline mape? (~50MB)")
            .setPositiveButton("✅ Obriši") { dialog, which ->
                // SIMULIRANO BRISANJE
                Toast.makeText(this, "🗑️ Offline mape obrisane", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun showOfflineMapsInfo() {
        AlertDialog.Builder(this)
            .setTitle("ℹ️ O Offline Mapama")
            .setMessage("""
            🗺️ OFFLINE MAPE
            
            Prednosti:
            📱 Radi bez interneta
            ⚡ Brži prikaz
            💰 Ušteda protoka
            🏞️ Pouzdano u prirodi
            
            Ograničenja:
            🔄 Ažurira se samo ručno
            📍 Manje detalja nego online
            💾 Zauzima prostor
            
            Savet: Preuzmite mape pre putovanja!
        """.trimIndent())
            .setPositiveButton("✅ Razumem", null)
            .show()
    }

    private fun setOsmTopoMap() {
        try {
            // Pokušaj sa OpenTopo, ako ne uspe, koristi fallback
            binding.mapView.setTileSource(TileSourceFactory.OpenTopo)
            binding.btnMapType.setBackgroundResource(R.drawable.button_dark_blue_accent)
            Toast.makeText(this, "🏔️ OSM Topo - Planine i reljef", Toast.LENGTH_LONG).show()
            binding.mapView.invalidate()
        } catch (e: Exception) {
            setHighQualityStandardMap()
            Toast.makeText(this, "Topo mapa nedostupna", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setOsmArtMap() {
        try {
            // KORISTI DEFAULT STYLE ZA ČIST PRIKAZ
            binding.mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
            binding.btnMapType.setBackgroundResource(R.drawable.button_dark_blue_accent)

            // VIZUELNI EFEKTI
            binding.mapView.setBackgroundColor(Color.parseColor("#F5F5DC")) // Bež pozadina

            Toast.makeText(this, "🎨 OSM Art Style - minimalistički prikaz", Toast.LENGTH_LONG).show()
            binding.mapView.invalidate()
        } catch (e: Exception) {
            setHighQualityStandardMap()
        }
    }
    private fun setSatelliteMap() {
        try {
            // KORISTI USGS_SAT KOJI RADI
            binding.mapView.setTileSource(TileSourceFactory.USGS_SAT)
            binding.btnMapType.setBackgroundResource(R.drawable.button_dark_blue_accent)

            // PODEŠAVANJA ZA BOLJI PRIKAZ
            binding.mapView.maxZoomLevel = 23.0  // Limit zoom za satelitsku
            binding.mapView.controller.setZoom(10.0)  // Automatski zoom out

            Toast.makeText(this, "🛰️ Satelitski prikaz - koristite za pregled terena", Toast.LENGTH_LONG).show()
            binding.mapView.invalidate()
        } catch (e: Exception) {
            Log.e("SatelliteMap", "Satelitska mapa nije dostupna: ${e.message}")
            setHighQualityStandardMap()
            Toast.makeText(this, "🌍 Satelitski prizak trenutno nedostupan", Toast.LENGTH_SHORT).show()
        }
    }


    private fun setOsmCycleMap() {
        try {
            // POKUŠAJ SA DOSTUPNIM CYCLE SOURCE-OVIMA
            val cycleSources = arrayOf(
                "https://tile.thunderforest.com/cycle/{z}/{x}/{y}.png?apikey=your_api_key",
                "https://a.tile.opencyclemap.org/cycle/{z}/{x}/{y}.png",
                "https://tile.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png"
            )

            var success = false
            for (sourceUrl in cycleSources) {
                try {
                    val source = org.osmdroid.tileprovider.tilesource.XYTileSource(
                        "CycleMap",
                        0, 18, 256, ".png",
                        arrayOf(sourceUrl)
                    )
                    binding.mapView.setTileSource(source)
                    success = true
                    break
                } catch (e: Exception) {
                    continue
                }
            }

            if (success) {
                binding.btnMapType.setBackgroundResource(R.drawable.button_dark_blue_accent)
                Toast.makeText(this, "🚴 OSM Biciklističke Staze", Toast.LENGTH_LONG).show()
                binding.mapView.invalidate()
            } else {
                throw Exception("Nijedan cycle source nije dostupan")
            }

        } catch (e: Exception) {
            Log.e("CycleMap", "Cycle mape nedostupne: ${e.message}")
            setHighQualityStandardMap()
            Toast.makeText(this, "🚴 Biciklističke mape trenutno nedostupne", Toast.LENGTH_LONG).show()
        }
    }
    private fun setOsmMaxDetail() {
        try {
            binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
            binding.btnMapType.setBackgroundResource(R.drawable.button_dark_blue_accent)

            // POVEĆAJ max zoom za više detalja
            binding.mapView.maxZoomLevel = 22.0
            binding.mapView.controller.setZoom(18.0) // Automatski zumiraj

            // Vizuelni efekat - tamnija pozadina
            binding.mapView.setBackgroundColor(Color.parseColor("#2E2E2E"))

            Toast.makeText(this, "🔍 OSM Max detalja (Zoom 22x)", Toast.LENGTH_LONG).show()
            binding.mapView.invalidate()
        } catch (e: Exception) {
            Toast.makeText(this, "Greška pri detaljnoj mapi", Toast.LENGTH_SHORT).show()
            setStandardMap()
        }
    }

    private fun setOsmFast() {
        try {
            binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
            binding.btnMapType.setBackgroundResource(R.drawable.button_dark_blue_accent)

            // SMANJI max zoom za brzinu
            binding.mapView.maxZoomLevel = 21.0
            binding.mapView.controller.setZoom(14.0) // Smanji zoom

            // Vizuelni efekat - svetlija pozadina
            binding.mapView.setBackgroundColor(Color.WHITE)

            Toast.makeText(this, "⚡ OSM Brza (Zoom 16x)", Toast.LENGTH_LONG).show()
            binding.mapView.invalidate()
        } catch (e: Exception) {
            Toast.makeText(this, "Greška pri brzoj mapi", Toast.LENGTH_SHORT).show()
            setStandardMap()
        }
    }

    private fun setOsmNoLabels() {
        try {
            // Koristi OSM bez labela (čistiji prikaz)
            binding.mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
            binding.btnMapType.setBackgroundResource(R.drawable.button_dark_blue_accent)

            binding.mapView.maxZoomLevel = 19.0

            // Vizuelni efekat - sepia ton
            binding.mapView.setBackgroundColor(Color.parseColor("#F5F5DC"))

            Toast.makeText(this, "🎨 OSM Bez naziva", Toast.LENGTH_LONG).show()
            binding.mapView.invalidate()
        } catch (e: Exception) {
            Toast.makeText(this, "Greška pri mapi bez naziva", Toast.LENGTH_SHORT).show()
            setStandardMap()
        }
    }

    // DODAJ OVU FUNKCIJU - ONA JE NEDOSTAJALA
    // U MainActivity.kt zameni postojeću setOsmTransport() funkciju:

    private fun setOsmTransport() {
        try {
            // POKUŠAJ SA DOSTUPNIM TRANSPORT SOURCE-OVIMA
            val transportSources = arrayOf(
                TileSourceFactory.PUBLIC_TRANSPORT,
                org.osmdroid.tileprovider.tilesource.XYTileSource(
                    "Transport", 0, 18, 256, ".png",
                    arrayOf("https://tile.thunderforest.com/transport/{z}/{x}/{y}.png?apikey=your_api_key")
                ),
                org.osmdroid.tileprovider.tilesource.XYTileSource(
                    "OSM_Transport", 0, 18, 256, ".png",
                    arrayOf("https://a.tile.opencyclemap.org/transport/{z}/{x}/{y}.png")
                )
            )

            var success = false
            for (source in transportSources) {
                try {
                    binding.mapView.setTileSource(source)
                    success = true
                    break
                } catch (e: Exception) {
                    continue
                }
            }

            if (success) {
                binding.btnMapType.setBackgroundResource(R.drawable.button_dark_blue_accent)
                Toast.makeText(this, "🚗 Transport/Saobraćaj", Toast.LENGTH_LONG).show()
                binding.mapView.invalidate()
            } else {
                throw Exception("Nijedan transport source nije dostupan")
            }

        } catch (e: Exception) {
            Log.e("TransportMap", "Transport mape nedostupne: ${e.message}")
            setHighQualityStandardMap()
            Toast.makeText(this, "🚗 Transport mape trenutno nedostupne", Toast.LENGTH_LONG).show()
        }
    }

    private fun setHighQualityMapSource() {
        try {
            // EKSPERIMENTIŠITE SA RAZLIČITIM SOURCE-OVIMA ZA BOLJI QUALITY:
            val highQualitySources = arrayOf(
                TileSourceFactory.MAPNIK to "🗺️ OSM Standard (HQ)",
                TileSourceFactory.OpenTopo to "🏔️ OSM Topo",
                TileSourceFactory.USGS_TOPO to "🗻 USGS Topo",
                TileSourceFactory.USGS_SAT to "🛰️ USGS Satelit"
            )

            // Prvo probaj MAPNIK (najpouzdaniji)
            binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
            Log.d("MapSource", "Korišćen MAPNIK source")

        } catch (e: Exception) {
            Log.e("MapSource", "HQ source nije dostupan: ${e.message}")
            binding.mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        }
    }
    // DODAJ I OVU FUNKCIJU ZA PREVIEW

    private fun showOsmPreview() {
        val previewText = """
    🗺️ OSM PREVIEW STILOVI:
    
    🔍 MAX DETALJA (22x zoom):
    • Vidite pojedinačne zgrade
    • Detaljne ulice
    • Pojačana oštrina
    • Tamnija pozadina
    
    ⚡ BRZA (16x zoom):
    • Brže učitavanje
    • Manje detalja  
    • Svetlija pozadina
    • Idealno za vožnju
    
    🎨 BEZ NAZIVA:
    • Čist prikaz bez labela
    • Sepia pozadina
    • Fokus na geometriju
    
    🚗 TRANSPORT:
    • Istaknute saobraćajnice
    • Javni prevoz
    • Biciklističke staze
""".trimIndent()

        AlertDialog.Builder(this)
            .setTitle("OSM Stilovi - Preview")
            .setMessage(previewText)
            .setPositiveButton("Prikaži stilove") { dialog, which ->
                showMapTypeDialog()
            }
            .setNegativeButton("Zatvori", null)
            .show()
    }
    private fun showLayerControlDialog() {
        val layers = arrayOf(
            "🗺️ Osnovna mapa",
            "📍 Moja lokacija",
            "🛣️ Trenutna ruta",
            "📌 Sačuvane tačke"
        )

        val checkedItems = booleanArrayOf(true, true, true, true)

        AlertDialog.Builder(this)
            .setTitle("Upravljaj slojevima")
            .setMultiChoiceItems(layers, checkedItems) { dialog, which, isChecked ->
                when (which) {
                    0 -> toggleBaseMap(isChecked)
                    1 -> toggleMyLocation(isChecked)
                    2 -> toggleCurrentRoute(isChecked)
                    3 -> toggleSavedPoints(isChecked)
                }
            }
            .setPositiveButton("Sačuvaj", null)
            .show()
    }

    private fun toggleBaseMap(show: Boolean) {
        if (show) {
            binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        } else {
            // Ako je mapa isključena, prikaži praznu mapu
            binding.mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        }
        binding.mapView.invalidate()
    }

    private fun toggleMyLocation(show: Boolean) {
        if (show) {
            locationOverlay.enableMyLocation()
            locationOverlay.enableFollowLocation()
        } else {
            locationOverlay.disableMyLocation()
            locationOverlay.disableFollowLocation()
        }
        binding.mapView.invalidate()
    }

    private fun measureDistanceToPoint() {
        Toast.makeText(this, "Izaberite tačku na mapi za merenje rastojanja", Toast.LENGTH_LONG).show()

        // Privremeno omogući dodavanje tački za merenje
        val originalPointMode = isPointMode
        isPointMode = true

        binding.mapView.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: org.osmdroid.views.MapView?): Boolean {
                if (mapView != null && e != null) {
                    val tappedPoint = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                    val myLocation = locationOverlay.myLocation

                    if (myLocation != null) {
                        val distance = calculateDistance(
                            myLocation.latitude, myLocation.longitude,
                            tappedPoint.latitude, tappedPoint.longitude
                        )

                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Rastojanje do tačke")
                            .setMessage("Rastojanje od vaše lokacije do izabrane tačke: ${formatDistance(distance)}")
                            .setPositiveButton("U redu", null)
                            .show()
                    } else {
                        Toast.makeText(this@MainActivity, "Lokacija nije dostupna", Toast.LENGTH_SHORT).show()
                    }

                    // Ukloni overlay nakon korišćenja
                    binding.mapView.overlays.remove(this)
                    isPointMode = originalPointMode
                    return true
                }
                return false
            }
        })
    }


    private fun showTrackingStatistics() {
        if (savedRoutes.isEmpty()) {
            Toast.makeText(this, "Nema sačuvanih ruta za statistiku", Toast.LENGTH_SHORT).show()
            return
        }

        val totalRoutes = savedRoutes.size
        val totalDistance = savedRoutes.sumOf { it.distance }
        val totalDuration = savedRoutes.sumOf { it.duration } / 1000 / 60 // u minutima
        val longestRoute = savedRoutes.maxByOrNull { it.distance }
        val averageDistance = totalDistance / totalRoutes

        val stats = """
        📊 Statistika praćenja:
        
        📈 Ukupno ruta: $totalRoutes
        🛣️ Ukupna udaljenost: ${formatDistance(totalDistance)}
        ⏱️ Ukupno vreme: ${totalDuration} minuta
        📏 Prosečna dužina: ${formatDistance(averageDistance)}
        
        🏆 Najduža ruta: 
        ${longestRoute?.name ?: "N/A"}
        ${formatDistance(longestRoute?.distance ?: 0.0)}
        
        🎯 Aktivno praćenje: ${if (isTracking) "DA" else "NE"}
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Statistika praćenja")
            .setMessage(stats)
            .setPositiveButton("U redu", null)
            .show()
    }

    private fun toggleCurrentRoute(show: Boolean) {
        if (!show) {
            polylines.forEach { binding.mapView.overlays.remove(it) }
            polylines.clear()
        } else if (isTracking && routePoints.size >= 2) {
            drawRouteOnMap()
        }
        binding.mapView.invalidate()
    }

    private fun toggleSavedPoints(show: Boolean) {
        pointMarkers.values.forEach { marker ->
            binding.mapView.overlays.remove(marker)
        }

        if (show) {
            pointMarkers.values.forEach { marker ->
                binding.mapView.overlays.add(marker)
            }
        }
        binding.mapView.invalidate()
    }

    private fun setStandardMap() {
        // Koristi default OSM koji je najbrži
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.btnMapType.setBackgroundResource(R.drawable.button_dark_blue)
        Toast.makeText(this, "Standardna mapa", Toast.LENGTH_SHORT).show()
        binding.mapView.invalidate()
    }

    private fun setMapTileSourceWithFallback(source: XYTileSource, fallback: org.osmdroid.tileprovider.tilesource.ITileSource = TileSourceFactory.MAPNIK) {
        try {
            binding.mapView.setTileSource(source)
            binding.mapView.invalidate()
        } catch (e: Exception) {
            // ISPRAVLJENA LINIJA: koristi name() umesto name
            Log.w("MainActivity", "Tile source ${source.name()} failed, using fallback")
            binding.mapView.setTileSource(fallback)
            binding.mapView.invalidate()
        }
    }
    private fun setMapTypeWithProgress(mapType: String, source: org.osmdroid.tileprovider.tilesource.ITileSource) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Učitavanje mape")
            .setMessage("Molimo sačekajte...")
            .setCancelable(false)
            .create()

        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                delay(500) // Daj vremena za prikaz progress dialoga

                withContext(Dispatchers.Main) {
                    binding.mapView.setTileSource(source)
                    binding.btnMapType.setBackgroundResource(R.drawable.button_dark_blue_accent)
                    Toast.makeText(this@MainActivity, mapType, Toast.LENGTH_SHORT).show()
                    binding.mapView.invalidate()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Greška: ${e.message}", Toast.LENGTH_SHORT).show()
                    setStandardMap()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                }
            }
        }
    }

    private fun zoomIn() {
        val currentZoom = binding.mapView.zoomLevelDouble
        val newZoom = currentZoom + 1.0

        if (newZoom <= binding.mapView.maxZoomLevel) {
            // ANIMIRANI ZOOM SA BOLJIM TIMING-OM
            binding.mapView.controller.animateTo(
                binding.mapView.mapCenter,
                newZoom,
                300L  // duže trajanje za glatkiji zoom
            )

            // PRIKAZI INFORMACIJU O ZOOM LEVEL-U
            showZoomInfo(newZoom)

            // OPTIMIZACIJA ZA VISOKI ZOOM
            if (newZoom > 18.0) {
                optimizeForHighZoom(newZoom)
            }
        } else {
            Toast.makeText(this, "🔍 Maksimalno uvećanje dostignuto!", Toast.LENGTH_SHORT).show()
        }
    }
    private fun zoomOut() {
        val currentZoom = binding.mapView.zoomLevelDouble
        val newZoom = currentZoom - 1.0

        if (newZoom >= binding.mapView.minZoomLevel) {
            binding.mapView.controller.animateTo(
                binding.mapView.mapCenter,
                newZoom,
                300L
            )
            showZoomInfo(newZoom)
        } else {
            Toast.makeText(this, "🔍 Minimalno uvećanje!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun optimizeForHighZoom(zoom: Double) {
        if (zoom > 18.0) {
            // NA VISOKOM ZOOM-U: povećaj cache i optimizuj
            Configuration.getInstance().cacheMapTileCount = 5000
            binding.mapView.setUseDataConnection(true)

            // FORSIRAJ REFRESH TILE-OVA
            binding.mapView.invalidate()
        }
    }
    private fun centerOnMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissions()
            return
        }

        // POKUŠAJTE SA POSLEDNJOM POZNATOM LOKACIJOM PRVO
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    if (it.accuracy < 50.0f) {
                        val currentLocation = GeoPoint(it.latitude, it.longitude)
                        binding.mapView.controller.animateTo(currentLocation)
                        binding.mapView.controller.setZoom(18.0)

                        // PRIKAŽI MARKER
                        showAccurateLocationMarker(currentLocation, it)

                        // OTVORI DIALOG AUTOMATSKI - OVO OSTAVLJAMO!
                        showMyLocationDetails() // OVO OSTAJE!

                        lastLocation = it
                        Log.d("Location", "Centriranje uspešno: ${it.accuracy}m")
                    } else {
                        // AKO NIJE TAČNA, POKAŽI DIALOG SA UPOTREBOM
                        val currentLocation = GeoPoint(it.latitude, it.longitude)
                        binding.mapView.controller.animateTo(currentLocation)
                        binding.mapView.controller.setZoom(16.0)
                        showAccurateLocationMarker(currentLocation, it)
                        showMyLocationDetails() // OVO OSTAJE!
                    }
                } ?: run {
                    // AKO NEMA LOKACIJE, POKAŽI DIALOG
                    Toast.makeText(this, "?? Tražim lokaciju...", Toast.LENGTH_SHORT).show()
                    startQuickLocationUpdate()
                }
            }
            .addOnFailureListener { e ->
                Log.e("Location", "Greška pri dobijanju lokacije: ${e.message}")
                Toast.makeText(this, "?? Tražim lokaciju...", Toast.LENGTH_SHORT).show()
                startQuickLocationUpdate()
            }
    }
    private fun setupPointOfInterestMode() {
        binding.fabAddPoint.setOnClickListener {
            togglePointMode()
        }
    }
    private fun togglePointMode() {
        isPointMode = !isPointMode
        if (isPointMode) {
            binding.fabAddPoint.setBackgroundResource(R.drawable.button_dark_blue)
            Toast.makeText(this, "Režim dodavanja tačaka - klikni na mapu", Toast.LENGTH_SHORT).show()
        } else {
            binding.fabAddPoint.setBackgroundResource(R.drawable.button_dark_blue_accent)
            Toast.makeText(this, "Režim dodavanja tačaka isključen", Toast.LENGTH_SHORT).show()
        }
    }
    private fun resetZoom() {
        try {
            val myLocation = locationOverlay.myLocation
            if (myLocation != null) {
                binding.mapView.controller.animateTo(myLocation, 16.0, 500L)
                Toast.makeText(this, "Zoom resetovan na lokaciju", Toast.LENGTH_SHORT).show()
            } else {
                val nis = GeoPoint(43.3209, 21.8958)
                binding.mapView.controller.animateTo(nis, 13.0, 500L)
                Toast.makeText(this, "Zoom resetovan na Niš", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Greška pri resetovanju zooma", Toast.LENGTH_SHORT).show()
        }
    }
    private fun loadPointsOfInterest() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val points = app.pointRepository.getUserPoints(getCurrentUserId())

                runOnUiThread {
                    // 1. OČISTI POSTOJEĆE TAČKE
                    pointsOfInterest.clear()
                    pointsOfInterest.addAll(points)

                    // 2. UKLONI SVE POSTOJEĆE MARKERE
                    pointMarkers.values.forEach { marker ->
                        binding.mapView.overlays.remove(marker)
                    }
                    pointMarkers.clear()

                    // 3. DODAJ NOVE MARKERE
                    points.forEach { point ->
                        addMarkerToMap(
                            GeoPoint(point.latitude, point.longitude),
                            point.name,
                            point.id
                        )
                    }

                    // 4. OSVEŽI MAPU
                    binding.mapView.invalidate()

                    Log.d("PointsLoad", "✅ Učitano ${points.size} tačaka")
                }
            } catch (e: Exception) {
                Log.e("PointsLoad", "❌ Greška pri učitavanju tačaka: ${e.message}")
            }
        }
    }
    private fun loadSavedRoutes() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as jovannedeljkovic.gps_tracker_pro.App
                savedRoutes = app.routeRepository.getUserRoutes(getCurrentUserId()).toMutableList()
            } catch (e: Exception) {
                Log.d("MainActivity", "Nema sačuvanih ruta: ${e.message}")
            }
        }
    }
    private fun showUserInfoDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val userEmail = getCurrentUserFromPrefs()
                val user = app.userRepository.getUserByEmail(userEmail)

                if (user != null) {
                    val benefits = FeatureManager.getRoleBenefits(user)
                    val benefitsText = benefits.joinToString("\n")

                    runOnUiThread {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("👤 Korisnički nalog")
                            .setMessage(
                                """
                            Email: ${user.email}
                            Uloga: ${FeatureManager.getUserRoleDisplayName(user)}
                            
                            Dostupne funkcionalnosti:
                            $benefitsText
                            """.trimIndent()
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Greška pri prikazu korisničkih informacija: ${e.message}")
            }
        }
    }



    // NOTIFICATION METHODS
    private fun startTrackingNotification() {
        notificationHelper.showTrackingNotification(
            formatDistance(totalDistance),
            "${String.format("%.1f", currentSpeed)} km/h"
        )
    }

    private fun updateTrackingNotification() {
        notificationHelper.updateTrackingNotification(
            formatDistance(totalDistance),
            "${String.format("%.1f", currentSpeed)} km/h"
        )
    }

    private fun stopTrackingNotification() {
        notificationHelper.cancelTrackingNotification()
    }

    private fun toggleTrackingMode() {
        try {
            // Trenutni režimi: "self" (sopstveno praćenje), "other" (praćenje drugog uređaja)
            trackingMode = if (trackingMode == "self") "other" else "self"

            when (trackingMode) {
                "self" -> {
                    binding.btnTrackingMode.text = " Pratim sebe"
                    binding.btnTrackingMode.setBackgroundResource(R.drawable.button_dark_blue)
                    Toast.makeText(this, "👤 Režim: Praćenje sebe", Toast.LENGTH_SHORT).show()
                    Log.d("TrackingMode", "Prebačen na režim praćenja sebe")
                }
                "other" -> {
                    binding.btnTrackingMode.text = "👤👤 Pratim Drugog"
                    binding.btnTrackingMode.setBackgroundResource(R.drawable.button_dark_blue_accent)
                    Toast.makeText(this, "\uD83D\uDC64\uD83D\uDC64 Režim: Praćenje drugog uređaja", Toast.LENGTH_SHORT).show()
                    Log.d("TrackingMode", "Prebačen na režim praćenja drugog")
                }
            }

            // Ažuriraj UI prema režimu
            updateTrackingModeUI()

        } catch (e: Exception) {
            Log.e("TrackingMode", "Greška pri promeni režima: ${e.message}")
            Toast.makeText(this, "Greška pri promeni režima", Toast.LENGTH_SHORT).show()
        }
    }
    private fun updateTrackingModeUI() {
        when (trackingMode) {
            "self" -> {
                // Režim praćenja sebe - prikaži sve kontrole
                binding.btnStartTracking.visibility = View.VISIBLE
                binding.btnStopTracking.visibility = View.VISIBLE
                binding.trackingPanel.visibility = if (isTracking) View.VISIBLE else View.GONE
            }
            "other" -> {
                // Režim praćenja drugog - sakrij nepotrebne kontrole
                if (isTracking) {
                    stopTracking() // Zaustavi sopstveno praćenje ako je aktivno
                }
                binding.btnStartTracking.visibility = View.GONE
                binding.btnStopTracking.visibility = View.GONE
                binding.trackingPanel.visibility = View.GONE

                // OVDE MOŽETE DODATI LOGIKU ZA PRAĆENJE DRUGOG UREĐAJA
                //showOtherDeviceTrackingDialog()
            }
        }
    }
    private fun openGoogleMaps() {
        try {
            val location = locationOverlay.myLocation
            if (location != null) {
                val uri = "geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                intent.setPackage("com.google.android.apps.maps")
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    val webUri = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri))
                    startActivity(webIntent)
                }
            } else {
                Toast.makeText(this, "Lokacija nije dostupna", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Greška pri otvaranju mapa: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showToolsDialog() {
        val tools = arrayOf(
            "🗺️ OSM Preview Stilovi",
            "📍 Upravljaj tačkama",
            "📤 Eksport podataka",
            "📥 Uvezi podatke",
            "🧭 Kompas On/Off",
            "📏 Izmeri rastojanje",
            "📊 Statistika praćenja",
            "🔄 Reset zoom",
            "🔁 Auto-rotate mapa"
        )

        AlertDialog.Builder(this)
            .setTitle("🛠️ Alatke za Mapu")
            .setItems(tools) { dialog, which ->
                when (which) {
                    0 -> showOsmPreview()
                    1 -> showPointsManagementDialog()
                    2 -> exportRouteData()
                    3 -> showImportOptions()
                    4 -> toggleCompass()
                    5 -> measureDistanceToPoint()
                    6 -> showTrackingStatistics()
                    7 -> resetZoom()
                    8 -> toggleMapRotation()
                }
            }
            .setNegativeButton("✖ Zatvori", null)
            .show()
    }
    private fun showImportOptions() {
        val importOptions = arrayOf(
            "🗺️ Uvezi rutu (GPX) - File Picker",
            "📍 Uvezi tačke (GPX)",
            "📍 Uvezi tačke (CSV)",
            "🔄 Uvezi sve iz backup-a",
            "ℹ️ Pomoc pri uvozu"
        )

        AlertDialog.Builder(this)
            .setTitle("📥 Uvoz podataka")
            .setItems(importOptions) { dialog, which ->
                when (which) {
                    0 -> importRouteFromFile()           // File Picker za rute
                    1 -> importPointsFromGpx()           // GPX za tačke
                    2 -> importPointsFromFile()          // CSV za tačke
                    3 -> importFromBackup()              // Backup
                    4 -> showImportHelpDialog()          // Pomoc
                }
            }
            .setNegativeButton("❌ Zatvori", null)
            .show()
    }
    private fun importPointsFromGpx() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/gpx+xml",
                    "application/xml",
                    "text/xml"
                ))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivityForResult(intent, IMPORT_GPX_POINTS_REQUEST_CODE)
            Toast.makeText(this, "🔍 Izaberite GPX fajl sa tačkama", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("Import", "Greška pri otvaranju file pickera: ${e.message}")
            Toast.makeText(this, "❌ Greška: ${e.message}", Toast.LENGTH_SHORT).show()

            // FALLBACK: Pokaži opciju za Download folder
            showImportFallbackForPoints()
        }
    }

    private fun importFromBackup() {
        Log.d("ImportDebug", "🎯 POZIVAM importFromBackup()")
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/zip",
                    "application/json"
                ))
            }

            startActivityForResult(intent, IMPORT_BACKUP_REQUEST_CODE)
            Toast.makeText(this, "🔍 Izaberite backup fajl", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Greška: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun importPointsFromFile() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "text/csv",
                    "text/comma-separated-values",
                    "text/plain"
                ))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivityForResult(intent, IMPORT_CSV_REQUEST_CODE)
            Toast.makeText(this, "🔍 Izaberite CSV fajl sa tačkama", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("Import", "Greška pri otvaranju file pickera: ${e.message}")
            Toast.makeText(this, "❌ Greška: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun showPointsManagementDialog() {
        loadPointsOfInterest()

        if (pointsOfInterest.isEmpty()) {
            Toast.makeText(this, "📭 Nema tačaka za upravljanje", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this).apply {
            setContentView(R.layout.dialog_points_list)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val recyclerView = dialog.findViewById<RecyclerView>(R.id.recyclerViewPoints)
        val tvPointsCount = dialog.findViewById<TextView>(R.id.tvPointsCount)
        val btnSelectAll = dialog.findViewById<Button>(R.id.btnSelectAll)
        val btnDeleteSelected = dialog.findViewById<Button>(R.id.btnDeleteSelected)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Kreiram adapter sa posebnom logikom za brojanje selektovanih
        var selectedCount = 0

        val adapter = PointsAdapter(
            points = pointsOfInterest,
            onItemClick = { point: PointOfInterest ->
                dialog.dismiss()
                showPointOptionsDialog(point.id, point.name, GeoPoint(point.latitude, point.longitude))
            },
            onCheckboxChange = { position: Int, isChecked: Boolean ->
                // Ažuriraj brojač direktno
                selectedCount = if (isChecked) selectedCount + 1 else selectedCount - 1
                tvPointsCount.text = "$selectedCount/${pointsOfInterest.size} selektovano"
            }
        )

        recyclerView.adapter = adapter
        tvPointsCount.text = "0/${pointsOfInterest.size} selektovano"

        var allSelected = false
        btnSelectAll.setOnClickListener {
            allSelected = !allSelected
            adapter.selectAll(allSelected)
            btnSelectAll.text = if (allSelected) "❌ Deselektuj sve" else "☑️ Selektuj sve"

            // Ažuriraj brojač
            selectedCount = if (allSelected) pointsOfInterest.size else 0
            tvPointsCount.text = "$selectedCount/${pointsOfInterest.size} selektovano"
        }

        btnDeleteSelected.setOnClickListener {
            val selectedPoints = adapter.getSelectedPoints()
            if (selectedPoints.isNotEmpty()) {
                dialog.dismiss()
                showBulkDeleteConfirmation(selectedPoints)
            } else {
                Toast.makeText(this, "⚠️ Nijedna tačka nije selektovana", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun showAllRoutesDialog() {
        if (savedRoutes.isEmpty()) {
            Toast.makeText(this, "❌ Nema sačuvanih ruta", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this).apply {
            setContentView(R.layout.dialog_routes_list)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val recyclerView = dialog.findViewById<RecyclerView>(R.id.recyclerViewRoutes)
        val tvRoutesCount = dialog.findViewById<TextView>(R.id.tvRoutesCount)
        val statisticsContainer = dialog.findViewById<LinearLayout>(R.id.statisticsContainer)
        val tvTotalDistance = dialog.findViewById<TextView>(R.id.tvTotalDistance)
        val tvTotalTime = dialog.findViewById<TextView>(R.id.tvTotalTime)
        val btnShowStats = dialog.findViewById<Button>(R.id.btnShowStats)
        val btnExport = dialog.findViewById<Button>(R.id.btnExport)
        val btnClose = dialog.findViewById<Button>(R.id.btnClose)

        recyclerView.layoutManager = LinearLayoutManager(this)

        val adapter = RoutesAdapter(
            routes = savedRoutes.sortedByDescending { it.startTime },
            onRouteClick = { route: Route ->
                // Klik na rutu - prikaži detalje
                dialog.dismiss()
                showAdvancedRouteOptions(route)
            },
            onShowOnMap = { route: Route ->
                // Prikaži na mapi
                dialog.dismiss()
                displayRouteOnMap(route)
            },
            onExportRoute = { route: Route ->
                // Izvezi rutu
                dialog.dismiss()
                // Ovdje dodajte logiku za eksport pojedinačne rute
                Toast.makeText(this, "📤 Izvoz rute: ${route.name}", Toast.LENGTH_SHORT).show()
            },
            onDeleteRoute = { route: Route ->
                // Obriši rutu
                dialog.dismiss()
                deleteRoute(route)
            }
        )

        recyclerView.adapter = adapter
        tvRoutesCount.text = "${savedRoutes.size} ruta"

        // Statistics
        var statsVisible = false
        btnShowStats.setOnClickListener {
            statsVisible = !statsVisible
            statisticsContainer.visibility = if (statsVisible) View.VISIBLE else View.GONE
            btnShowStats.text = if (statsVisible) "📊 Sakrij" else "📊 Statistika"

            if (statsVisible) {
                val totalDistance = adapter.calculateTotalDistance()
                val totalTime = adapter.calculateTotalTime() / 1000 / 60

                tvTotalDistance.text = "Ukupna udaljenost: ${formatDistance(totalDistance)}"
                tvTotalTime.text = "Ukupno vreme: ${totalTime} min"
            }
        }

        // Export all
        btnExport.setOnClickListener {
            dialog.dismiss()
            showExportRoutesOptions()
        }

        // Close
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun showBulkDeleteConfirmation(pointsToDelete: List<PointOfInterest>) {
        val pointsList = pointsToDelete.joinToString("\n") { "📍 ${it.name}" }
        AlertDialog.Builder(this)
            .setTitle("Brisanje tačaka")
            .setMessage("Da li ste sigurni da želite da obrišete ${pointsToDelete.size} tačku/a?\n\n$pointsList")
            .setPositiveButton("✅ Obriši sve") { dialog, which ->
                deleteMultiplePoints(pointsToDelete)
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun deleteMultiplePoints(pointsToDelete: List<PointOfInterest>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as jovannedeljkovic.gps_tracker_pro.App
                pointsToDelete.forEach { point ->
                    app.pointRepository.deletePoint(point)
                }
                runOnUiThread {
                    pointsToDelete.forEach { point ->
                        pointMarkers[point.id]?.let { marker ->
                            binding.mapView.overlays.remove(marker)
                            pointMarkers.remove(point.id)
                        }
                        pointsOfInterest.remove(point)
                    }
                    binding.mapView.invalidate()
                    Toast.makeText(this@MainActivity, "Obrisano ${pointsToDelete.size} tačaka", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška pri brisanju: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showAllPointsPreview() {
        val pointsInfo = pointsOfInterest.joinToString("\n\n") { point ->
            val distance = calculateDistanceToPoint(point)
            "📍 ${point.name}\n" +
                    "   📏 ${formatDistance(distance)} od vas\n" +
                    "   📅 ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(point.createdAt))}\n" +
                    "   🌐 ${String.format("%.6f", point.latitude)}, ${String.format("%.6f", point.longitude)}"
        }

        AlertDialog.Builder(this)
            .setTitle("Sve tačke (${pointsOfInterest.size})")
            .setMessage(pointsInfo.takeIf { it.isNotBlank() } ?: "Nema tačaka")
            .setPositiveButton("✅ U redu", null)
            .show()
    }

    private fun calculateDistanceToPoint(point: PointOfInterest): Double {
        val myLocation = locationOverlay.myLocation ?: return 0.0
        return calculateDistance(
            myLocation.latitude,
            myLocation.longitude,
            point.latitude,
            point.longitude
        )
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun showSavedRoutes() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as jovannedeljkovic.gps_tracker_pro.App
                savedRoutes = app.routeRepository.getUserRoutes(getCurrentUserId()).toMutableList()
                runOnUiThread {
                    if (savedRoutes.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Nema sačuvanih ruta", Toast.LENGTH_SHORT).show()
                    } else {
                        showAdvancedRoutesDialog()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška pri učitavanju ruta", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAdvancedRoutesDialog() {
        val routesInfo = savedRoutes.joinToString("\n\n") { route ->
            val duration = route.duration / 1000
            val minutes = duration / 60
            val seconds = duration % 60
            "🗺️ ${route.name}\n" +
                    "📏 ${formatDistance(route.distance)}\n" +
                    "⏱️ ${minutes}m ${seconds}s\n" +
                    "📅 ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(route.startTime))}"
        }

        AlertDialog.Builder(this)
            .setTitle("🗂️ Sačuvane rute (${savedRoutes.size})")
            .setMessage(routesInfo)
            .setPositiveButton("📋 Prikaži sve") { dialog, which ->
                showRoutesManagementDialog()
            }
            .setNeutralButton("🥇 Najduža ruta") { dialog, which ->
                showLongestRoute()
            }
            // DODAJ OVO NOVO DUGME ZA EKSPORT
            .setNegativeButton("📤 Eksport") { dialog, which ->
                showExportRoutesOptions()
            }
            .show()
    }
    private fun showExportRoutesOptions() {
        if (savedRoutes.isEmpty()) {
            Toast.makeText(this, "❌ Nema ruta za eksport", Toast.LENGTH_SHORT).show()
            return
        }

        val exportOptions = arrayOf(
            "🗺️ Izvezi pojedinačne rute (GPX)",
            "🗺️ Izvezi pojedinačne rute (CSV)",
            "📦 Izvezi sve rute (GPX)",
            "📦 Izvezi sve rute (CSV)",
            "❌ Otkaži"
        )

        AlertDialog.Builder(this)
            .setTitle("📤 Eksport ruta")
            .setItems(exportOptions) { dialog, which ->
                when (which) {
                    0 -> showRouteSelectionForExport("GPX")  // Pojedinačne GPX
                    1 -> showRouteSelectionForExport("CSV")  // Pojedinačne CSV
                    2 -> exportAllRoutes("GPX")             // Sve GPX
                    3 -> exportAllRoutes("CSV")             // Sve CSV
                    // 4 -> Otkaži (ne radi ništa)
                }
            }
            .setNegativeButton("❌ Zatvori", null)
            .show()
    }
    private fun showRouteSelectionForExport(format: String) {
        if (savedRoutes.isEmpty()) {
            Toast.makeText(this, "❌ Nema ruta za eksport", Toast.LENGTH_SHORT).show()
            return
        }

        val routeNames = savedRoutes.map { route ->
            val duration = route.duration / 1000 / 60
            "${route.name}\n   📏 ${formatDistance(route.distance)} • ⏱️ ${duration}min • 📅 ${formatDate(route.startTime)}"
        }.toTypedArray()

        val selectedRoutes = BooleanArray(savedRoutes.size) { false }

        AlertDialog.Builder(this)
            .setTitle("🔍 Izaberite rute za eksport ($format)")
            .setMultiChoiceItems(routeNames, selectedRoutes) { dialog, which, isChecked ->
                selectedRoutes[which] = isChecked
            }
            .setPositiveButton("📤 Eksportuj selektovane") { dialog, which ->
                val routesToExport = savedRoutes.filterIndexed { index, _ ->
                    selectedRoutes[index]
                }

                if (routesToExport.isNotEmpty()) {
                    when (format) {
                        "GPX" -> exportSelectedRoutesAsGpx(routesToExport)
                        "CSV" -> exportSelectedRoutesAsCsv(routesToExport)
                    }
                } else {
                    Toast.makeText(this, "⚠️ Nijedna ruta nije selektovana", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }
    private fun exportSelectedRoutesAsGpx(routes: List<Route>) {
        if (!checkStoragePermissionsForExport()) {
            Toast.makeText(this, "🔐 Potrebne su dozvole za pristup skladištu", Toast.LENGTH_LONG).show()
            requestStoragePermissions()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("🗺️ Eksport ruta u GPX...")
            .setMessage("Pripremam ${routes.size} rutu/a...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val allPoints = mutableListOf<LocationPoint>()

                routes.forEach { route ->
                    val points = app.routeRepository.getRoutePoints(route.id)
                    allPoints.addAll(points)
                }

                if (allPoints.isEmpty()) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@MainActivity, "❌ Nema podataka za eksport", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val gpxContent = generateMultipleRoutesGPX(routes, allPoints)
                val fileName = if (routes.size == 1) {
                    "ruta_${routes.first().name.replace(" ", "_")}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.gpx"
                } else {
                    "multiple_routes_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.gpx"
                }

                // KLJUČNA PROMENA: Koristi Download folder
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val gpxFile = File(downloadsDir, fileName)

                FileWriter(gpxFile).use { writer ->
                    writer.write(gpxContent)
                }

                runOnUiThread {
                    progressDialog.dismiss()

                    if (gpxFile.exists()) {
                        val fileSize = String.format("%.1f", gpxFile.length() / 1024.0)
                        Toast.makeText(
                            this@MainActivity,
                            "✅ ${routes.size} ruta uspešno izveženo u GPX!\n📁 Download/${fileName}\n📏 ${fileSize}KB",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.d("ExportDebug", "📁 Multiple ruta sačuvana u: ${gpxFile.absolutePath}")
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Greška pri čuvanju fajla", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "❌ Greška pri GPX eksportu: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("ExportDebug", "💥 Greška pri multiple export: ${e.message}")
                }
            }
        }
    }

    private fun exportSelectedRoutesAsCsv(routes: List<Route>) {
        if (!checkStoragePermissionsForExport()) {
            Toast.makeText(this, "🔐 Potrebne su dozvole za pristup skladištu", Toast.LENGTH_LONG).show()
            requestStoragePermissions()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("📊 Eksport ruta u CSV...")
            .setMessage("Pripremam ${routes.size} rutu/a...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val fileName = if (routes.size == 1) {
                    "ruta_${routes.first().name.replace(" ", "_")}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
                } else {
                    "multiple_routes_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
                }

                // KLJUČNA PROMENA: Koristi Download folder
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val csvFile = File(downloadsDir, fileName)

                FileWriter(csvFile).use { writer ->
                    writer.append("Ruta,Vreme,Širina,Dužina,Tačnost\n")

                    routes.forEach { route ->
                        val points = app.routeRepository.getRoutePoints(route.id)
                        points.forEach { point ->
                            val time = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(point.timestamp))
                            writer.append("${route.name},$time,${point.latitude},${point.longitude},${point.accuracy}\n")
                        }
                    }
                }

                runOnUiThread {
                    progressDialog.dismiss()

                    if (csvFile.exists()) {
                        val fileSize = String.format("%.1f", csvFile.length() / 1024.0)
                        Toast.makeText(
                            this@MainActivity,
                            "✅ ${routes.size} ruta uspešno izveženo u CSV!\n📁 Download/${fileName}\n📏 ${fileSize}KB",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.d("ExportDebug", "📁 Multiple CSV ruta sačuvana u: ${csvFile.absolutePath}")
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Greška pri čuvanju CSV fajla", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "❌ Greška pri CSV eksportu: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun debugExportPath() {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        Log.d("ExportDebug", "📁 Download folder path: ${downloadsDir.absolutePath}")
        Log.d("ExportDebug", "📁 Download folder exists: ${downloadsDir.exists()}")
        Log.d("ExportDebug", "📁 Download folder canWrite: ${downloadsDir.canWrite()}")

        // Proba da kreira test fajl
        val testFile = File(downloadsDir, "test_export.txt")
        try {
            testFile.writeText("Test export - ${Date()}")
            Log.d("ExportDebug", "✅ Test file created: ${testFile.exists()}")
            testFile.delete()
        } catch (e: Exception) {
            Log.e("ExportDebug", "❌ Test file failed: ${e.message}")
        }
    }
    private fun exportAllRoutes(format: String) {
        if (savedRoutes.isEmpty()) {
            Toast.makeText(this, "❌ Nema ruta za eksport", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("📦 Eksport svih ruta")
            .setMessage("Da li želite da izvezete SVIH ${savedRoutes.size} rutu/a u $format format?")
            .setPositiveButton("✅ Da, eksportuj sve") { dialog, which ->
                when (format) {
                    "GPX" -> exportSelectedRoutesAsGpx(savedRoutes)
                    "CSV" -> exportSelectedRoutesAsCsv(savedRoutes)
                }
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }
    private fun generateMultipleRoutesGPX(routes: List<Route>, allPoints: List<LocationPoint>): String {
        val builder = StringBuilder()

        builder.append("""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="GPS Tracker DS" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <name>Multiple Routes Export</name>
    <time>${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())}</time>
  </metadata>
""")

        // DODAJ SVAKU RUTU KAO POSEBNI TRK
        routes.forEach { route ->
            val routePoints = allPoints.filter { it.routeId == route.id }

            if (routePoints.isNotEmpty()) {
                builder.append("""
  <trk>
    <name>${escapeXml(route.name)}</name>
    <desc>Udaljenost: ${formatDistance(route.distance)}, Vreme: ${route.duration / 1000 / 60} minuta</desc>
    <trkseg>
""")

                routePoints.forEach { point ->
                    val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date(point.timestamp))
                    builder.append("""      <trkpt lat="${point.latitude}" lon="${point.longitude}">
        <time>$time</time>
        <ele>0</ele>
      </trkpt>
""")
                }

                builder.append("""    </trkseg>
  </trk>
""")
            }
        }

        builder.append("</gpx>")
        return builder.toString()
    }

    private fun showRoutesManagementDialog() {
        val options = arrayOf(
            "📋 Pregled svih ruta",
            "🗑️ Brisanje selektovanih ruta",
            "💥 Brisanje svih ruta",
            "❌ Zatvori"
        )

        AlertDialog.Builder(this)
            .setTitle("⚙️ Upravljanje rutama (${savedRoutes.size})")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showAllRoutesPreview()
                    1 -> showMultiSelectRoutesDialog()
                    2 -> deleteAllRoutesConfirmation()
                }
            }
            .show()
    }

    private fun showAllRoutesPreview() {
        if (savedRoutes.isEmpty()) {
            Toast.makeText(this, "❌ Nema sačuvanih ruta", Toast.LENGTH_SHORT).show()
            return
        }

        val routeNames = savedRoutes.sortedByDescending { it.startTime }
            .map { route ->
                val duration = route.duration / 1000
                val minutes = duration / 60
                val seconds = duration % 60
                "${route.name}\n📏 ${formatDistance(route.distance)} • ⏱️ ${minutes}m ${seconds}s • 📅 ${formatDate(route.startTime)}"
            }
            .toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("🗂️ Sve rute (${savedRoutes.size})")
            .setItems(routeNames) { dialog, which ->
                val selectedRoute = savedRoutes.sortedByDescending { it.startTime }[which]
                showAdvancedRouteOptions(selectedRoute)
            }
            .setPositiveButton("✅ U redu", null)
            .setNeutralButton("⚙️ Upravljaj rutama") { dialog, which ->
                showRoutesManagementDialog()
            }
            .show()
    }

    private fun showMultiSelectRoutesDialog() {
        if (savedRoutes.isEmpty()) {
            Toast.makeText(this, "❌ Nema ruta za brisanje", Toast.LENGTH_SHORT).show()
            return
        }

        val routeNames = savedRoutes.map {
            "${it.name}\n   📅 ${formatDate(it.startTime)} - 📏 ${formatDistance(it.distance)}"
        }.toTypedArray()

        val selectedRoutes = BooleanArray(savedRoutes.size) { false }

        AlertDialog.Builder(this)
            .setTitle("🔍 Selektuj rute za brisanje")
            .setMultiChoiceItems(routeNames, selectedRoutes) { dialog, which, isChecked ->
                selectedRoutes[which] = isChecked
            }
            .setPositiveButton("🗑️ Obriši selektovane") { dialog, which ->
                val routesToDelete = savedRoutes.filterIndexed { index, _ ->
                    selectedRoutes[index]
                }
                if (routesToDelete.isNotEmpty()) {
                    showBulkRoutesDeleteConfirmation(routesToDelete)
                } else {
                    Toast.makeText(this, "⚠️ Nijedna ruta nije selektovana", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun showBulkRoutesDeleteConfirmation(routesToDelete: List<Route>) {
        val routesList = routesToDelete.joinToString("\n") {
            " • ${it.name} (📏 ${formatDistance(it.distance)})"
        }
        val totalDistance = routesToDelete.sumOf { it.distance }

        AlertDialog.Builder(this)
            .setTitle("🗑️ Brisanje ruta")
            .setMessage(
                "⚠️ Da li ste sigurni da želite da obrišete ${routesToDelete.size} rutu/a?\n\n" +
                        "📏 Ukupna udaljenost: ${formatDistance(totalDistance)}\n\n" +
                        "🗂️ Rute za brisanje:\n$routesList"
            )
            .setPositiveButton("✅ Obriši sve") { dialog, which ->
                deleteMultipleRoutes(routesToDelete)
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun deleteMultipleRoutes(routesToDelete: List<Route>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as jovannedeljkovic.gps_tracker_pro.App
                routesToDelete.forEach { route ->
                    app.routeRepository.deleteRoute(route)
                }
                runOnUiThread {
                    savedRoutes.removeAll(routesToDelete.toSet())
                    polylines.clear()
                    binding.mapView.overlays.removeAll(polylines)
                    binding.mapView.invalidate()
                    Toast.makeText(
                        this@MainActivity,
                        "Uspešno obrisano ${routesToDelete.size} ruta",
                        Toast.LENGTH_LONG
                    ).show()
                    notificationHelper.showBulkDeleteSuccess(routesToDelete.size)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Greška pri brisanju: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun deleteAllRoutesConfirmation() {
        if (savedRoutes.isEmpty()) {
            Toast.makeText(this, "❌ Nema ruta za brisanje", Toast.LENGTH_SHORT).show()
            return
        }

        val totalDistance = savedRoutes.sumOf { it.distance }
        val totalDuration = savedRoutes.sumOf { it.duration } / 1000 / 60

        AlertDialog.Builder(this)
            .setTitle("💥 Brisanje svih ruta")
            .setMessage(
                "🚨 PAŽNJA: Ovo će obrisati SVE vaše rute!\n\n" +
                        "📊 Ukupno: ${savedRoutes.size} ruta\n" +
                        "📏 Ukupna udaljenost: ${formatDistance(totalDistance)}\n" +
                        "⏱️ Ukupno vreme: ${totalDuration} minuta\n\n" +
                        "⚠️ Ova akcija se NE MOŽE poništiti!"
            )
            .setPositiveButton("💣 OBRISI SVE") { dialog, which ->
                deleteAllRoutes()
            }
            .setNegativeButton("❌ Otkaži", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun deleteAllRoutes() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as jovannedeljkovic.gps_tracker_pro.App
                val routesToDelete = savedRoutes.toList()
                routesToDelete.forEach { route ->
                    app.routeRepository.deleteRoute(route)
                }
                runOnUiThread {
                    polylines.clear()
                    binding.mapView.overlays.removeAll(polylines)
                    binding.mapView.invalidate()
                    savedRoutes.clear()
                    Toast.makeText(
                        this@MainActivity,
                        "Sve rute su obrisane (${routesToDelete.size})",
                        Toast.LENGTH_LONG
                    ).show()
                    notificationHelper.showAllRoutesDeleted(routesToDelete.size)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Greška pri brisanju: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showAdvancedRouteOptions(route: Route) {
        Toast.makeText(this, "📂 Otvaranje opcija za: ${route.name}", Toast.LENGTH_SHORT).show()
        val options = arrayOf(
            "📊 Prikaži statistiku",
            "🗺️ Prikaži na mapi",
            "📤 Podeli rutu",
            "✏️ Preimenuj",
            "🗑️ Obriši"
        )

        AlertDialog.Builder(this)
            .setTitle("📍 ${route.name}")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showRouteStatistics(route)
                    1 -> displayRouteOnMap(route)
                    2 -> shareRoute(route)
                    3 -> renameRoute(route)
                    4 -> deleteRoute(route)
                }
            }
            .show()
    }

    private fun renameRoute(route: Route) {
        val editText = EditText(this).apply {
            setText(route.name)
            hint = "Unesite novo ime rute"
            setSelectAllOnFocus(true)
        }

        AlertDialog.Builder(this)
            .setTitle("Preimenuj rutu")
            .setMessage("Trenutno ime: ${route.name}")
            .setView(editText)
            .setPositiveButton("Sačuvaj") { dialog, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isBlank()) {
                    Toast.makeText(this, "Ime ne može biti prazno!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newName == route.name) {
                    Toast.makeText(this, "Ime nije promenjeno", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                updateRouteName(route, newName)
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun updateRouteName(route: Route, newName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as jovannedeljkovic.gps_tracker_pro.App
                val updatedRoute = route.copy(name = newName)
                app.routeRepository.updateRoute(updatedRoute)
                runOnUiThread {
                    val index = savedRoutes.indexOfFirst { it.id == route.id }
                    if (index != -1) {
                        savedRoutes[index] = updatedRoute
                    }
                    Toast.makeText(
                        this@MainActivity,
                        "Ruta '${route.name}' preimenovana u '$newName'",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Greška pri preimenovanju: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showRouteStatistics(route: Route) {
        val duration = route.duration / 1000
        val minutes = duration / 60
        val seconds = duration % 60
        val avgSpeed = if (duration > 0) (route.distance / duration * 3.6) else 0.0

        val stats = """
        📊 Statistika rute:
        
        📏 Udaljenost: ${formatDistance(route.distance)}
        ⏱️ Vreme: ${minutes}m ${seconds}s
        🚀 Prosečna brzina: ${String.format("%.1f", avgSpeed)} km/h
        📅 Datum: ${formatDate(route.startTime)}
        
        ${if (route.isCompleted) "✅ Završena" else "🟡 U toku"}
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("📈 Statistika: ${route.name}")
            .setMessage(stats)
            .setPositiveButton("✅ U redu", null)
            .show()
    }

    private fun showLongestRoute() {
        val longestRoute = savedRoutes.maxByOrNull { it.distance }
        longestRoute?.let { route ->
            Toast.makeText(this, "🥇 Najduža ruta: ${route.name} (📏 ${formatDistance(route.distance)})", Toast.LENGTH_LONG).show()
            displayRouteOnMap(route)
        }
    }

    private fun shareRoute(route: Route) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as jovannedeljkovic.gps_tracker_pro.App
                val points = app.routeRepository.getRoutePoints(route.id)
                val routeInfo = """
                ??? Ruta: ${route.name}
                
                ?? Udaljenost: ${formatDistance(route.distance)}
                ?? Vreme: ${route.duration / 1000 / 60} minuta
                ?? Datum: ${formatDate(route.startTime)}
                
                Tačke rute (${points.size}):
                ${points.joinToString("\n") { point ->
                    "${point.latitude}, ${point.longitude}"
                }}
                
                Deljeno iz GPS Tracker aplikacije
            """.trimIndent()

                runOnUiThread {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, routeInfo)
                        putExtra(Intent.EXTRA_SUBJECT, "Ruta: ${route.name}")
                    }
                    startActivity(Intent.createChooser(intent, "Podeli rutu"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška pri deljenju rute", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun displayRouteOnMap(route: Route) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as jovannedeljkovic.gps_tracker_pro.App
                val points = app.routeRepository.getRoutePoints(route.id)
                runOnUiThread {
                    val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
                    polylines.forEach { binding.mapView.overlays.remove(it) }
                    polylines.clear()
                    if (geoPoints.size >= 2) {
                        val polyline = Polyline().apply {
                            setPoints(geoPoints)
                            color = Color.parseColor("#FFFF9800")
                            width = 8.0f
                        }
                        binding.mapView.overlays.add(polyline)
                        polylines.add(polyline)
                        binding.mapView.controller.animateTo(geoPoints.first())
                        binding.mapView.controller.setZoom(13.0)
                    }
                    binding.mapView.invalidate()
                    Toast.makeText(this@MainActivity, "Ruta '${route.name}' prikazana na mapi", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška pri prikazu rute: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteRoute(route: Route) {
        AlertDialog.Builder(this)
            .setTitle("Brisanje rute")
            .setMessage("Da li ste sigurni da želite da obrišete rutu '${route.name}'?")
            .setPositiveButton("Obriši") { dialog, which ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val app = application as jovannedeljkovic.gps_tracker_pro.App
                        app.routeRepository.deleteRoute(route)
                        runOnUiThread {
                            savedRoutes.remove(route)
                            Toast.makeText(this@MainActivity, "Ruta '${route.name}' obrisana", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Greška pri brisanju: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun resetCurrentRoute() {
        val options = arrayOf(
            "🔄 Resetuj tekuću rutu (snimanje)",
            "🗑️ Obriši prikazane rute sa mape",  // 👈 NOVA OPCIJA
            "💥 Obriši SVE rute iz baze",
            "❌ Otkaži"
        )

        AlertDialog.Builder(this)
            .setTitle("🔄 Resetovanje ruta")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> performRouteReset()  // Reset tekuće rute
                    1 -> showRouteDeletionOptions()  // Brisanje prikazanih ruta
                    2 -> deleteAllRoutesConfirmation()  // Brisanje svih ruta iz baze
                    // 3 -> Otkaži
                }
            }
            .setNegativeButton("❌ Zatvori", null)
            .show()
    }
    private fun performRouteReset() {
        currentRoute = null
        routePoints.clear()
        totalDistance = 0.0
        lastLocation = null
        polylines.forEach { binding.mapView.overlays.remove(it) }
        polylines.clear()
        binding.mapView.invalidate()
        updateTrackingStats(0.0, 0.0)

        Toast.makeText(this, "✅ Ruta resetovana!", Toast.LENGTH_SHORT).show()
        Log.d("RouteReset", "Ruta uspešno resetovana")
    }

    private fun exportRouteData() {
        val options = arrayOf(
            "🗺️ Izvezi tekuću rutu (GPX/CSV)",
            "🗂️ Izvezi sačuvane rute (GPX/CSV)",  // 👈 NOVA OPCIJA
            "📍 Izvezi tačke u GPX",
            "📋 Izvezi tačke u CSV",
            "📊 Izvezi statistiku",
            "❌ Otkaži"
        )

        AlertDialog.Builder(this)
            .setTitle("📤 Izvoz podataka")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showCurrentRouteExportOptions()     // Tekuća ruta
                    1 -> showSavedRoutesExportOptions()      // Sačuvane rute 👈 NOVO
                    2 -> exportPointsToGPX()                 // Tačke GPX
                    3 -> exportPointsToCSV()                 // Tačke CSV
                    4 -> exportStatistics()                  // Statistika 👈 NOVO
                    // 5 -> Otkaži
                }
            }
            .setNegativeButton("❌ Zatvori", null)
            .show()
    }
    private fun showCurrentRouteExportOptions() {
        if (currentRoute == null && !isTracking) {
            Toast.makeText(this, "❌ Nema aktivne rute za eksport", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf(
            "🗺️ GPX format (za druge aplikacije)",
            "📊 CSV format (za Excel analizu)",
            "❌ Otkaži"
        )

        AlertDialog.Builder(this)
            .setTitle("📤 Izvoz tekuće rute")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> exportRouteToGPX()  // GPX
                    1 -> exportRouteToCSV()  // CSV
                    // 2 -> Otkaži
                }
            }
            .setNegativeButton("❌ Nazad", null)
            .show()
    }
    private fun showSavedRoutesExportOptions() {
        if (savedRoutes.isEmpty()) {
            Toast.makeText(this, "❌ Nema sačuvanih ruta", Toast.LENGTH_SHORT).show()
            showSavedRoutes() // Otvori dijalog za pregled ruta
            return
        }

        val options = arrayOf(
            "🗺️ Izvezi pojedinačne rute (GPX)",
            "🗺️ Izvezi pojedinačne rute (CSV)",
            "📦 Izvezi sve rute (GPX)",
            "📦 Izvezi sve rute (CSV)",
            "📊 Pregled svih ruta",
            "❌ Otkaži"
        )

        AlertDialog.Builder(this)
            .setTitle("📤 Izvoz sačuvanih ruta (${savedRoutes.size})")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showRouteSelectionForExport("GPX")  // Pojedinačne GPX
                    1 -> showRouteSelectionForExport("CSV")  // Pojedinačne CSV
                    2 -> exportAllRoutes("GPX")             // Sve GPX
                    3 -> exportAllRoutes("CSV")             // Sve CSV
                    4 -> showAllRoutesPreview()             // Pregled ruta
                    // 5 -> Otkaži
                }
            }
            .setNegativeButton("❌ Nazad", null)
            .show()
    }
    private fun exportStatistics() {
        if (savedRoutes.isEmpty() && pointsOfInterest.isEmpty()) {
            Toast.makeText(this, "❌ Nema podataka za statistiku", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("📊 Generisanje statistike...")
            .setMessage("Pripremam izveštaj...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val statsContent = generateStatisticsReport()
                val fileName = "statistika_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val statsFile = File(downloadsDir, fileName)

                FileWriter(statsFile).use { writer ->
                    writer.write(statsContent)
                }

                runOnUiThread {
                    progressDialog.dismiss()

                    if (statsFile.exists()) {
                        val fileSize = String.format("%.1f", statsFile.length() / 1024.0)
                        Toast.makeText(
                            this@MainActivity,
                            "✅ Statistika izvežena!\n📁 Download/${fileName}\n📏 ${fileSize}KB",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Greška pri čuvanju fajla", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "❌ Greška pri eksportu statistike: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun generateStatisticsReport(): String {
        val builder = StringBuilder()

        builder.append("=" * 50).append("\n")
        builder.append("📊 GPS TRACKER - STATISTIČKI IZVEŠTAJ\n")
        builder.append("=" * 50).append("\n\n")

        // STATISTIKA RUTA
        builder.append("🗺️ STATISTIKA RUTA:\n")
        builder.append("-" * 30).append("\n")

        if (savedRoutes.isNotEmpty()) {
            val totalRoutes = savedRoutes.size
            val totalDistance = savedRoutes.sumOf { it.distance }
            val totalDuration = savedRoutes.sumOf { it.duration } / 1000 / 60
            val longestRoute = savedRoutes.maxByOrNull { it.distance }
            val averageDistance = totalDistance / totalRoutes

            builder.append("• Ukupno ruta: $totalRoutes\n")
            builder.append("• Ukupna udaljenost: ${formatDistance(totalDistance)}\n")
            builder.append("• Ukupno vreme: ${totalDuration} minuta\n")
            builder.append("• Prosečna dužina: ${formatDistance(averageDistance)}\n")

            longestRoute?.let { route ->
                builder.append("• Najduža ruta: ${route.name} (${formatDistance(route.distance)})\n")
            }

            builder.append("\n")
            builder.append("📈 DETALJI RUTA:\n")
            savedRoutes.sortedByDescending { it.startTime }.forEach { route ->
                val duration = route.duration / 1000 / 60
                builder.append("• ${route.name}: ${formatDistance(route.distance)} | ${duration}min | ${formatDate(route.startTime)}\n")
            }
        } else {
            builder.append("Nema sačuvanih ruta\n")
        }

        builder.append("\n")

        // STATISTIKA TAČAKA
        builder.append("📍 STATISTIKA TAČAKA:\n")
        builder.append("-" * 30).append("\n")

        if (pointsOfInterest.isNotEmpty()) {
            builder.append("• Ukupno tačaka: ${pointsOfInterest.size}\n")
            builder.append("• Prva tačka: ${pointsOfInterest.minByOrNull { it.createdAt }?.name ?: "N/A"}\n")
            builder.append("• Poslednja tačka: ${pointsOfInterest.maxByOrNull { it.createdAt }?.name ?: "N/A"}\n")

            builder.append("\n")
            builder.append("📋 SPISAK TAČAKA:\n")
            pointsOfInterest.sortedByDescending { it.createdAt }.forEach { point ->
                builder.append("• ${point.name}: ${String.format("%.6f", point.latitude)}, ${String.format("%.6f", point.longitude)} | ${formatDate(point.createdAt)}\n")
            }
        } else {
            builder.append("Nema sačuvanih tačaka\n")
        }

        builder.append("\n")
        builder.append("=" * 50).append("\n")
        builder.append("Izveštaj generisan: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        builder.append("=" * 50).append("\n")

        return builder.toString()
    }

    // Pomoćna ekstenzija za ponavljanje stringa
    private operator fun String.times(n: Int): String = repeat(n)
    private fun exportRouteToGPX() {
        if (!checkStoragePermissionsForExport()) {
            Toast.makeText(this, "🔐 Potrebne su dozvole za pristup skladištu", Toast.LENGTH_LONG).show()
            requestStoragePermissions()
            return
        }

        if (currentRoute == null) {
            Toast.makeText(this, "❌ Nema rute za eksport", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("🗺️ Izvoz rute u GPX...")
            .setMessage("Pripremam GPX fajl...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val points = app.routeRepository.getRoutePoints(currentRoute!!.id)

                if (points.isEmpty()) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@MainActivity, "❌ Nema podataka rute za eksport", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val routeName = currentRoute!!.name
                val gpxContent = generateRouteGPX(routeName, points)
                val fileName = "ruta_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.gpx"

                // KLJUČNA PROMENA: Koristi Download folder
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val gpxFile = File(downloadsDir, fileName)

                FileWriter(gpxFile).use { writer ->
                    writer.write(gpxContent)
                }

                runOnUiThread {
                    progressDialog.dismiss()

                    if (gpxFile.exists()) {
                        val fileSize = String.format("%.1f", gpxFile.length() / 1024.0)
                        Toast.makeText(
                            this@MainActivity,
                            "✅ Ruta izvežena!\n📁 Download/${fileName}\n📏 ${fileSize}KB",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.d("ExportDebug", "📁 Ruta sačuvana u: ${gpxFile.absolutePath}")
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Greška pri čuvanju fajla", Toast.LENGTH_SHORT).show()
                        Log.e("ExportDebug", "❌ Fajl nije kreiran: ${gpxFile.absolutePath}")
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "❌ Greška pri GPX eksportu: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("ExportDebug", "💥 Greška: ${e.message}")
                }
            }
        }
    }
    private fun openDownloadsFolder() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)),
                    "resource/folder"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Ako ne može da otvori folder, pokaži putanju
            val downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            Toast.makeText(this, "📁 Fajl sačuvan u: $downloadsPath", Toast.LENGTH_LONG).show()
        }
    }
    private fun checkStoragePermissionsForExport(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Za Android 9 i niže, proveri WRITE_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            // Za Android 10+, MANAGE_EXTERNAL_STORAGE nije obavezan ali je dobro ga imati
            true
        }
    }
    private fun exportPointsToGPX() {
        if (!checkStoragePermissionsForExport()) {
            Toast.makeText(this, "🔐 Potrebne su dozvole za pristup skladištu", Toast.LENGTH_LONG).show()
            requestStoragePermissions()
            return
        }
        if (pointsOfInterest.isEmpty()) {
            Toast.makeText(this, "❌ Nema tačaka za eksport", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("📍 Izvoz tačaka u GPX...")
            .setMessage("Pripremam GPX fajl...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val gpxContent = generatePointsGPX(pointsOfInterest)
                val fileName = "tacke_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.gpx"

                val gpxFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )

                FileWriter(gpxFile).use { writer ->
                    writer.write(gpxContent)
                }

                runOnUiThread {
                    progressDialog.dismiss()
                    shareFile(gpxFile, "application/gpx+xml", "GPX fajl tačaka")
                    notificationHelper.showExportSuccess(gpxFile.name)
                    Toast.makeText(this@MainActivity, "✅ Tačke uspešno izvežene u GPX!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "❌ Greška pri GPX eksportu: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun generateRouteGPX(routeName: String, points: List<LocationPoint>): String {
        val builder = StringBuilder()

        builder.append("""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="GPS Tracker DS" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <name>${escapeXml(routeName)}</name>
    <time>${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())}</time>
  </metadata>
  <trk>
    <name>${escapeXml(routeName)}</name>
    <trkseg>
""")

        points.forEach { point ->
            val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date(point.timestamp))
            builder.append("""      <trkpt lat="${point.latitude}" lon="${point.longitude}">
        <time>$time</time>
        <ele>0</ele>
      </trkpt>
""")
        }

        builder.append("""    </trkseg>
  </trk>
</gpx>""")

        return builder.toString()
    }

    private fun generatePointsGPX(points: List<PointOfInterest>): String {
        val builder = StringBuilder()

        builder.append("""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="GPS Tracker DS" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <name>Tačke interesa</name>
    <time>${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())}</time>
  </metadata>
""")

        points.forEach { point ->
            val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date(point.createdAt))
            builder.append("""  <wpt lat="${point.latitude}" lon="${point.longitude}">
    <name>${escapeXml(point.name)}</name>
    <time>$time</time>
    <desc>Tačka interesa: ${escapeXml(point.name)}</desc>
    <sym>Flag</sym>
  </wpt>
""")
        }

        builder.append("</gpx>")

        return builder.toString()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
    private fun exportRouteToCSV() {
        if (!checkStoragePermissionsForExport()) {
            Toast.makeText(this, "🔐 Potrebne su dozvole za pristup skladištu", Toast.LENGTH_LONG).show()
            requestStoragePermissions()
            return
        }

        if (currentRoute == null) {
            Toast.makeText(this, "❌ Nema rute za eksport", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("📊 Izvoz rute u CSV...")
            .setMessage("Pripremam CSV fajl...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val points = app.routeRepository.getRoutePoints(currentRoute!!.id)

                if (points.isEmpty()) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@MainActivity, "❌ Nema podataka rute za eksport", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val fileName = "ruta_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"

                // KLJUČNA PROMENA: Koristi Download folder
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val csvFile = File(downloadsDir, fileName)

                FileWriter(csvFile).use { writer ->
                    writer.append("Vreme,Širina,Dužina,Tačnost\n")
                    points.forEach { point ->
                        val time = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(point.timestamp))
                        writer.append("$time,${point.latitude},${point.longitude},${point.accuracy}\n")
                    }
                }

                runOnUiThread {
                    progressDialog.dismiss()

                    if (csvFile.exists()) {
                        val fileSize = String.format("%.1f", csvFile.length() / 1024.0)
                        Toast.makeText(
                            this@MainActivity,
                            "✅ Ruta izvežena!\n📁 Download/${fileName}\n📏 ${fileSize}KB",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.d("ExportDebug", "📁 CSV ruta sačuvana u: ${csvFile.absolutePath}")
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Greška pri čuvanju CSV fajla", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "❌ Greška pri CSV eksportu: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportPointsToCSV() {
        if (!checkStoragePermissionsForExport()) {
            Toast.makeText(this, "🔐 Potrebne su dozvole za pristup skladištu", Toast.LENGTH_LONG).show()
            requestStoragePermissions()
            return
        }
        if (pointsOfInterest.isEmpty()) {
            Toast.makeText(this, "Nema tačaka za eksport", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val csvFile = File(
                    getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    "tacke_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
                )
                FileWriter(csvFile).use { writer ->
                    writer.append("Ime,Širina,Dužina,Datum kreiranja\n")
                    pointsOfInterest.forEach { point ->
                        val time = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(point.createdAt))
                        writer.append("${point.name},${point.latitude},${point.longitude},$time\n")
                    }
                }
                runOnUiThread {
                    shareFile(csvFile, "text/csv", "CSV fajl tačaka")
                    notificationHelper.showExportSuccess(csvFile.name)
                    Toast.makeText(this@MainActivity, "Tačke uspešno eksportovane!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška pri eksportu: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareFile(file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun getBearingDirection(bearing: Float): String {
        return when {
            bearing < 22.5 || bearing >= 337.5 -> "Sever"
            bearing < 67.5 -> "Severoistok"
            bearing < 112.5 -> "Istok"
            bearing < 157.5 -> "Jugoistok"
            bearing < 202.5 -> "Jug"
            bearing < 247.5 -> "Jugozapad"
            bearing < 292.5 -> "Zapad"
            else -> "Severozapad"
        }
    }
    private fun getBatteryLevel(): Int {
        return try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level == -1 || scale == -1) 0 else (level * 100 / scale.toFloat()).toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun getCurrentUserId(): String {
        return try {
            val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
            sharedPreferences.getString("user_email", "default-user") ?: "default-user"
        } catch (e: Exception) {
            "default-user"
        }
    }

    private fun showSettings() {
        Toast.makeText(this, "Podešavanja će biti dostupna uskoro", Toast.LENGTH_SHORT).show()
    }

    private fun showHistory() {
        Toast.makeText(this, "Istorija će biti dostupna uskoro", Toast.LENGTH_SHORT).show()
    }

    private fun showHelp() {
        Toast.makeText(this, "Pomoć će biti dostupna uskoro", Toast.LENGTH_SHORT).show()
    }

    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle("Odjava")
            .setMessage("Da li ste sigurni da želite da se odjavite?")
            .setPositiveButton("Da") { dialog, which ->
                performLogout()
            }
            .setNegativeButton("Ne", null)
            .show()
    }

    private fun performLogout() {
        if (isTracking) {
            stopTracking()
        }
        stopLocationUpdates()
        notificationHelper.cancelAllNotifications()
        val intent = Intent(this, jovannedeljkovic.gps_tracker_pro.ui.auth.LoginActivity::class.java)
        startActivity(intent)
        finish()
        Toast.makeText(this, "Uspešno ste se odjavili", Toast.LENGTH_SHORT).show()
    }

    // LOCATION PERMISSIONS
    private fun checkLocationPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates()
            }
            else -> {
                requestLocationPermissions()
            }
        }
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates()
                } else {
                    Toast.makeText(this, "Dozvola za lokaciju je neophodna za rad aplikacije", Toast.LENGTH_LONG).show()
                }
            }
            LOCATION_PERMISSION_REQUEST_CODE + 100 -> { // Nove dozvole za skladištenje
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "✅ Dozvole za skladištenje odobrene!", Toast.LENGTH_SHORT).show()
                    importGpxFromDownloads() // Ponovo pokušaj
                } else {
                    Toast.makeText(this, "❌ Dozvole za skladištenje odbijene", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("Location", "Nema dozvola za lokaciju!")
            requestLocationPermissions()
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("Location", "Location updates started")
        } catch (e: SecurityException) {
            Log.e("Location", "Security exception: ${e.message}")
            requestLocationPermissions()
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
    // DODAJ U MAIN ACTIVITY
    private fun getCurrentUserFromPrefs(): String {
        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        return sharedPreferences.getString("user_email", "unknown") ?: "unknown"
    }

    private fun checkUserFeatures() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val userEmail = getCurrentUserFromPrefs()
                val user = app.userRepository.getUserByEmail(userEmail)

                runOnUiThread {
                    // Ažurirajte UI na osnovu korisničkih privilegija
                    if (user?.role == "PREMIUM") {
                        // Omogućite premium funkcionalnosti
                    }
                }
            } catch (e: Exception) {
                Log.e("UserFeatures", "Greška pri proveri korisničkih feature-a: ${e.message}")
            }
        }
    }
    private fun safeButtonSetup() {
        // Proveri da li je binding inicijalizovan i da li dugmići postoje
        if (::binding.isInitialized) {
            // TRACKING DUGMIĆI
            binding.btnStartTracking?.setOnClickListener { startTracking() }
            binding.btnStopTracking?.setOnClickListener { stopTracking() }

            // LOKACIJA I ZOOM
            binding.btnMyLocation?.setOnClickListener { centerOnMyLocationSilent() }
            binding.btnMyLocation?.setOnLongClickListener {
                resetZoomToLocation()
                true
            }

            // MENI I NAVIGACIJA
            binding.btnMenu?.setOnClickListener { showNavigationMenu() }
            binding.btnSavedRoutes?.setOnClickListener { showSavedRoutes() }
            binding.btnNavigation?.setOnClickListener { openGoogleMaps() }

            // ZOOM KONTROLE
            binding.btnZoomIn?.setOnClickListener { zoomIn() }
            binding.btnZoomOut?.setOnClickListener { zoomOut() }

            // TAČKE I MAPA
            binding.fabAddPoint?.setOnClickListener { togglePointMode() }
            binding.btnMapType?.setOnClickListener { showMapTypeDialog() }

            // TRACKING MODE I KOMPAS
            binding.btnTrackingMode?.setOnClickListener { toggleTrackingMode() }
            binding.btnCompass?.setOnClickListener { toggleCompass() }

            // ORIJENTACIJA I PRAĆENJE
            binding.btnMapOrientation?.setOnClickListener { toggleMapOrientation() }
            binding.btnFollowLocation?.setOnClickListener { toggleAutoFollow() }

            // EKSPORT I RESET
            binding.btnExport?.setOnClickListener { exportRouteData() }
            binding.btnReset?.setOnClickListener { resetCurrentRoute() }

            Log.d("SafeSetup", "✅ Svi dugmići bezbedno postavljeni")
        } else {
            Log.e("SafeSetup", "❌ Binding nije inicijalizovan")
        }
    }
    private fun createOfflineSatelliteTileSource(): XYTileSource {
        return object : XYTileSource(
            "OfflineSatellite",
            0, 18, 256, ".png",
            arrayOf("") // prazan URL jer koristimo cache
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)

                // OSMdroid će automatski tražiti tile u cache-u
                return "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x"
            }
        }
    }


    // Poboljšana metoda za tačno izračunavanje udaljenosti
    private fun calculateAccurateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    // Ažuriraj prikaz statistike
    private fun updateStatsDisplay() {
        try {
            val distanceKm = totalDistance / 1000.0
            val speedKmh = currentSpeed * 3.6 // m/s to km/h

            runOnUiThread {
                findViewById<TextView>(R.id.tvDistance)?.text =
                    String.format("%.2f km", distanceKm)
                findViewById<TextView>(R.id.tvSpeed)?.text =
                    String.format("%.1f km/h", speedKmh)
                findViewById<TextView>(R.id.tvTime)?.text =
                    formatTime(trackingTime)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating stats", e)
        }
    }
    private fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
        } else {
            String.format("%02d:%02d", minutes, seconds % 60)
        }
    }

    // I dodaj trackingTime varijablu u deklaracije:
    private fun refreshMap() {
        // OSVEŽI PRIKAZ RUTE AKO JE AKTIVNO SNIMANJE
        if (isTracking && routePoints.size >= 2) {
            runOnUiThread {
                drawSmoothRouteOnMap()
                Log.d("MapRefresh", "🔄 Mapa osvežena - ${routePoints.size} tačaka")
            }
        }

        // OSVEŽI CELE MAPI
        binding.mapView.invalidate()
        binding.mapView.postInvalidate()
    }
    private fun createNewRouteSegment() {
        if (currentSegment.isNotEmpty()) {
            // SAČUVAJ TEKUĆI SEGMENT
            routeSegments.add(currentSegment.toMutableList())

            // ZAPOČNI NOVI SEGMENT SA POSLEDNJOM TAČKOM
            val lastPoint = currentSegment.lastOrNull()
            currentSegment = mutableListOf<GeoPoint>()

            lastPoint?.let { point ->
                currentSegment.add(point)
                Log.d("Tracking", "🔄 Novi segment započet sa tačkom: ${point.latitude}, ${point.longitude}")
            }
        }
    }
    private fun refreshMapAndRoute() {
        try {
            // PROVERI DA LI JE BINDING INICIJALIZOVAN
            if (!::binding.isInitialized) {
                Log.w("MapRefresh", "Binding nije inicijalizovan, preskačem osvežavanje")
                return
            }

            Log.d("MapRefresh", "Osvežavam mapu i rutu...")

            runOnUiThread {
                // 1. OSVEŽI MAP VIEW
                binding.mapView.invalidate()
                binding.mapView.postInvalidate()

                // 2. PONOVO ISCRTAJ RUTU AKO POSTOJI
                if (isTracking && routePoints.isNotEmpty()) {
                    Log.d("RouteRefresh", "Ponovo iscrtavam rutu sa ${routePoints.size} tačaka")
                    drawSmoothRouteOnMap()
                }

                // 3. OSVEŽI LOKACIJU MARKER
                lastLocation?.let {
                    val currentLocation = GeoPoint(it.latitude, it.longitude)
                    showAccurateLocationMarker(currentLocation, it)
                }

                // 4. OSVEŽI TAČKE INTERESA
                refreshPointsOfInterest()

                Log.d("MapRefresh", "Mapa i ruta uspešno osvežene")
            }
        } catch (e: Exception) {
            Log.e("MapRefresh", "Greška pri osvežavanju mape: ${e.message}")
        }
    }
    private fun importGpxFromDownloads() {
        Log.d("ImportDebug", "🎯 POZIVAM importGpxFromDownloads()")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Za Android 10+ koristi MediaStore
            queryGpxFilesWithMediaStoreAndroid10()
        } else {
            // Za starije Androide koristi legacy pristup
            queryGpxFilesLegacy()
        }
    }
@RequiresApi(Build.VERSION_CODES.Q)
private fun queryGpxFilesWithMediaStoreAndroid10() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        queryGpxFilesLegacy()
        return
    }

    val progressDialog = AlertDialog.Builder(this)
        .setTitle("🔍 Tražim GPX fajlove...")
        .setMessage("Pretražujem Download folder...")
        .setCancelable(false)
        .create()
    progressDialog.show()

    lifecycleScope.launch(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED
            )

            val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%.gpx")

            val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

            val cursor = contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            val gpxFiles = mutableListOf<GpxFileInfo>()

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameColumn = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateColumn = c.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)

                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val name = c.getString(nameColumn)
                    val size = c.getLong(sizeColumn)
                    val dateModified = c.getLong(dateColumn)

                    gpxFiles.add(GpxFileInfo(id, name, size, dateModified))
                }
            }

            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                if (gpxFiles.isNotEmpty()) {
                    showMediaStoreGpxFilesAndroid10(gpxFiles)
                } else {
                    showNoGpxFilesFound()
                }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                Log.e("MediaStore", "Greška pri pretrazi: ${e.message}")
                Toast.makeText(this@MainActivity, "❌ Greška pri pretrazi: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun showMediaStoreGpxFilesAndroid10(gpxFiles: List<GpxFileInfo>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        val fileNames = gpxFiles.map { file ->
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(file.dateModified * 1000))
            val size = String.format("%.1f", file.size / 1024.0)
            "${file.name}\n   📅 $date • 📏 ${size}KB"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("📥 GPX fajlovi u Download folderu (${gpxFiles.size})")
            .setItems(fileNames) { dialog, which ->
                val selectedFile = gpxFiles[which]
                importGpxFromMediaStoreAndroid10(selectedFile)
            }
            .setPositiveButton("🔄 Osveži listu") { dialog, which ->
                queryGpxFilesWithMediaStoreAndroid10()
            }
            .setNegativeButton("❌ Otkaži", null)
            .show()
    }

    private fun importGpxFromMediaStoreAndroid10(gpxFile: GpxFileInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "❌ Ova funkcija zahteva Android 10+", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("📥 Uvoz rute...")
            .setMessage("Učitavam GPX fajl: ${gpxFile.name}")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    gpxFile.id
                )

                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val gpxContent = inputStream.bufferedReader().use { it.readText() }
                    val points = parseGpxContent(gpxContent)

                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        if (points.isNotEmpty()) {
                            showImportedRoutePreview(points)
                        } else {
                            Toast.makeText(this@MainActivity, "❌ Nema tačaka u GPX fajlu", Toast.LENGTH_LONG).show()
                        }
                    }
                } ?: run {
                    throw Exception("Ne mogu da otvorim fajl: ${gpxFile.name}")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Log.e("MediaStoreImport", "Greška pri uvozu: ${e.message}")
                    Toast.makeText(this@MainActivity, "❌ Greška pri uvozu: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun queryGpxFilesLegacy() {
        try {
            // PROVERI DOZVOLE PRVO
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                Log.d("ImportDebug", "🔐 Tražim dozvole za storage...")
                requestStoragePermissions()
                return
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            Log.d("DownloadImport", "📁 Pristupam Download folderu: ${downloadsDir.absolutePath}")

            if (!downloadsDir.exists() || !downloadsDir.isDirectory) {
                Toast.makeText(this, "❌ Download folder ne postoji", Toast.LENGTH_LONG).show()
                return
            }

            val gpxFiles = downloadsDir.listFiles { file ->
                file.isFile && (file.name.endsWith(".gpx", ignoreCase = true) ||
                        file.name.endsWith(".xml", ignoreCase = true))
            }

            Log.d("DownloadImport", "📊 Pronađeno ${gpxFiles?.size ?: 0} GPX fajlova")

            if (gpxFiles.isNullOrEmpty()) {
                showNoGpxFilesFound()
                return
            }

            val sortedFiles = gpxFiles.sortedByDescending { it.lastModified() }
            val fileNames = sortedFiles.map { file ->
                val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                val size = String.format("%.1f", file.length() / 1024.0)
                "${file.name}\n   📅 $date • 📏 ${size}KB"
            }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("📥 GPX fajlovi u Download folderu (${gpxFiles.size})")
                .setItems(fileNames) { dialog, which ->
                    val selectedFile = sortedFiles[which]
                    Log.d("DownloadImport", "✅ Izabran fajl: ${selectedFile.name}")
                    importGpxFileDirect(selectedFile)
                }
                .setPositiveButton("🔄 Osveži listu") { dialog, which ->
                    queryGpxFilesLegacy() // Rekurzivni poziv za osvežavanje
                }
                .setNegativeButton("❌ Otkaži", null)
                .show()

        } catch (e: SecurityException) {
            Log.e("LegacyImport", "🔐 Bezbednosni izuzetak: ${e.message}")
            Toast.makeText(this, "❌ Nema dozvola za pristup fajlovima", Toast.LENGTH_LONG).show()
            requestStoragePermissions()
        } catch (e: Exception) {
            Log.e("LegacyImport", "💥 Greška: ${e.message}")
            Toast.makeText(this, "❌ Greška: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    private fun importGpxFileDirect(file: File) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("📥 Uvoz rute...")
            .setMessage("Učitavam GPX fajl: ${file.name}\n\nMolimo sačekajte...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("GPXImport", "🔄 Počinjem uvoz fajla: ${file.absolutePath}")

                // PROVERI VELIČINU FAJLA
                if (file.length() > 10 * 1024 * 1024) { // 10MB limit
                    throw Exception("Fajl je prevelik (${file.length() / 1024 / 1024}MB). Maksimalna veličina je 10MB.")
                }

                // PROVERI DA LI FAJL POSTOJI I MOŽE DA SE ČITA
                if (!file.exists()) {
                    throw Exception("Fajl ne postoji: ${file.name}")
                }

                if (!file.canRead()) {
                    throw Exception("Nemamo dozvolu za čitanje fajla: ${file.name}")
                }

                val gpxContent = file.readText(Charsets.UTF_8)
                Log.d("GPXImport", "✅ Pročitao ${gpxContent.length} karaktera iz fajla")

                if (gpxContent.isBlank()) {
                    throw Exception("Fajl je prazan: ${file.name}")
                }

                val points = parseGpxContent(gpxContent)
                Log.d("GPXImport", "✅ Parsovano ${points.size} tačaka iz GPX fajla")

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    if (points.isNotEmpty()) {
                        showImportedRoutePreview(points) // ISPRAVLJENO: Bez dodatnog parametra
                    } else {
                        Toast.makeText(this@MainActivity,
                            "❌ Nema validnih tačaka u GPX fajlu '${file.name}'",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("GPXImport", "💥 Greška pri uvozu fajla '${file.name}': ${e.message}")
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    showImportErrorDialog(file.name, e.message ?: "Nepoznata greška")
                }
            }
        }
    }
    private fun updateUIForUserRole(user: User) {
        val roleDisplay = FeatureManager.getUserRoleDisplayName(user)

        // Ažuriraj UI u zavisnosti od role
        when (user.role) {
            "BASIC" -> {
                // Indikator za BASIC korisnike
                binding.btnMapType.alpha = 0.7f
                binding.btnTrackingMode.alpha = 0.7f
                binding.fabAddPoint.alpha = 0.7f

                // Dodaj badge ili indikator
                binding.tvUserRole.visibility = View.VISIBLE
                binding.tvUserRole.text = "🔹 BASIC"
                binding.tvUserRole.setTextColor(ContextCompat.getColor(this, R.color.blue_primary))

                // Log za debug
                Log.d("UserRole", "Korisnik: ${user.name}, Role: $roleDisplay, Ograničenja: BASIC")
            }
            "PREMIUM" -> {
                binding.btnMapType.alpha = 1.0f
                binding.btnTrackingMode.alpha = 1.0f
                binding.fabAddPoint.alpha = 1.0f

                binding.tvUserRole.visibility = View.VISIBLE
                binding.tvUserRole.text = "⭐ PREMIUM"
                binding.tvUserRole.setTextColor(ContextCompat.getColor(this, R.color.accent_green))

                Log.d("UserRole", "Korisnik: ${user.name}, Role: $roleDisplay, Ograničenja: NEMA")
            }
            "ADMIN" -> {
                binding.btnMapType.alpha = 1.0f
                binding.btnTrackingMode.alpha = 1.0f
                binding.fabAddPoint.alpha = 1.0f

                binding.tvUserRole.visibility = View.VISIBLE
                binding.tvUserRole.text = "👑 ADMIN"
                binding.tvUserRole.setTextColor(ContextCompat.getColor(this, R.color.accent_red))

                Log.d("UserRole", "Korisnik: ${user.name}, Role: $roleDisplay, Ograničenja: NEMA")
            }
        }
    }
    // LIFECYCLE METHODS
    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        Log.d("Lifecycle", "?? MainActivity onResume - vidljiva")
        // DODAJ OVO: OSVEŽI TAČKE PRI POVRATKU NA APLIKACIJU
        loadPointsOfInterest()
        // Obavesti servis da je aplikacija u foregroundu
        if (isTracking) {
            val intent = Intent(this, TrackingService::class.java).apply {
                action = TrackingService.ACTION_APP_IN_FOREGROUND
            }
            startService(intent)
            Log.d("Tracking", "?? Aplikacija u foregroundu - maksimalna tačnost")
        }
        // KLJUČNO: OSVEŽI MAPU I RUTU PRI POVRATKU
        refreshMapAndRoute()
    }
    override fun onPause() {
        super.onPause()
        isActivityVisible = false
        Log.d("Lifecycle", "?? MainActivity onPause - nije vidljiva")
        // Obavesti servis da je aplikacija u pozadini
        if (isTracking) {
            val intent = Intent(this, TrackingService::class.java).apply {
                action = TrackingService.ACTION_APP_IN_BACKGROUND
            }
            startService(intent)
            Log.d("Tracking", "?? Aplikacija u pozadini - optimizujem bateriju")
        }
    }
    override fun onStop() {
        super.onStop()
        isActivityVisible = false
        Log.d("Lifecycle", "?? MainActivity onStop - nije vidljiva")
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.d("ActivityResult", "Primljen rezultat: requestCode=$requestCode, resultCode=$resultCode")

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                IMPORT_GPX_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        Log.d("ActivityResult", "GPX uvoz rute: $uri")
                        importGpxRoute(uri)
                    } ?: run {
                        Toast.makeText(this, "❌ Nije izabran fajl", Toast.LENGTH_SHORT).show()
                    }
                }
                IMPORT_GPX_POINTS_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        Log.d("ActivityResult", "GPX uvoz tačaka: $uri")
                        importGpxPoints(uri)  // OVO JE KLJUČNO - OVA METODA TREBA DA POSTOJI
                    } ?: run {
                        Toast.makeText(this, "❌ Nije izabran fajl", Toast.LENGTH_SHORT).show()
                    }
                }
                IMPORT_CSV_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        Log.d("ActivityResult", "CSV uvoz: $uri")
                        importCsvPoints(uri)  // OVO JE KLJUČNO - OVA METODA TREBA DA POSTOJI
                    }
                }
                IMPORT_BACKUP_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        Log.d("ActivityResult", "Backup uvoz: $uri")
                        importBackupData(uri)
                    }
                }
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            Log.d("ActivityResult", "Korisnik je otkazao izbor fajla")
        }
    }

    private fun importGpxPoints(uri: Uri) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("📍 Uvoz tačaka...")
            .setMessage("Učitavam tačke iz GPX fajla...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val gpxContent = inputStream.bufferedReader().use { it.readText() }

                    // PARSIRAJ TAČKE IZ GPX (waypoints)
                    val points = parseGpxPoints(gpxContent)

                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        if (points.isNotEmpty()) {
                            showImportedPointsPreview(points)
                        } else {
                            Toast.makeText(this@MainActivity, "❌ Nema tačaka u GPX fajlu", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "❌ Greška pri uvozu tačaka: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private suspend fun parseGpxPoints(gpxContent: String): List<PointOfInterest> {
        val points = mutableListOf<PointOfInterest>()

        try {
            // TRAŽI <wpt> (waypoints) TAGOVE - TO SU TAČKE INTERESA
            val waypointPattern = """<wpt lat="([^"]+)" lon="([^"]+)">([\s\S]*?)</wpt>""".toRegex()
            val matches = waypointPattern.findAll(gpxContent)

            matches.forEach { match ->
                val lat = match.groupValues[1].toDoubleOrNull()
                val lon = match.groupValues[2].toDoubleOrNull()
                val content = match.groupValues[3]

                if (lat != null && lon != null) {
                    // IZVLACI IME TAČKE
                    val namePattern = """<name>([^<]+)</name>""".toRegex()
                    val nameMatch = namePattern.find(content)
                    val name = nameMatch?.groupValues?.get(1) ?: "Tačka ${points.size + 1}"

                    // KREIRAJ POINT OF INTEREST
                    val point = PointOfInterest(
                        userId = getCurrentUserId(),
                        name = name,
                        latitude = lat,
                        longitude = lon,
                        createdAt = System.currentTimeMillis()
                    )
                    points.add(point)

                    Log.d("GPXPoints", "Pronađena tačka: $name ($lat, $lon)")
                }
            }

            // ALTERNATIVNO: TRAŽI I <rtept> (route points) KAO TAČKE
            val routePointPattern = """<rtept lat="([^"]+)" lon="([^"]+)">([\s\S]*?)</rtept>""".toRegex()
            val routeMatches = routePointPattern.findAll(gpxContent)

            routeMatches.forEach { match ->
                val lat = match.groupValues[1].toDoubleOrNull()
                val lon = match.groupValues[2].toDoubleOrNull()
                val content = match.groupValues[3]

                if (lat != null && lon != null) {
                    val namePattern = """<name>([^<]+)</name>""".toRegex()
                    val nameMatch = namePattern.find(content)
                    val name = nameMatch?.groupValues?.get(1) ?: "Ruta tačka ${points.size + 1}"

                    val point = PointOfInterest(
                        userId = getCurrentUserId(),
                        name = name,
                        latitude = lat,
                        longitude = lon,
                        createdAt = System.currentTimeMillis()
                    )
                    points.add(point)

                    Log.d("GPXPoints", "Pronađena ruta tačka: $name ($lat, $lon)")
                }
            }

            Log.d("GPXPoints", "Ukupno pronađeno ${points.size} tačaka u GPX fajlu")

        } catch (e: Exception) {
            Log.e("GPXPoints", "Greška pri parsiranju GPX tačaka: ${e.message}")
        }

        return points
    }
    private fun showNoGpxFilesHelp() {
        AlertDialog.Builder(this)
            .setTitle("📭 Nema GPX fajlova")
            .setMessage("""
        Nema GPX fajlova u Download folderu.
        
        Kako dodati GPX fajlove:
        
        1. 📥 Preuzmite GPX fajl sa interneta
        2. 💾 Sačuvajte ga u Download folder
        3. 🔄 Vratite se ovde i osvežite listu
        4. ✅ Izaberite fajl za uvoz
        
        Ili koristite File Picker opciju.
        """.trimIndent())
            .setPositiveButton("📁 File Picker") { dialog, which ->
                importRouteFromFile()
            }
            .setNegativeButton("❌ Zatvori", null)
            .show()
    }

    private fun showImportErrorDialog(fileName: String, error: String) {
        AlertDialog.Builder(this)
            .setTitle("❌ Greška pri uvozu")
            .setMessage("""
        Greška pri uvozu fajla: $fileName
        
        Problem: $error
        
        Rešenja:
        • Proverite da li je fajl oštećen
        • Pokušajte sa drugim GPX fajlom  
        • Koristite File Picker umesto Download foldera
        """.trimIndent())
            .setPositiveButton("🔄 Pokušaj Ponovo") { dialog, which ->
                importGpxFromDownloads()
            }
            .setNegativeButton("❌ Zatvori", null)
            .show()
    }

private fun showImportHelpDialog() {
    AlertDialog.Builder(this)
        .setTitle("ℹ️ Pomoc pri uvozu GPX fajlova")
        .setMessage("""
        📥 Kako uvesti GPX rute:
        
        1. **File Picker** (Preporučeno):
           • Otvara sistemski file browser
           • Radi sa svim folderima
           • Podržava Cloud storage
        
        2. **Download Folder**:
           • Direktan pristup Download folderu
           • Brži pristup lokalnim fajlovima
           • Može zahtevati dozvole
        
        📋 Podržani formati:
        • .gpx (GPS Exchange Format)
        • .xml (XML fajlovi sa rutama)
        
        💡 Savet: Ako jedna opcija ne radi, probajte drugu!
        """.trimIndent())
        .setPositiveButton("✅ Razumem", null)
        .show()
}
    override fun onDestroy() {
        super.onDestroy()
        // OČISTI RECEIVER
        if (isReceivingBackgroundUpdates) {
            try {
                unregisterReceiver(backgroundLocationReceiver)
                isReceivingBackgroundUpdates = false
            } catch (e: Exception) {
                Log.e("MainActivity", "Greška pri uklanjanju receivera u onDestroy: ${e.message}")
            }
        }
        // ZAUSTAVI LOCATION UPDATES
        stopLocationUpdates()
    }
}