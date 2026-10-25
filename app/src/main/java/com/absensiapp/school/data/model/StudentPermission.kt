package com.absensiapp.school.data.model

import java.io.Serializable

data class StudentPermission(
    val id: String,
    val studentNisn: String,
    val studentName: String,
    val className: String,
    val permissionType: String, // e.g. "Sakit", "Acara Keluarga", "Keperluan Penting", "Lainnya"
    val startDate: String,      // format "dd/MM/yyyy"
    val endDate: String,        // format "dd/MM/yyyy"
    val totalDays: Int,         // calculated days (e.g. 12/02/2026 sampai 13/02/2026 = 1 hari)
    val reason: String,
    val photoBase64: String,    // Base64 encoded string of surat izin
    val status: String = "Menunggu", // "Menunggu", "Disetujui", "Ditolak"
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
