package com.absensiapp.school.data.model

import java.io.Serializable

data class AttendanceSchedule(
    val id: String,
    val className: String, // e.g., "XI TKJ 1"
    val subject: String, // e.g., "Administrasi Server & Jaringan"
    val teacherId: String,
    val teacherName: String,
    val periodStart: Int, // e.g., 1 (Jam ke-1)
    val periodEnd: Int, // e.g., 2 (Jam ke-2)
    val timeSlot: String, // e.g., "07:00 - 08:30"
    val dayName: String = "Senin",
    val room: String = "Lab Komputer 2",
    var isOpenForAttendance: Boolean = true,
    var sessionDate: String = "Hari ini"
) : Serializable {
    val periodDescription: String
        get() = if (periodStart == periodEnd) "Jam Ke-$periodStart" else "Jam Ke-$periodStart s/d $periodEnd"

    fun isPastDay(): Boolean {
        val clean = sessionDate.trim()
        if (clean.isBlank() || clean.equals("Hari ini", ignoreCase = true) || clean == "DELETED") {
            return false
        }
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val sDate = sdf.parse(clean) ?: return false
            val tDate = sdf.parse(sdf.format(java.util.Date())) ?: return false
            sDate.before(tDate)
        } catch (_: Exception) {
            false
        }
    }

    fun isExpired(): Boolean {
        if (isPastDay()) return true

        val clean = sessionDate.trim()
        if (clean.isNotBlank() && !clean.equals("Hari ini", ignoreCase = true) && clean != "DELETED") {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val sDate = sdf.parse(clean)
                val tDate = sdf.parse(sdf.format(java.util.Date()))
                if (sDate != null && tDate != null && sDate.after(tDate)) {
                    return false
                }
            } catch (_: Exception) {}
        }

        val parts = timeSlot.split("-").map { it.trim() }
        if (parts.size < 2) return false
        val endStr = parts[1].replace("WIB", "").replace("wib", "").trim()
        val timeParts = endStr.split(":")
        if (timeParts.size < 2) return false
        val h = timeParts[0].trim().toIntOrNull() ?: return false
        val m = timeParts[1].trim().toIntOrNull() ?: return false
        val endMinutes = h * 60 + m

        val cal = java.util.Calendar.getInstance()
        val currentMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return currentMinutes >= endMinutes
    }
}
