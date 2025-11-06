package jovannedeljkovic.gps_tracker_ds.data.database.dao

import androidx.room.*
import jovannedeljkovic.gps_tracker_ds.data.entities.LocationPoint
import jovannedeljkovic.gps_tracker_ds.data.entities.Route

@Dao
interface RouteDao {

    // Rute
    @Insert
    suspend fun insertRoute(route: Route)

    @Update
    suspend fun updateRoute(route: Route)

    @Query("SELECT * FROM routes WHERE userId = :userId ORDER BY startTime DESC")
    suspend fun getUserRoutes(userId: String): List<Route>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: String): Route?

    // Tačke
    @Insert
    suspend fun insertLocationPoint(point: LocationPoint)

    @Query("SELECT * FROM location_points WHERE routeId = :routeId ORDER BY timestamp ASC")
    suspend fun getRoutePoints(routeId: String): List<LocationPoint>

    @Query("DELETE FROM location_points WHERE routeId = :routeId")
    suspend fun deleteRoutePoints(routeId: String)
}