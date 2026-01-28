package jovannedeljkovic.gps_tracker_pro.data.database.dao

import androidx.room.*
import jovannedeljkovic.gps_tracker_pro.data.entities.PointOfInterest

@Dao
interface PointOfInterestDao {

    @Insert
    suspend fun insertPoint(point: PointOfInterest)

    @Update
    suspend fun updatePoint(point: PointOfInterest)

    @Delete
    suspend fun deletePoint(point: PointOfInterest)

    @Query("SELECT * FROM points_of_interest WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getUserPoints(userId: String): List<PointOfInterest>

    @Query("SELECT * FROM points_of_interest WHERE id = :pointId")
    suspend fun getPointById(pointId: String): PointOfInterest?
}