package jovannedeljkovic.gps_tracker_ds.service

import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.*
import jovannedeljkovic.gps_tracker_ds.App
import jovannedeljkovic.gps_tracker_ds.data.entities.LocationPoint
import jovannedeljkovic.gps_tracker_ds.data.entities.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var currentRoute: Route? = null
    private var lastLocation: Location? = null
    private var totalDistance = 0.0

    companion object {
        val isTracking = MutableLiveData<Boolean>().apply { value = false }
        val currentDistance = MutableLiveData<Double>().apply { value = 0.0 }
    }

    override fun onCreate() {
        super.onCreate()
        initializeLocation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_TRACKING" -> startTracking()
            "STOP_TRACKING" -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initializeLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    onNewLocation(location)
                }
            }
        }
    }

    private fun startTracking() {
        CoroutineScope(Dispatchers.IO).launch {
            val app = applicationContext as App

            // Privremeno: kreiraj rutu bez usera (kasnije ćemo dodati user management)
            currentRoute = Route(userId = "temp-user")
            val routeId = app.routeRepository.createRoute(currentRoute!!)
            currentRoute = currentRoute!!.copy(id = routeId)

            // Postavi LiveData na UI thread
            with(kotlinx.coroutines.Dispatchers.Main) {
                isTracking.value = true
            }

            startLocationUpdates()

            // Toast mora biti na UI thread
            with(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(this@TrackingService, "Snimanje započeto!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopTracking() {
        stopLocationUpdates()

        currentRoute?.let { route ->
            val completedRoute = route.copy(
                isCompleted = true,
                endTime = System.currentTimeMillis(),
                distance = totalDistance
            )

            CoroutineScope(Dispatchers.IO).launch {
                val app = applicationContext as App
                app.routeRepository.updateRoute(completedRoute)
            }
        }

        isTracking.postValue(false)
        currentRoute = null
        totalDistance = 0.0
        currentDistance.postValue(0.0)

        Toast.makeText(this, "Snimanje zaustavljeno!", Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    private fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null
            )
        } catch (e: SecurityException) {
            Toast.makeText(this, "Greška: Dozvola za lokaciju nije data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun onNewLocation(location: Location) {
        currentRoute?.let { route ->
            CoroutineScope(Dispatchers.IO).launch {
                val app = applicationContext as App

                // Snimi tačku
                val point = LocationPoint(
                    routeId = route.id,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy
                )
                app.routeRepository.addLocationPoint(point)

                // Izračunaj distancu
                lastLocation?.let { last ->
                    val distance = last.distanceTo(location).toDouble()
                    totalDistance += distance
                    currentDistance.postValue(totalDistance)
                }

                lastLocation = location
            }
        }
    }
}