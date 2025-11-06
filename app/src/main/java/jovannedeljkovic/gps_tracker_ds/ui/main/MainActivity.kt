package jovannedeljkovic.gps_tracker_ds.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import com.google.android.gms.location.LocationServices
import jovannedeljkovic.gps_tracker_ds.App
import jovannedeljkovic.gps_tracker_ds.data.entities.LocationPoint
import jovannedeljkovic.gps_tracker_ds.data.entities.PointOfInterest
import jovannedeljkovic.gps_tracker_ds.data.entities.Route
import jovannedeljkovic.gps_tracker_ds.databinding.ActivityMainBinding
import jovannedeljkovic.gps_tracker_ds.service.TrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var map: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var isFollowingLocation = false
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
        setupClickListeners()

        // Inicijalizuj tracking UI
        updateTrackingStats(0.0)

        // Point of Interest funkcionalnosti
        setupPointOfInterestMode()
        setupMapClickListener()
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

    private fun initializeLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun setupClickListeners() {
        // FAB za lokaciju - toggle follow mode
        binding.fabMyLocation.setOnClickListener {
            if (isFollowingLocation) {
                // Isključi follow mode
                isFollowingLocation = false
                updateFabIcon()
                Toast.makeText(this, "Auto-follow isključen", Toast.LENGTH_SHORT).show()
            } else {
                // Uključi follow mode i centriraj
                isFollowingLocation = true
                updateFabIcon()
                getCurrentLocation()
                Toast.makeText(this, "Auto-follow uključen", Toast.LENGTH_SHORT).show()
            }
        }

        // Odjava dugme
        binding.btnLogout.setOnClickListener {
            // Zaustavi tracking ako je aktivan
            if (isTracking) {
                stopTracking()
            }
            finish()
            Toast.makeText(this, "Uspešno ste se odjavili", Toast.LENGTH_SHORT).show()
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
        // FAB za point mode
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

    private fun setupMapClickListener() {
        map.setOnClickListener { event ->
            if (isPointMode) {
                val iGeoPoint = map.projection.fromPixels(event.x.toInt(), event.y.toInt())
                val geoPoint = GeoPoint(iGeoPoint.latitude, iGeoPoint.longitude)
                showAddPointDialog(geoPoint)
            }
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
                // Ažuriraj marker
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
                // Ukloni marker
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

        // Update UI
        binding.btnStartTracking.isEnabled = false
        binding.btnStopTracking.isEnabled = true
        binding.btnStartTracking.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        binding.btnStopTracking.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
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
        binding.btnStartTracking.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        binding.btnStopTracking.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
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

        // Izračunaj brzinu (prosečna brzina)
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
            color = ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark)
            width = 8.0f
        }

        map.overlays.add(routePolyline)
        map.invalidate()
    }

    private fun updateFabIcon() {
        if (isFollowingLocation) {
            binding.fabMyLocation.setImageResource(android.R.drawable.ic_media_play)
        } else {
            binding.fabMyLocation.setImageResource(android.R.drawable.ic_menu_mylocation)
        }
    }

    private fun checkLocationPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation()
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
                    getCurrentLocation()
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

    private fun getCurrentLocation() {
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

        showLoading()

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLocation = GeoPoint(location.latitude, location.longitude)
                    showLocationOnMap(currentLocation)

                    // Dodaj tačku u rutu ako je tracking aktivan
                    if (isTracking) {
                        addPointToRoute(currentLocation)
                    }

                    Toast.makeText(
                        this,
                        "Lokacija pronađena!",
                        Toast.LENGTH_SHORT
                    ).show()
                } ?: run {
                    Toast.makeText(
                        this,
                        "Lokacija nije dostupna. Proverite GPS.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                hideLoading()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Greška: ${e.message}", Toast.LENGTH_SHORT).show()
                hideLoading()
            }
    }

    private fun showLocationOnMap(location: GeoPoint) {
        // Ukloni stari marker
        currentLocationMarker?.let { marker ->
            map.overlays.remove(marker)
        }

        // Kreiraj novi marker
        val marker = Marker(map)
        marker.position = location
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.title = "Vaša lokacija"

        map.overlays.add(marker)
        currentLocationMarker = marker

        // Auto-follow ako je uključen
        if (isFollowingLocation) {
            map.controller.animateTo(location)
        }

        map.invalidate()
    }

    private fun showLoading() {
        binding.fabMyLocation.isEnabled = false
        binding.fabMyLocation.setImageResource(android.R.drawable.ic_popup_sync)
    }

    private fun hideLoading() {
        binding.fabMyLocation.isEnabled = true
        updateFabIcon()
    }

    // Lifecycle metode za mapu
    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}