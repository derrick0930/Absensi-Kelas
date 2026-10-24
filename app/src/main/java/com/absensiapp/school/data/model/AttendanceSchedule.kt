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
    val sessionDate: String = "Hari ini"
) : Serializable {
    val periodDescription: String
        get() = if (periodStart == periodEnd) "Jam Ke-$periodStart" else "Jam Ke-$periodStart s/d $periodEnd"
}
