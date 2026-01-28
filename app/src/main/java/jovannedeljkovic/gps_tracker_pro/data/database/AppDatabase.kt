package jovannedeljkovic.gps_tracker_pro.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import jovannedeljkovic.gps_tracker_pro.data.database.dao.PointOfInterestDao
import jovannedeljkovic.gps_tracker_pro.data.database.dao.RouteDao
import jovannedeljkovic.gps_tracker_pro.data.database.dao.UserDao
import jovannedeljkovic.gps_tracker_pro.data.entities.LocationPoint
import jovannedeljkovic.gps_tracker_pro.data.entities.PointOfInterest
import jovannedeljkovic.gps_tracker_pro.data.entities.Route
import jovannedeljkovic.gps_tracker_pro.data.entities.User

@Database(
    entities = [
        User::class,
        Route::class,
        LocationPoint::class,
        PointOfInterest::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun routeDao(): RouteDao
    abstract fun pointOfInterestDao(): PointOfInterestDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gps_tracker_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}