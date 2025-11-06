package jovannedeljkovic.gps_tracker_ds.data.repository

import jovannedeljkovic.gps_tracker_ds.data.database.dao.RouteDao
import jovannedeljkovic.gps_tracker_ds.data.entities.LocationPoint
import jovannedeljkovic.gps_tracker_ds.data.entities.Route

class RouteRepository(private val routeDao: RouteDao) {

    suspend fun createRoute(route: Route): String {
        routeDao.insertRoute(route)
        return route.id
    }

    suspend fun updateRoute(route: Route) {
        routeDao.updateRoute(route)
    }

    suspend fun addLocationPoint(point: LocationPoint) {
        routeDao.insertLocationPoint(point)
    }

    suspend fun getUserRoutes(userId: String): List<Route> {
        return routeDao.getUserRoutes(userId)
    }

    suspend fun getRoutePoints(routeId: String): List<LocationPoint> {
        return routeDao.getRoutePoints(routeId)
    }

    suspend fun getRouteById(routeId: String): Route? {
        return routeDao.getRouteById(routeId)
    }
}