package jovannedeljkovic.gps_tracker_ds.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import jovannedeljkovic.gps_tracker_ds.App
import jovannedeljkovic.gps_tracker_ds.R
import jovannedeljkovic.gps_tracker_ds.data.entities.LocationPoint
import jovannedeljkovic.gps_tracker_ds.data.entities.PointOfInterest
import jovannedeljkovic.gps_tracker_ds.data.entities.Route
import jovannedeljkovic.gps_tracker_ds.databinding.ActivityMainBinding
import jovannedeljkovic.gps_tracker_ds.service.TrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var map: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapEventsOverlay: MapEventsOverlay

    private var currentLocationMarker: Marker? = null
    private var myLocationMarker: Marker? = null

    // Tracking varijable
    private var isTracking = false
    private var trackingStartTime: Long = 0
    private var totalDistance = 0.0
    private var currentRoute: Route? = null
    private val routePoints = mutableListOf<GeoPoint>()
    private var routePolyline: Polyline? = null
    private var lastLocation: Location? = null

    // Point of Interest varijable
    private val pointsOfInterest = mutableListOf<PointOfInterest>()
    private val pointMarkers = mutableMapOf<String, Marker>()
    private var isPointMode = false

    // Location updates varijable
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var isLocationUpdatesActive = false

    // Nove varijable za tracking mode
    private var trackingMode = "self" // "self" ili "all"

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Konfiguracija OSMdroid
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osm_prefs", MODE_PRIVATE)
        )

        initializeMap()
        initializeLocationClient()
        setupMapClickListeners()
        setupClickListeners()

        // Inicijalizuj tracking UI
        updateTrackingStats(0.0, 0.0)

        // Point of Interest funkcionalnosti
        setupPointOfInterestMode()
        loadPointsOfInterest()

        checkLocationPermissions()
    }

    private fun initializeMap() {
        map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val controller = map.controller
        controller.setZoom(15.0)
        val defaultLocation = GeoPoint(44.7866, 20.4489) // Beograd
        controller.setCenter(defaultLocation)
    }

    private fun setupMapClickListeners() {
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let { geoPoint ->
                    if (isPointMode) {
                        showAddPointDialog(geoPoint)
                        return true
                    }
                }
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                return false
            }
        }

        mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
        map.overlays.add(0, mapEventsOverlay)
    }

    private fun initializeLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000 // 3 sekunde
        ).setMinUpdateIntervalMillis(2000) // 2 sekunde minimum
            .setMaxUpdateDelayMillis(5000)   // 5 sekundi maksimalno kašnjenje
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.forEach { location ->
                    val currentLocation = GeoPoint(location.latitude, location.longitude)

                    // Ažuriraj marker na mapi
                    showMyLocationMarker(currentLocation)

                    // Ako je tracking aktivan, dodaj tačku u rutu
                    if (isTracking) {
                        addPointToRoute(currentLocation)

                        // Ažuriraj distancu i brzinu
                        updateDistanceAndSpeed(location)
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnMenu.setOnClickListener {
            showNavigationMenu()
        }

        binding.btnMyLocation.setOnClickListener {
            centerOnMyLocation()
        }

        binding.btnTrackingMode.setOnClickListener {
            toggleTrackingMode()
        }

        binding.btnExport.setOnClickListener {
            exportRouteData()
        }

        binding.btnReset.setOnClickListener {
            resetCurrentRoute()
        }

        setupTrackingListeners()
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

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLocation = GeoPoint(location.latitude, location.longitude)
                    map.controller.animateTo(currentLocation)
                    map.controller.setZoom(18.0)
                    showMyLocationMarker(currentLocation)
                    Toast.makeText(this, "Centrirano na vašu lokaciju!", Toast.LENGTH_SHORT).show()
                } ?: run {
                    startLocationUpdates()
                    Toast.makeText(this, "Tražim lokaciju...", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Greška: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showMyLocationMarker(location: GeoPoint) {
        // Ukloni stari marker
        myLocationMarker?.let { marker ->
            map.overlays.remove(marker)
        }

        // Kreiraj novi marker
        val marker = Marker(map).apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Moja lokacija"
            // Pokušaj da postaviš custom ikonicu ako postoji
            try {
                // Prvo proveri da li drawable postoji
                val resourceId = resources.getIdentifier("ic_map_location_pin", "drawable", packageName)
                if (resourceId != 0) {
                    val drawable = ContextCompat.getDrawable(this@MainActivity, resourceId)
                    setIcon(drawable)
                }
            } catch (e: Exception) {
                // Koristi default ikonicu ako custom ne postoji
                e.printStackTrace()
            }
        }

        map.overlays.add(marker)
        myLocationMarker = marker

        if (isTracking) {
            map.controller.animateTo(location)
        }

        map.invalidate()
    }

    private fun toggleTrackingMode() {
        trackingMode = if (trackingMode == "self") "all" else "self"

        // Ažuriraj tooltip ili toast sa trenutnim modom
        val modeText = if (trackingMode == "self") "Samo sebe" else "Svi uređaji"
        Toast.makeText(this, "Režim praćenja: $modeText", Toast.LENGTH_SHORT).show()
    }

    private fun showNavigationMenu() {
        val options = arrayOf("Postavke", "Istorija", "Pomoć", "Odjava")

        AlertDialog.Builder(this)
            .setTitle("Meni")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showSettings()
                    1 -> showHistory()
                    2 -> showHelp()
                    3 -> logout()
                }
            }
            .show()
    }

    private fun setupTrackingListeners() {
        binding.btnStartTracking.setOnClickListener {
            startTracking()
        }

        binding.btnStopTracking.setOnClickListener {
            stopTracking()
        }
    }

    // POINT OF INTEREST FUNKCIONALNOSTI
    private fun setupPointOfInterestMode() {
        binding.fabAddPoint.setOnClickListener {
            togglePointMode()
        }
    }

    private fun togglePointMode() {
        isPointMode = !isPointMode

        if (isPointMode) {
            binding.fabAddPoint.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            binding.fabAddPoint.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_light)
            Toast.makeText(this, "📌 Režim dodavanja tačaka - klikni na mapu", Toast.LENGTH_SHORT).show()
        } else {
            binding.fabAddPoint.setImageResource(android.R.drawable.ic_input_add)
            binding.fabAddPoint.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_blue_light)
            Toast.makeText(this, "Režim dodavanja tačaka isključen", Toast.LENGTH_SHORT).show()
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
                val point = PointOfInterest(
                    userId = "current-user", // Koristi konstantan user ID za bolje čuvanje
                    name = name,
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                app.pointRepository.addPoint(point)

                runOnUiThread {
                    addPointMarker(point)
                    Toast.makeText(this@MainActivity, "Tačka '$name' dodata!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška pri čuvanju tačke: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addPointMarker(point: PointOfInterest) {
        val marker = Marker(map).apply {
            position = GeoPoint(point.latitude, point.longitude)
            title = point.name
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { marker, _ ->
                showPointOptionsDialog(point, marker)
                true
            }
        }

        map.overlays.add(marker)
        pointMarkers[point.id] = marker
        map.invalidate()
    }

    private fun showPointOptionsDialog(point: PointOfInterest, marker: Marker) {
        val options = arrayOf("Preimenuj", "Obriši", "Otkaži")

        AlertDialog.Builder(this)
            .setTitle("Tačka: ${point.name}")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showRenamePointDialog(point)
                    1 -> showDeletePointDialog(point, marker)
                    // 2 je Otkaži - ne radi ništa
                }
            }
            .show()
    }

    private fun showRenamePointDialog(point: PointOfInterest) {
        val editText = EditText(this).apply {
            setText(point.name)
            hint = "Novo ime tačke"
        }

        AlertDialog.Builder(this)
            .setTitle("Preimenuj tačku")
            .setView(editText)
            .setPositiveButton("Sačuvaj") { dialog, _ ->
                val newName = editText.text?.toString()?.takeIf { it.isNotBlank() } ?: point.name
                renamePoint(point, newName)
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun renamePoint(point: PointOfInterest, newName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val updatedPoint = point.copy(name = newName)
                app.pointRepository.updatePoint(updatedPoint)

                runOnUiThread {
                    pointMarkers[point.id]?.title = newName
                    map.invalidate()
                    Toast.makeText(this@MainActivity, "Tačka preimenovana u '$newName'", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška pri preimenovanju", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeletePointDialog(point: PointOfInterest, marker: Marker) {
        AlertDialog.Builder(this)
            .setTitle("Brisanje tačke")
            .setMessage("Da li ste sigurni da želite da obrišete tačku '${point.name}'?")
            .setPositiveButton("Obriši") { dialog, _ ->
                deletePoint(point, marker)
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun deletePoint(point: PointOfInterest, marker: Marker) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                app.pointRepository.deletePoint(point)

                runOnUiThread {
                    map.overlays.remove(marker)
                    pointMarkers.remove(point.id)
                    map.invalidate()
                    Toast.makeText(this@MainActivity, "Tačka '${point.name}' obrisana", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška pri brisanju", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadPointsOfInterest() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val points = app.pointRepository.getUserPoints("current-user")

                runOnUiThread {
                    points.forEach { point ->
                        if (!pointsOfInterest.any { it.id == point.id }) {
                            pointsOfInterest.add(point)
                            addPointMarker(point)
                        }
                    }
                }
            } catch (e: Exception) {
                // Baza je prazna - to je ok
            }
        }
    }

    private fun startTracking() {
        isTracking = true
        trackingStartTime = System.currentTimeMillis()
        totalDistance = 0.0
        routePoints.clear()
        lastLocation = null

        lifecycleScope.launch(Dispatchers.IO) {
            val app = application as App
            currentRoute = Route(
                userId = "current-user",
                name = "Ruta ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}"
            )
            val routeId = app.routeRepository.createRoute(currentRoute!!)
            currentRoute = currentRoute!!.copy(id = routeId)
        }

        if (!isLocationUpdatesActive) {
            startLocationUpdates()
        }

        binding.btnStartTracking.isEnabled = false
        binding.btnStopTracking.isEnabled = true
        binding.btnExport.isEnabled = false
        binding.btnReset.isEnabled = false

        val intent = Intent(this, TrackingService::class.java).apply {
            action = "START_TRACKING"
        }
        startService(intent)

        Toast.makeText(this, "Snimanje rute započeto!", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        isTracking = false

        binding.btnStartTracking.isEnabled = true
        binding.btnStopTracking.isEnabled = false
        binding.btnExport.isEnabled = true
        binding.btnReset.isEnabled = true

        val intent = Intent(this, TrackingService::class.java).apply {
            action = "STOP_TRACKING"
        }
        startService(intent)

        currentRoute?.let { route ->
            val duration = System.currentTimeMillis() - trackingStartTime
            val completedRoute = route.copy(
                isCompleted = true,
                endTime = System.currentTimeMillis(),
                distance = totalDistance,
                duration = duration
            )

            lifecycleScope.launch(Dispatchers.IO) {
                val app = application as App
                app.routeRepository.updateRoute(completedRoute)
            }
        }

        val duration = (System.currentTimeMillis() - trackingStartTime) / 1000
        val minutes = duration / 60
        val seconds = duration % 60

        Toast.makeText(
            this,
            "Snimanje zaustavljeno!\nUdaljenost: ${String.format("%.2f", totalDistance)} m\nVreme: ${minutes}m ${seconds}s",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun updateDistanceAndSpeed(newLocation: Location) {
        lastLocation?.let { lastLoc ->
            val distance = lastLoc.distanceTo(newLocation).toDouble()
            totalDistance += distance

            // Izračunaj brzinu (m/s u km/h)
            val timeDiff = (newLocation.time - lastLoc.time) / 1000.0 // u sekundama
            val speedMs = if (timeDiff > 0) distance / timeDiff else 0.0
            val speedKmh = speedMs * 3.6

            updateTrackingStats(totalDistance, speedKmh)
        }
        lastLocation = newLocation
    }

    private fun updateTrackingStats(distance: Double, speed: Double) {
        binding.tvDistance.text = "Udaljenost: ${String.format("%.2f", distance)} m"
        binding.tvSpeed.text = "Brzina: ${String.format("%.2f", speed)} km/h"
    }

    private fun exportRouteData() {
        if (currentRoute == null || routePoints.isEmpty()) {
            Toast.makeText(this, "Nema podataka za eksport!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val points = app.routeRepository.getRoutePoints(currentRoute!!.id)

                // Kreiraj CSV fajl
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "gps_tracker_export_$timestamp.csv"
                val file = File(getExternalFilesDir(null), fileName)

                FileWriter(file).use { writer ->
                    // Header
                    writer.append("Latitude,Longitude,Timestamp,Distance\n")

                    // Podaci
                    var cumulativeDistance = 0.0
                    points.forEachIndexed { index, point ->
                        if (index > 0) {
                            val prevPoint = points[index - 1]
                            val distance = calculateDistance(
                                GeoPoint(prevPoint.latitude, prevPoint.longitude),
                                GeoPoint(point.latitude, point.longitude)
                            )
                            cumulativeDistance += distance
                        }
                        writer.append("${point.latitude},${point.longitude},${point.timestamp},${String.format("%.2f", cumulativeDistance)}\n")
                    }
                }

                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Podaci eksportovani u: ${file.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Greška pri eksportu: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0].toDouble()
    }

    private fun resetCurrentRoute() {
        currentRoute = null
        routePoints.clear()
        totalDistance = 0.0
        lastLocation = null

        routePolyline?.let { polyline ->
            map.overlays.remove(polyline)
            map.invalidate()
        }
        routePolyline = null

        updateTrackingStats(0.0, 0.0)
        binding.btnExport.isEnabled = false
        binding.btnReset.isEnabled = false

        Toast.makeText(this, "Ruta resetovana!", Toast.LENGTH_SHORT).show()
    }

    private fun addPointToRoute(location: GeoPoint) {
        if (!isTracking) return

        routePoints.add(location)

        currentRoute?.let { route ->
            lifecycleScope.launch(Dispatchers.IO) {
                val app = application as App
                val point = LocationPoint(
                    routeId = route.id,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis()
                )
                app.routeRepository.addLocationPoint(point)
            }
        }

        updateRoutePolyline()
    }

    private fun updateRoutePolyline() {
        if (routePoints.size < 2) return

        routePolyline?.let { polyline ->
            map.overlays.remove(polyline)
        }

        routePolyline = Polyline().apply {
            setPoints(routePoints)
            color = Color.RED
            width = 8.0f
        }

        map.overlays.add(routePolyline)
        map.invalidate()
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
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null
        )
        isLocationUpdatesActive = true
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationUpdatesActive = false
    }

    private fun checkLocationPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                Toast.makeText(
                    this,
                    "Lokacija je potrebna za praćenje kretanja",
                    Toast.LENGTH_LONG
                ).show()
                requestLocationPermissions()
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
                    Toast.makeText(
                        this,
                        "Lokacija nije dozvoljena. Neke funkcionalnosti neće raditi.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showSettings() {
        Toast.makeText(this, "Postavke će biti dostupne uskoro", Toast.LENGTH_SHORT).show()
    }

    private fun showHistory() {
        Toast.makeText(this, "Istorija će biti dostupna uskoro", Toast.LENGTH_SHORT).show()
    }

    private fun showHelp() {
        Toast.makeText(this, "Pomoć će biti dostupna uskoro", Toast.LENGTH_SHORT).show()
    }

    private fun logout() {
        if (isTracking) {
            stopTracking()
        }
        if (isLocationUpdatesActive) {
            stopLocationUpdates()
        }
        finish()
        Toast.makeText(this, "Uspešno ste se odjavili", Toast.LENGTH_SHORT).show()
    }

    // Lifecycle metode za mapu
    override fun onResume() {
        super.onResume()
        map.onResume()
        if ((isTracking || !isLocationUpdatesActive) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isLocationUpdatesActive) {
            stopLocationUpdates()
        }
    }
}