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

        // Seed real student accounts
        val initialStudents = listOf(
            StudentCardData("2410031", "Rina Aulia Putri", "Tuban, 15 Juli 2009", "Jl. Basuki Rahmat No. 45 Tuban", "Islam", "O", "SMKS TARUNA JAYA PRAWIRA TUBAN", "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA", "Bambang Kusdiyanto, ST.M.Pd", "JL. DR. WAHIDIN SUDIROHUSODO TUBAN", "XI IPA 2"),
            StudentCardData("2410032", "Bagas Prasetyo", "Tuban, 22 Agustus 2009", "Jl. Veteran No. 12 Tuban", "Islam", "B", "SMKS TARUNA JAYA PRAWIRA TUBAN", "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA", "Bambang Kusdiyanto, ST.M.Pd", "JL. DR. WAHIDIN SUDIROHUSODO TUBAN", "XI IPA 2"),
            StudentCardData("2410033", "Citra Maharani", "Tuban, 10 Maret 2009", "Jl. Panglima Sudirman No. 8 Tuban", "Islam", "A", "SMKS TARUNA JAYA PRAWIRA TUBAN", "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA", "Bambang Kusdiyanto, ST.M.Pd", "JL. DR. WAHIDIN SUDIROHUSODO TUBAN", "XI IPA 2"),
            StudentCardData("2410034", "Dimas Anggara", "Tuban, 5 Januari 2009", "Jl. Hayam Wuruk No. 19 Tuban", "Islam", "AB", "SMKS TARUNA JAYA PRAWIRA TUBAN", "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA", "Bambang Kusdiyanto, ST.M.Pd", "JL. DR. WAHIDIN SUDIROHUSODO TUBAN", "XI IPA 2"),
            StudentCardData("2410035", "Eka Fitriani", "Tuban, 18 September 2009", "Jl. Gajah Mada No. 33 Tuban", "Islam", "O", "SMKS TARUNA JAYA PRAWIRA TUBAN", "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA", "Bambang Kusdiyanto, ST.M.Pd", "JL. DR. WAHIDIN SUDIROHUSODO TUBAN", "XI IPA 2"),
            StudentCardData("2410036", "Fajar Hidayat", "Tuban, 12 Desember 2009", "Jl. Letda Sucipto No. 70 Tuban", "Islam", "B", "SMKS TARUNA JAYA PRAWIRA TUBAN", "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA", "Bambang Kusdiyanto, ST.M.Pd", "JL. DR. WAHIDIN SUDIROHUSODO TUBAN", "XI IPA 2"),
            StudentCardData("2410041", "Gilang Ramadhan", "Tuban, 3 April 2009", "Jl. Pramuka No. 14 Tuban", "Islam", "A", "SMKS TARUNA JAYA PRAWIRA TUBAN", "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA", "Bambang Kusdiyanto, ST.M.Pd", "JL. DR. WAHIDIN SUDIROHUSODO TUBAN", "XI TKJ 1"),
            StudentCardData("2410042", "Budi Santoso", "Tuban, 29 Mei 2009", "Jl. Diponegoro No. 25 Tuban", "Islam", "O", "SMKS TARUNA JAYA PRAWIRA TUBAN", "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA", "Bambang Kusdiyanto, ST.M.Pd", "JL. DR. WAHIDIN SUDIROHUSODO TUBAN", "XI TKJ 1"),
            StudentCardData("2410051", "Hana Lestari", "Tuban, 14 Juli 2008", "Jl. Ronggolawe No. 3 Tuban", "Islam", "B", "SMKS TARUNA JAYA PRAWIRA TUBAN", "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA", "Bambang Kusdiyanto, ST.M.Pd", "JL. DR. WAHIDIN SUDIROHUSODO TUBAN", "XII RPL 1"),
            StudentCardData("2410052", "Kevin Pratama", "Tuban, 20 November 2008", "Jl. Manunggal No. 56 Tuban", "Islam", "O", "SMKS TARUNA JAYA PRAWIRA TUBAN", "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA", "Bambang Kusdiyanto, ST.M.Pd", "JL. DR. WAHIDIN SUDIROHUSODO TUBAN", "XII RPL 1")
        )

        for (st in initialStudents) {
            studentCardsMap[st.nisn] = st
            val uName = when (st.nisn) {
                "2410031" -> "rina.xipa2"
                "2410032" -> "bagas.xipa2"
                "2410033" -> "citra.xipa2"
                else -> null
            }
            if (!usersMap.containsKey(st.nisn)) {
                val studentAcc = UserAccount(
                    id = st.nisn,
                    nisn = st.nisn,
                    username = uName,
                    name = st.name,
                    role = UserRole.STUDENT,
                    passwordHash = "12345678",
                    cardData = st,
                    isBiometricEnrolled = true,
                    isFaceEnrolled = true
                )
                usersMap[st.nisn] = studentAcc
                if (uName != null) {
                    usersMap[uName] = studentAcc
                }
            }
        }
        if (!usersMap.containsKey("rina.xipa2") && usersMap.containsKey("2410031")) {
            usersMap["rina.xipa2"] = usersMap["2410031"]!!
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
        if (!usersMap.containsKey("1982051001")) {
            val teacherAcc = UserAccount(
                id = "1982051001",
                username = "guru_dedi",
                name = "Pak Dedi Kurniawan",
                role = UserRole.TEACHER,
                passwordHash = "guru123"
            )
            usersMap["1982051001"] = teacherAcc
            usersMap["guru_dedi"] = teacherAcc
        }
        if (!usersMap.containsKey("guru_dedi") && usersMap.containsKey("1982051001")) {
            usersMap["guru_dedi"] = usersMap["1982051001"]!!
        }

        // Admin account configured in database
        if (!usersMap.containsKey("admin")) {
            usersMap["admin"] = UserAccount(
                id = "admin",
                username = "admin",
                name = "Administrator HadirKu",
                role = UserRole.ADMIN,
                passwordHash = "admin123"
            )
        }

        val hasSeeded = prefs.getBoolean("has_seeded_initial_data_v5", false)
        if (hasSeeded) {
            saveAll()
            return
        }

        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateMon = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val dateTue = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val dateWed = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val dateThu = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val dateFri = dateFormat.format(cal.time)

        // Real schedules accurately divided by Day (Senin, Selasa, Rabu, Kamis, Jumat) and specific date
        schedulesList.clear()
        val realSchedules = listOf(
            // --- SENIN ---
            AttendanceSchedule("sch_sen_01", "XI IPA 2", "Bahasa Indonesia", "1978010101", "Pak Joko Santoso", 1, 1, "07:00 - 07:45 WIB", "Senin", "R. 12", false, dateMon),
            AttendanceSchedule("sch_sen_02", "XI IPA 2", "Matematika", "1982051001", "Pak Dedi Kurniawan", 2, 3, "07:45 - 09:15 WIB", "Senin", "R. 12", true, dateMon),
            AttendanceSchedule("sch_sen_03", "XI IPA 2", "Fisika", "1987091201", "Bu Ratna Ningrum", 4, 5, "09:30 - 11:00 WIB", "Senin", "Lab Fisika", false, dateMon),
            AttendanceSchedule("sch_sen_04", "XI IPA 2", "Bahasa Inggris", "1991040501", "Pak Arif Hidayat", 6, 7, "11:00 - 12:30 WIB", "Senin", "R. 12", false, dateMon),
            AttendanceSchedule("sch_sen_05", "XI IPA 2", "Kimia", "1989021401", "Bu Ratna Dewi", 8, 9, "13:00 - 14:30 WIB", "Senin", "Lab Kimia", false, dateMon),

            // --- SELASA ---
            AttendanceSchedule("sch_sel_01", "XI IPA 2", "Biologi", "1984031101", "Bu Endang Sulistyo", 1, 2, "07:15 - 08:45 WIB", "Selasa", "Lab Biologi", true, dateTue),
            AttendanceSchedule("sch_sel_02", "XI IPA 2", "Sejarah Indonesia", "1986112001", "Pak Agus Wibowo", 3, 4, "08:45 - 10:15 WIB", "Selasa", "R. 12", false, dateTue),
            AttendanceSchedule("sch_sel_03", "XI TKJ 1", "Administrasi Server Jaringan", "1985031201", "Pak Budi Santoso, S.Kom", 1, 2, "07:00 - 08:30 WIB", "Selasa", "Lab Komputer 1", true, dateTue),

            // --- RABU ---
            AttendanceSchedule("sch_rab_01", "XI IPA 2", "Seni Budaya", "1992011501", "Bu Maya Astuti", 1, 2, "07:15 - 08:45 WIB", "Rabu", "R. Seni", false, dateWed),
            AttendanceSchedule("sch_rab_02", "XI IPA 2", "Pendidikan Jasmani", "1980061201", "Pak Hendro Kusumo", 3, 4, "09:00 - 10:30 WIB", "Rabu", "Lapangan Utama", false, dateWed),
            AttendanceSchedule("sch_rab_03", "XII RPL 1", "Pemodelan Perangkat Lunak", "1985031201", "Pak Budi Santoso, S.Kom", 1, 3, "07:15 - 09:30 WIB", "Rabu", "Lab RPL", true, dateWed),

            // --- KAMIS ---
            AttendanceSchedule("sch_kam_01", "XI IPA 2", "Pendidikan Agama Islam", "1975082101", "Pak H. Ahmad Fauzi", 1, 2, "07:15 - 08:45 WIB", "Kamis", "R. 12", true, dateThu),
            AttendanceSchedule("sch_kam_02", "XI IPA 2", "Pendidikan Pancasila & Kewarganegaraan", "1988100501", "Bu Sri Wahyuni", 3, 4, "08:45 - 10:15 WIB", "Kamis", "R. 12", false, dateThu),

            // --- JUMAT ---
            AttendanceSchedule("sch_jum_01", "XI IPA 2", "Matematika Peminatan", "1982051001", "Pak Dedi Kurniawan", 1, 2, "07:00 - 08:30 WIB", "Jumat", "R. 12", true, dateFri),
            AttendanceSchedule("sch_jum_02", "XI TKJ 1", "Teknologi Layanan Jaringan", "1990082402", "Bu Siti Rahmawati, M.Pd", 1, 2, "07:00 - 08:30 WIB", "Jumat", "Lab Komputer 2", true, dateFri)
        )
        schedulesList.addAll(realSchedules)

        // Seed initial attendance records for real student participation
        if (attendanceList.isEmpty()) {
            val now = System.currentTimeMillis()
            attendanceList.add(AttendanceRecord(UUID.randomUUID().toString(), "sch_sen_02", "2410031", "Rina Aulia Putri", "XI IPA 2", now - 3600000, AttendanceStatus.PRESENT, true, true, 8, "3-Lapis", "Hadir Tepat Waktu"))
            attendanceList.add(AttendanceRecord(UUID.randomUUID().toString(), "sch_sen_02", "2410032", "Bagas Prasetyo", "XI IPA 2", now - 3400000, AttendanceStatus.PRESENT, true, true, 12, "3-Lapis", "Hadir Tepat Waktu"))
            attendanceList.add(AttendanceRecord(UUID.randomUUID().toString(), "sch_sen_02", "2410033", "Citra Maharani", "XI IPA 2", now - 3200000, AttendanceStatus.PERMITTED, false, false, 0, "Manual", "Surat Keterangan Izin"))
            attendanceList.add(AttendanceRecord(UUID.randomUUID().toString(), "sch_sen_02", "2410034", "Dimas Anggara", "XI IPA 2", now - 3000000, AttendanceStatus.PRESENT, true, true, 15, "3-Lapis", "Hadir Tepat Waktu"))
            attendanceList.add(AttendanceRecord(UUID.randomUUID().toString(), "sch_sen_02", "2410035", "Eka Fitriani", "XI IPA 2", now - 2800000, AttendanceStatus.PRESENT, true, true, 9, "3-Lapis", "Hadir Tepat Waktu"))
            attendanceList.add(AttendanceRecord(UUID.randomUUID().toString(), "sch_sel_03", "2410041", "Gilang Ramadhan", "XI TKJ 1", now - 86400000, AttendanceStatus.PRESENT, true, true, 11, "3-Lapis", "Hadir Tepat Waktu"))
            attendanceList.add(AttendanceRecord(UUID.randomUUID().toString(), "sch_rab_03", "2410051", "Hana Lestari", "XII RPL 1", now - 172800000, AttendanceStatus.PRESENT, true, true, 10, "3-Lapis", "Hadir Tepat Waktu"))
        }

        prefs.edit().putBoolean("has_seeded_initial_data_v5", true).apply()
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
        return getAllStudents().filter {
            it.cardData?.className.equals(clean, ignoreCase = true)
        }
    }

    fun getAllAttendanceRecords(): List<AttendanceRecord> = attendanceList.toList()

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

    fun addOrUpdateSession(
        className: String,
        sessionDate: String,
        isOpen: Boolean,
        teacherId: String,
        teacherName: String
    ): AttendanceSchedule {
        val existing = schedulesList.firstOrNull { it.className.equals(className, ignoreCase = true) }
        val schedule = if (existing != null) {
            val updated = existing.copy(sessionDate = sessionDate, isOpenForAttendance = isOpen)
            val idx = schedulesList.indexOf(existing)
            schedulesList[idx] = updated
            updated
        } else {
            val newSched = AttendanceSchedule(
                id = "sch_${System.currentTimeMillis()}",
                className = className,
                subject = "Presensi Harian",
                teacherId = teacherId,
                teacherName = teacherName,
                periodStart = 1,
                periodEnd = 2,
                timeSlot = "07:15 - 09:15 WIB",
                dayName = "Senin",
                room = "R. Kelas",
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
        return permissionsList.filter { it.studentNisn == nisn }
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
                for (st in remoteStudents) {
                    studentCardsMap[st.nisn] = st
                    if (!usersMap.containsKey(st.nisn)) {
                        usersMap[st.nisn] = UserAccount(
                            id = st.nisn,
                            nisn = st.nisn,
                            name = st.name,
                            role = UserRole.STUDENT,
                            passwordHash = "12345678",
                            cardData = st
                        )
                    } else {
                        val existing = usersMap[st.nisn]!!
                        val updated = existing.copy(name = st.name, cardData = st)
                        usersMap[st.nisn] = updated
                        updated.username?.let { usersMap[it] = updated }
                    }
                }
            }

            // 2. Fetch live schedules directly from Supabase
            val remoteSchedules = SupabaseClient.getSchedules()
            if (remoteSchedules.isNotEmpty()) {
                schedulesList.clear()
                schedulesList.addAll(remoteSchedules)
            }

            // 3. Fetch live classes directly from Supabase
            val remoteClasses = SupabaseClient.getClasses()
            if (remoteClasses.isNotEmpty()) {
                classesList.clear()
                classesList.addAll(remoteClasses)
            }

            // 4. Fetch live attendance directly from Supabase
            val remoteAttendance = SupabaseClient.getAllAttendanceRecords()
            if (remoteAttendance.isNotEmpty()) {
                attendanceList.clear()
                attendanceList.addAll(remoteAttendance)
            }

            // 5. Fetch live permissions directly from Supabase
            val remotePermissions = SupabaseClient.getAllPermissions()
            if (remotePermissions.isNotEmpty()) {
                permissionsList.clear()
                permissionsList.addAll(remotePermissions)
            }

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
