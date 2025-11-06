package jovannedeljkovic.gps_tracker_ds.ui.main

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
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
import java.util.*
import kotlin.collections.HashMap

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

    // Nove varijable za saved routes
    private var savedRoutes = mutableListOf<Route>()
    private var displayedRoutePolyline: Polyline? = null

    // Nove varijable za detalje lokacije
    private var currentSpeed: Double = 0.0
    private var currentAltitude: Double = 0.0
    private var currentBearing: Float = 0.0f
    private var stationaryStartTime: Long = 0
    private var lastMovementTime: Long = 0
    private var isStationary: Boolean = false

    // Nove varijable za navigaciju do tačke
    private var navigationPolyline: Polyline? = null

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

                    // Ažuriraj podatke lokacije
                    currentSpeed = location.speed.toDouble() * 3.6 // Pretvori u km/h
                    currentAltitude = location.altitude
                    currentBearing = location.bearing

                    // Provera da li stoji u mestu
                    checkIfStationary(location)

                    // Ažuriraj marker na mapi
                    showMyLocationMarker(currentLocation)

                    // Ako je tracking aktivan, dodaj tačku u rutu
                    if (isTracking) {
                        addPointToRoute(currentLocation)
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

        // DODATO: Saved Routes dugme
        binding.btnSavedRoutes.setOnClickListener {
            showSavedRoutes()
        }

        // DODATO: Google Maps dugme
        binding.btnGoogleMaps.setOnClickListener {
            openGoogleMaps()
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

        // Kreiraj novi marker sa detaljnim informacijama
        val marker = Marker(map).apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Moja lokacija"

            // DODATO: Detaljne informacije
            val stationaryTime = getStationaryDuration()
            val direction = getBearingDirection(currentBearing)
            val batteryLevel = getBatteryLevel()

            subDescription = """
                Korisnik: ${getUserName()}
                Baterija: ${batteryLevel}%
                Vreme: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
                Brzina: ${String.format("%.1f", currentSpeed)} km/h
                Smer: $direction (${currentBearing.toInt()}°)
                Visina: ${String.format("%.0f", currentAltitude)} m
                Stajanje: $stationaryTime
            """.trimIndent()

            // Pokušaj da postaviš custom ikonicu
            try {
                val resourceId = resources.getIdentifier("ic_map_location_pin", "drawable", packageName)
                if (resourceId != 0) {
                    val drawable = ContextCompat.getDrawable(this@MainActivity, resourceId)
                    setIcon(drawable)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            setOnMarkerClickListener { marker, _ ->
                showLocationDetailsDialog()
                true
            }
        }

        map.overlays.add(marker)
        myLocationMarker = marker

        if (isTracking) {
            map.controller.animateTo(location)
        }

        map.invalidate()
    }

    // DODATO: Funkcije za detalje lokacije
    private fun checkIfStationary(location: Location) {
        val currentTime = System.currentTimeMillis()
        val speedKmh = location.speed * 3.6

        if (speedKmh < 1.0) { // Manje od 1 km/h smatra se stajanjem
            if (!isStationary) {
                isStationary = true
                stationaryStartTime = currentTime
                lastMovementTime = currentTime
            } else {
                lastMovementTime = currentTime
            }
        } else {
            isStationary = false
            stationaryStartTime = 0
        }
    }

    private fun getStationaryDuration(): String {
        if (!isStationary || stationaryStartTime == 0L) return "0s"

        val duration = (System.currentTimeMillis() - stationaryStartTime) / 1000
        val minutes = duration / 60
        val seconds = duration % 60

        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    private fun getBearingDirection(bearing: Float): String {
        return when {
            bearing < 22.5 || bearing >= 337.5 -> "S"
            bearing < 67.5 -> "SI"
            bearing < 112.5 -> "I"
            bearing < 157.5 -> "JI"
            bearing < 202.5 -> "J"
            bearing < 247.5 -> "JZ"
            bearing < 292.5 -> "Z"
            else -> "SZ"
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

            if (level == -1 || scale == -1) 50 else (level * 100 / scale.toFloat()).toInt()
        } catch (e: Exception) {
            50 // Default vrednost ako ne može da se dobije
        }
    }

    // ISPRAVLJENA FUNKCIJA ZA KORISNIČKO IME
    private fun getUserName(): String {
        return try {
            // Proverite da li postoji shared preferences sa korisničkim podacima
            val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
            val userEmail = sharedPreferences.getString("user_email", null)

            userEmail ?: "Korisnik" // Vrati email ili "Korisnik" ako nema podataka
        } catch (e: Exception) {
            "Korisnik"
        }
    }

    private fun showLocationDetailsDialog() {
        val stationaryTime = getStationaryDuration()
        val direction = getBearingDirection(currentBearing)
        val batteryLevel = getBatteryLevel()

        val details = """
            📍 Detalji lokacije:
            
            👤 Korisnik: ${getUserName()}
            🔋 Baterija: ${batteryLevel}%
            🕐 Vreme: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
            🚗 Brzina: ${String.format("%.1f", currentSpeed)} km/h
            🧭 Smer: $direction (${currentBearing.toInt()}°)
            🏔️ Visina: ${String.format("%.0f", currentAltitude)} m
            ⏱️ Stajanje: $stationaryTime
            📍 Koordinate: 
               ${String.format("%.6f", myLocationMarker?.position?.latitude)}, 
               ${String.format("%.6f", myLocationMarker?.position?.longitude)}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Detalji lokacije")
            .setMessage(details)
            .setPositiveButton("U redu", null)
            .setNeutralButton("Google Mape") { dialog, which ->
                openGoogleMaps()
            }
            .show()
    }

    // DODATO: Funkcija za Google Mape
    private fun openGoogleMaps() {
        try {
            val location = myLocationMarker?.position
            if (location != null) {
                val uri = "geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                intent.setPackage("com.google.android.apps.maps")

                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    // Ako Google Maps nije instaliran, otvori u browseru
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

    private fun toggleTrackingMode() {
        trackingMode = if (trackingMode == "self") "all" else "self"

        // Ažuriraj tooltip ili toast sa trenutnim modom
        val modeText = if (trackingMode == "self") "Samo sebe" else "Svi uređaji"
        Toast.makeText(this, "Režim praćenja: $modeText", Toast.LENGTH_SHORT).show()
    }

    // PROMENJENO: Postavke → Podešavanja
    private fun showNavigationMenu() {
        val options = arrayOf("Podešavanja", "Istorija", "Pomoć", "Odjava")

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

    // DODATO: Saved Routes funkcionalnost
    private fun showSavedRoutes() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                savedRoutes = app.routeRepository.getUserRoutes("current-user").toMutableList()

                runOnUiThread {
                    showSavedRoutesDialog()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška pri učitavanju ruta", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSavedRoutesDialog() {
        if (savedRoutes.isEmpty()) {
            Toast.makeText(this, "Nema sačuvanih ruta", Toast.LENGTH_SHORT).show()
            return
        }

        val routeNames = savedRoutes.map {
            "${it.name}\n${formatDate(it.startTime)} - ${formatDistance(it.distance)}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Sačuvane rute (${savedRoutes.size})")
            .setItems(routeNames) { dialog, which ->
                val selectedRoute = savedRoutes[which]
                showRouteOptions(selectedRoute)
            }
            .setNegativeButton("Zatvori", null)
            .show()
    }

    private fun showRouteOptions(route: Route) {
        val options = arrayOf("Prikaži na mapi", "Obriši rutu", "Otkaži")

        AlertDialog.Builder(this)
            .setTitle("Opcije za: ${route.name}")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> displayRouteOnMap(route)
                    1 -> deleteRoute(route)
                    // 2 je Otkaži
                }
            }
            .show()
    }

    private fun displayRouteOnMap(route: Route) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val app = application as App
                val points = app.routeRepository.getRoutePoints(route.id)

                runOnUiThread {
                    // Očistite prethodno prikazanu rutu
                    clearDisplayedRoute()

                    // Prikažite tačke rute na mapi
                    val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
                    if (geoPoints.isNotEmpty()) {
                        displayedRoutePolyline = Polyline().apply {
                            setPoints(geoPoints)
                            color = Color.BLUE
                            width = 6.0f
                        }
                        map.overlays.add(displayedRoutePolyline)

                        // Centriraj mapu na prvu tačku
                        map.controller.animateTo(geoPoints.first())
                        map.controller.setZoom(15.0)

                        Toast.makeText(this@MainActivity, "Ruta '${route.name}' prikazana", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Ruta nema tačaka", Toast.LENGTH_SHORT).show()
                    }
                    map.invalidate()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Greška pri prikazu rute: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun clearDisplayedRoute() {
        // Ukloni samo prikazanu rutu (plavu), ne i trenutnu tracking rutu (crvenu)
        displayedRoutePolyline?.let { polyline ->
            map.overlays.remove(polyline)
            displayedRoutePolyline = null
            map.invalidate()
        }
    }

    private fun deleteRoute(route: Route) {
        AlertDialog.Builder(this)
            .setTitle("Brisanje rute")
            .setMessage("Da li ste sigurni da želite da obrišete rutu '${route.name}'?")
            .setPositiveButton("Obriši") { dialog, which ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val app = application as App
                        app.routeRepository.deleteRoute(route)

                        runOnUiThread {
                            // Ako je ova ruta trenutno prikazana, ukloni je
                            if (displayedRoutePolyline != null) {
                                clearDisplayedRoute()
                            }
                            Toast.makeText(this@MainActivity, "Ruta '${route.name}' obrisana", Toast.LENGTH_SHORT).show()
                            showSavedRoutes() // Osveži listu
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
            Toast.makeText(this, "📍 Režim dodavanja tačaka - klikni na mapu", Toast.LENGTH_SHORT).show()
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
                    userId = "current-user",
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

    // ISPRAVLJENA addPointMarker FUNKCIJA - POBOLJŠAN PRIKAZ IMENA
    private fun addPointMarker(point: PointOfInterest) {
        val marker = Marker(map).apply {
            position = GeoPoint(point.latitude, point.longitude)

            // POBOLJŠAN PRIKAZ TEKSTA
            title = point.name // OVO ĆE PRIKAZATI IME ISPOD MARKERA
            textLabelFontSize = 16 // Povećana veličina fonta
            textLabelBackgroundColor = Color.argb(200, 255, 255, 255) // Bela pozadina
            textLabelForegroundColor = Color.BLACK // Crna boja teksta

            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            // POKUŠAJTE SA RAZLIČITIM IKONAMA
            try {
                // Prvo probajte sa custom ikonom
                val resourceId = resources.getIdentifier("ic_point_of_interest", "drawable", packageName)
                if (resourceId != 0) {
                    val drawable = ContextCompat.getDrawable(this@MainActivity, resourceId)
                    setIcon(drawable)
                } else {
                    // Fallback na drugu ikonu
                    val fallbackId = resources.getIdentifier("ic_my_location_pin", "drawable", packageName)
                    if (fallbackId != 0) {
                        val fallbackDrawable = ContextCompat.getDrawable(this@MainActivity, fallbackId)
                        setIcon(fallbackDrawable)
                    } else {
                        // Final fallback - sistemska ikona
                        setIcon(ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_dialog_map))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                setIcon(ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_dialog_map))
            }

            // DODAJTE DUGME ZA PRIKAZ DETALJA
            setOnMarkerClickListener { marker, _ ->
                showPointDetailsDialog(point)
                true
            }
        }

        map.overlays.add(marker)
        pointMarkers[point.id] = marker
        map.invalidate()

        // FORSIRAJTE OSNVEŽAVANJE MAPE
        map.post {
            map.invalidate()
        }
    }

    // DODATA FUNKCIJA ZA DETALJAN PRIKAZ TAČKE SA NAVIGACIJOM
    private fun showPointDetailsDialog(point: PointOfInterest) {
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(point.createdAt))

        // Izračunaj udaljenost do tačke
        val distanceToPoint = calculateDistanceToPoint(point)

        val message = """
            📍 ${point.name}
            
            🌐 Koordinate:
            Lat: ${String.format("%.6f", point.latitude)}
            Lng: ${String.format("%.6f", point.longitude)}
            
            📅 Kreirano: $date
            👤 Korisnik: ${getUserName()}
            📏 Udaljenost: ${formatDistance(distanceToPoint)}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Detalji tačke")
            .setMessage(message)
            .setPositiveButton("U redu") { dialog, _ -> }
            .setNeutralButton("Navigiraj") { dialog, _ ->
                showNavigationToPoint(point)
            }
            .setNegativeButton("Obriši") { dialog, _ ->
                pointMarkers[point.id]?.let { marker ->
                    showDeletePointDialog(point, marker)
                }
            }
            .show()
    }

    // DODATA FUNKCIJA ZA NAVIGACIJU DO TAČKE
    private fun showNavigationToPoint(point: PointOfInterest) {
        val myLocation = myLocationMarker?.position
        if (myLocation == null) {
            Toast.makeText(this, "Lokacija nije dostupna", Toast.LENGTH_SHORT).show()
            return
        }

        // Očistite prethodnu navigacionu liniju
        clearNavigationLine()

        // Kreirajte tačke za navigaciju (pojednostavljena putanja)
        val navigationPoints = listOf(
            myLocation,
            GeoPoint(point.latitude, point.longitude)
        )

        // Dodajte liniju za navigaciju
        navigationPolyline = Polyline().apply {
            setPoints(navigationPoints)
            color = Color.GREEN
            width = 8.0f
        }
        map.overlays.add(navigationPolyline)

        // Izračunajte udaljenost
        val distance = calculateDistance(myLocation, GeoPoint(point.latitude, point.longitude))

        // Centrirajte mapu na sredinu između dve tačke
        val midPoint = GeoPoint(
            (myLocation.latitude + point.latitude) / 2,
            (myLocation.longitude + point.longitude) / 2
        )
        map.controller.animateTo(midPoint)

        Toast.makeText(
            this,
            "Navigacija do '${point.name}'\nUdaljenost: ${formatDistance(distance)}",
            Toast.LENGTH_LONG
        ).show()

        map.invalidate()
    }

    // DODATA FUNKCIJA ZA RAČUNANJE UDALJENOSTI DO TAČKE
    private fun calculateDistanceToPoint(point: PointOfInterest): Double {
        val myLocation = myLocationMarker?.position
        return if (myLocation != null) {
            calculateDistance(myLocation, GeoPoint(point.latitude, point.longitude))
        } else {
            0.0
        }
    }

    // DODATA FUNKCIJA ZA ČIŠĆENJE NAVIGACIONE LINIJE
    private fun clearNavigationLine() {
        navigationPolyline?.let { polyline ->
            map.overlays.remove(polyline)
            navigationPolyline = null
            map.invalidate()
        }
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

    // POBOLJŠANA loadPointsOfInterest FUNKCIJA
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

                            // DODAJTE MALO KAŠNJENJE ZA BOLJI PRIKAZ
                            Handler(Looper.getMainLooper()).postDelayed({
                                map.invalidate()
                            }, 100)
                        }
                    }

                    // FORSIRAJTE OSNVEŽAVANJE NAKON ŠTO SE SVE TAČKE DODAJU
                    map.postDelayed({
                        map.invalidate()
                        if (points.isNotEmpty()) {
                            Toast.makeText(this@MainActivity, "Učitano ${points.size} tačaka", Toast.LENGTH_SHORT).show()
                        }
                    }, 500)
                }
            } catch (e: Exception) {
                // Baza je prazna - to je ok
                Log.d("MainActivity", "Nema sačuvanih tačaka: ${e.message}")
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
            "Snimanje zaustavljeno!\nUdaljenost: ${formatDistance(totalDistance)}\nVreme: ${minutes}m ${seconds}s",
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

    // POBOLJŠANA FUNKCIJA SA FORMATIRANJEM DUŽINE
    private fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            String.format("%.1f m", meters)
        } else {
            String.format("%.2f km", meters / 1000)
        }
    }

    private fun updateTrackingStats(distance: Double, speed: Double) {
        binding.tvDistance.text = "Udaljenost: ${formatDistance(distance)}"
        binding.tvSpeed.text = "Brzina: ${String.format("%.1f", speed)} km/h"
    }

    // POBOLJŠANA EXPORT FUNKCIJA - SA DETALJNOM LOKACIJOM
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
                val fileName = "gps_tracker_${currentRoute!!.name.replace(" ", "_")}_$timestamp.csv"

                // KORISTIMO OSNOVNI EXTERNAL FILES DIR - OVO JE SIGURNA LOKACIJA
                val baseDir = getExternalFilesDir(null)
                val file = File(baseDir, fileName)

                FileWriter(file).use { writer ->
                    // Header
                    writer.append("Route: ${currentRoute!!.name}\n")
                    writer.append("Start Time: ${formatDate(currentRoute!!.startTime)}\n")
                    writer.append("Total Distance: ${formatDistance(currentRoute!!.distance)}\n")
                    writer.append("Points Count: ${points.size}\n")
                    writer.append("Exported: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                    writer.append("\n")

                    // Podaci
                    writer.append("Point,Latitude,Longitude,Timestamp,Distance (m),Time\n")

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

                        val pointTime = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(point.timestamp))

                        writer.append("${index + 1},${point.latitude},${point.longitude},${point.timestamp},${String.format("%.2f", cumulativeDistance)},\"$pointTime\"\n")
                    }
                }

                runOnUiThread {
                    val absolutePath = file.absolutePath

                    // POKAŽI TAČNU LOKACIJU KORISNIKU
                    val message = """
                        📊 EKSPORT ZAVRŠEN!
                        
                        📁 FAJL: $fileName
                        📍 LOKACIJA: $absolutePath
                        
                        🔍 KAKO PRONAĆI FAJL:
                        1. Otvorite 'File Manager' na telefonu
                        2. Idite na 'Internal Storage'
                        3. Pronađite: 'Android → data → jovannedeljkovic.gps_tracker_ds → files'
                        4. Tu je vaš CSV fajl!
                        
                        💡 Kopirajte fajl na računar i otvorite ga u Excelu!
                    """.trimIndent()

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Eksport Uspešan! ✅")
                        .setMessage(message)
                        .setPositiveButton("OK") { dialog, _ -> }
                        .setNeutralButton("Podeli fajl") { dialog, which ->
                            shareFile(file)
                        }
                        .show()

                    // Takođe pokaži u Toastu gde je fajl
                    Toast.makeText(
                        this@MainActivity,
                        "Fajl sačuvan: $absolutePath",
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

    // DODAJTE OVU FUNKCIJU ZA DELJENJE FAJLA
    private fun shareFile(file: File) {
        try {
            val uri = Uri.fromFile(file)
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/csv"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.putExtra(Intent.EXTRA_SUBJECT, "GPS Tracker Export")
            intent.putExtra(Intent.EXTRA_TEXT, "Eksportovana ruta iz GPS Tracker aplikacije")

            startActivity(Intent.createChooser(intent, "Podeli CSV fajl"))
        } catch (e: Exception) {
            Toast.makeText(this, "Greška pri deljenju: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // POBOLJŠANA resetCurrentRoute FUNKCIJA
    private fun resetCurrentRoute() {
        // Resetuj samo trenutno snimljenu rutu, ne briši tačke
        currentRoute = null
        routePoints.clear()
        totalDistance = 0.0
        lastLocation = null

        // Ukloni samo trenutnu rutu (crvenu), ne i prikazane sačuvane rute ili tačke
        routePolyline?.let { polyline ->
            map.overlays.remove(polyline)
            routePolyline = null
            map.invalidate()
        }

        updateTrackingStats(0.0, 0.0)
        binding.btnExport.isEnabled = false
        binding.btnReset.isEnabled = false

        Toast.makeText(this, "Trenutna ruta resetovana!", Toast.LENGTH_SHORT).show()
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

    // DODATA FUNKCIJA ZA FORMATIRANJE DATUMA
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
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

    // PROMENJENO: showSettings → showSettings (ali sa promenjenim tekstom)
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