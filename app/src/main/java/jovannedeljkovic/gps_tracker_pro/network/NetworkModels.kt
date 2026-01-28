package jovannedeljkovic.gps_tracker_pro.network

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val user: User?,
    val token: String?
)

data class User(
    val id: Int,
    val email: String,
    val name: String,
    val role: String
)

data class LocationData(
    val userId: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String
)

data class ApiResponse(
    val success: Boolean,
    val message: String
)