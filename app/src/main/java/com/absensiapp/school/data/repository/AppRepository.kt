package com.absensiapp.school.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.absensiapp.school.data.model.AttendanceRecord
import com.absensiapp.school.data.model.AttendanceSchedule
import com.absensiapp.school.data.model.AttendanceStatus
import com.absensiapp.school.data.model.SchoolClass
import com.absensiapp.school.data.model.StudentCardData
import com.absensiapp.school.data.model.UserAccount
import com.absensiapp.school.data.model.UserRole
import com.absensiapp.school.data.remote.SupabaseClient
import com.absensiapp.school.data.remote.SupabaseConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    init {
        loadData()
        seedInitialAccountsIfEmpty()
    }

    private fun loadData() {
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
    }

    private fun seedInitialAccountsIfEmpty() {
        if (classesList.isEmpty()) {
            classesList.addAll(SchoolClass.DEFAULT_CLASSES)
        }

        // Student sample matching Figma photo: Rina Aulia Putri
        val rinaCard = StudentCardData(
            nisn = "2410031",
            name = "Rina Aulia Putri",
            birthPlaceDate = "Tuban, 15 Juli 2009",
            address = "Jl. Basuki Rahmat No. 45 Tuban",
            religion = "Islam",
            bloodType = "O",
            schoolName = "SMKS TARUNA JAYA PRAWIRA TUBAN",
            foundationName = "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA",
            headmasterName = "Bambang Kusdiyanto, ST.M.Pd",
            schoolAddress = "JL. DR. WAHIDIN SUDIROHUSODO TUBAN - JAWA TIMUR",
            className = "XI IPA 2"
        )
        studentCardsMap["2410031"] = rinaCard

        if (!usersMap.containsKey("2410031")) {
            usersMap["2410031"] = UserAccount(
                id = "2410031",
                nisn = "2410031",
                name = "Rina Aulia Putri",
                role = UserRole.STUDENT,
                passwordHash = "12345678",
                cardData = rinaCard,
                isBiometricEnrolled = true,
                isFaceEnrolled = true
            )
        }
        if (!usersMap.containsKey("rina.xipa2")) {
            usersMap["rina.xipa2"] = usersMap["2410031"]!!
        }

        val hasSeeded = prefs.getBoolean("has_seeded_initial_data_v2", false)
        if (hasSeeded) {
            saveAll()
            return
        }

        // Teacher accounts configured in database
        if (!usersMap.containsKey("1985031201")) {
            usersMap["1985031201"] = UserAccount(
                id = "1985031201",
                name = "Pak Budi Santoso, S.Kom",
                role = UserRole.TEACHER,
                passwordHash = "guru123"
            )
        }

        if (!usersMap.containsKey("1990082402")) {
            usersMap["1990082402"] = UserAccount(
                id = "1990082402",
                name = "Bu Siti Rahmawati, M.Pd",
                role = UserRole.TEACHER,
                passwordHash = "guru123"
            )
        }

        // Teacher matching Figma: Pak Dedi Kurniawan
        if (!usersMap.containsKey("1982051001")) {
            usersMap["1982051001"] = UserAccount(
                id = "1982051001",
                name = "Pak Dedi Kurniawan",
                role = UserRole.TEACHER,
                passwordHash = "guru123"
            )
        }

        // Admin account configured in database
        if (!usersMap.containsKey("admin")) {
            usersMap["admin"] = UserAccount(
                id = "admin",
                name = "Administrator HadirKu",
                role = UserRole.ADMIN,
                passwordHash = "admin123"
            )
        }

        // Add schedules for XI IPA 2 matching the Figma screenshot
        val figmaSchedules = listOf(
            AttendanceSchedule(
                id = "sch_ipa_01",
                className = "XI IPA 2",
                subject = "Bahasa Indonesia",
                teacherId = "1978010101",
                teacherName = "Pak Joko Santoso",
                periodStart = 1,
                periodEnd = 1,
                timeSlot = "07:00 - 07:45 WIB",
                dayName = "Senin",
                room = "R. 12",
                isOpenForAttendance = false
            ),
            AttendanceSchedule(
                id = "sch_ipa_02",
                className = "XI IPA 2",
                subject = "Matematika",
                teacherId = "1982051001",
                teacherName = "Pak Dedi Kurniawan",
                periodStart = 2,
                periodEnd = 3,
                timeSlot = "07:45 - 09:15 WIB",
                dayName = "Senin",
                room = "R. 12",
                isOpenForAttendance = true
            ),
            AttendanceSchedule(
                id = "sch_ipa_03",
                className = "XI IPA 2",
                subject = "Fisika",
                teacherId = "1987091201",
                teacherName = "Bu Ratna Ningrum",
                periodStart = 4,
                periodEnd = 5,
                timeSlot = "09:30 - 11:00 WIB",
                dayName = "Senin",
                room = "Lab Fisika",
                isOpenForAttendance = false
            ),
            AttendanceSchedule(
                id = "sch_ipa_04",
                className = "XI IPA 2",
                subject = "Bahasa Inggris",
                teacherId = "1991040501",
                teacherName = "Pak Arif Hidayat",
                periodStart = 6,
                periodEnd = 7,
                timeSlot = "11:00 - 12:30 WIB",
                dayName = "Senin",
                room = "R. 12",
                isOpenForAttendance = false
            )
        )

        for (sch in figmaSchedules) {
            if (schedulesList.none { it.id == sch.id }) {
                schedulesList.add(sch)
            }
        }

        // Also ensure XI TKJ 1 schedules exist
        if (schedulesList.none { it.className == "XI TKJ 1" }) {
            schedulesList.addAll(
                listOf(
                    AttendanceSchedule(
                        id = "sch_01",
                        className = "XI TKJ 1",
                        subject = "Administrasi Server Jaringan",
                        teacherId = "1985031201",
                        teacherName = "Pak Budi Santoso, S.Kom",
                        periodStart = 1,
                        periodEnd = 2,
                        timeSlot = "07:00 - 08:30 WIB",
                        room = "Lab Komputer 1",
                        isOpenForAttendance = true
                    ),
                    AttendanceSchedule(
                        id = "sch_02",
                        className = "XI TKJ 1",
                        subject = "Teknologi Layanan Jaringan",
                        teacherId = "1990082402",
                        teacherName = "Bu Siti Rahmawati, M.Pd",
                        periodStart = 3,
                        periodEnd = 4,
                        timeSlot = "08:45 - 10:15 WIB",
                        room = "Lab Komputer 2",
                        isOpenForAttendance = true
                    )
                )
            )
        }

        prefs.edit().putBoolean("has_seeded_initial_data_v2", true).apply()
        saveAll()
    }

    private fun saveAll() {
        prefs.edit()
            .putString(KEY_USERS, gson.toJson(usersMap))
            .putString(KEY_STUDENT_CARDS, gson.toJson(studentCardsMap))
            .putString(KEY_CLASSES, gson.toJson(classesList))
            .putString(KEY_SCHEDULES, gson.toJson(schedulesList))
            .putString(KEY_ATTENDANCE, gson.toJson(attendanceList))
            .apply()
    }

    // --- Classes Management with Coordinates ---
    fun getAllClasses(): List<SchoolClass> = classesList.toList()

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
            it.nisn.equals(trimmedId, ignoreCase = true) || it.name.equals(trimmedId, ignoreCase = true)
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
            val card = SupabaseClient.getStudentProfile(user.id) ?: studentCardsMap[user.id]
            resolvedUser = user.copy(cardData = card)
        }

        usersMap[trimmedId] = resolvedUser
        saveAll()
        saveCurrentSession(resolvedUser)
        return Result.success(resolvedUser)
    }

    fun isUsernameTaken(username: String): Boolean {
        val clean = username.trim().lowercase()
        return usersMap.keys.any { it.lowercase() == clean }
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
        return attendanceList.filter { it.studentNisn == clean }
    }

    // --- Schedules & Attendance ---
    fun getAllSchedules(): List<AttendanceSchedule> = schedulesList.toList()

    fun addSchedule(schedule: AttendanceSchedule) {
        schedulesList.add(schedule)
        saveAll()
        scope.launch {
            SupabaseClient.saveSchedule(schedule)
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
            schedulesList[index] = item.copy(isOpenForAttendance = isOpen)
            saveAll()
        }
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

    /**
     * Complete synchronization with Supabase remote database.
     * Replaces local cached schedules with true remote state,
     * ensuring schedules deleted in the database are removed from the app!
     */
    suspend fun syncAllFromRemote(): Result<Int> {
        if (!SupabaseConfig.isConfigured()) {
            return Result.success(schedulesList.size)
        }
        return try {
            // 1. Fetch live schedules directly from Supabase
            val remoteSchedules = SupabaseClient.getSchedules()
            if (remoteSchedules.isNotEmpty()) {
                schedulesList.clear()
                schedulesList.addAll(remoteSchedules)
            }

            // 2. Fetch live classes directly from Supabase
            val remoteClasses = SupabaseClient.getClasses()
            if (remoteClasses.isNotEmpty()) {
                classesList.clear()
                classesList.addAll(remoteClasses)
            }

            // 3. Fetch live attendance directly from Supabase
            val remoteAttendance = SupabaseClient.getAllAttendanceRecords()
            val validScheduleIds = schedulesList.map { it.id }.toSet()
            // Clean up: only retain attendance for existing schedules
            val validAttendance = remoteAttendance.filter { it.scheduleId in validScheduleIds }
            attendanceList.clear()
            attendanceList.addAll(validAttendance)

            saveAll()
            Result.success(schedulesList.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSchedule(scheduleId: String): Boolean {
        schedulesList.removeAll { it.id == scheduleId }
        attendanceList.removeAll { it.scheduleId == scheduleId }
        saveAll()
        return SupabaseClient.deleteSchedule(scheduleId)
    }

    companion object {
        private const val KEY_USERS = "users_cache"
        private const val KEY_STUDENT_CARDS = "cards_cache"
        private const val KEY_CLASSES = "classes_cache"
        private const val KEY_SCHEDULES = "schedules_cache"
        private const val KEY_ATTENDANCE = "attendance_cache"
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
