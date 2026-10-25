package com.absensiapp.school.data.model

import java.io.Serializable

data class UserAccount(
    val id: String, // NISN for student, NIP for teacher, username for admin
    val dbId: Long? = null, // Auto-increment integer id from database (BIGSERIAL)
    val nisn: String? = null, // Dedicated NISN column in database
    val username: String? = null, // Dedicated username column in database
    val name: String,
    val role: UserRole,
    val passwordHash: String,
    val cardData: StudentCardData? = null,
    val registeredAt: Long = System.currentTimeMillis(),
    val isBiometricEnrolled: Boolean = false,
    val isFaceEnrolled: Boolean = false,
    val faceSignature: String? = null,
    val biometricEnrolledAt: Long = 0L,
    val enrolledDeviceId: String? = null
) : Serializable
