package com.absensiapp.school.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.absensiapp.school.data.model.AttendanceRecord
import com.absensiapp.school.data.model.AttendanceSchedule
import com.absensiapp.school.data.model.AttendanceStatus
import com.absensiapp.school.data.model.SchoolClass
import com.absensiapp.school.data.model.StudentCardData
import com.absensiapp.school.data.model.StudentPermission
import com.absensiapp.school.data.model.UserAccount
import com.absensiapp.school.data.model.UserRole
import com.absensiapp.school.data.remote.SupabaseClient
import com.absensiapp.school.data.remote.SupabaseConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AppRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("absensi_taruna_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val usersMap = mutableMapOf<String, UserAccount>()
    private val studentCardsMap = mutableMapOf<String, StudentCardData>()
    private val classesList = mutableListOf<SchoolClass>()
    private val schedulesList = mutableListOf<AttendanceSchedule>()
    private val attendanceList = mutableListOf<AttendanceRecord>()
    private val permissionsList = mutableListOf<StudentPermission>()

    init {
        loadData()
        seedInitialAccountsIfEmpty()
    }

    private fun loadData() {
        val hasPurgedMockData = prefs.getBoolean("has_purged_mock_seed_v7", false)
        if (!hasPurgedMockData) {
            prefs.edit()
                .remove(KEY_USERS)
                .remove(KEY_STUDENT_CARDS)
                .remove(KEY_SCHEDULES)
                .remove(KEY_ATTENDANCE)
                .remove(KEY_PERMISSIONS)
                .remove(KEY_CURRENT_USER)
                .putBoolean("has_purged_mock_seed_v7", true)
                .apply()
            usersMap.clear()
            studentCardsMap.clear()
            schedulesList.clear()
            attendanceList.clear()
            permissionsList.clear()
        }

        val usersJson = prefs.getString(KEY_USERS, null)
        if (!usersJson.isNullOrEmpty()) {
            val type = object : TypeToken<Map<String, UserAccount>>() {}.type
            val savedUsers: Map<String, UserAccount> = gson.fromJson(usersJson, type)
            usersMap.putAll(savedUsers)
        }

        val cardsJson = prefs.getString(KEY_STUDENT_CARDS, null)
        if (!cardsJson.isNullOrEmpty()) {
            val type = object : TypeToken<Map<String, StudentCardData>>() {}.type
            val savedCards: Map<String, StudentCardData> = gson.fromJson(cardsJson, type)
            studentCardsMap.putAll(savedCards)
        }

        val classesJson = prefs.getString(KEY_CLASSES, null)
        if (!classesJson.isNullOrEmpty()) {
            val type = object : TypeToken<List<SchoolClass>>() {}.type
            val savedClasses: List<SchoolClass> = gson.fromJson(classesJson, type)
            classesList.addAll(savedClasses)
        }

        val schedulesJson = prefs.getString(KEY_SCHEDULES, null)
        if (!schedulesJson.isNullOrEmpty()) {
            val type = object : TypeToken<List<AttendanceSchedule>>() {}.type
            val savedSchedules: List<AttendanceSchedule> = gson.fromJson(schedulesJson, type)
            schedulesList.addAll(savedSchedules)
        }

        val attendanceJson = prefs.getString(KEY_ATTENDANCE, null)
        if (!attendanceJson.isNullOrEmpty()) {
            val type = object : TypeToken<List<AttendanceRecord>>() {}.type
            val savedAttendance: List<AttendanceRecord> = gson.fromJson(attendanceJson, type)
            attendanceList.addAll(savedAttendance)
        }

        val permissionsJson = prefs.getString(KEY_PERMISSIONS, null)
        if (!permissionsJson.isNullOrEmpty()) {
            val type = object : TypeToken<List<StudentPermission>>() {}.type
            val savedPermissions: List<StudentPermission> = gson.fromJson(permissionsJson, type)
            permissionsList.addAll(savedPermissions)
        }
    }

    private fun seedInitialAccountsIfEmpty() {
        if (classesList.isEmpty()) {
            classesList.addAll(SchoolClass.DEFAULT_CLASSES)
        }

        // Official fallback accounts matching Supabase remote database in case of offline launch
        if (!usersMap.containsKey("admin")) {
            val adminAcc = UserAccount(
                id = "admin",
                username = "admin",
                name = "ADMINISTRATOR",
                role = UserRole.ADMIN,
                passwordHash = "Admin123"
            )
            usersMap["admin"] = adminAcc
        }

        if (!usersMap.containsKey("budi.santoso") && !usersMap.containsKey("198501012010011001")) {
            val teacherAcc = UserAccount(
                id = "198501012010011001",
                username = "budi.santoso",
                nisn = "198501012010011001",
                name = "BUDI SANTOSO, S.Pd",
                role = UserRole.TEACHER,
                passwordHash = "Guru123"
            )
            usersMap["budi.santoso"] = teacherAcc
            usersMap["198501012010011001"] = teacherAcc
        }

        if (!usersMap.containsKey("siti.nurhaliza") && !usersMap.containsKey("198703152011012002")) {
            val teacherAcc = UserAccount(
                id = "198703152011012002",
                username = "siti.nurhaliza",
                nisn = "198703152011012002",
                name = "SITI NURHALIZA, S.Kom",
                role = UserRole.TEACHER,
                passwordHash = "Guru123"
            )
            usersMap["siti.nurhaliza"] = teacherAcc
            usersMap["198703152011012002"] = teacherAcc
        }

        // Real official student matching Supabase database
        val officialStudentCard = StudentCardData(
            nisn = "0095687758",
            name = "DERRICK VITO RENGGADITA",
            birthPlaceDate = "Tuban, 23 Februari 2009",
            address = "DS. Jarorejo RT.03/02 Kerek - Tuban",
            religion = "Islam",
            bloodType = "A",
            schoolName = "SMKS TARUNA JAYA PRAWIRA TUBAN",
            className = "XI RPL 1",
            headmasterName = "Bambang Kusdiyanto, ST.M.Pd"
        )

        studentCardsMap["0095687758"] = officialStudentCard

        if (!usersMap.containsKey("0095687758")) {
            val studentAcc = UserAccount(
                id = "0095687758",
                username = "0095687758",
                nisn = "0095687758",
                name = "DERRICK VITO RENGGADITA",
                role = UserRole.STUDENT,
                passwordHash = "Rengga123",
                cardData = officialStudentCard,
                enrolledDeviceId = "a978f676f77c44a2"
            )
            usersMap["0095687758"] = studentAcc
        }

        saveAll()
    }

    private fun saveAll() {
        prefs.edit()
            .putString(KEY_USERS, gson.toJson(usersMap))
            .putString(KEY_STUDENT_CARDS, gson.toJson(studentCardsMap))
            .putString(KEY_CLASSES, gson.toJson(classesList))
            .putString(KEY_SCHEDULES, gson.toJson(schedulesList))
            .putString(KEY_ATTENDANCE, gson.toJson(attendanceList))
            .putString(KEY_PERMISSIONS, gson.toJson(permissionsList))
            .apply()
    }

    fun getAllStudents(): List<UserAccount> {
        val list = mutableListOf<UserAccount>()
        val seenNisns = mutableSetOf<String>()
        for (user in usersMap.values.filter { it.role == UserRole.STUDENT }) {
            val nis = user.nisn ?: user.id
            if (seenNisns.add(nis)) {
                list.add(user)
            }
        }
        for ((nisn, card) in studentCardsMap) {
            if (seenNisns.add(nisn)) {
                list.add(
                    UserAccount(
                        id = nisn,
                        nisn = nisn,
                        name = card.name,
                        role = UserRole.STUDENT,
                        passwordHash = "12345678",
                        cardData = card
                    )
                )
            }
        }
        return list.sortedBy { it.name }
    }

    fun getStudentsByClass(className: String): List<UserAccount> {
        val clean = className.trim()
        if (clean.equals("Semua", ignoreCase = true)) {
            return getAllStudents()
        }
        return getAllStudents().filter {
            it.cardData?.className?.trim().equals(clean, ignoreCase = true)
        }
    }

    fun getAllAttendanceRecords(): List<AttendanceRecord> = attendanceList.toList()

    // --- Classes Management with Coordinates ---
    fun getAllClasses(): List<SchoolClass> {
        val result = mutableListOf<SchoolClass>()
        result.addAll(classesList)
        val existingNames = result.map { it.name.trim().lowercase() }.toSet()
        for (st in studentCardsMap.values) {
            val cName = st.className.trim()
            if (cName.isNotEmpty() && !existingNames.contains(cName.lowercase())) {
                result.add(
                    SchoolClass(
                        id = "cls_${cName.lowercase().replace(" ", "_")}",
                        name = cName,
                        room = "R. Kelas",
                        latitude = -6.89972,
                        longitude = 112.04944,
                        radiusMeters = 50f
                    )
                )
            }
        }
        return result
    }

    fun getClassByName(className: String): SchoolClass? {
        val trimmed = className.trim()
        return classesList.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            ?: SchoolClass.DEFAULT_CLASSES.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
    }

    fun addClass(schoolClass: SchoolClass) {
        val index = classesList.indexOfFirst { it.name.equals(schoolClass.name, ignoreCase = true) }
        if (index != -1) {
            classesList[index] = schoolClass
        } else {
            classesList.add(schoolClass)
        }
        saveAll()
        scope.launch {
            SupabaseClient.saveClass(schoolClass)
        }
    }

    // --- Authentication (Unified Login - Role determined directly from Database) ---
    suspend fun login(id: String, passwordAttempt: String): Result<UserAccount> {
        val trimmedId = id.trim()

        // 1. Check Supabase first
        val remoteUser = SupabaseClient.getUser(trimmedId)
        val user = remoteUser ?: usersMap[trimmedId] ?: usersMap.values.firstOrNull {
            it.nisn.equals(trimmedId, ignoreCase = true) ||
            it.username.equals(trimmedId, ignoreCase = true) ||
            it.name.equals(trimmedId, ignoreCase = true)
        }

        if (user == null) {
            return Result.failure(
                Exception("Akun dengan ID $trimmedId tidak ditemukan dalam sistem.")
            )
        }

        if (user.passwordHash != passwordAttempt) {
            return Result.failure(Exception("Kata sandi yang Anda masukkan salah."))
        }

        // If student, attach student card details
        var resolvedUser = user
        if (user.role == UserRole.STUDENT) {
            val nisnToLookup = user.nisn ?: user.id
            val card = SupabaseClient.getStudentProfile(nisnToLookup)
                ?: studentCardsMap[nisnToLookup]
                ?: studentCardsMap[user.id]
            resolvedUser = user.copy(cardData = card)
        }

        usersMap[trimmedId] = resolvedUser
        user.username?.let { usersMap[it] = resolvedUser }
        user.nisn?.let { usersMap[it] = resolvedUser }
        saveAll()
        saveCurrentSession(resolvedUser)
        return Result.success(resolvedUser)
    }

    fun isUsernameTaken(username: String): Boolean {
        val clean = username.trim().lowercase()
        return usersMap.keys.any { it.lowercase() == clean } ||
               usersMap.values.any { it.username?.lowercase() == clean }
    }

    suspend fun registerStudentCard(
        cardData: StudentCardData,
        password: String,
        username: String = ""
    ): Result<UserAccount> {
        val nisn = cardData.nisn.trim()
        val chosenUsername = if (username.isNotBlank()) username.trim().lowercase() else nisn
        if (password.length < 4) {
            return Result.failure(Exception("Kata sandi minimal 4 karakter."))
        }

        studentCardsMap[nisn] = cardData

        val newAccount = UserAccount(
            id = chosenUsername,
            nisn = nisn,
            username = chosenUsername,
            name = cardData.name,
            role = UserRole.STUDENT,
            passwordHash = password,
            cardData = cardData
        )

        usersMap[chosenUsername] = newAccount
        usersMap[nisn] = newAccount
        saveAll()
        saveCurrentSession(newAccount)

        // Asynchronously persist to Supabase database
        scope.launch {
            SupabaseClient.saveUser(newAccount)
            SupabaseClient.saveStudentProfile(cardData)
        }

        return Result.success(newAccount)
    }

    fun lookupStudentCard(nisn: String): StudentCardData? {
        val cleanNisn = nisn.replace(Regex("[^0-9]"), "")
        return studentCardsMap[cleanNisn]
    }

    fun isStudentAccountRegistered(nisn: String): Boolean {
        return usersMap.containsKey(nisn)
    }

    fun getUserById(id: String): UserAccount? {
        return usersMap[id.trim()]
    }

    fun enrollStudentBiometrics(
        studentNisn: String,
        faceSignature: String,
        deviceId: String
    ): UserAccount? {
        val targetId = studentNisn.trim()
        val existing = usersMap[targetId] ?: getCurrentSession()
        if (existing != null) {
            val enrolledAt = System.currentTimeMillis()
            val enrolledUser = existing.copy(
                isBiometricEnrolled = true,
                isFaceEnrolled = true,
                faceSignature = faceSignature,
                biometricEnrolledAt = enrolledAt,
                enrolledDeviceId = deviceId
            )
            usersMap[targetId] = enrolledUser
            saveAll()
            if (getCurrentSession()?.id == targetId) {
                saveCurrentSession(enrolledUser)
            }
            scope.launch {
                SupabaseClient.updateBiometricEnrollment(
                    userId = targetId,
                    faceSignature = faceSignature,
                    deviceId = deviceId,
                    enrolledAt = enrolledAt
                )
            }
            return enrolledUser
        }
        return null
    }

    fun resetStudentBiometrics(studentNisn: String): UserAccount? {
        val targetId = studentNisn.trim()
        val existing = usersMap[targetId] ?: getCurrentSession()
        if (existing != null) {
            val resetUser = existing.copy(
                isBiometricEnrolled = false,
                isFaceEnrolled = false,
                faceSignature = null,
                biometricEnrolledAt = 0L,
                enrolledDeviceId = null
            )
            usersMap[targetId] = resetUser
            saveAll()
            if (getCurrentSession()?.id == targetId) {
                saveCurrentSession(resetUser)
            }
            scope.launch {
                SupabaseClient.updateBiometricEnrollment(
                    userId = targetId,
                    faceSignature = "",
                    deviceId = "",
                    enrolledAt = 0L
                )
            }
            return resetUser
        }
        return null
    }

    fun saveCurrentSession(user: UserAccount) {
        prefs.edit().putString(KEY_CURRENT_USER, gson.toJson(user)).commit()
    }

    fun getCurrentSession(): UserAccount? {
        val json = prefs.getString(KEY_CURRENT_USER, null) ?: return null
        return try {
            gson.fromJson(json, UserAccount::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun clearSession() {
        prefs.edit().remove(KEY_CURRENT_USER).commit()
    }

    fun changePassword(userId: String, newPasswordPlain: String): Boolean {
        val targetId = userId.trim()
        val user = usersMap[targetId] ?: getCurrentSession() ?: return false
        val updated = user.copy(passwordHash = newPasswordPlain)
        usersMap[targetId] = updated
        saveAll()
        if (getCurrentSession()?.id == targetId) {
            saveCurrentSession(updated)
        }
        return true
    }

    fun getStudentAttendanceRecords(studentNisn: String): List<AttendanceRecord> {
        val clean = studentNisn.trim()
        val user = usersMap[clean] ?: usersMap.values.firstOrNull {
            it.nisn?.equals(clean, ignoreCase = true) == true ||
            it.username?.equals(clean, ignoreCase = true) == true
        }
        val targetNis = user?.nisn ?: user?.id ?: clean
        return attendanceList.filter {
            it.studentNisn.trim().equals(clean, ignoreCase = true) ||
            it.studentNisn.trim().equals(targetNis, ignoreCase = true)
        }.sortedByDescending { it.timestamp }
    }

    // --- Schedules & Attendance ---
    fun getAllSchedules(): List<AttendanceSchedule> {
        var modified = false
        for (i in schedulesList.indices) {
            val item = schedulesList[i]
            if (item.isOpenForAttendance && item.isExpired()) {
                val updated = item.copy(isOpenForAttendance = false)
                schedulesList[i] = updated
                modified = true
                scope.launch { SupabaseClient.saveSchedule(updated) }
            }
        }
        if (modified) {
            saveAll()
        }
        return schedulesList.toList()
    }

    fun addSchedule(schedule: AttendanceSchedule) {
        saveSchedule(schedule)
    }

    fun saveSchedule(schedule: AttendanceSchedule) {
        val scheduleToSave = if (schedule.isExpired()) schedule.copy(isOpenForAttendance = false) else schedule
        val existingIndex = schedulesList.indexOfFirst {
            it.id == scheduleToSave.id || (
                it.className.trim().equals(scheduleToSave.className.trim(), ignoreCase = true) &&
                it.sessionDate == scheduleToSave.sessionDate &&
                scheduleToSave.sessionDate.isNotBlank() &&
                scheduleToSave.sessionDate != "DELETED"
            )
        }
        val finalSchedule = if (existingIndex != -1) {
            scheduleToSave.copy(id = schedulesList[existingIndex].id)
        } else {
            scheduleToSave
        }

        if (existingIndex != -1) {
            schedulesList[existingIndex] = finalSchedule
        } else {
            schedulesList.add(finalSchedule)
        }

        // Clean up any remaining duplicate schedules for the exact same class and date
        val duplicates = schedulesList.filter {
            it.id != finalSchedule.id &&
            it.className.trim().equals(finalSchedule.className.trim(), ignoreCase = true) &&
            it.sessionDate == finalSchedule.sessionDate &&
            finalSchedule.sessionDate.isNotBlank() &&
            finalSchedule.sessionDate != "DELETED"
        }
        for (dup in duplicates) {
            schedulesList.remove(dup)
            scope.launch { SupabaseClient.deleteSchedule(dup.id) }
        }

        saveAll()
        scope.launch {
            SupabaseClient.saveSchedule(finalSchedule)
        }
    }

    fun deleteSchedule(scheduleId: String) {
        val target = schedulesList.firstOrNull { it.id == scheduleId }
        schedulesList.removeAll { it.id == scheduleId }
        attendanceList.removeAll { it.scheduleId == scheduleId }
        saveAll()
        scope.launch {
            SupabaseClient.deleteSchedule(scheduleId)
            if (target != null && target.sessionDate.isNotBlank()) {
                val remotes = SupabaseClient.getSchedules()
                for (r in remotes) {
                    if (r.className.trim().equals(target.className.trim(), ignoreCase = true) && r.sessionDate == target.sessionDate) {
                        SupabaseClient.deleteSchedule(r.id)
                    }
                }
            }
        }
    }

    fun updateScheduleTimes(
        scheduleId: String,
        startTime: String,
        endTime: String,
        periodStart: Int,
        periodEnd: Int
    ) {
        val index = schedulesList.indexOfFirst { it.id == scheduleId }
        if (index != -1) {
            val old = schedulesList[index]
            val updated = old.copy(
                timeSlot = "$startTime - $endTime WIB",
                periodStart = periodStart,
                periodEnd = periodEnd
            )
            schedulesList[index] = updated
            saveAll()
            scope.launch {
                SupabaseClient.saveSchedule(updated)
            }
        }
    }

    fun toggleScheduleStatus(scheduleId: String, isOpen: Boolean) {
        val index = schedulesList.indexOfFirst { it.id == scheduleId }
        if (index != -1) {
            val item = schedulesList[index]
            if (isOpen && item.isExpired()) {
                return
            }
            val updated = item.copy(isOpenForAttendance = isOpen)
            schedulesList[index] = updated
            saveAll()
            scope.launch {
                SupabaseClient.saveSchedule(updated)
            }
        }
    }

    fun addOrUpdateSession(
        className: String,
        sessionDate: String,
        isOpen: Boolean,
        teacherId: String,
        teacherName: String,
        startTime: String = "07:10",
        endTime: String = "08:00"
    ): AttendanceSchedule {
        val cleanClass = className.trim()
        val timeSlot = "$startTime - $endTime WIB"
        val existing = schedulesList.firstOrNull {
            it.className.trim().equals(cleanClass, ignoreCase = true) &&
            (it.sessionDate == sessionDate || it.sessionDate.isBlank())
        }
        val dayName = try {
            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(sessionDate)
            SimpleDateFormat("EEEE", Locale.forLanguageTag("id-ID")).format(sdfDate ?: java.util.Date())
        } catch (_: Exception) {
            "Senin"
        }

        val schedule = if (existing != null) {
            val updated = existing.copy(
                sessionDate = sessionDate,
                timeSlot = timeSlot,
                isOpenForAttendance = isOpen,
                dayName = dayName,
                teacherId = teacherId,
                teacherName = teacherName
            )
            val idx = schedulesList.indexOf(existing)
            schedulesList[idx] = updated
            updated
        } else {
            val newSched = AttendanceSchedule(
                id = "sch_${System.currentTimeMillis()}",
                className = cleanClass,
                subject = "Presensi Kelas $cleanClass",
                teacherId = teacherId,
                teacherName = teacherName,
                periodStart = 1,
                periodEnd = 2,
                timeSlot = timeSlot,
                dayName = dayName,
                room = getClassByName(cleanClass)?.room ?: "R. Kelas",
                isOpenForAttendance = isOpen,
                sessionDate = sessionDate
            )
            schedulesList.add(newSched)
            newSched
        }
        saveAll()
        scope.launch {
            SupabaseClient.saveSchedule(schedule)
        }
        return schedule
    }

    fun markAttendance(
        scheduleId: String,
        studentNisn: String,
        studentName: String,
        className: String,
        biometricVerified: Boolean = true,
        faceVerified: Boolean = true,
        locationDistanceMeters: Int = 0,
        verificationMethod: String = "3-Lapis (GPS + Biometrik + Wajah)",
        remarks: String = "Presensi Mandiri Terverifikasi (Lokasi + Biometrik + Wajah)"
    ): Boolean {
        val alreadyMarked = attendanceList.any { it.scheduleId == scheduleId && it.studentNisn == studentNisn }
        if (alreadyMarked) return false

        val record = AttendanceRecord(
            id = UUID.randomUUID().toString(),
            scheduleId = scheduleId,
            studentNisn = studentNisn,
            studentName = studentName,
            className = className,
            status = AttendanceStatus.PRESENT,
            biometricVerified = biometricVerified,
            faceVerified = faceVerified,
            locationDistanceMeters = locationDistanceMeters,
            verificationMethod = verificationMethod,
            remarks = remarks
        )
        attendanceList.add(record)
        saveAll()

        scope.launch {
            SupabaseClient.saveAttendanceRecord(record)
        }
        return true
    }

    fun isStudentAttended(scheduleId: String, studentNisn: String): Boolean {
        return attendanceList.any { it.scheduleId == scheduleId && it.studentNisn == studentNisn }
    }

    fun getTodayDateKey(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    fun formatDateKey(timeMillis: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timeMillis))
    }

    fun hasStudentClockedInToday(studentNisn: String): Boolean {
        val todayKey = getTodayDateKey()
        return attendanceList.any { record ->
            record.studentNisn == studentNisn && formatDateKey(record.timestamp) == todayKey
        }
    }

    fun getStudentAttendanceForDate(studentNisn: String, dateKey: String): AttendanceRecord? {
        return attendanceList.firstOrNull { record ->
            record.studentNisn == studentNisn && formatDateKey(record.timestamp) == dateKey
        }
    }

    fun getAttendanceForSchedule(scheduleId: String): List<AttendanceRecord> {
        return attendanceList.filter { it.scheduleId == scheduleId }
    }

    /**
     * Strictly retrieves attendance records for a specific class only.
     * Prevents viewing attendance of other classes.
     */
    fun getAttendanceForClass(className: String): List<AttendanceRecord> {
        if (className.isBlank()) return emptyList()
        return attendanceList.filter { it.className.equals(className, ignoreCase = true) }
    }

    /**
     * Strictly retrieves attendance records for a specific class and schedule.
     */
    fun getAttendanceForClassAndSchedule(className: String, scheduleId: String): List<AttendanceRecord> {
        if (className.isBlank()) return emptyList()
        return attendanceList.filter {
            it.className.equals(className, ignoreCase = true) && it.scheduleId == scheduleId
        }
    }

    suspend fun syncAttendanceForClass(className: String) {
        if (className.isBlank()) return
        val remoteRecords = SupabaseClient.getAttendanceForClass(className)
        if (remoteRecords.isNotEmpty()) {
            for (rec in remoteRecords) {
                if (attendanceList.none { it.id == rec.id }) {
                    attendanceList.add(rec)
                }
            }
            saveAll()
        }
    }

    // --- Student Permission (Izin) Management ---
    fun getAllPermissions(): List<StudentPermission> = permissionsList.toList()

    fun getStudentPermissions(nisn: String): List<StudentPermission> {
        val clean = nisn.trim()
        val user = usersMap[clean] ?: usersMap.values.firstOrNull {
            it.nisn?.equals(clean, ignoreCase = true) == true ||
            it.username?.equals(clean, ignoreCase = true) == true
        }
        val targetNis = user?.nisn ?: user?.id ?: clean
        return permissionsList.filter {
            it.studentNisn.trim().equals(clean, ignoreCase = true) ||
            it.studentNisn.trim().equals(targetNis, ignoreCase = true)
        }.sortedByDescending { it.timestamp }
    }

    fun submitPermission(permission: StudentPermission): Result<Boolean> {
        permissionsList.removeAll { it.id == permission.id }
        permissionsList.add(0, permission)

        // Automatically record attendance as PERMITTED so reports and schedules reflect Izin
        val attendanceId = UUID.randomUUID().toString()
        val attendanceRecord = AttendanceRecord(
            id = attendanceId,
            scheduleId = "permission_${permission.id}",
            studentNisn = permission.studentNisn,
            studentName = permission.studentName,
            className = permission.className,
            timestamp = permission.timestamp,
            status = AttendanceStatus.PERMITTED,
            biometricVerified = false,
            faceVerified = false,
            locationDistanceMeters = 0,
            verificationMethod = "Surat Izin Resmi",
            remarks = "Izin: ${permission.permissionType} (${permission.startDate} s/d ${permission.endDate}, ${permission.totalDays} hari) - ${permission.reason}"
        )
        attendanceList.add(0, attendanceRecord)
        saveAll()

        scope.launch {
            SupabaseClient.savePermission(permission)
            SupabaseClient.saveAttendanceRecord(attendanceRecord)
        }
        return Result.success(true)
    }

    fun updatePermissionStatus(permissionId: String, newStatus: String) {
        val index = permissionsList.indexOfFirst { it.id == permissionId }
        if (index != -1) {
            val updated = permissionsList[index].copy(status = newStatus)
            permissionsList[index] = updated
            saveAll()
            scope.launch {
                SupabaseClient.savePermission(updated)
            }
        }
    }

    /**
     * Complete synchronization with Supabase remote database.
     * Synchronizes live students from students table, schedules, classes, attendance, and permissions.
     */
    suspend fun syncAllFromRemote(): Result<Int> {
        if (!SupabaseConfig.isConfigured()) {
            return Result.success(schedulesList.size)
        }
        return try {
            // 0. Fetch live users from Supabase database table `users`
            val remoteUsers = SupabaseClient.getAllUsersFromDb()
            if (remoteUsers.isNotEmpty()) {
                usersMap.clear()
                for (u in remoteUsers) {
                    val key = u.username ?: u.nisn ?: u.id
                    usersMap[key] = u
                    u.username?.let { usersMap[it] = u }
                    u.nisn?.let { usersMap[it] = u }
                }
            }

            // 1. Fetch live students directly from Supabase database table `students`
            val remoteStudents = SupabaseClient.getAllStudentsFromDb()
            if (remoteStudents.isNotEmpty()) {
                studentCardsMap.clear()
                for (st in remoteStudents) {
                    studentCardsMap[st.nisn] = st
                    val existing = usersMap[st.nisn] ?: usersMap.values.firstOrNull { it.nisn == st.nisn }
                    if (existing != null) {
                        val updated = existing.copy(name = st.name, cardData = st)
                        usersMap[st.nisn] = updated
                        updated.username?.let { usersMap[it] = updated }
                        usersMap[updated.id] = updated
                    } else {
                        val studentAcc = UserAccount(
                            id = st.nisn,
                            nisn = st.nisn,
                            username = st.nisn,
                            name = st.name,
                            role = UserRole.STUDENT,
                            passwordHash = "12345678",
                            cardData = st
                        )
                        usersMap[st.nisn] = studentAcc
                    }
                }
            }

            // 2. Fetch live schedules directly from Supabase
            val remoteSchedules = SupabaseClient.getSchedules()
            schedulesList.clear()
            schedulesList.addAll(remoteSchedules)

            // 3. Fetch live classes directly from Supabase
            val remoteClasses = SupabaseClient.getClasses()
            if (remoteClasses.isNotEmpty()) {
                classesList.clear()
                classesList.addAll(remoteClasses)
            }

            // 4. Fetch live attendance directly from Supabase
            val remoteAttendance = SupabaseClient.getAllAttendanceRecords()
            attendanceList.clear()
            attendanceList.addAll(remoteAttendance)

            // 5. Fetch live permissions directly from Supabase
            val remotePermissions = SupabaseClient.getAllPermissions()
            permissionsList.clear()
            permissionsList.addAll(remotePermissions)

            saveAll()
            Result.success(schedulesList.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPureStudentsFromDatabase(): List<UserAccount> {
        if (!SupabaseConfig.isConfigured()) {
            return getAllStudents()
        }
        return try {
            val remoteUsers = SupabaseClient.getAllUsersFromDb()
            val remoteStudents = SupabaseClient.getAllStudentsFromDb()
            val remoteAttendance = SupabaseClient.getAllAttendanceRecords()
            val remotePermissions = SupabaseClient.getAllPermissions()

            if (remoteAttendance.isNotEmpty()) {
                attendanceList.clear()
                attendanceList.addAll(remoteAttendance)
            }
            if (remotePermissions.isNotEmpty()) {
                permissionsList.clear()
                permissionsList.addAll(remotePermissions)
            }

            studentCardsMap.clear()
            for (st in remoteStudents) {
                studentCardsMap[st.nisn] = st
            }

            val studentAccounts = mutableMapOf<String, UserAccount>()

            // 1. From remote students table
            for (st in remoteStudents) {
                val matchingUser = remoteUsers.firstOrNull {
                    it.nisn == st.nisn || it.username == st.nisn || it.id == st.nisn
                }
                val acc = UserAccount(
                    id = matchingUser?.id ?: st.nisn,
                    dbId = matchingUser?.dbId,
                    nisn = st.nisn,
                    username = matchingUser?.username ?: st.nisn,
                    name = st.name,
                    role = UserRole.STUDENT,
                    passwordHash = matchingUser?.passwordHash ?: "12345678",
                    cardData = st,
                    isBiometricEnrolled = matchingUser?.isBiometricEnrolled ?: false,
                    isFaceEnrolled = matchingUser?.isFaceEnrolled ?: false,
                    faceSignature = matchingUser?.faceSignature,
                    biometricEnrolledAt = matchingUser?.biometricEnrolledAt ?: 0L,
                    enrolledDeviceId = matchingUser?.enrolledDeviceId
                )
                studentAccounts[st.nisn] = acc
                usersMap[st.nisn] = acc
            }

            // 2. From remote users table with role == STUDENT
            for (u in remoteUsers.filter { it.role == UserRole.STUDENT }) {
                val nisn = u.nisn ?: u.username ?: u.id
                if (!studentAccounts.containsKey(nisn)) {
                    val card = studentCardsMap[nisn] ?: StudentCardData(
                        nisn = nisn,
                        name = u.name,
                        birthPlaceDate = "-",
                        address = "-",
                        religion = "-",
                        bloodType = "-",
                        schoolName = "SMKS TARUNA JAYA PRAWIRA TUBAN",
                        className = "XI RPL 1",
                        headmasterName = "Bambang Kusdiyanto, ST.M.Pd"
                    )
                    studentCardsMap[nisn] = card
                    val acc = u.copy(cardData = card)
                    studentAccounts[nisn] = acc
                    usersMap[nisn] = acc
                }
            }

            saveAll()
            studentAccounts.values.sortedBy { it.name }
        } catch (_: Exception) {
            getAllStudents()
        }
    }

    suspend fun syncStudentAttendanceAndPermits(studentNisn: String) {
        if (!SupabaseConfig.isConfigured()) return
        try {
            val remoteRecords = SupabaseClient.getAttendanceForStudent(studentNisn)
            if (remoteRecords.isNotEmpty()) {
                attendanceList.removeAll { it.studentNisn == studentNisn }
                attendanceList.addAll(remoteRecords)
            }
            val remotePermits = SupabaseClient.getPermissionsForStudent(studentNisn)
            if (remotePermits.isNotEmpty()) {
                permissionsList.removeAll { it.studentNisn == studentNisn }
                permissionsList.addAll(remotePermits)
            }
            saveAll()
        } catch (_: Exception) {}
    }

    companion object {
        private const val KEY_USERS = "users_cache"
        private const val KEY_STUDENT_CARDS = "cards_cache"
        private const val KEY_CLASSES = "classes_cache"
        private const val KEY_SCHEDULES = "schedules_cache"
        private const val KEY_ATTENDANCE = "attendance_cache"
        private const val KEY_PERMISSIONS = "permissions_cache"
        private const val KEY_CURRENT_USER = "current_session_user"

        @Volatile
        private var instance: AppRepository? = null

        fun getInstance(context: Context): AppRepository {
            return instance ?: synchronized(this) {
                instance ?: AppRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
