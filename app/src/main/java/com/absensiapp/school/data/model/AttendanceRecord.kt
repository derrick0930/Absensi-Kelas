package com.absensiapp.school.data.model

import java.io.Serializable

enum class AttendanceStatus(val label: String) {
    PRESENT("Hadir"),
    LATE("Terlambat"),
    PERMITTED("Izin"),
    SICK("Sakit"),
    ABSENT("Alfa")
}

data class AttendanceRecord(
    val id: String,
    val scheduleId: String,
    val studentNisn: String,
    val studentName: String,
    val className: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: AttendanceStatus = AttendanceStatus.PRESENT,
    val biometricVerified: Boolean = true,
    val faceVerified: Boolean = true,
    val locationDistanceMeters: Int = 0,
    val verificationMethod: String = "3-Lapis (GPS + Biometrik + Wajah)",
    val remarks: String = "Presensi Mandiri Terverifikasi"
) : Serializable
