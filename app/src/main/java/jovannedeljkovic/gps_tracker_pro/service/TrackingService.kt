package jovannedeljkovic.gps_tracker_pro.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import jovannedeljkovic.gps_tracker_pro.R
import jovannedeljkovic.gps_tracker_pro.ui.main.MainActivity

class TrackingService : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var isAppInForeground = true
    private var backgroundUpdateInterval = 15000L
    private var foregroundUpdateInterval = 5000L

    companion object {
        private const val CHANNEL_ID = "tracking_service_channel"
        private const val TAG = "TrackingService"
        private const val SERVICE_NOTIFICATION_ID = 1001
        const val ACTION_START_TRACKING = "START_TRACKING"
        const val ACTION_STOP_TRACKING = "STOP_TRACKING"
        const val ACTION_APP_IN_FOREGROUND = "APP_FOREGROUND"
        const val ACTION_APP_IN_BACKGROUND = "APP_BACKGROUND"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        createNotificationChannel()
        initializeOptimizedLocationClient()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GPSTracker:TrackingService"
        )
        wakeLock.acquire()
    }

    private fun initializeOptimizedLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            foregroundUpdateInterval
        )
            .setMinUpdateIntervalMillis(3000L)
            .setWaitForAccurateLocation(true)
            .setMinUpdateDistanceMeters(15.0f)
            .setMaxUpdateDelayMillis(30000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.lastOrNull()?.let { location ->
                    if (location.accuracy < 50.0f && location.accuracy > 0) {
                        Log.d(TAG, "Lokacija u pozadini: ${location.accuracy}m")
                        broadcastLocationToActivity(location)
                    }
                }
            }
        }
    }

    private fun broadcastLocationToActivity(location: Location) {
        try {
            val intent = Intent("BACKGROUND_LOCATION_UPDATE").apply {
                putExtra("latitude", location.latitude)
                putExtra("longitude", location.longitude)
                putExtra("accuracy", location.accuracy)
                putExtra("speed", location.speed)
                putExtra("bearing", location.bearing)
                putExtra("timestamp", location.time)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Greška pri slanju lokacije: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_TRACKING -> {
                startForegroundTracking()
                startOptimizedLocationUpdates()
            }
            ACTION_STOP_TRACKING -> stopTracking()
            ACTION_APP_IN_FOREGROUND -> {
                isAppInForeground = true
                adjustLocationUpdatesForForeground()
            }
            ACTION_APP_IN_BACKGROUND -> {
                isAppInForeground = false
                adjustLocationUpdatesForBackground()
            }
            else -> startForegroundTracking()
        }

        return START_STICKY
    }

    private fun startOptimizedLocationUpdates() {
        if (checkLocationPermission()) {
            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "Location updates pokrenuti")
            } catch (e: SecurityException) {
                Log.e(TAG, "Greška pri pokretanju location updates: ${e.message}")
            }
        } else {
            Log.w(TAG, "Nema dozvola za lokaciju")
            stopSelf()
        }
    }

    private fun adjustLocationUpdatesForForeground() {
        stopLocationUpdates()
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            foregroundUpdateInterval
        )
            .setMinUpdateIntervalMillis(2000L)
            .setWaitForAccurateLocation(true)
            .setMinUpdateDistanceMeters(5.0f)
            .build()
        startOptimizedLocationUpdates()
        Log.d(TAG, "Podešavanja za FOREGROUND")
    }

    private fun adjustLocationUpdatesForBackground() {
        stopLocationUpdates()
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            backgroundUpdateInterval
        )
            .setMinUpdateIntervalMillis(10000L)
            .setWaitForAccurateLocation(false)
            .setMinUpdateDistanceMeters(25.0f)
            .setMaxUpdateDelayMillis(60000L)
            .build()
        startOptimizedLocationUpdates()
        Log.d(TAG, "Podešavanja za BACKGROUND")
    }

    private fun startForegroundTracking() {
        try {
            val notification = createOptimizedTrackingNotification()
            startForeground(SERVICE_NOTIFICATION_ID, notification)
            Log.d(TAG, "Foreground service pokrenut")
        } catch (e: Exception) {
            Log.e(TAG, "Greška pri pokretanju foreground service: ${e.message}")
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun stopTracking() {
        stopLocationUpdates()
        stopSelf()
        Log.d(TAG, "Snimanje zaustavljeno")
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d(TAG, "Location updates zaustavljeni")
        } catch (e: Exception) {
            Log.e(TAG, "Greška pri zaustavljanju location updates: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "GPS Tracking Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Service za praćenje lokacije u pozadini"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    private fun createOptimizedTrackingNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "OPEN_TRACKING"
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Tracker - Snimanje aktivno")
            .setContentText("Aplikacija snima vašu rutu u pozadini")
            .setSmallIcon(android.R.drawable.ic_dialog_map) // Koristi sistemsku ikonu
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Zaustavi", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")

        stopLocationUpdates()

        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        Log.d(TAG, "Service uspešno zaustavljen")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}