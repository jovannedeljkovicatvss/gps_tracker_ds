package jovannedeljkovic.gps_tracker_pro.network

import retrofit2.http.Body
import retrofit2.http.POST

interface GpsTrackerApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("locations")
    suspend fun sendLocation(@Body location: LocationData): ApiResponse
}