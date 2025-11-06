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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var map: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapEventsOverlay: MapEventsOverlay

    private var currentLocationMarker: Marker? = null

    // Tracking varijable
    private var isTracking = false
    private var trackingStartTime: Long = 0
    private var totalDistance = 0.0
    private var currentRoute: Route? = null
    private val routePoints = mutableListOf<GeoPoint>()
    private var routePolyline: Polyline? = null

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
        setupMapClickListeners() // Za Point of Interest klikove
        setupClickListeners()

        // Inicijalizuj tracking UI
        updateTrackingStats(0.0)

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
                // Ne koristimo long press za sada
                return false
            }
        }

        mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
        map.overlays.add(0, mapEventsOverlay) // Dodaj na početak da ne prekriva druge overlay-e
    }

    private fun initializeLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Kreiraj LocationRequest za real-time updates
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000 // 5 sekundi
        ).setMinUpdateIntervalMillis(3000) // 3 sekunde minimum
            .setMaxUpdateDelayMillis(10000)   // 10 sekundi maksimalno kašnjenje
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val currentLocation = GeoPoint(location.latitude, location.longitude)

                    // Ažuriraj marker na mapi
                    showMyLocationMarker(currentLocation)

                    // Ako je tracking aktivan, dodaj tačku u rutu
                    if (isTracking) {
                        addPointToRoute(currentLocation)

                        // Ažuriraj distancu
                        updateDistance(currentLocation)
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        // Meni dugme u toolbaru
        binding.btnMenu.setOnClickListener {
            showNavigationMenu()
        }

        // Moja lokacija dugme
        binding.btnMyLocation.setOnClickListener {
            centerOnMyLocation()
        }

        // Tracking mode dugme
        binding.btnTrackingMode.setOnClickListener {
            toggleTrackingMode()
        }

        // Tracking dugmad
        setupTrackingListeners()

        // Eksport dugme
        binding.btnExport.setOnClickListener {
            exportRouteData()
        }

        // Reset dugme
        binding.btnReset.setOnClickListener {
            resetCurrentRoute()
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

        // Koristimo lastLocation za brži rezultat
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLocation = GeoPoint(location.latitude, location.longitude)

                    // Centriraj mapu na lokaciju
                    map.controller.animateTo(currentLocation)
                    map.controller.setZoom(18.0)

                    // Prikaži marker
                    showMyLocationMarker(currentLocation)

                    Toast.makeText(
                        this,
                        "Centrirano na vašu lokaciju!",
                        Toast.LENGTH_SHORT
                    ).show()
                } ?: run {
                    // Ako lastLocation nije dostupan, pokreni location updates
                    startLocationUpdates()
                    Toast.makeText(
                        this,
                        "Tražim lokaciju...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Greška: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showMyLocationMarker(location: GeoPoint) {
        // Ukloni stari marker
        currentLocationMarker?.let { marker ->
            map.overlays.remove(marker)
        }

        // Kreiraj novi marker
        val marker = Marker(map).apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "Moja lokacija"
        }

        map.overlays.add(marker)
        currentLocationMarker = marker

        // Ako je tracking aktivan, centriraj mapu automatski
        if (isTracking) {
            map.controller.animateTo(location)
        }

        map.invalidate()
    }

    private fun toggleTrackingMode() {
        trackingMode = if (trackingMode == "self") "all" else "self"

        // Ažuriraj UI
        if (trackingMode == "self") {
            binding.btnTrackingMode.text = "Samo sebe"
        } else {
            binding.btnTrackingMode.text = "Svi uređaji"
        }

        Toast.makeText(this, "Režim praćenja: ${if (trackingMode == "self") "Samo sebe" else "Svi uređaji"}", Toast.LENGTH_SHORT).show()
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
        // Start tracking
        binding.btnStartTracking.setOnClickListener {
            startTracking()
        }

        // Stop tracking
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
            Toast.makeText(this, "📌 Režim dodavanja tačaka\nKlikni na mapu da dodaš tačku", Toast.LENGTH_LONG).show()
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

        val dialog = AlertDialog.Builder(this)
            .setTitle("Dodaj tačku")
            .setMessage("Unesi ime tačke:")
            .setView(editText)
            .setPositiveButton("Dodaj") { dialog, _ ->
                val name = editText.text?.toString()?.takeIf { it.isNotBlank() } ?: "Nova tačka"
                addPointOfInterest(name, location)
            }
            .setNegativeButton("Otkaži", null)
            .create()

        dialog.show()
    }

    private fun addPointOfInterest(name: String, location: GeoPoint) {
        lifecycleScope.launch(Dispatchers.IO) {
            val app = application as App
            val point = PointOfInterest(
                userId = "user-${System.currentTimeMillis()}",
                name = name,
                latitude = location.latitude,
                longitude = location.longitude
            )

            app.pointRepository.addPoint(point)
            pointsOfInterest.add(point)

            runOnUiThread {
                addPointMarker(point)
                Toast.makeText(this@MainActivity, "Tačka '$name' dodata!", Toast.LENGTH_SHORT).show()
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

        val dialog = AlertDialog.Builder(this)
            .setTitle("Preimenuj tačku")
            .setView(editText)
            .setPositiveButton("Sačuvaj") { dialog, _ ->
                val newName = editText.text?.toString()?.takeIf { it.isNotBlank() } ?: point.name
                renamePoint(point, newName)
            }
            .setNegativeButton("Otkaži", null)
            .create()

        dialog.show()
    }

    private fun renamePoint(point: PointOfInterest, newName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val app = application as App
            val updatedPoint = point.copy(name = newName)
            app.pointRepository.updatePoint(updatedPoint)

            runOnUiThread {
                pointMarkers[point.id]?.title = newName
                map.invalidate()
                Toast.makeText(this@MainActivity, "Tačka preimenovana u '$newName'", Toast.LENGTH_SHORT).show()
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
            val app = application as App
            app.pointRepository.deletePoint(point)
            pointsOfInterest.remove(point)

            runOnUiThread {
                map.overlays.remove(marker)
                pointMarkers.remove(point.id)
                map.invalidate()
                Toast.makeText(this@MainActivity, "Tačka '${point.name}' obrisana", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadPointsOfInterest() {
        lifecycleScope.launch(Dispatchers.IO) {
            val app = application as App
            try {
                val points = app.pointRepository.getUserPoints("user-default")

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

        // Kreiraj novu rutu u bazi
        lifecycleScope.launch(Dispatchers.IO) {
            val app = application as App
            currentRoute = Route(
                userId = "user-${System.currentTimeMillis()}",
                name = "Ruta ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(java.util.Date())}"
            )
            val routeId = app.routeRepository.createRoute(currentRoute!!)
            currentRoute = currentRoute!!.copy(id = routeId)
        }

        // Pokreni location updates ako već nisu aktivni
        if (!isLocationUpdatesActive) {
            startLocationUpdates()
        }

        // Update UI
        binding.btnStartTracking.isEnabled = false
        binding.btnStopTracking.isEnabled = true
        binding.btnExport.isEnabled = false
        binding.btnReset.isEnabled = false

        // Pokreni TrackingService
        val intent = Intent(this, TrackingService::class.java).apply {
            action = "START_TRACKING"
        }
        startService(intent)

        Toast.makeText(this, "Snimanje rute započeto!", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        isTracking = false

        // Update UI
        binding.btnStartTracking.isEnabled = true
        binding.btnStopTracking.isEnabled = false
        binding.btnExport.isEnabled = true
        binding.btnReset.isEnabled = true

        // Zaustavi TrackingService
        val intent = Intent(this, TrackingService::class.java).apply {
            action = "STOP_TRACKING"
        }
        startService(intent)

        // Ažuriraj rutu u bazi
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

        // Prikaži statistike
        val duration = (System.currentTimeMillis() - trackingStartTime) / 1000
        val minutes = duration / 60
        val seconds = duration % 60

        Toast.makeText(
            this,
            "Snimanje zaustavljeno!\nUdaljenost: ${String.format("%.2f", totalDistance)} m\nVreme: ${minutes}m ${seconds}s",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun updateTrackingStats(distance: Double) {
        totalDistance = distance
        binding.tvDistance.text = "Udaljenost: ${String.format("%.2f", distance)} m"

        if (isTracking && trackingStartTime > 0) {
            val durationHours = (System.currentTimeMillis() - trackingStartTime) / 3600000.0
            if (durationHours > 0) {
                val speedKmh = (distance / 1000) / durationHours
                binding.tvSpeed.text = "Brzina: ${String.format("%.2f", speedKmh)} km/h"
            }
        } else {
            binding.tvSpeed.text = "Brzina: 0.0 km/h"
        }
    }

    private fun exportRouteData() {
        if (currentRoute == null || routePoints.isEmpty()) {
            Toast.makeText(this, "Nema podataka za eksport!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val app = application as App
            val points = app.routeRepository.getRoutePoints(currentRoute!!.id)

            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Podaci spremni za eksport!\n${points.size} tačaka",
                    Toast.LENGTH_LONG
                ).show()

                val exportText = """
                    GPS Tracker - Eksport rute
                    Ruta: ${currentRoute!!.name}
                    Udaljenost: ${String.format("%.2f", totalDistance)} m
                    Tačaka: ${points.size}
                """.trimIndent()

                println("📤 EKSPORT PODACI:\n$exportText")
            }
        }
    }

    private fun resetCurrentRoute() {
        currentRoute = null
        routePoints.clear()
        totalDistance = 0.0

        // Ukloni polyline sa mape
        routePolyline?.let { polyline ->
            map.overlays.remove(polyline)
            map.invalidate()
        }
        routePolyline = null

        // Resetuj UI
        updateTrackingStats(0.0)
        binding.btnExport.isEnabled = false
        binding.btnReset.isEnabled = false

        Toast.makeText(this, "Ruta resetovana!", Toast.LENGTH_SHORT).show()
    }

    private fun addPointToRoute(location: GeoPoint) {
        if (!isTracking) return

        routePoints.add(location)

        // Dodaj tačku u bazu
        currentRoute?.let { route ->
            lifecycleScope.launch(Dispatchers.IO) {
                val app = application as App
                val point = LocationPoint(
                    routeId = route.id,
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                app.routeRepository.addLocationPoint(point)
            }
        }

        // Ažuriraj polyline na mapi
        updateRoutePolyline()
    }

    private fun updateRoutePolyline() {
        if (routePoints.size < 2) return

        // Ukloni stari polyline
        routePolyline?.let { polyline ->
            map.overlays.remove(polyline)
        }

        // Kreiraj novi polyline
        routePolyline = Polyline().apply {
            setPoints(routePoints)
            color = Color.RED
            width = 8.0f
        }

        map.overlays.add(routePolyline)
        map.invalidate()
    }

    // Metoda za ažuriranje distance
    private fun updateDistance(newLocation: GeoPoint) {
        if (routePoints.isNotEmpty()) {
            val lastLocation = routePoints.last()
            val distance = calculateDistance(lastLocation, newLocation)
            totalDistance += distance
            updateTrackingStats(totalDistance)
        }
    }

    // Metoda za računanje distance između dve tačke
    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0].toDouble()
    }

    // Metode za upravljanje location updates
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
                // Pokreni location updates kada su dozvole date
                startLocationUpdates()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                Toast.makeText(
                    this,
                    "Lokacija je potrebna za prikaz vaše trenutne pozicije na mapi",
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
                    // Dozvole su date, pokreni location updates
                    startLocationUpdates()
                } else {
                    Toast.makeText(
                        this,
                        "Lokacija nije dozvoljena. Aplikacija će raditi sa podrazumevanom mapom.",
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
        if (isLocationUpdatesActive) {
            stopLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isLocationUpdatesActive) {
            stopLocationUpdates()
        }
    }
}