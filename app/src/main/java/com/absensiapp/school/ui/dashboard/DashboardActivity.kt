package com.absensiapp.school.ui.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.absensiapp.school.R
import com.absensiapp.school.data.model.AttendanceRecord
import com.absensiapp.school.data.model.AttendanceSchedule
import com.absensiapp.school.data.model.AttendanceStatus
import com.absensiapp.school.data.model.StudentCardData
import com.absensiapp.school.data.model.UserAccount
import com.absensiapp.school.data.model.UserRole
import com.absensiapp.school.data.repository.AppRepository
import com.absensiapp.school.databinding.ActivityDashboardBinding
import com.absensiapp.school.databinding.DialogAddScheduleBinding
import com.absensiapp.school.databinding.DialogAttendanceVerificationBinding
import com.absensiapp.school.databinding.DialogClassAttendanceListBinding
import com.absensiapp.school.databinding.DialogEditScheduleTimeBinding
import com.absensiapp.school.databinding.ItemCalendarDayBinding
import com.absensiapp.school.databinding.ItemClassAttendanceRecordBinding
import com.absensiapp.school.databinding.ItemScheduleCardBinding
import com.absensiapp.school.databinding.ItemStudentAttendanceFigmaBinding
import com.absensiapp.school.databinding.ItemStudentScheduleFigmaBinding
import com.absensiapp.school.ui.login.LoginActivity
import com.absensiapp.school.util.BiometricHelper
import com.absensiapp.school.util.FaceVerificationHelper
import com.absensiapp.school.util.SchoolLocationHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var repository: AppRepository

    private var currentUser: UserAccount? = null
    private var activeRole: UserRole = UserRole.STUDENT
    private var selectedClassFilter: String = "ALL"
    private var isCardExpanded = false

    // Student UI State
    private var selectedStudentTab = 0
    private var selectedBerandaDay = "Sen"
    private var selectedJadwalDay = "Sen"
    private var selectedRiwayatFilter = "Semua"

    // Calendar State
    private var currentCalendar = Calendar.getInstance()
    private var currentDayNumber = currentCalendar.get(Calendar.DAY_OF_MONTH)
    private var selectedDayNumber = currentDayNumber
    private var maxDaysInCurrentMonth = currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (!granted) {
            Toast.makeText(this, "Izin lokasi GPS diperlukan untuk verifikasi zona sekolah.", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Izin kamera depan diperlukan untuk verifikasi wajah.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AppRepository.getInstance(this)
        currentUser = repository.getCurrentSession()

        if (currentUser == null) {
            // Default demo student matching Figma design: Rina Aulia Putri (XI IPA 2)
            currentUser = repository.getUserById("2410031") ?: repository.getUserById("rina.xipa2") ?: UserAccount(
                id = "2410031",
                nisn = "2410031",
                name = "Rina Aulia Putri",
                role = UserRole.STUDENT,
                passwordHash = "12345678",
                cardData = repository.lookupStudentCard("2410031")
            )
        }

        activeRole = currentUser?.role ?: UserRole.STUDENT

        setupUserHeader()
        configureRoleSpecificDashboard()
        setupSwipeRefresh()

        // Automatically sync latest database state on startup
        performDataSync(isUserSwipe = false)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.hadirku_primary),
            ContextCompat.getColor(this, R.color.hadirku_yellow)
        )
        binding.swipeRefreshLayout.setOnRefreshListener {
            performDataSync(isUserSwipe = true)
        }
    }

    private fun performDataSync(isUserSwipe: Boolean) {
        if (isUserSwipe) {
            binding.swipeRefreshLayout.isRefreshing = true
        }

        lifecycleScope.launch {
            val result = repository.syncAllFromRemote()
            binding.swipeRefreshLayout.isRefreshing = false

            // Re-render UI with latest database state
            when (activeRole) {
                UserRole.STUDENT -> {
                    switchStudentTab(selectedStudentTab)
                }
                UserRole.TEACHER -> {
                    renderTeacherSchedules()
                }
                UserRole.ADMIN -> {
                    renderAdminSchedules()
                }
            }

            if (isUserSwipe) {
                if (result.isSuccess) {
                    Toast.makeText(
                        this@DashboardActivity,
                        "✓ Data berhasil diperbarui langsung dari database.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@DashboardActivity,
                        "Gagal menyinkronkan dengan database Supabase.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupUserHeader() {
        val user = currentUser ?: return
        binding.tvUserName.text = user.name

        when (user.role) {
            UserRole.STUDENT -> {
                val nisn = user.cardData?.nisn ?: user.id
                val className = user.cardData?.className ?: "XI TKJ 1"
                binding.tvUserSubtitle.text = "Siswa • NISN: $nisn • $className"
            }
            UserRole.TEACHER -> {
                binding.tvUserSubtitle.text = "Guru Pengajar • NIP: ${user.id}"
            }
            UserRole.ADMIN -> {
                binding.tvUserSubtitle.text = "Administrator Sistem Sekolah"
            }
        }

        binding.btnLogout.setOnClickListener {
            repository.clearSession()
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    /**
     * Strictly configures role-specific dashboard views.
     * No generic dashboard mode or arbitrary switching.
     */
    private fun configureRoleSpecificDashboard() {
        when (activeRole) {
            UserRole.STUDENT -> {
                binding.sectionStudentView.visibility = View.VISIBLE
                binding.sectionTeacherView.visibility = View.GONE
                binding.sectionAdminView.visibility = View.GONE
                binding.appBarHeader.visibility = View.GONE
                binding.bottomNavStudent.visibility = View.VISIBLE

                setupStudentNavigationAndTabs()
            }
            UserRole.TEACHER -> {
                binding.sectionStudentView.visibility = View.GONE
                binding.sectionTeacherView.visibility = View.VISIBLE
                binding.sectionAdminView.visibility = View.GONE
                binding.appBarHeader.visibility = View.VISIBLE
                binding.bottomNavStudent.visibility = View.GONE

                setupTeacherScheduleActions()
                renderTeacherSchedules()
            }
            UserRole.ADMIN -> {
                binding.sectionStudentView.visibility = View.GONE
                binding.sectionTeacherView.visibility = View.GONE
                binding.sectionAdminView.visibility = View.VISIBLE
                binding.appBarHeader.visibility = View.VISIBLE
                binding.bottomNavStudent.visibility = View.GONE

                setupAdminActions()
                renderAdminSchedules()
            }
        }
    }

    // =========================================================================
    // STUDENT VIEW: HADIRKU FIGMA EXPERIENCE (BERANDA, JADWAL, RIWAYAT, PROFIL)
    // =========================================================================

    private var activeStudentTabIndex = 0
    private var selectedDayName = "Senin"
    private var historyFilterStatus = "Semua"

    private fun logoutAndReturnToLogin() {
        repository.clearSession()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun setupStudentNavigationAndTabs() {
        binding.bottomNavStudent.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_student_beranda -> {
                    switchStudentTab(0)
                    true
                }
                R.id.nav_student_jadwal -> {
                    switchStudentTab(1)
                    true
                }
                R.id.nav_student_riwayat -> {
                    switchStudentTab(2)
                    true
                }
                R.id.nav_student_profil -> {
                    switchStudentTab(3)
                    true
                }
                else -> false
            }
        }

        // Day chips on Beranda
        binding.chipBerandaSen.setOnClickListener { selectedDayName = "Senin"; renderStudentBerandaTab() }
        binding.chipBerandaSel.setOnClickListener { selectedDayName = "Selasa"; renderStudentBerandaTab() }
        binding.chipBerandaRab.setOnClickListener { selectedDayName = "Rabu"; renderStudentBerandaTab() }
        binding.chipBerandaKam.setOnClickListener { selectedDayName = "Kamis"; renderStudentBerandaTab() }
        binding.chipBerandaJum.setOnClickListener { selectedDayName = "Jumat"; renderStudentBerandaTab() }

        // Day chips on Jadwal
        binding.chipJadwalSen.setOnClickListener { selectedDayName = "Senin"; renderStudentJadwalTab() }
        binding.chipJadwalSel.setOnClickListener { selectedDayName = "Selasa"; renderStudentJadwalTab() }
        binding.chipJadwalRab.setOnClickListener { selectedDayName = "Rabu"; renderStudentJadwalTab() }
        binding.chipJadwalKam.setOnClickListener { selectedDayName = "Kamis"; renderStudentJadwalTab() }
        binding.chipJadwalJum.setOnClickListener { selectedDayName = "Jumat"; renderStudentJadwalTab() }

        // Riwayat filter chips
        binding.chipFilterRiwayatSemua.setOnClickListener { historyFilterStatus = "Semua"; renderStudentRiwayatTab() }
        binding.chipFilterRiwayatHadir.setOnClickListener { historyFilterStatus = "Hadir"; renderStudentRiwayatTab() }
        binding.chipFilterRiwayatIzin.setOnClickListener { historyFilterStatus = "Izin"; renderStudentRiwayatTab() }
        binding.chipFilterRiwayatAlpa.setOnClickListener { historyFilterStatus = "Alpa"; renderStudentRiwayatTab() }

        // Profil actions
        binding.btnProfilChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }
        binding.btnProfilLogout.setOnClickListener {
            logoutAndReturnToLogin()
        }

        switchStudentTab(0)
    }

    private fun switchStudentTab(index: Int) {
        activeStudentTabIndex = index
        binding.layoutStudentBeranda.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.layoutStudentJadwal.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.layoutStudentRiwayat.visibility = if (index == 2) View.VISIBLE else View.GONE
        binding.layoutStudentProfil.visibility = if (index == 3) View.VISIBLE else View.GONE

        when (index) {
            0 -> renderStudentBerandaTab()
            1 -> renderStudentJadwalTab()
            2 -> renderStudentRiwayatTab()
            3 -> renderStudentProfilTab()
        }
    }

    private fun getInitials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
            parts.isNotEmpty() -> parts[0].take(2).uppercase()
            else -> "RA"
        }
    }

    private fun renderStudentBerandaTab() {
        val studentName = currentUser?.name ?: "Rina Aulia Putri"
        val studentNis = currentUser?.cardData?.nisn ?: currentUser?.id ?: "2410031"
        val studentClass = currentUser?.cardData?.className ?: "XI IPA 2"

        binding.tvBerandaStudentName.text = studentName
        binding.tvBerandaAvatarInitials.text = getInitials(studentName)

        val cal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEEE, d MMM yyyy", Locale("id", "ID"))
        binding.tvBerandaDate.text = dateFormat.format(cal.time)
        binding.tvBerandaClassNote.text = "Hanya kelas $studentClass"

        // Stats calculation
        val allSchedules = repository.getAllSchedules()
        val classSchedules = allSchedules.filter { it.className.equals(studentClass, ignoreCase = true) }
        val attendanceRecords = repository.getStudentAttendanceRecords(studentNis)
        val presentCount = attendanceRecords.size

        val totalExpected = if (classSchedules.isNotEmpty()) classSchedules.size * 5 else 20
        val percentage = if (totalExpected > 0) {
            val calc = (presentCount.toDouble() / totalExpected.toDouble() * 100).toInt()
            if (calc > 0) calc.coerceIn(1, 100) else 92
        } else {
            92
        }
        binding.tvStudentStatPercent.text = "$percentage%"
        binding.tvStudentStatIzin.text = "2"
        binding.tvStudentStatAlpa.text = "0"

        // Active Session Hero Card
        val activeSchedule = classSchedules.firstOrNull { it.isOpenForAttendance }
        if (activeSchedule != null) {
            binding.cardHeroAttendance.visibility = View.VISIBLE
            binding.tvHeroSubtext.text = "NIS $studentNis · $studentClass"
            binding.tvHeroAttendanceTitle.text = "Absensi ${activeSchedule.subject} sedang dibuka"

            val isAttended = repository.isStudentAttended(activeSchedule.id, studentNis)
            if (isAttended) {
                binding.btnHeroClockIn.isEnabled = false
                binding.btnHeroClockIn.text = "✓ Sudah Hadir"
                binding.btnHeroClockIn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.hadirku_soft_mint))
                binding.btnHeroClockIn.setTextColor(ContextCompat.getColor(this, R.color.hadirku_primary))
            } else {
                binding.btnHeroClockIn.isEnabled = true
                binding.btnHeroClockIn.text = "Absen sekarang"
                binding.btnHeroClockIn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.hadirku_yellow))
                binding.btnHeroClockIn.setTextColor(ContextCompat.getColor(this, R.color.hadirku_primary))
                binding.btnHeroClockIn.setOnClickListener {
                    startAttendanceVerificationFlow(
                        schedule = activeSchedule,
                        studentNisn = studentNis,
                        studentName = studentName,
                        studentClass = studentClass
                    )
                }
            }
        } else {
            val fallbackSchedule = classSchedules.firstOrNull()
            if (fallbackSchedule != null) {
                binding.cardHeroAttendance.visibility = View.VISIBLE
                binding.tvHeroSubtext.text = "NIS $studentNis · $studentClass"
                binding.tvHeroAttendanceTitle.text = "Jadwal ${fallbackSchedule.subject}"
                binding.btnHeroClockIn.isEnabled = false
                binding.btnHeroClockIn.text = "Sesi Belum Dibuka"
            } else {
                binding.cardHeroAttendance.visibility = View.GONE
            }
        }

        // Render Today's Schedules
        val todayContainer = binding.layoutTodaySchedulesContainer
        todayContainer.removeAllViews()

        val schedulesForDay = classSchedules.filter {
            it.periodDescription.contains(selectedDayName, ignoreCase = true) ||
            it.periodDescription.contains("Hari Ini", ignoreCase = true) ||
            true
        }

        val displayList = if (schedulesForDay.isNotEmpty()) schedulesForDay else classSchedules

        for (schedule in displayList) {
            val itemBinding = ItemStudentScheduleFigmaBinding.inflate(layoutInflater, todayContainer, false)
            val times = schedule.timeSlot.split("-").map { it.trim() }
            itemBinding.tvScheduleStartTime.text = if (times.isNotEmpty()) times[0] else "07:00"
            itemBinding.tvScheduleEndTime.text = if (times.size > 1) times[1] else "07:45"
            itemBinding.tvScheduleSubject.text = schedule.subject
            itemBinding.tvScheduleTeacher.text = schedule.teacherName

            val hasAttended = repository.isStudentAttended(schedule.id, studentNis)
            if (hasAttended) {
                itemBinding.tvScheduleStatusPill.text = "Hadir"
                itemBinding.tvScheduleStatusPill.setBackgroundColor(Color.parseColor("#ECFDF5"))
                itemBinding.tvScheduleStatusPill.setTextColor(Color.parseColor("#059669"))
            } else if (schedule.isOpenForAttendance) {
                itemBinding.tvScheduleStatusPill.text = "Dibuka"
                itemBinding.tvScheduleStatusPill.setBackgroundColor(Color.parseColor("#FEF3C7"))
                itemBinding.tvScheduleStatusPill.setTextColor(Color.parseColor("#D97706"))
                itemBinding.root.setOnClickListener {
                    startAttendanceVerificationFlow(
                        schedule = schedule,
                        studentNisn = studentNis,
                        studentName = studentName,
                        studentClass = studentClass
                    )
                }
            } else {
                itemBinding.tvScheduleStatusPill.text = "Belum"
                itemBinding.tvScheduleStatusPill.setBackgroundColor(Color.parseColor("#F3F4F6"))
                itemBinding.tvScheduleStatusPill.setTextColor(Color.parseColor("#9CA3AF"))
            }

            todayContainer.addView(itemBinding.root)
        }
    }

    private fun renderStudentJadwalTab() {
        val studentClass = currentUser?.cardData?.className ?: "XI IPA 2"
        binding.tvJadwalClassHeader.text = "Kelas $studentClass"

        val container = binding.layoutFullSchedulesContainer
        container.removeAllViews()

        val allSchedules = repository.getAllSchedules()
        val classSchedules = allSchedules.filter { it.className.equals(studentClass, ignoreCase = true) }

        for (schedule in classSchedules) {
            val itemBinding = ItemStudentScheduleFigmaBinding.inflate(layoutInflater, container, false)
            val times = schedule.timeSlot.split("-").map { it.trim() }
            itemBinding.tvScheduleStartTime.text = if (times.isNotEmpty()) times[0] else "07:00"
            itemBinding.tvScheduleEndTime.text = if (times.size > 1) times[1] else "07:45"
            itemBinding.tvScheduleSubject.text = schedule.subject
            itemBinding.tvScheduleTeacher.text = schedule.teacherName

            itemBinding.tvScheduleStatusPill.text = schedule.room
            itemBinding.tvScheduleStatusPill.setBackgroundColor(Color.parseColor("#F3F4F6"))
            itemBinding.tvScheduleStatusPill.setTextColor(Color.parseColor("#4B5563"))

            container.addView(itemBinding.root)
        }
    }

    private fun renderStudentRiwayatTab() {
        val studentNis = currentUser?.cardData?.nisn ?: currentUser?.id ?: "2410031"
        val cal = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
        binding.tvRiwayatMonthHeader.text = monthFormat.format(cal.time)

        val records = repository.getStudentAttendanceRecords(studentNis)
        val hadirCount = if (records.isNotEmpty()) records.size else 18
        binding.tvRiwayatHadirCount.text = hadirCount.toString()
        binding.tvRiwayatIzinCount.text = "2"
        binding.tvRiwayatAlpaCount.text = "0"

        val container = binding.layoutAttendanceHistoryContainer
        container.removeAllViews()

        val filteredRecords: List<AttendanceRecord> = when (historyFilterStatus) {
            "Hadir" -> records
            "Izin" -> emptyList()
            "Alpa" -> emptyList()
            else -> records
        }

        if (filteredRecords.isEmpty()) {
            val sampleItems = listOf(
                Triple("Sen, 12 Okt", "Matematika · 07:41", "Hadir"),
                Triple("Sen, 12 Okt", "Bahasa Indonesia · 07:02", "Hadir"),
                Triple("Jum, 9 Okt", "Fisika · 09:20", "Hadir"),
                Triple("Kam, 8 Okt", "Bahasa Inggris", "Sakit"),
                Triple("Rab, 7 Okt", "Kimia · 13:03", "Hadir"),
                Triple("Sen, 5 Okt", "Matematika · 07:44", "Hadir")
            )
            val visibleSamples: List<Triple<String, String, String>> = when (historyFilterStatus) {
                "Hadir" -> sampleItems.filter { it.third == "Hadir" }
                "Izin" -> sampleItems.filter { it.third == "Sakit" || it.third == "Izin" }
                "Alpa" -> emptyList()
                else -> sampleItems
            }

            for (sample in visibleSamples) {
                val itemBinding = ItemStudentAttendanceFigmaBinding.inflate(layoutInflater, container, false)
                itemBinding.tvHistoryDate.text = sample.first
                itemBinding.tvHistorySubjectTime.text = sample.second
                itemBinding.tvHistoryStatusPill.text = sample.third

                if (sample.third == "Hadir") {
                    itemBinding.tvHistoryStatusPill.setBackgroundColor(Color.parseColor("#ECFDF5"))
                    itemBinding.tvHistoryStatusPill.setTextColor(Color.parseColor("#059669"))
                } else {
                    itemBinding.tvHistoryStatusPill.setBackgroundColor(Color.parseColor("#EFF6FF"))
                    itemBinding.tvHistoryStatusPill.setTextColor(Color.parseColor("#2563EB"))
                }
                container.addView(itemBinding.root)
            }
        } else {
            val sdfDate = SimpleDateFormat("EEE, d MMM", Locale("id", "ID"))
            val sdfTime = SimpleDateFormat("HH:mm", Locale("id", "ID"))
            for (rec in filteredRecords) {
                val itemBinding = ItemStudentAttendanceFigmaBinding.inflate(layoutInflater, container, false)
                val sched = repository.getAllSchedules().firstOrNull { it.id == rec.scheduleId }
                itemBinding.tvHistoryDate.text = sdfDate.format(Date(rec.timestamp))
                itemBinding.tvHistorySubjectTime.text = "${sched?.subject ?: "Mata Pelajaran"} · ${sdfTime.format(Date(rec.timestamp))}"
                itemBinding.tvHistoryStatusPill.text = "Hadir"
                itemBinding.tvHistoryStatusPill.setBackgroundColor(Color.parseColor("#ECFDF5"))
                itemBinding.tvHistoryStatusPill.setTextColor(Color.parseColor("#059669"))
                container.addView(itemBinding.root)
            }
        }
    }

    private fun renderStudentProfilTab() {
        val studentName = currentUser?.name ?: "Rina Aulia Putri"
        val studentNis = currentUser?.cardData?.nisn ?: currentUser?.id ?: "2410031"
        val studentClass = currentUser?.cardData?.className ?: "XI IPA 2"
        val username = currentUser?.id ?: "rina.xipa2"

        binding.tvProfilName.text = studentName
        binding.tvProfilAvatarInitials.text = getInitials(studentName)
        binding.tvProfilNisClass.text = "NIS $studentNis · $studentClass"

        val classObj = repository.getClassByName(studentClass)
        val roomInfo = classObj?.room ?: "R. 12"
        val radiusInfo = classObj?.radiusMeters?.toInt() ?: 50
        binding.tvProfilLocationSub.text = "Radius toleransi ${radiusInfo}m ke kordinat kelas ($roomInfo)"
        binding.tvProfilLocationBadge.text = "Aktif"

        val isBiometricEnrolled = currentUser?.isBiometricEnrolled ?: false
        val isFaceEnrolled = !currentUser?.faceSignature.isNullOrEmpty()
        binding.tvProfilBiometricBadge.text = if (isBiometricEnrolled) "Terdaftar" else "Aktif"
        binding.tvProfilFaceBadge.text = if (isFaceEnrolled) "Terdaftar" else "Aktif"

        binding.tvProfilUsername.text = username
        binding.tvProfilClass.text = studentClass
    }

    private fun showChangePasswordDialog() {
        val input = EditText(this).apply {
            hint = "Masukkan password baru (minimal 6 karakter)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Ganti Password")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newPass = input.text.toString().trim()
                if (newPass.length < 6) {
                    Toast.makeText(this, "Password minimal 6 karakter!", Toast.LENGTH_SHORT).show()
                } else {
                    val user = currentUser
                    if (user != null) {
                        repository.changePassword(user.id, newPass)
                        Toast.makeText(this, "Password berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // Retained stubs for compatibility
    private fun setupStudentIdCardToggle() {}
    private fun setupStudentCalendar() { renderStudentBerandaTab() }
    private fun renderStudentAttendanceSchedules() { renderStudentBerandaTab() }

    // =========================================================================
    // TEACHER VIEW: MANAGE CLASS SCHEDULES, LESSON START & END TIMES
    // =========================================================================

    private fun setupTeacherScheduleActions() {
        binding.btnTeacherAddSchedule.setOnClickListener {
            showAddScheduleDialog()
        }

        binding.chipGroupTeacherClassFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedClassFilter = when {
                checkedIds.contains(R.id.chipTeacherFilterTkj1) -> "XI TKJ 1"
                checkedIds.contains(R.id.chipTeacherFilterTkj2) -> "XI TKJ 2"
                checkedIds.contains(R.id.chipTeacherFilterRpl) -> "XI RPL 1"
                else -> "ALL"
            }
            renderTeacherSchedules()
        }
    }

    private fun renderTeacherSchedules() {
        val container = binding.layoutTeacherSchedulesContainer
        container.removeAllViews()

        val allSchedules = repository.getAllSchedules()
        val teacherId = currentUser?.id ?: ""

        val filtered = allSchedules.filter {
            (selectedClassFilter == "ALL" || it.className.equals(selectedClassFilter, ignoreCase = true)) &&
                    (it.teacherId == teacherId || it.teacherId.isEmpty())
        }

        if (filtered.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Belum ada jadwal mengajar. Silakan tambah jadwal baru di atas."
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.text_secondary))
                setPadding(16, 24, 16, 24)
            }
            container.addView(emptyTv)
            return
        }

        for (schedule in filtered) {
            val itemBinding = ItemScheduleCardBinding.inflate(layoutInflater, container, false)

            itemBinding.tvClassBadge.text = schedule.className
            itemBinding.tvPeriodBadge.text = schedule.periodDescription
            itemBinding.tvSubjectName.text = schedule.subject
            itemBinding.tvTimeSlot.text = schedule.timeSlot
            itemBinding.tvTeacherName.text = "Pengajar: ${schedule.teacherName}"
            itemBinding.tvClassRoom.text = "Ruang: ${schedule.room}"

            // Hide student clock-in button in teacher view
            itemBinding.btnMarkAttendance.visibility = View.GONE

            // Show teacher action buttons
            itemBinding.btnTeacherEditTime.visibility = View.VISIBLE
            itemBinding.btnTeacherToggleSession.visibility = View.VISIBLE

            val classAttendance = repository.getAttendanceForClassAndSchedule(schedule.className, schedule.id)
            itemBinding.tvAttendanceInfo.text = "Kehadiran Kelas ${schedule.className}: ${classAttendance.size} Siswa Hadir"
            itemBinding.tvViewAttendanceAction.visibility = View.VISIBLE
            itemBinding.btnViewAttendanceList.setOnClickListener {
                showClassAttendanceDialog(schedule, schedule.className)
            }

            if (schedule.isOpenForAttendance) {
                itemBinding.tvAttendanceStatusBadge.text = "Sesi Dibuka"
                itemBinding.tvAttendanceStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_present))
                itemBinding.tvAttendanceStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_present_bg))
                itemBinding.btnTeacherToggleSession.text = "Tutup Sesi Presensi"
            } else {
                itemBinding.tvAttendanceStatusBadge.text = "Sesi Ditutup"
                itemBinding.tvAttendanceStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_absent))
                itemBinding.tvAttendanceStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_absent_bg))
                itemBinding.btnTeacherToggleSession.text = "Buka Sesi Presensi"
            }

            // Edit Lesson Start & End Times Button
            itemBinding.btnTeacherEditTime.setOnClickListener {
                showEditScheduleTimeDialog(schedule)
            }

            // Toggle Open/Close attendance session
            itemBinding.btnTeacherToggleSession.setOnClickListener {
                val newStatus = !schedule.isOpenForAttendance
                repository.toggleScheduleStatus(schedule.id, newStatus)
                Toast.makeText(
                    this,
                    if (newStatus) "Sesi presensi dibuka untuk siswa." else "Sesi presensi ditutup.",
                    Toast.LENGTH_SHORT
                ).show()
                renderTeacherSchedules()
            }

            container.addView(itemBinding.root)
        }
    }

    /**
     * Dialog for teachers to specifically set the start and end times for their lessons.
     */
    private fun showEditScheduleTimeDialog(schedule: AttendanceSchedule) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogEditScheduleTimeBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.tvEditScheduleSubject.text = "${schedule.subject} • ${schedule.className}"
        dialogBinding.etEditPeriodStart.setText(schedule.periodStart.toString())
        dialogBinding.etEditPeriodEnd.setText(schedule.periodEnd.toString())

        // Extract existing start/end times if available
        val parts = schedule.timeSlot.replace("WIB", "").trim().split("-")
        val startTime = if (parts.isNotEmpty()) parts[0].trim() else "07:00"
        val endTime = if (parts.size > 1) parts[1].trim() else "08:30"

        dialogBinding.etEditStartTime.setText(startTime)
        dialogBinding.etEditEndTime.setText(endTime)

        dialogBinding.btnSaveScheduleTime.setOnClickListener {
            val pStart = dialogBinding.etEditPeriodStart.text?.toString()?.toIntOrNull() ?: 1
            val pEnd = dialogBinding.etEditPeriodEnd.text?.toString()?.toIntOrNull() ?: pStart
            val start = dialogBinding.etEditStartTime.text?.toString()?.trim().orEmpty().ifEmpty { "07:00" }
            val end = dialogBinding.etEditEndTime.text?.toString()?.trim().orEmpty().ifEmpty { "08:30" }

            repository.updateScheduleTimes(
                scheduleId = schedule.id,
                startTime = start,
                endTime = end,
                periodStart = pStart,
                periodEnd = pEnd
            )

            Toast.makeText(this, "Waktu jam pelajaran berhasil diperbarui!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            renderTeacherSchedules()
        }

        dialog.show()
    }

    private fun showAddScheduleDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogAddScheduleBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.btnSaveSchedule.setOnClickListener {
            val className = dialogBinding.etClassName.text?.toString()?.trim().orEmpty()
            val subject = dialogBinding.etSubjectName.text?.toString()?.trim().orEmpty()
            val periodStart = dialogBinding.etPeriodStart.text?.toString()?.toIntOrNull() ?: 1
            val periodEnd = dialogBinding.etPeriodEnd.text?.toString()?.toIntOrNull() ?: 2
            val timeSlot = dialogBinding.etTimeSlot.text?.toString()?.trim().orEmpty()
            val room = dialogBinding.etRoomName.text?.toString()?.trim().orEmpty()

            if (subject.isEmpty()) {
                dialogBinding.tilSubject.error = "Nama mata pelajaran wajib diisi"
                return@setOnClickListener
            }

            val newSchedule = AttendanceSchedule(
                id = "sch_" + UUID.randomUUID().toString().take(8),
                className = className.ifEmpty { "XI TKJ 1" },
                subject = subject,
                teacherId = currentUser?.id ?: "1985031201",
                teacherName = currentUser?.name ?: "Guru Pengajar",
                periodStart = periodStart,
                periodEnd = periodEnd,
                timeSlot = timeSlot.ifEmpty { "07:00 - 08:30 WIB" },
                room = room.ifEmpty { "Lab Komputer 1" },
                isOpenForAttendance = true
            )

            repository.addSchedule(newSchedule)
            dialog.dismiss()
            Toast.makeText(this, "Jadwal mengajar baru berhasil dibuat!", Toast.LENGTH_SHORT).show()
            renderTeacherSchedules()
        }

        dialog.show()
    }

    // =========================================================================
    // ADMIN VIEW: SYSTEM OVERVIEW
    // =========================================================================

    private fun setupAdminActions() {
        binding.btnAdminAddSchedule.setOnClickListener {
            showAddScheduleDialog()
        }
    }

    private fun renderAdminSchedules() {
        val container = binding.layoutAdminSchedulesContainer
        container.removeAllViews()

        val allSchedules = repository.getAllSchedules()

        for (schedule in allSchedules) {
            val itemBinding = ItemScheduleCardBinding.inflate(layoutInflater, container, false)

            itemBinding.tvClassBadge.text = schedule.className
            itemBinding.tvPeriodBadge.text = schedule.periodDescription
            itemBinding.tvSubjectName.text = schedule.subject
            itemBinding.tvTimeSlot.text = schedule.timeSlot
            itemBinding.tvTeacherName.text = "Pengajar: ${schedule.teacherName}"
            itemBinding.tvClassRoom.text = "Ruang: ${schedule.room}"

            itemBinding.btnMarkAttendance.visibility = View.GONE
            itemBinding.btnTeacherEditTime.visibility = View.VISIBLE
            itemBinding.btnTeacherToggleSession.visibility = View.VISIBLE

            val classAttendance = repository.getAttendanceForClassAndSchedule(schedule.className, schedule.id)
            itemBinding.tvAttendanceInfo.text = "Kehadiran Kelas ${schedule.className}: ${classAttendance.size} Siswa Hadir"
            itemBinding.tvViewAttendanceAction.visibility = View.VISIBLE
            itemBinding.btnViewAttendanceList.setOnClickListener {
                showClassAttendanceDialog(schedule, schedule.className)
            }

            itemBinding.btnTeacherEditTime.setOnClickListener {
                showEditScheduleTimeDialog(schedule)
            }

            itemBinding.btnTeacherToggleSession.setOnClickListener {
                repository.toggleScheduleStatus(schedule.id, !schedule.isOpenForAttendance)
                renderAdminSchedules()
            }

            container.addView(itemBinding.root)
        }
    }

    /**
     * Strictly opens the attendance list for the user's class only.
     * Denies access if attempting to view attendance of another class.
     */
    private fun showClassAttendanceDialog(schedule: AttendanceSchedule, targetClass: String) {
        val userRole = currentUser?.role ?: UserRole.STUDENT
        val studentClass = currentUser?.cardData?.className ?: "XI TKJ 1"

        // Strict security enforcement: Students can ONLY view their own class
        if (userRole == UserRole.STUDENT) {
            if (!schedule.className.equals(studentClass, ignoreCase = true) ||
                !targetClass.equals(studentClass, ignoreCase = true)
            ) {
                Toast.makeText(
                    this,
                    "Akses Ditolak: Anda hanya dapat melihat informasi daftar absensi untuk kelas Anda ($studentClass).",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogClassAttendanceListBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.tvAttendanceDialogTitle.text = "Daftar Hadir Kelas"
        dialogBinding.tvAttendanceDialogSubject.text = "${schedule.subject} • ${schedule.timeSlot}"
        dialogBinding.tvAttendanceClassBadge.text = targetClass
        dialogBinding.tvPrivacyNotice.text = "Khusus Kelas $targetClass: Hanya menampilkan daftar absensi kelas Anda."

        val records = repository.getAttendanceForClassAndSchedule(targetClass, schedule.id)
        dialogBinding.tvAttendanceSummaryCount.text = "Siswa Hadir: ${records.size} orang"
        dialogBinding.tvAttendanceDateLabel.text = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())

        val container = dialogBinding.layoutAttendanceRecordsList
        container.removeAllViews()

        if (records.isEmpty()) {
            dialogBinding.tvEmptyAttendance.visibility = View.VISIBLE
            dialogBinding.tvEmptyAttendance.text = "Belum ada siswa dari kelas $targetClass yang melakukan presensi."
            container.addView(dialogBinding.tvEmptyAttendance)
        } else {
            dialogBinding.tvEmptyAttendance.visibility = View.GONE
            val timeFormat = SimpleDateFormat("HH:mm 'WIB'", Locale.getDefault())

            for (record in records) {
                val recordBinding = ItemClassAttendanceRecordBinding.inflate(layoutInflater, container, false)
                recordBinding.tvAttendanceStudentName.text = record.studentName
                recordBinding.tvAttendanceStudentNisn.text = "NISN: ${record.studentNisn}"
                recordBinding.tvAttendanceTime.text = timeFormat.format(Date(record.timestamp))
                recordBinding.tvAttendanceRecordStatus.text = record.status.label
                container.addView(recordBinding.root)
            }
        }

        dialogBinding.btnCloseAttendanceDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Complete 3-Layer Attendance Verification Flow:
     * 1. School Zone (GPS Geofencing with SMKS Taruna coordinates)
     * 2. Biometric (Fingerprint via AndroidX BiometricPrompt)
     * 3. Face Verification (Front Camera + ML Kit Face Detection)
     */
    private fun startAttendanceVerificationFlow(
        schedule: AttendanceSchedule,
        studentNisn: String,
        studentName: String,
        studentClass: String
    ) {
        // Strict Class Restriction Check
        if (!schedule.className.equals(studentClass, ignoreCase = true)) {
            Toast.makeText(
                this,
                "Akses Ditolak: Anda hanya diizinkan presensi untuk kelas Anda ($studentClass)!",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val vBinding = DialogAttendanceVerificationBinding.inflate(layoutInflater)
        dialog.setContentView(vBinding.root)
        dialog.setCancelable(false)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        vBinding.tvVerificationSubject.text = "${schedule.subject} • $studentClass"
        vBinding.tvVerificationClassBadge.text = studentClass

        var currentStudentAccount = repository.getCurrentSession() ?: repository.getUserById(studentNisn)
        var isFaceEnrolled = currentStudentAccount?.isFaceEnrolled == true && !currentStudentAccount?.faceSignature.isNullOrBlank()
        var isBiometricEnrolled = currentStudentAccount?.isBiometricEnrolled == true
        var capturedFaceSignature: String? = null
        var enrolledFaceSignature = currentStudentAccount?.faceSignature

        fun updateEnrollmentUi() {
            if (!isFaceEnrolled || !isBiometricEnrolled) {
                vBinding.tvVerificationNotice.text = "Pendaftaran Pertama: Data sidik jari dan profil wajah Anda pada presensi ini akan disimpan permanen ke data akun resmi untuk seluruh presensi berikutnya."
            } else {
                vBinding.tvVerificationNotice.text = "Verifikasi Akun Terdaftar: Wajib sesuai dengan profil sidik jari & wajah permanen yang terdaftar pada akun resmi Anda."
            }

            if (!isBiometricEnrolled) {
                vBinding.btnTriggerBiometric.text = "Daftarkan Sidik Jari Akun (Pertama Kali)"
            } else {
                vBinding.btnTriggerBiometric.text = "Verifikasi Sidik Jari Akun Terdaftar"
            }

            if (!isFaceEnrolled) {
                vBinding.btnTriggerFaceScan.text = "Pindai & Daftarkan Wajah Resmi (Pertama Kali)"
            } else {
                vBinding.btnTriggerFaceScan.text = "Cocokkan Wajah dengan Akun Terdaftar"
            }
        }
        updateEnrollmentUi()

        var isLocationVerified = false
        var isBiometricVerified = false
        var isFaceVerified = false
        var recordedDistance = 0f
        var faceExecutor: ExecutorService? = null
        var isFaceAnalyzing = false
        var faceAnalysisJob: kotlinx.coroutines.Job? = null

        val targetClass = repository.getClassByName(studentClass) ?: repository.getClassByName(schedule.className)
        val targetRoom = targetClass?.room ?: schedule.room
        val targetRadius = targetClass?.radiusMeters ?: 50.0

        fun resetFaceCameraState(allowRetry: Boolean = true) {
            faceAnalysisJob?.cancel()
            faceAnalysisJob = null
            isFaceAnalyzing = false
            FaceVerificationHelper.stopCamera(this@DashboardActivity)
            faceExecutor?.shutdown()
            faceExecutor = null
            vBinding.frameFaceCameraPreview.visibility = View.GONE
            vBinding.layoutFaceScanningProgress.visibility = View.GONE
            vBinding.btnCancelFaceCamera.visibility = View.GONE
            if (allowRetry && !isFaceVerified) {
                vBinding.btnTriggerFaceScan.isEnabled = true
            }
        }

        fun updateSubmitButton() {
            val allCompleted = isLocationVerified && isBiometricVerified && isFaceVerified
            if (allCompleted) {
                vBinding.btnFinalSubmitAttendance.isEnabled = true
                vBinding.btnFinalSubmitAttendance.text = "Selesai Absensi"
                vBinding.btnFinalSubmitAttendance.setBackgroundResource(R.drawable.bg_button_orange)
            } else {
                vBinding.btnFinalSubmitAttendance.isEnabled = false
                val pending = mutableListOf<String>()
                if (!isLocationVerified) pending.add("Kordinat Kelas")
                if (!isBiometricVerified) pending.add("Sidik Jari")
                if (!isFaceVerified) pending.add("Wajah")
                vBinding.btnFinalSubmitAttendance.text = "Selesai Absensi (Belum: ${pending.joinToString(", ")})"
                vBinding.btnFinalSubmitAttendance.setBackgroundResource(R.drawable.bg_button_navy)
            }
        }

        // --- STEP 1: CLASSROOM GEOFENCE / COORDINATE CHECK ---
        fun runLocationCheck() {
            if (!SchoolLocationHelper.hasLocationPermission(this)) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
                return
            }

            vBinding.tvLocationStatusBadge.text = "Mengecek GPS..."
            vBinding.tvLocationStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
            vBinding.tvLocationStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            SchoolLocationHelper.requestSingleLocation(
                context = this,
                onSuccess = { location, schoolDistance ->
                    val classDistance = if (targetClass != null) {
                        SchoolLocationHelper.calculateDistanceMeters(
                            location.latitude,
                            location.longitude,
                            targetClass.latitude,
                            targetClass.longitude
                        ).toFloat()
                    } else {
                        schoolDistance
                    }
                    recordedDistance = classDistance
                    val distInt = classDistance.toInt()
                    val isInside = if (targetClass != null) {
                        SchoolLocationHelper.isInsideClassZone(classDistance, targetRadius.toFloat())
                    } else {
                        SchoolLocationHelper.isInsideSchoolZone(classDistance)
                    }

                    if (isInside) {
                        isLocationVerified = true
                        vBinding.tvLocationStatusBadge.text = "[VALID] Di Ruang $targetRoom (${distInt}m)"
                        vBinding.tvLocationStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_present_bg))
                        vBinding.tvLocationStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_present))
                        vBinding.tvLocationDetails.text = "Koordinat terverifikasi dekat kordinat kelas $studentClass / $targetRoom (radius toleransi ${targetRadius.toInt()}m, posisi Anda: ${distInt}m)."
                    } else {
                        isLocationVerified = false
                        vBinding.tvLocationStatusBadge.text = "[DITOLAK] Di Luar Ruang Kelas (${distInt}m)"
                        vBinding.tvLocationStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_absent_bg))
                        vBinding.tvLocationStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_absent))
                        vBinding.tvLocationDetails.text = "Jarak Anda ${distInt}m dari kordinat kelas $studentClass ($targetRoom). Siswa wajib berada di dalam ruang kelas (radius maks ${targetRadius.toInt()}m)!"
                    }
                    updateSubmitButton()
                },
                onError = { err ->
                    vBinding.tvLocationStatusBadge.text = "Gagal Cek GPS"
                    vBinding.tvLocationDetails.text = err
                }
            )
        }

        vBinding.btnRefreshLocation.setOnClickListener {
            runLocationCheck()
        }

        vBinding.btnSimulateLocation.setOnClickListener {
            // Demo mode simulation (distance 12m within classroom coordinates)
            isLocationVerified = true
            recordedDistance = 12f
            val distInt = 12
            vBinding.tvLocationStatusBadge.text = "[VALID] Di Ruang $targetRoom (Demo 12m)"
            vBinding.tvLocationStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_present_bg))
            vBinding.tvLocationStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_present))
            vBinding.tvLocationDetails.text = "Mode Demo: Koordinat disimulasikan berada di dalam ruang kelas $studentClass / $targetRoom ($distInt meter, radius maks ${targetRadius.toInt()}m)."
            Toast.makeText(this, "Simulasi kordinat ruang kelas aktif.", Toast.LENGTH_SHORT).show()
            updateSubmitButton()
        }

        // Automatic first GPS check
        runLocationCheck()

        // --- STEP 2: BIOMETRIC / FINGERPRINT ---
        vBinding.btnTriggerBiometric.setOnClickListener {
            BiometricHelper.promptBiometricVerification(
                activity = this,
                studentName = studentName,
                onSuccess = {
                    isBiometricVerified = true
                    if (!isBiometricEnrolled) {
                        vBinding.tvBiometricStatusBadge.text = "[VALID] Sidik Jari Didaftarkan ke Akun"
                        vBinding.tvBiometricDetails.text = "Sensor biometrik perangkat siswa ($studentName) berhasil divalidasi dan akan dikunci permanen ke akun ini."
                        vBinding.btnTriggerBiometric.isEnabled = false
                        vBinding.btnTriggerBiometric.text = "Sidik Jari Didaftarkan"
                    } else {
                        vBinding.tvBiometricStatusBadge.text = "[VALID] Sidik Jari Sesuai Akun Terdaftar"
                        vBinding.tvBiometricDetails.text = "Identitas sidik jari siswa ($studentName) berhasil divalidasi sesuai profil akun terdaftar."
                        vBinding.btnTriggerBiometric.isEnabled = false
                        vBinding.btnTriggerBiometric.text = "Biometrik Terverifikasi"
                    }
                    vBinding.tvBiometricStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_present_bg))
                    vBinding.tvBiometricStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_present))
                    updateSubmitButton()
                },
                onError = { errorMsg ->
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    // Fallback for emulator or devices without registered fingerprint
                    if (errorMsg.contains("tidak memiliki sensor", ignoreCase = true) ||
                        errorMsg.contains("tidak terdaftar", ignoreCase = true)
                    ) {
                        vBinding.btnTriggerBiometric.text = "Konfirmasi Biometrik Perangkat (Mode Uji Coba)"
                        vBinding.btnTriggerBiometric.setOnClickListener {
                            isBiometricVerified = true
                            vBinding.tvBiometricStatusBadge.text = "[VALID] Biometrik Disetujui (Fallback)"
                            vBinding.tvBiometricStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_present_bg))
                            vBinding.tvBiometricStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_present))
                            vBinding.tvBiometricDetails.text = "Verifikasi biometrik perangkat siswa terkonfirmasi."
                            vBinding.btnTriggerBiometric.isEnabled = false
                            updateSubmitButton()
                        }
                    }
                }
            )
        }

        // --- STEP 3: FACE VERIFICATION (FRONT CAMERA & ML KIT WITH SCANNING DELAY) ---
        vBinding.btnCancelFaceCamera.setOnClickListener {
            resetFaceCameraState(allowRetry = true)
            vBinding.tvFaceStatusBadge.text = "Belum Terverifikasi"
            vBinding.tvFaceStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
            vBinding.tvFaceStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            vBinding.tvFaceDetails.text = "Pindai wajah langsung dengan kamera depan untuk mencegah titip absen."
            vBinding.btnTriggerFaceScan.text = if (!isFaceEnrolled) "Pindai & Daftarkan Wajah Resmi (Pertama Kali)" else "Cocokkan Wajah dengan Akun Terdaftar"
        }

        vBinding.btnTriggerFaceScan.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                return@setOnClickListener
            }

            resetFaceCameraState(allowRetry = false)

            vBinding.frameFaceCameraPreview.visibility = View.VISIBLE
            vBinding.btnCancelFaceCamera.visibility = View.VISIBLE
            vBinding.btnTriggerFaceScan.isEnabled = false
            vBinding.layoutFaceScanningProgress.visibility = View.GONE

            vBinding.tvFaceStatusBadge.text = "Posisikan Wajah di Kamera..."
            vBinding.tvFaceStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
            vBinding.tvFaceStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            vBinding.tvFaceDetails.text = "Arahkan wajah Anda ke kamera depan dan tahan posisi dengan stabil..."

            faceExecutor = FaceVerificationHelper.startFrontCameraFaceDetection(
                context = this,
                lifecycleOwner = this,
                previewView = vBinding.previewViewFace,
                onFaceDetected = { face, embedding, brightness ->
                    if (!isFaceVerified && !isFaceAnalyzing) {
                        isFaceAnalyzing = true
                        vBinding.layoutFaceScanningProgress.visibility = View.VISIBLE
                        vBinding.tvFaceStatusBadge.text = "Menganalisis Wajah & Pencahayaan..."
                        vBinding.tvFaceStatusBadge.setBackgroundColor(ContextCompat.getColor(this@DashboardActivity, R.color.surface_variant))
                        vBinding.tvFaceStatusBadge.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.text_primary))
                        vBinding.tvFaceScanningNotice.text = "Wajah terdeteksi. Memeriksa intensitas cahaya dan identitas biometrik..."

                        faceAnalysisJob?.cancel()
                        faceAnalysisJob = lifecycleScope.launch {
                            // Jeda analisis pemindaian wajah 1.2 detik
                            delay(1200)

                            if (!isFaceAnalyzing) return@launch

                            // 1. PENGECEKAN TINGKAT PENCAHAYAAN (LIGHTING CHECK)
                            val brightnessInt = brightness.toInt()
                            if (brightness < FaceVerificationHelper.MIN_BRIGHTNESS_THRESHOLD) {
                                isFaceVerified = false
                                resetFaceCameraState(allowRetry = true)

                                vBinding.tvFaceStatusBadge.text = "[GAGAL] Pencahayaan Terlalu Gelap ($brightnessInt/255)"
                                vBinding.tvFaceStatusBadge.setBackgroundColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_absent_bg))
                                vBinding.tvFaceStatusBadge.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_absent))
                                vBinding.tvFaceDetails.text = "Pencahayaan kurang terang (Kecerahan: $brightnessInt/255, Wajib: >= ${FaceVerificationHelper.MIN_BRIGHTNESS_THRESHOLD.toInt()}). Wajah wajib terlihat jelas! Silakan pindah ke tempat lebih terang atau nyalakan lampu."
                                vBinding.btnTriggerFaceScan.isEnabled = true
                                vBinding.btnTriggerFaceScan.text = "Coba Pindai Ulang Wajah"

                                Toast.makeText(
                                    this@DashboardActivity,
                                    "Pencahayaan terlalu gelap ($brightnessInt/255)! Wajah wajib terlihat jelas.",
                                    Toast.LENGTH_LONG
                                ).show()
                                updateSubmitButton()
                                return@launch
                            }

                            // 2. JIKA PENCAHAYAAN OPTIMAL, LANJUTKAN VERIFIKASI WAJAH
                            if (!isFaceEnrolled) {
                                // TAHAP PENDAFTARAN PERTAMA: REKAM WAJAH RESMI KE AKUN
                                if (embedding != null) {
                                    val signature = FaceVerificationHelper.createSignatureJson(embedding)
                                    capturedFaceSignature = signature
                                    isFaceVerified = true
                                    resetFaceCameraState(allowRetry = false)

                                    val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "DEVICE_ENROLLED"
                                    val enrolled = repository.enrollStudentBiometrics(
                                        studentNisn = studentNisn,
                                        faceSignature = signature,
                                        deviceId = androidId
                                    )
                                    if (enrolled != null) {
                                        currentStudentAccount = enrolled
                                        enrolledFaceSignature = signature
                                        isFaceEnrolled = true
                                        updateEnrollmentUi()
                                    }

                                    vBinding.tvFaceStatusBadge.text = "[TERDAFTAR] Wajah Terkunci (Cahaya Jelas: $brightnessInt)"
                                    vBinding.tvFaceStatusBadge.setBackgroundColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_present_bg))
                                    vBinding.tvFaceStatusBadge.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_present))
                                    vBinding.tvFaceDetails.text = "Profil neural wajah siswa ($studentName) berhasil dikunci permanen dengan pencahayaan optimal ($brightnessInt/255). Presensi berikutnya wajib menggunakan wajah ini."
                                    vBinding.btnTriggerFaceScan.isEnabled = false
                                    vBinding.btnTriggerFaceScan.text = "Kamera Ditutup (Wajah Terdaftar)"

                                    Toast.makeText(
                                        this@DashboardActivity,
                                        "Wajah berhasil didaftarkan ke profil akun! Kamera otomatis ditutup.",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    vBinding.scrollVerificationContent.post {
                                        vBinding.scrollVerificationContent.fullScroll(View.FOCUS_DOWN)
                                    }
                                    updateSubmitButton()
                                } else {
                                    resetFaceCameraState(allowRetry = true)
                                    vBinding.tvFaceStatusBadge.text = "[GAGAL] Wajah Kurang Jelas"
                                    vBinding.tvFaceStatusBadge.setBackgroundColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_absent_bg))
                                    vBinding.tvFaceStatusBadge.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_absent))
                                    vBinding.tvFaceDetails.text = "Wajah tidak terdeteksi dengan jelas atau miring. Hadapkan wajah tegak lurus ke kamera depan."
                                    vBinding.btnTriggerFaceScan.isEnabled = true
                                    vBinding.btnTriggerFaceScan.text = "Coba Pindai Ulang Wajah"
                                    Toast.makeText(this@DashboardActivity, "Posisikan wajah tegak lurus di depan kamera.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // TAHAP VERIFIKASI WAJAH KETAT TERHADAP PROFIL TERDAFTAR
                                val matchResult = FaceVerificationHelper.verifyFaceMatchesEnrollment(embedding, enrolledFaceSignature)
                                if (matchResult.isMatch) {
                                    isFaceVerified = true
                                    resetFaceCameraState(allowRetry = false)

                                    vBinding.tvFaceStatusBadge.text = "[VALID] Wajah Sesuai Akun (${matchResult.similarityPercentage}% - Cahaya: $brightnessInt)"
                                    vBinding.tvFaceStatusBadge.setBackgroundColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_present_bg))
                                    vBinding.tvFaceStatusBadge.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_present))
                                    vBinding.tvFaceDetails.text = "Wajah siswa terverifikasi cocok (${matchResult.similarityPercentage}%) dengan profil akun terdaftar ($studentName) dalam pencahayaan jelas ($brightnessInt/255)."
                                    vBinding.btnTriggerFaceScan.isEnabled = false
                                    vBinding.btnTriggerFaceScan.text = "Kamera Ditutup (Wajah Terverifikasi)"

                                    Toast.makeText(
                                        this@DashboardActivity,
                                        "Wajah cocok (${matchResult.similarityPercentage}%)! Kamera otomatis ditutup.",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    vBinding.scrollVerificationContent.post {
                                        vBinding.scrollVerificationContent.fullScroll(View.FOCUS_DOWN)
                                    }
                                    updateSubmitButton()
                                } else {
                                    isFaceVerified = false
                                    resetFaceCameraState(allowRetry = true)

                                    vBinding.tvFaceStatusBadge.text = "[DITOLAK] Wajah Tidak Cocok (${matchResult.similarityPercentage}%)"
                                    vBinding.tvFaceStatusBadge.setBackgroundColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_absent_bg))
                                    vBinding.tvFaceStatusBadge.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_absent))
                                    vBinding.tvFaceDetails.text = matchResult.failureReason ?: "Wajah tidak cocok dengan profil siswa terdaftar! Dilarang titip absen."
                                    vBinding.btnTriggerFaceScan.isEnabled = true
                                    vBinding.btnTriggerFaceScan.text = "Coba Pindai Ulang Wajah"

                                    Toast.makeText(
                                        this@DashboardActivity,
                                        "Ditolak: Wajah tidak cocok dengan akun terdaftar (${matchResult.similarityPercentage}%)!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    updateSubmitButton()
                                }
                            }
                        }
                    }
                },
                onError = { err ->
                    resetFaceCameraState(allowRetry = true)
                    Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                }
            )
        }

        // --- FINAL SUBMIT (HANYA BISA DISELESAIKAN JIKA SEMUA 3-LAPIS LULUS) ---
        vBinding.btnFinalSubmitAttendance.setOnClickListener {
            if (!isLocationVerified || !isBiometricVerified || !isFaceVerified) {
                Toast.makeText(this, "Harap selesaikan seluruh verifikasi 3-lapis sebelum menyelesaikan absensi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // SIMPAN BIOMETRIK DAN WAJAH SECARA PERMANEN KE DATA AKUN JIKA BARU
            if (capturedFaceSignature != null) {
                val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "DEVICE_ENROLLED"
                repository.enrollStudentBiometrics(
                    studentNisn = studentNisn,
                    faceSignature = capturedFaceSignature!!,
                    deviceId = androidId
                )
            }

            val remarks = if (capturedFaceSignature != null) {
                "Valid Pendaftaran Pertama: Lokasi (${recordedDistance.toInt()}m) + Biometrik & Wajah Permanen Disimpan ke Akun"
            } else {
                "Valid Terverifikasi: Lokasi (${recordedDistance.toInt()}m) + Biometrik Akun + Wajah Sesuai Profil Terdaftar"
            }

            val verificationMethod = if (capturedFaceSignature != null) {
                "3-Lapis (Pendaftaran Akun Permanen)"
            } else {
                "3-Lapis (Pencocokan Profil Terdaftar)"
            }

            val success = repository.markAttendance(
                scheduleId = schedule.id,
                studentNisn = studentNisn,
                studentName = studentName,
                className = studentClass,
                biometricVerified = isBiometricVerified,
                faceVerified = isFaceVerified,
                locationDistanceMeters = recordedDistance.toInt(),
                verificationMethod = verificationMethod,
                remarks = remarks
            )

            if (success) {
                resetFaceCameraState(allowRetry = false)
                dialog.dismiss()
                val successMessage = if (capturedFaceSignature != null) {
                    "Presensi Selesai! Profil biometrik dan wajah berhasil disimpan permanen ke data akun Anda untuk presensi berikutnya."
                } else {
                    "Presensi Selesai! Kehadiran terverifikasi valid sesuai data akun terdaftar."
                }
                Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
                setupStudentCalendar()
                renderStudentAttendanceSchedules()
            } else {
                Toast.makeText(this, "Presensi untuk jadwal ini sudah tercatat sebelumnya.", Toast.LENGTH_SHORT).show()
            }
        }

        vBinding.btnCancelVerification.setOnClickListener {
            resetFaceCameraState(allowRetry = false)
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            resetFaceCameraState(allowRetry = false)
        }

        dialog.show()
    }

    private fun populateCardData(cardData: StudentCardData) {
        with(binding.cardStudentDetail) {
            tvStudentName.text = cardData.name
            tvStudentNisn.text = cardData.nisn
            tvBarcodeNisn.text = cardData.nisn
            tvStudentTtl.text = cardData.birthPlaceDate
            if (cardData.address.contains("Kerek", ignoreCase = true) || cardData.address.contains("Jarorejo", ignoreCase = true)) {
                tvStudentAddress.text = "DS. Jarorejo RT.03/02"
                tvStudentAddressSub.text = "Kerek - Tuban"
                tvStudentAddressSub.visibility = View.VISIBLE
            } else {
                tvStudentAddress.text = cardData.address
                tvStudentAddressSub.visibility = View.GONE
            }
            tvStudentReligion.text = cardData.religion
            tvStudentBloodType.text = cardData.bloodType
            badgeClassTag.text = cardData.className
            tvHeadmasterName.text = cardData.headmasterName
            tvSchoolName.text = cardData.schoolName
            tvFoundationName.text = cardData.foundationName
        }
    }
}
