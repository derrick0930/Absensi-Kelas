package com.absensiapp.school.data.remote

import com.absensiapp.school.data.model.AttendanceRecord
import com.absensiapp.school.data.model.AttendanceSchedule
import com.absensiapp.school.data.model.AttendanceStatus
import com.absensiapp.school.data.model.SchoolClass
import com.absensiapp.school.data.model.StudentCardData
import com.absensiapp.school.data.model.StudentPermission
import com.absensiapp.school.data.model.UserAccount
import com.absensiapp.school.data.model.UserRole
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SupabaseClient {

    private val gson = Gson()

    private fun getBaseRestUrl(): String {
        val base = SupabaseConfig.SUPABASE_URL.trim().trimEnd('/')
        return "$base/rest/v1"
    }

    private fun createConnection(endpoint: String, method: String): HttpURLConnection {
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
        conn.setRequestProperty("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return conn
    }

    suspend fun getUser(id: String): UserAccount? = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext null
        try {
            val encodedId = URLEncoder.encode(id, "UTF-8")
            val endpoint = "${getBaseRestUrl()}/users?or=(nisn.eq.$encodedId,username.eq.$encodedId,id.eq.$encodedId)&select=*"
            val conn = createConnection(endpoint, "GET")
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val array = gson.fromJson(response, JsonArray::class.java)
                if (array.size() > 0) {
                    val obj = array[0].asJsonObject
                    val roleStr = obj.get("role").asString
                    val role = try {
                        UserRole.valueOf(roleStr)
                    } catch (_: Exception) {
                        UserRole.STUDENT
                    }
                    val isBioEnrolled = obj.get("biometric_enrolled")?.asBoolean ?: false
                    val isFaceEnrolled = obj.get("face_enrolled")?.asBoolean ?: false
                    val faceSig = obj.get("face_signature")?.takeIf { !it.isJsonNull }?.asString
                    val bioEnrolledAt = obj.get("biometric_enrolled_at")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                    val devId = obj.get("enrolled_device_id")?.takeIf { !it.isJsonNull }?.asString
                    val rawNisn = obj.get("nisn")?.takeIf { !it.isJsonNull }?.asString
                    val rawUsername = obj.get("username")?.takeIf { !it.isJsonNull }?.asString
                    val rawId = obj.get("id")?.asString ?: ""
                    val dbId = try { obj.get("id")?.asLong } catch (_: Exception) { null }
                    val effectiveNisn = rawNisn?.ifEmpty { null }
                    val effectiveUsername = rawUsername?.ifEmpty { null }
                    val effectiveId = effectiveUsername ?: effectiveNisn ?: rawId

                    val user = UserAccount(
                        id = effectiveId,
                        dbId = dbId,
                        nisn = effectiveNisn,
                        username = effectiveUsername,
                        name = obj.get("name").asString,
                        role = role,
                        passwordHash = obj.get("password").asString,
                        isBiometricEnrolled = isBioEnrolled,
                        isFaceEnrolled = isFaceEnrolled,
                        faceSignature = faceSig,
                        biometricEnrolledAt = bioEnrolledAt,
                        enrolledDeviceId = devId
                    )
                    return@withContext user
                }
            }
        } catch (_: Exception) {
            // Silently fall back to local database
        }
        null
    }

    suspend fun saveUser(user: UserAccount): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext false
        try {
            val endpoint = "${getBaseRestUrl()}/users"
            val conn = createConnection(endpoint, "POST")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
            conn.doOutput = true

            val json = JsonObject().apply {
                val nisnToSave = user.nisn ?: user.id
                addProperty("nisn", nisnToSave)
                val usernameToSave = user.username ?: if (user.role != UserRole.STUDENT) user.id else null
                if (!usernameToSave.isNullOrBlank()) {
                    addProperty("username", usernameToSave)
                }
                addProperty("name", user.name)
                addProperty("role", user.role.name)
                addProperty("password", user.passwordHash)
                addProperty("biometric_enrolled", user.isBiometricEnrolled)
                addProperty("face_enrolled", user.isFaceEnrolled)
                if (user.faceSignature != null) addProperty("face_signature", user.faceSignature)
                addProperty("biometric_enrolled_at", user.biometricEnrolledAt)
                if (user.enrolledDeviceId != null) addProperty("enrolled_device_id", user.enrolledDeviceId)
            }

            conn.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { it.write(json.toString()) }
            }

            return@withContext conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateBiometricEnrollment(
        userId: String,
        faceSignature: String,
        deviceId: String,
        enrolledAt: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext false
        try {
            val encodedId = URLEncoder.encode(userId, "UTF-8")
            val endpoint = "${getBaseRestUrl()}/users?or=(nisn.eq.$encodedId,username.eq.$encodedId,id.eq.$encodedId)"
            val conn = createConnection(endpoint, "PATCH")
            conn.doOutput = true

            val json = JsonObject().apply {
                addProperty("biometric_enrolled", true)
                addProperty("face_enrolled", true)
                addProperty("face_signature", faceSignature)
                addProperty("biometric_enrolled_at", enrolledAt)
                addProperty("enrolled_device_id", deviceId)
            }

            conn.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { it.write(json.toString()) }
            }

            return@withContext conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updatePassword(
        userId: String,
        newPasswordPlain: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext false
        try {
            val encodedId = URLEncoder.encode(userId, "UTF-8")
            val endpoint = "${getBaseRestUrl()}/users?or=(nisn.eq.$encodedId,username.eq.$encodedId,id.eq.$encodedId)"
            val conn = createConnection(endpoint, "PATCH")
            conn.doOutput = true

            val json = JsonObject().apply {
                addProperty("password", newPasswordPlain)
            }

            conn.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { it.write(json.toString()) }
            }

            return@withContext conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getAllUsersFromDb(): List<UserAccount> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext emptyList()
        try {
            val endpoint = "${getBaseRestUrl()}/users?select=*"
            val conn = createConnection(endpoint, "GET")
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val array = gson.fromJson(response, JsonArray::class.java)
                val list = mutableListOf<UserAccount>()
                for (elem in array) {
                    val obj = elem.asJsonObject
                    val roleStr = obj.get("role")?.asString ?: "STUDENT"
                    val role = try { UserRole.valueOf(roleStr) } catch (_: Exception) { UserRole.STUDENT }
                    val isBioEnrolled = obj.get("biometric_enrolled")?.asBoolean ?: false
                    val isFaceEnrolled = obj.get("face_enrolled")?.asBoolean ?: false
                    val faceSig = obj.get("face_signature")?.takeIf { !it.isJsonNull }?.asString
                    val bioEnrolledAt = obj.get("biometric_enrolled_at")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                    val devId = obj.get("enrolled_device_id")?.takeIf { !it.isJsonNull }?.asString
                    val rawNisn = obj.get("nisn")?.takeIf { !it.isJsonNull }?.asString
                    val rawUsername = obj.get("username")?.takeIf { !it.isJsonNull }?.asString
                    val rawId = obj.get("id")?.asString ?: ""
                    val dbId = try { obj.get("id")?.asLong } catch (_: Exception) { null }
                    val effectiveNisn = rawNisn?.ifEmpty { null }
                    val effectiveUsername = rawUsername?.ifEmpty { null }
                    val effectiveId = effectiveUsername ?: effectiveNisn ?: rawId

                    list.add(
                        UserAccount(
                            id = effectiveId,
                            dbId = dbId,
                            nisn = effectiveNisn,
                            username = effectiveUsername,
                            name = obj.get("name")?.asString ?: "",
                            role = role,
                            passwordHash = obj.get("password")?.asString ?: "12345678",
                            isBiometricEnrolled = isBioEnrolled,
                            isFaceEnrolled = isFaceEnrolled,
                            faceSignature = faceSig,
                            biometricEnrolledAt = bioEnrolledAt,
                            enrolledDeviceId = devId
                        )
                    )
                }
                return@withContext list
            }
        } catch (_: Exception) {
            // Silently fall back
        }
        emptyList()
    }

    suspend fun saveStudentProfile(card: StudentCardData): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext false
        try {
            val endpoint = "${getBaseRestUrl()}/students"
            val conn = createConnection(endpoint, "POST")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
            conn.doOutput = true

            val json = JsonObject().apply {
                addProperty("nisn", card.nisn)
                addProperty("name", card.name)
                addProperty("birth_place_date", card.birthPlaceDate)
                addProperty("address", card.address)
                addProperty("religion", card.religion)
                addProperty("blood_type", card.bloodType)
                addProperty("school_name", card.schoolName)
                addProperty("class_name", card.className)
                addProperty("headmaster_name", card.headmasterName)
            }

            conn.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { it.write(json.toString()) }
            }

            return@withContext conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getStudentProfile(nisn: String): StudentCardData? = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext null
        try {
            val encodedNisn = URLEncoder.encode(nisn, "UTF-8")
            val endpoint = "${getBaseRestUrl()}/students?nisn=eq.$encodedNisn&select=*"
            val conn = createConnection(endpoint, "GET")
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val array = gson.fromJson(response, JsonArray::class.java)
                if (array.size() > 0) {
                    val obj = array[0].asJsonObject
                    return@withContext StudentCardData(
                        nisn = obj.get("nisn").asString,
                        name = obj.get("name").asString,
                        birthPlaceDate = obj.get("birth_place_date")?.asString.orEmpty(),
                        address = obj.get("address")?.asString.orEmpty(),
                        religion = obj.get("religion")?.asString.orEmpty(),
                        bloodType = obj.get("blood_type")?.asString.orEmpty(),
                        schoolName = obj.get("school_name")?.asString ?: "SMKS TARUNA JAYA PRAWIRA TUBAN",
                        className = obj.get("class_name")?.asString ?: "XI TKJ 1",
                        headmasterName = obj.get("headmaster_name")?.asString ?: "Bambang Kusdiyanto, ST.M.Pd"
                    )
                }
            }
        } catch (_: Exception) {
            // Silently fall back
        }
        null
    }

    suspend fun getAllStudentsFromDb(): List<StudentCardData> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext emptyList()
        try {
            val endpoint = "${getBaseRestUrl()}/students?select=*&order=name.asc"
            val conn = createConnection(endpoint, "GET")
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val array = gson.fromJson(response, JsonArray::class.java)
                val list = mutableListOf<StudentCardData>()
                for (elem in array) {
                    val obj = elem.asJsonObject
                    list.add(
                        StudentCardData(
                            nisn = obj.get("nisn").asString,
                            name = obj.get("name").asString,
                            birthPlaceDate = obj.get("birth_place_date")?.asString.orEmpty(),
                            address = obj.get("address")?.asString.orEmpty(),
                            religion = obj.get("religion")?.asString.orEmpty(),
                            bloodType = obj.get("blood_type")?.asString.orEmpty(),
                            schoolName = obj.get("school_name")?.asString ?: "SMKS TARUNA JAYA PRAWIRA TUBAN",
                            className = obj.get("class_name")?.asString ?: "XI IPA 2",
                            headmasterName = obj.get("headmaster_name")?.asString ?: "Bambang Kusdiyanto, ST.M.Pd"
                        )
                    )
                }
                return@withContext list
            }
        } catch (_: Exception) {
            // Silently fall back
        }
        emptyList()
    }

    suspend fun getSchedules(): List<AttendanceSchedule> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext emptyList()
        try {
            val endpoint = "${getBaseRestUrl()}/schedules?session_date=neq.DELETED&select=*"
            val conn = createConnection(endpoint, "GET")
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val array = gson.fromJson(response, JsonArray::class.java)
                val list = mutableListOf<AttendanceSchedule>()
                for (elem in array) {
                    val obj = elem.asJsonObject
                    val sDate = obj.get("session_date")?.asString.orEmpty()
                    val subj = obj.get("subject")?.asString.orEmpty()
                    if (sDate.equals("DELETED", ignoreCase = true) || subj.equals("DELETED", ignoreCase = true)) {
                        continue
                    }
                    list.add(
                        AttendanceSchedule(
                            id = obj.get("id").asString,
                            className = obj.get("class_name").asString,
                            subject = subj,
                            teacherId = obj.get("teacher_id").asString,
                            teacherName = obj.get("teacher_name").asString,
                            periodStart = obj.get("period_start").asInt,
                            periodEnd = obj.get("period_end").asInt,
                            timeSlot = obj.get("time_slot").asString,
                            dayName = obj.get("day_name")?.asString ?: "Senin",
                            room = obj.get("room").asString,
                            isOpenForAttendance = obj.get("is_open")?.asBoolean ?: true,
                            sessionDate = sDate
                        )
                    )
                }
                return@withContext list
            }
        } catch (_: Exception) {
            // Silently fall back
        }
        emptyList()
    }

    suspend fun saveSchedule(schedule: AttendanceSchedule): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext false
        try {
            val endpoint = "${getBaseRestUrl()}/schedules"
            val conn = createConnection(endpoint, "POST")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
            conn.doOutput = true

            val json = JsonObject().apply {
                addProperty("id", schedule.id)
                addProperty("class_name", schedule.className)
                addProperty("subject", schedule.subject)
                addProperty("teacher_id", schedule.teacherId)
                addProperty("teacher_name", schedule.teacherName)
                addProperty("period_start", schedule.periodStart)
                addProperty("period_end", schedule.periodEnd)
                addProperty("time_slot", schedule.timeSlot)
                addProperty("day_name", schedule.dayName)
                addProperty("room", schedule.room)
                addProperty("is_open", schedule.isOpenForAttendance)
                addProperty("session_date", schedule.sessionDate)
            }

            conn.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { it.write(json.toString()) }
            }

            return@withContext conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    suspend fun saveAttendanceRecord(record: AttendanceRecord): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext false
        try {
            val endpoint = "${getBaseRestUrl()}/attendance"
            val conn = createConnection(endpoint, "POST")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
            conn.doOutput = true

            val json = JsonObject().apply {
                addProperty("id", record.id)
                addProperty("schedule_id", record.scheduleId)
                addProperty("student_nisn", record.studentNisn)
                addProperty("student_name", record.studentName)
                addProperty("class_name", record.className)
                addProperty("timestamp", record.timestamp)
                addProperty("status", record.status.label)
                addProperty("biometric_verified", record.biometricVerified)
                addProperty("face_verified", record.faceVerified)
                addProperty("location_distance_meters", record.locationDistanceMeters)
                addProperty("verification_method", record.verificationMethod)
                addProperty("remarks", record.remarks)
            }

            conn.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { it.write(json.toString()) }
            }

            return@withContext conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getAttendanceForStudent(studentNisn: String): List<AttendanceRecord> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext emptyList()
        try {
            val encodedNisn = URLEncoder.encode(studentNisn, "UTF-8")
            val endpoint = "${getBaseRestUrl()}/attendance?student_nisn=eq.$encodedNisn&select=*&order=timestamp.desc"
            val conn = createConnection(endpoint, "GET")
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val array = gson.fromJson(response, JsonArray::class.java)
                val list = mutableListOf<AttendanceRecord>()
                for (elem in array) {
                    val obj = elem.asJsonObject
                    val statusStr = obj.get("status")?.asString ?: "Hadir"
                    val status = when (statusStr.lowercase()) {
                        "terlambat" -> AttendanceStatus.LATE
                        "izin" -> AttendanceStatus.PERMITTED
                        "sakit" -> AttendanceStatus.SICK
                        "alfa" -> AttendanceStatus.ABSENT
                        else -> AttendanceStatus.PRESENT
                    }
                    val biometricVerified = obj.get("biometric_verified")?.asBoolean ?: true
                    val faceVerified = obj.get("face_verified")?.asBoolean ?: true
                    val locationDist = obj.get("location_distance_meters")?.asInt ?: 0
                    val verMethod = obj.get("verification_method")?.asString ?: "3-Lapis (GPS + Biometrik + Wajah)"
                    val remarks = obj.get("remarks")?.asString ?: "Presensi Mandiri Terverifikasi"

                    list.add(
                        AttendanceRecord(
                            id = obj.get("id").asString,
                            scheduleId = obj.get("schedule_id").asString,
                            studentNisn = obj.get("student_nisn").asString,
                            studentName = obj.get("student_name").asString,
                            className = obj.get("class_name").asString,
                            timestamp = obj.get("timestamp")?.asLong ?: System.currentTimeMillis(),
                            status = status,
                            biometricVerified = biometricVerified,
                            faceVerified = faceVerified,
                            locationDistanceMeters = locationDist,
                            verificationMethod = verMethod,
                            remarks = remarks
                        )
                    )
                }
                return@withContext list
            }
        } catch (_: Exception) {
            // Silently fall back
        }
        emptyList()
    }

    suspend fun getAttendanceForClass(className: String): List<AttendanceRecord> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext emptyList()
        try {
            val encodedClass = URLEncoder.encode(className, "UTF-8")
            val endpoint = "${getBaseRestUrl()}/attendance?class_name=eq.$encodedClass&select=*"
            val conn = createConnection(endpoint, "GET")
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val array = gson.fromJson(response, JsonArray::class.java)
                val list = mutableListOf<AttendanceRecord>()
                for (elem in array) {
                    val obj = elem.asJsonObject
                    val statusStr = obj.get("status")?.asString ?: "Hadir"
                    val status = when (statusStr.lowercase()) {
                        "terlambat" -> AttendanceStatus.LATE
                        "izin" -> AttendanceStatus.PERMITTED
                        "sakit" -> AttendanceStatus.SICK
                        "alfa" -> AttendanceStatus.ABSENT
                        else -> AttendanceStatus.PRESENT
                    }
                    val biometricVerified = obj.get("biometric_verified")?.asBoolean ?: true
                    val faceVerified = obj.get("face_verified")?.asBoolean ?: true
                    val locationDist = obj.get("location_distance_meters")?.asInt ?: 0
                    val verMethod = obj.get("verification_method")?.asString ?: "3-Lapis (GPS + Biometrik + Wajah)"
                    val remarks = obj.get("remarks")?.asString ?: "Presensi Mandiri Terverifikasi"

                    list.add(
                        AttendanceRecord(
                            id = obj.get("id").asString,
                            scheduleId = obj.get("schedule_id").asString,
                            studentNisn = obj.get("student_nisn").asString,
                            studentName = obj.get("student_name").asString,
                            className = obj.get("class_name").asString,
                            timestamp = obj.get("timestamp")?.asLong ?: System.currentTimeMillis(),
                            status = status,
                            biometricVerified = biometricVerified,
                            faceVerified = faceVerified,
                            locationDistanceMeters = locationDist,
                            verificationMethod = verMethod,
                            remarks = remarks
                        )
                    )
                }
                return@withContext list
            }
        } catch (_: Exception) {
            // Silently fall back
        }
        emptyList()
    }

    suspend fun getAllAttendanceRecords(): List<AttendanceRecord> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext emptyList()
        try {
            val endpoint = "${getBaseRestUrl()}/attendance?select=*"
            val conn = createConnection(endpoint, "GET")
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val array = gson.fromJson(response, JsonArray::class.java)
                val list = mutableListOf<AttendanceRecord>()
                for (elem in array) {
                    val obj = elem.asJsonObject
                    val statusStr = obj.get("status")?.asString ?: "Hadir"
                    val status = when (statusStr.lowercase()) {
                        "terlambat" -> AttendanceStatus.LATE
                        "izin" -> AttendanceStatus.PERMITTED
                        "sakit" -> AttendanceStatus.SICK
                        "alfa" -> AttendanceStatus.ABSENT
                        else -> AttendanceStatus.PRESENT
                    }
                    val biometricVerified = obj.get("biometric_verified")?.asBoolean ?: true
                    val faceVerified = obj.get("face_verified")?.asBoolean ?: true
                    val locationDist = obj.get("location_distance_meters")?.asInt ?: 0
                    val verMethod = obj.get("verification_method")?.asString ?: "3-Lapis (GPS + Biometrik + Wajah)"
                    val remarks = obj.get("remarks")?.asString ?: "Presensi Mandiri Terverifikasi"

                    list.add(
                        AttendanceRecord(
                            id = obj.get("id").asString,
                            scheduleId = obj.get("schedule_id").asString,
                            studentNisn = obj.get("student_nisn").asString,
                            studentName = obj.get("student_name").asString,
                            className = obj.get("class_name").asString,
                            timestamp = obj.get("timestamp")?.asLong ?: System.currentTimeMillis(),
                            status = status,
                            biometricVerified = biometricVerified,
                            faceVerified = faceVerified,
                            locationDistanceMeters = locationDist,
                            verificationMethod = verMethod,
                            remarks = remarks
                        )
                    )
                }
                return@withContext list
            }
        } catch (_: Exception) {
            // Silently fall back
        }
        emptyList()
    }

    suspend fun deleteSchedule(scheduleId: String): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext false
        try {
            val encodedId = URLEncoder.encode(scheduleId, "UTF-8")
            val endpoint = "${getBaseRestUrl()}/schedules?id=eq.$encodedId"

            // 1. Mark as DELETED in Supabase via PATCH (works 100% with Supabase UPDATE policy)
            var patchSuccess = false
            try {
                val patchConn = createConnection(endpoint, "PATCH")
                patchConn.doOutput = true
                val json = JsonObject().apply {
                    addProperty("session_date", "DELETED")
                    addProperty("is_open", false)
                }
                patchConn.outputStream.use { os ->
                    OutputStreamWriter(os, "UTF-8").use { it.write(json.toString()) }
                }
                patchSuccess = patchConn.responseCode in 200..299
            } catch (_: Exception) {}

            // 2. Also execute HTTP DELETE
            var deleteSuccess = false
            try {
                val delConn = createConnection(endpoint, "DELETE")
                deleteSuccess = delConn.responseCode in 200..299
            } catch (_: Exception) {}

            return@withContext patchSuccess || deleteSuccess
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getClasses(): List<SchoolClass> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext emptyList()
        try {
            val endpoint = "${getBaseRestUrl()}/classes?select=*"
            val conn = createConnection(endpoint, "GET")
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val array = gson.fromJson(response, JsonArray::class.java)
                val list = mutableListOf<SchoolClass>()
                for (element in array) {
                    val obj = element.asJsonObject
                    list.add(
                        SchoolClass(
                            id = obj.get("id").asString,
                            name = obj.get("name").asString,
                            room = obj.get("room")?.takeIf { !it.isJsonNull }?.asString ?: "R. 12",
                            latitude = obj.get("latitude")?.asDouble ?: -6.89972,
                            longitude = obj.get("longitude")?.asDouble ?: 112.04944,
                            radiusMeters = obj.get("radius_meters")?.asFloat ?: 50f
                        )
                    )
                }
                return@withContext list
            }
        } catch (_: Exception) {
            // Silently fall back
        }
        emptyList()
    }

    suspend fun saveClass(schoolClass: SchoolClass): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext false
        try {
            val endpoint = "${getBaseRestUrl()}/classes"
            val conn = createConnection(endpoint, "POST")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
            conn.doOutput = true

            val obj = JsonObject().apply {
                addProperty("id", schoolClass.id)
                addProperty("name", schoolClass.name)
                addProperty("room", schoolClass.room)
                addProperty("latitude", schoolClass.latitude)
                addProperty("longitude", schoolClass.longitude)
                addProperty("radius_meters", schoolClass.radiusMeters.toInt())
            }

            OutputStreamWriter(conn.outputStream).use { it.write(obj.toString()) }
            return@withContext conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    suspend fun savePermission(permission: StudentPermission): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext false
        try {
            val endpoint = "${getBaseRestUrl()}/permissions"
            val conn = createConnection(endpoint, "POST")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
            conn.doOutput = true

            val obj = JsonObject().apply {
                addProperty("id", permission.id)
                addProperty("student_nisn", permission.studentNisn)
                addProperty("student_name", permission.studentName)
                addProperty("class_name", permission.className)
                addProperty("permission_type", permission.permissionType)
                addProperty("start_date", permission.startDate)
                addProperty("end_date", permission.endDate)
                addProperty("total_days", permission.totalDays)
                addProperty("reason", permission.reason)
                addProperty("photo_base64", permission.photoBase64)
                addProperty("status", permission.status)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(obj.toString()) }
            return@withContext conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getAllPermissions(): List<StudentPermission> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext emptyList()
        try {
            val endpoint = "${getBaseRestUrl()}/permissions?select=*&order=created_at.desc"
            val conn = createConnection(endpoint, "GET")
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val array = gson.fromJson(response, JsonArray::class.java)
                val list = mutableListOf<StudentPermission>()
                for (elem in array) {
                    val obj = elem.asJsonObject
                    list.add(
                        StudentPermission(
                            id = obj.get("id").asString,
                            studentNisn = obj.get("student_nisn").asString,
                            studentName = obj.get("student_name").asString,
                            className = obj.get("class_name").asString,
                            permissionType = obj.get("permission_type").asString,
                            startDate = obj.get("start_date").asString,
                            endDate = obj.get("end_date").asString,
                            totalDays = obj.get("total_days")?.asInt ?: 1,
                            reason = obj.get("reason")?.asString.orEmpty(),
                            photoBase64 = obj.get("photo_base64")?.asString.orEmpty(),
                            status = obj.get("status")?.asString ?: "Menunggu"
                        )
                    )
                }
                return@withContext list
            }
        } catch (_: Exception) {
            // Silently fall back
        }
        emptyList()
    }

    suspend fun getPermissionsForStudent(studentNisn: String): List<StudentPermission> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext emptyList()
        try {
            val encodedNisn = URLEncoder.encode(studentNisn, "UTF-8")
            val endpoint = "${getBaseRestUrl()}/permissions?student_nisn=eq.$encodedNisn&select=*&order=created_at.desc"
            val conn = createConnection(endpoint, "GET")
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val array = gson.fromJson(response, JsonArray::class.java)
                val list = mutableListOf<StudentPermission>()
                for (elem in array) {
                    val obj = elem.asJsonObject
                    list.add(
                        StudentPermission(
                            id = obj.get("id").asString,
                            studentNisn = obj.get("student_nisn").asString,
                            studentName = obj.get("student_name").asString,
                            className = obj.get("class_name").asString,
                            permissionType = obj.get("permission_type").asString,
                            startDate = obj.get("start_date").asString,
                            endDate = obj.get("end_date").asString,
                            totalDays = obj.get("total_days")?.asInt ?: 1,
                            reason = obj.get("reason")?.asString.orEmpty(),
                            photoBase64 = obj.get("photo_base64")?.asString.orEmpty(),
                            status = obj.get("status")?.asString ?: "Menunggu"
                        )
                    )
                }
                return@withContext list
            }
        } catch (_: Exception) {
            // Silently fall back
        }
        emptyList()
    }
}
