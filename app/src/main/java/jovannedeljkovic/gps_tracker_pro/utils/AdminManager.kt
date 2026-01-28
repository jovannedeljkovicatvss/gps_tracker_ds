// app/src/main/java/jovannedeljkovic/gps_tracker_pro/utils/AdminManager.kt
package jovannedeljkovic.gps_tracker_pro.utils

object AdminManager {
    // OVDE STAVI SVOJ EMAIL I BUDUĆE ADMIN EMAILOVE
    private val masterAdminEmails = setOf(
        "jocaned@gmail.com", // ZAMENI SA TVOJIM STVARNIM EMAILOM
        "admin@gpstracker.com",
        "backupadmin@localhost"
    )

    fun isMasterAdmin(email: String): Boolean {
        return masterAdminEmails.contains(email.toLowerCase())
    }

    fun getMasterAdminEmails(): Set<String> {
        return masterAdminEmails
    }

    fun isValidSecretCode(code: String): Boolean {
        return code == "ADMIN123" // Promeni ovaj kod po želji
    }
}