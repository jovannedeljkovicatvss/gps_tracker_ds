package jovannedeljkovic.gps_tracker_ds

import android.app.Application
import jovannedeljkovic.gps_tracker_ds.data.database.AppDatabase
import jovannedeljkovic.gps_tracker_ds.data.repository.PointRepository
import jovannedeljkovic.gps_tracker_ds.data.repository.RouteRepository
import jovannedeljkovic.gps_tracker_ds.data.repository.UserRepository

class App : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val userRepository by lazy { UserRepository(database.userDao()) }
    val routeRepository by lazy { RouteRepository(database.routeDao()) }
    val pointRepository by lazy { PointRepository(database.pointOfInterestDao()) }
}