package jovannedeljkovic.gps_tracker_pro.data.repository

import jovannedeljkovic.gps_tracker_pro.data.database.dao.RouteDao
import jovannedeljkovic.gps_tracker_pro.data.entities.LocationPoint
import jovannedeljkovic.gps_tracker_pro.data.entities.Route

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

    // DODATA METODA ZA BRISANJE RUTE
    suspend fun deleteRoute(route: Route) {
        // Prvo obriši sve tačke rute
        routeDao.deleteRoutePoints(route.id)
        // Onda obriši rutu
        routeDao.deleteRoute(route)
    }

    // DODATA METODA ZA BRISANJE RUTE PO ID
    suspend fun deleteRouteById(routeId: String) {
        routeDao.deleteRoutePoints(routeId)
        routeDao.deleteRouteById(routeId)
    }
}