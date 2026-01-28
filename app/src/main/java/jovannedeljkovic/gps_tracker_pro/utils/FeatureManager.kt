package jovannedeljkovic.gps_tracker_pro.utils

import jovannedeljkovic.gps_tracker_pro.data.entities.User

object FeatureManager {

    // PROVERE ZA RUTE
    fun canCreateUnlimitedRoutes(user: User): Boolean {
        return user.role == "PREMIUM" || user.role == "ADMIN"
    }

    fun getMaxDailyRoutes(user: User): Int {
        return when (user.role) {
            "ADMIN", "PREMIUM" -> Int.MAX_VALUE
            else -> 3 // BASIC korisnici maksimalno 3 rute dnevno
        }
    }

    // PROVERE ZA TAČKE
    fun canCreateUnlimitedPoints(user: User): Boolean {
        return user.role == "PREMIUM" || user.role == "ADMIN"
    }

    fun getMaxPoints(user: User): Int {
        return when (user.role) {
            "ADMIN", "PREMIUM" -> Int.MAX_VALUE
            else -> 10 // BASIC korisnici maksimalno 10 tačaka
        }
    }

    // PROVERE ZA MAPE
    fun canUseSatelliteMaps(user: User): Boolean {
        return user.role == "PREMIUM" || user.role == "ADMIN"
    }

    fun canUseOfflineMaps(user: User): Boolean {
        return user.role == "PREMIUM" || user.role == "ADMIN"
    }

    fun canUseTopoMaps(user: User): Boolean {
        return user.role == "PREMIUM" || user.role == "ADMIN"
    }

    // PROVERE ZA EKSPORT
    fun canExportGPX(user: User): Boolean {
        return user.role == "PREMIUM" || user.role == "ADMIN"
    }

    fun canExportKML(user: User): Boolean {
        return user.role == "PREMIUM" || user.role == "ADMIN"
    }

    fun canExportPDF(user: User): Boolean {
        return user.role == "PREMIUM" || user.role == "ADMIN"
    }

    // PROVERE ZA NAPREDNE FUNKCIONALNOSTI
    fun canUseRealTimeTracking(user: User): Boolean {
        return user.role == "PREMIUM" || user.role == "ADMIN"
    }

    fun canUseCloudBackup(user: User): Boolean {
        return user.role == "PREMIUM" || user.role == "ADMIN"
    }

    fun canUseAdvancedAnalytics(user: User): Boolean {
        return user.role == "PREMIUM" || user.role == "ADMIN"
    }

    // PRIKAZ INFORMACIJA O KORISNIKU
    fun getUserRoleDisplayName(user: User): String {
        return when (user.role) {
            "ADMIN" -> "👑 Administrator"
            "PREMIUM" -> "⭐ Premium"
            else -> "🔹 Basic"
        }
    }

    fun getRoleBenefits(user: User): List<String> {
        return when (user.role) {
            "ADMIN" -> listOf(
                "✅ Neograničene rute",
                "✅ Neograničene tačke",
                "✅ Sve vrste mapa",
                "✅ Offline mape",
                "✅ Napredni eksport",
                "✅ Cloud backup",
                "👑 Administratorske privilegije"
            )
            "PREMIUM" -> listOf(
                "✅ Neograničene rute",
                "✅ Neograničene tačke",
                "✅ Sve vrste mapa",
                "✅ Offline mape",
                "✅ Napredni eksport",
                "✅ Cloud backup"
            )
            else -> listOf(
                "✅ Do 3 rute dnevno",
                "✅ Do 10 tačaka",
                "✅ Osnovne mape",
                "❌ Offline mape",
                "❌ Napredni eksport",
                "❌ Cloud backup",
                "💡 Nadogradi na Premium za više!"
            )
        }
    }
}