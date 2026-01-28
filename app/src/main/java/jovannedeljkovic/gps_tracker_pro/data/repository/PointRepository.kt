package jovannedeljkovic.gps_tracker_pro.data.repository

import jovannedeljkovic.gps_tracker_pro.data.database.dao.PointOfInterestDao
import jovannedeljkovic.gps_tracker_pro.data.entities.PointOfInterest

class PointRepository(private val pointDao: PointOfInterestDao) {

    suspend fun addPoint(point: PointOfInterest) {
        pointDao.insertPoint(point)
    }

    suspend fun updatePoint(point: PointOfInterest) {
        pointDao.updatePoint(point)
    }

    suspend fun deletePoint(point: PointOfInterest) {
        pointDao.deletePoint(point)
    }

    suspend fun getUserPoints(userId: String): List<PointOfInterest> {
        return pointDao.getUserPoints(userId)
    }

    suspend fun getPointById(pointId: String): PointOfInterest? {
        return pointDao.getPointById(pointId)
    }
}