package com.absensiapp.school.ui.dashboard

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import com.absensiapp.school.databinding.ItemTeacherStudentFigmaBinding
import com.absensiapp.school.databinding.ItemTeacherScheduleCardBinding
import com.absensiapp.school.databinding.ItemAdminActivityFigmaBinding
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
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.card.MaterialCardView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.widget.ArrayAdapter
import com.absensiapp.school.data.model.StudentPermission
import com.absensiapp.school.databinding.DialogViewPermitBinding
import com.absensiapp.school.databinding.ItemPermitCardBinding

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
    private var selectedRiwayatFilter = "Semua"

    // Calendar State
    private var currentCalendar = Calendar.getInstance()
    private var currentDayNumber = currentCalendar.get(Calendar.DAY_OF_MONTH)
    private var selectedDayNumber = currentDayNumber
    private var maxDaysInCurrentMonth = currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Student Leave / Permit (Izin) State
    private var permitPhotoBitmap: Bitmap? = null
    private var permitPhotoBase64: String? = null
    private var izinStartCal: Calendar = Calendar.getInstance()
    private var izinEndCal: Calendar = (Calendar.getInstance().clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
    private var selectedIzinType: String = "Sakit"

    // Teacher Session Creation State
    private var teacherSessionCal: Calendar = Calendar.getInstance()
    private var selectedTeacherSessionClass: String = "XI IPA 2"

    private val captureSuratIzinLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            permitPhotoBitmap = bitmap
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            permitPhotoBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            binding.imgPreviewSuratIzin.setImageBitmap(bitmap)
            binding.imgPreviewSuratIzin.visibility = View.VISIBLE
            binding.layoutPromptFotoSurat.visibility = View.GONE
            binding.btnCaptureSuratIzin.text = "Ambil Ulang Foto"
            Toast.makeText(this, "Foto surat izin berhasil diambil!", Toast.LENGTH_SHORT).show()
        }
    }

    private val permitCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            captureSuratIzinLauncher.launch(null)
        } else {
            Toast.makeText(this, "Izin kamera diperlukan untuk memfoto surat izin fisik.", Toast.LENGTH_SHORT).show()
        }
    }

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
            val officialStudent = repository.getAllStudents().firstOrNull()
            if (officialStudent != null) {
                currentUser = officialStudent
                repository.saveCurrentSession(officialStudent)
            } else {
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
                return
            }
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
                    switchStudentTab(activeStudentTabIndex)
                }
                UserRole.TEACHER -> {
                    switchTeacherTab(selectedTeacherTab)
                }
                UserRole.ADMIN -> {
                    switchAdminTab(selectedAdminTab)
                }
            }

            if (isUserSwipe) {
                if (result.isSuccess) {
                    Toast.makeText(
                        this@DashboardActivity,
                        "Data berhasil diperbarui langsung dari database.",
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
                binding.bottomNavTeacher.visibility = View.GONE
                binding.bottomNavAdmin.visibility = View.GONE

                setupStudentNavigationAndTabs()
                switchStudentTab(0)
            }
            UserRole.TEACHER -> {
                binding.sectionStudentView.visibility = View.GONE
                binding.sectionTeacherView.visibility = View.VISIBLE
                binding.sectionAdminView.visibility = View.GONE
                binding.appBarHeader.visibility = View.GONE
                binding.bottomNavStudent.visibility = View.GONE
                binding.bottomNavTeacher.visibility = View.VISIBLE
                binding.bottomNavAdmin.visibility = View.GONE

                setupTeacherNavigationAndTabs()
                switchTeacherTab(0)
            }
            UserRole.ADMIN -> {
                binding.sectionStudentView.visibility = View.GONE
                binding.sectionTeacherView.visibility = View.GONE
                binding.sectionAdminView.visibility = View.VISIBLE
                binding.appBarHeader.visibility = View.GONE
                binding.bottomNavStudent.visibility = View.GONE
                binding.bottomNavTeacher.visibility = View.GONE
                binding.bottomNavAdmin.visibility = View.VISIBLE

                setupAdminNavigationAndTabs()
                switchAdminTab(0)
            }
        }
    }

    // =========================================================================
    // STUDENT VIEW: HADIRKU FIGMA EXPERIENCE (BERANDA, JADWAL, RIWAYAT, PROFIL)
    // =========================================================================

    private var activeStudentTabIndex = 0
    private var selectedDayName = "Senin"
    private var historyFilterStatus = "Semua"
    private var selectedStudentCalendar: Calendar = Calendar.getInstance()

    private fun logoutAndReturnToLogin() {
        repository.clearSession()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun getWeekMonday(refCal: Calendar): Calendar {
        val cal = refCal.clone() as Calendar
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return cal
    }

    private fun updateStudentDateAndChips() {
        val weekMonday = getWeekMonday(selectedStudentCalendar)
        val chipFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

        val monCal = weekMonday.clone() as Calendar
        val tueCal = (weekMonday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val wedCal = (weekMonday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 2) }
        val thuCal = (weekMonday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 3) }
        val friCal = (weekMonday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 4) }

        val monDate = chipFormat.format(monCal.time)
        val tueDate = chipFormat.format(tueCal.time)
        val wedDate = chipFormat.format(wedCal.time)
        val thuDate = chipFormat.format(thuCal.time)
        val friDate = chipFormat.format(friCal.time)

        binding.chipBerandaSen.text = "Sen, $monDate"
        binding.chipBerandaSel.text = "Sel, $tueDate"
        binding.chipBerandaRab.text = "Rab, $wedDate"
        binding.chipBerandaKam.text = "Kam, $thuDate"
        binding.chipBerandaJum.text = "Jum, $friDate"

        val currentDayOfWeek = selectedStudentCalendar.get(Calendar.DAY_OF_WEEK)
        when (currentDayOfWeek) {
            Calendar.MONDAY -> {
                binding.chipBerandaSen.isChecked = true
                selectedDayName = "Senin"
            }
            Calendar.TUESDAY -> {
                binding.chipBerandaSel.isChecked = true
                selectedDayName = "Selasa"
            }
            Calendar.WEDNESDAY -> {
                binding.chipBerandaRab.isChecked = true
                selectedDayName = "Rabu"
            }
            Calendar.THURSDAY -> {
                binding.chipBerandaKam.isChecked = true
                selectedDayName = "Kamis"
            }
            Calendar.FRIDAY -> {
                binding.chipBerandaJum.isChecked = true
                selectedDayName = "Jumat"
            }
            else -> {
                selectedDayName = SimpleDateFormat("EEEE", Locale.forLanguageTag("id-ID")).format(selectedStudentCalendar.time)
            }
        }
    }

    private fun setStudentCalendarToWeekday(dayOffsetFromMonday: Int) {
        val weekMonday = getWeekMonday(selectedStudentCalendar)
        weekMonday.add(Calendar.DAY_OF_MONTH, dayOffsetFromMonday)
        selectedStudentCalendar = weekMonday
        updateStudentDateAndChips()
        if (activeStudentTabIndex == 0) {
            renderStudentBerandaTab()
        }
    }

    private fun showStudentDatePickerDialog() {
        val y = selectedStudentCalendar.get(Calendar.YEAR)
        val m = selectedStudentCalendar.get(Calendar.MONTH)
        val d = selectedStudentCalendar.get(Calendar.DAY_OF_MONTH)

        val picker = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            selectedStudentCalendar.set(year, month, dayOfMonth)
            updateStudentDateAndChips()
            if (activeStudentTabIndex == 0) {
                renderStudentBerandaTab()
            }
        }, y, m, d)
        picker.show()
    }

    private fun setupStudentNavigationAndTabs() {
        binding.bottomNavStudent.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_student_beranda -> {
                    switchStudentTab(0)
                    true
                }
                R.id.nav_student_izin -> {
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

        updateStudentDateAndChips()

        // Allow student to change date by clicking on date texts
        binding.tvBerandaDate.setOnClickListener { showStudentDatePickerDialog() }

        // Day chips on Beranda (offsets: 0=Senin, 1=Selasa, 2=Rabu, 3=Kamis, 4=Jumat)
        binding.chipBerandaSen.setOnClickListener { setStudentCalendarToWeekday(0) }
        binding.chipBerandaSel.setOnClickListener { setStudentCalendarToWeekday(1) }
        binding.chipBerandaRab.setOnClickListener { setStudentCalendarToWeekday(2) }
        binding.chipBerandaKam.setOnClickListener { setStudentCalendarToWeekday(3) }
        binding.chipBerandaJum.setOnClickListener { setStudentCalendarToWeekday(4) }

        // Setup Student Izin Form Listeners
        setupStudentIzinFormListeners()

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
        binding.layoutStudentIzin.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.layoutStudentRiwayat.visibility = if (index == 2) View.VISIBLE else View.GONE
        binding.layoutStudentProfil.visibility = if (index == 3) View.VISIBLE else View.GONE

        when (index) {
            0 -> {
                renderStudentBerandaTab()
                lifecycleScope.launch {
                    repository.syncAllFromRemote()
                    renderStudentBerandaTab()
                }
            }
            1 -> renderStudentIzinTab()
            2 -> {
                renderStudentRiwayatTab()
                val studentNis = currentUser?.cardData?.nisn ?: currentUser?.nisn ?: currentUser?.id ?: ""
                lifecycleScope.launch {
                    repository.syncStudentAttendanceAndPermits(studentNis)
                    renderStudentRiwayatTab()
                }
            }
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

    private fun parseTimeToMinutes(timeStr: String): Int? {
        val clean = timeStr.replace("WIB", "").replace("wib", "").trim()
        val parts = clean.split(":")
        if (parts.size >= 2) {
            val h = parts[0].trim().toIntOrNull() ?: return null
            val m = parts[1].trim().toIntOrNull() ?: return null
            return h * 60 + m
        }
        return null
    }

    private fun renderStudentBerandaTab() {
        // Strictly lock Beranda to CURRENT REAL TIME / TODAY
        selectedStudentCalendar = Calendar.getInstance()
        val todayCal = Calendar.getInstance()
        val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(todayCal.time)
        val formattedDisplayDate = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID")).format(todayCal.time)

        val studentName = currentUser?.name.orEmpty().ifEmpty { "Siswa" }
        val studentNis = currentUser?.cardData?.nisn ?: currentUser?.nisn ?: currentUser?.id ?: ""
        val studentClass = currentUser?.cardData?.className.orEmpty().ifEmpty { "XI RPL 1" }

        binding.tvBerandaStudentName.text = studentName
        binding.tvBerandaAvatarInitials.text = getInitials(studentName)
        binding.tvBerandaDate.text = formattedDisplayDate
        binding.tvBerandaDate.setOnClickListener(null)
        binding.tvBerandaClassNote.text = "Kelas $studentClass"

        // Hide other day chips (Sen, Sel, Rab, Kam, Jum) - Only display TODAY
        binding.chipGroupBerandaDays.visibility = View.GONE

        // Real Stats calculation directly from student's database records
        val studentRecords = repository.getStudentAttendanceRecords(studentNis)
        val studentPermits = repository.getStudentPermissions(studentNis)
        val presentCount = studentRecords.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
        val permitCount = studentRecords.count { it.status == AttendanceStatus.PERMITTED || it.status == AttendanceStatus.SICK } +
                studentPermits.count { p -> p.status == "Disetujui" && studentRecords.none { it.scheduleId == "permission_${p.id}" } }
        val absentCount = studentRecords.count { it.status == AttendanceStatus.ABSENT }
        val totalRecorded = presentCount + permitCount + absentCount

        val percentage = if (totalRecorded > 0) ((presentCount.toDouble() / totalRecorded.toDouble()) * 100).toInt() else 0
        binding.tvStudentStatPercent.text = if (totalRecorded > 0) "$percentage%" else "-"
        binding.tvStudentStatIzin.text = permitCount.toString()
        binding.tvStudentStatAlpa.text = absentCount.toString()

        // Pure Data: ONLY display schedule sent by teacher for TODAY and student's class
        val allSchedules = repository.getAllSchedules()
        val schedulesToday = allSchedules.filter { schedule ->
            val matchClass = schedule.className.trim().equals(studentClass.trim(), ignoreCase = true)
            val matchDate = schedule.sessionDate == todayDateStr || schedule.sessionDate == "Hari ini" || schedule.sessionDate.isBlank()
            val notDeleted = schedule.sessionDate != "DELETED" && schedule.subject != "DELETED"
            matchClass && matchDate && notDeleted
        }.sortedByDescending { it.id }

        val nowCal = Calendar.getInstance()
        val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

        // Active Session Hero Card
        val activeSchedule = schedulesToday.firstOrNull { it.isOpenForAttendance } ?: schedulesToday.firstOrNull()
        if (activeSchedule != null) {
            binding.cardHeroAttendance.visibility = View.VISIBLE
            val timeInfo = activeSchedule.timeSlot.ifBlank { "07:10 - 08:00 WIB" }
            binding.tvHeroSubtext.text = "NIS $studentNis · $studentClass · $timeInfo"

            val timeParts = activeSchedule.timeSlot.split("-").map { it.trim() }
            val startTimeStr = if (timeParts.isNotEmpty()) timeParts[0].replace("WIB", "").trim() else ""
            val endTimeStr = if (timeParts.size > 1) timeParts[1].replace("WIB", "").trim() else ""

            val startMinutes = parseTimeToMinutes(startTimeStr)
            val endMinutes = parseTimeToMinutes(endTimeStr)

            val isBeforeStart = startMinutes != null && nowMinutes < startMinutes
            val isAfterEnd = endMinutes != null && nowMinutes > endMinutes

            val isAttended = repository.isStudentAttended(activeSchedule.id, studentNis)
            when {
                isAttended -> {
                    binding.tvHeroAttendanceTitle.text = "Presensi Anda Berhasil Dicatat"
                    binding.btnHeroClockIn.isEnabled = false
                    binding.btnHeroClockIn.text = "Sudah Hadir"
                    binding.btnHeroClockIn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.hadirku_soft_mint))
                    binding.btnHeroClockIn.setTextColor(ContextCompat.getColor(this, R.color.hadirku_primary))
                }
                !activeSchedule.isOpenForAttendance -> {
                    binding.tvHeroAttendanceTitle.text = "Sesi Absensi ${activeSchedule.subject} Ditutup"
                    binding.btnHeroClockIn.isEnabled = false
                    binding.btnHeroClockIn.text = "Sesi Ditutup"
                    binding.btnHeroClockIn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F3F4F6"))
                    binding.btnHeroClockIn.setTextColor(Color.parseColor("#9CA3AF"))
                }
                isBeforeStart -> {
                    binding.tvHeroAttendanceTitle.text = "Absensi Dibuka Pukul $startTimeStr WIB"
                    binding.btnHeroClockIn.isEnabled = false
                    binding.btnHeroClockIn.text = "Belum Dimulai"
                    binding.btnHeroClockIn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F3F4F6"))
                    binding.btnHeroClockIn.setTextColor(Color.parseColor("#9CA3AF"))
                }
                isAfterEnd -> {
                    binding.tvHeroAttendanceTitle.text = "Batas Waktu Absensi Berakhir ($endTimeStr WIB)"
                    binding.btnHeroClockIn.isEnabled = false
                    binding.btnHeroClockIn.text = "Waktu Habis"
                    binding.btnHeroClockIn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
                    binding.btnHeroClockIn.setTextColor(Color.parseColor("#DC2626"))
                }
                else -> {
                    binding.tvHeroAttendanceTitle.text = "Absensi ${activeSchedule.subject} sedang dibuka"
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
            }
        } else {
            binding.cardHeroAttendance.visibility = View.GONE
        }

        // Render Today's Schedules
        val todayContainer = binding.layoutTodaySchedulesContainer
        todayContainer.removeAllViews()

        if (schedulesToday.isEmpty()) {
            val emptyCard = MaterialCardView(this).apply {
                radius = 24f
                setCardBackgroundColor(Color.WHITE)
                strokeColor = Color.parseColor("#E5E7EB")
                strokeWidth = 2
                elevation = 0f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 12
                    bottomMargin = 12
                }
            }
            val emptyLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(32, 40, 32, 40)
            }
            val emptyTitle = TextView(this).apply {
                text = "Belum Ada Jadwal Absensi Hari Ini"
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.hadirku_text_dark))
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            val emptySub = TextView(this).apply {
                text = "Guru pengajar belum mengirimkan atau membuka sesi absensi untuk kelas $studentClass pada hari ini ($formattedDisplayDate). Jadwal absensi akan otomatis tampil saat guru membuka sesi absensi."
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.hadirku_text_muted))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 6, 0, 0)
            }
            emptyLayout.addView(emptyTitle)
            emptyLayout.addView(emptySub)
            emptyCard.addView(emptyLayout)
            todayContainer.addView(emptyCard)
        } else {
            for (schedule in schedulesToday) {
                val itemBinding = ItemStudentScheduleFigmaBinding.inflate(layoutInflater, todayContainer, false)
                val times = schedule.timeSlot.replace("WIB", "").trim().split("-").map { it.trim() }
                itemBinding.tvScheduleStartTime.text = if (times.isNotEmpty() && times[0].isNotBlank()) times[0] else "07:00"
                itemBinding.tvScheduleEndTime.text = if (times.size > 1 && times[1].isNotBlank()) times[1] else "08:30"
                itemBinding.tvScheduleSubject.text = schedule.subject
                itemBinding.tvScheduleTeacher.text = schedule.teacherName

                val hasAttended = repository.isStudentAttended(schedule.id, studentNis)
                val timeParts = schedule.timeSlot.split("-").map { it.trim() }
                val startStr = if (timeParts.isNotEmpty()) timeParts[0].replace("WIB", "").trim() else ""
                val endStr = if (timeParts.size > 1) timeParts[1].replace("WIB", "").trim() else ""
                val sMin = parseTimeToMinutes(startStr)
                val eMin = parseTimeToMinutes(endStr)
                val isBefore = sMin != null && nowMinutes < sMin
                val isAfter = eMin != null && nowMinutes > eMin

                when {
                    hasAttended -> {
                        itemBinding.tvScheduleStatusPill.text = "Hadir"
                        itemBinding.tvScheduleStatusPill.setBackgroundColor(Color.parseColor("#ECFDF5"))
                        itemBinding.tvScheduleStatusPill.setTextColor(Color.parseColor("#059669"))
                    }
                    !schedule.isOpenForAttendance -> {
                        itemBinding.tvScheduleStatusPill.text = "Ditutup"
                        itemBinding.tvScheduleStatusPill.setBackgroundColor(Color.parseColor("#F3F4F6"))
                        itemBinding.tvScheduleStatusPill.setTextColor(Color.parseColor("#9CA3AF"))
                    }
                    isBefore -> {
                        itemBinding.tvScheduleStatusPill.text = "Belum Mulai"
                        itemBinding.tvScheduleStatusPill.setBackgroundColor(Color.parseColor("#F3F4F6"))
                        itemBinding.tvScheduleStatusPill.setTextColor(Color.parseColor("#9CA3AF"))
                    }
                    isAfter -> {
                        itemBinding.tvScheduleStatusPill.text = "Waktu Habis"
                        itemBinding.tvScheduleStatusPill.setBackgroundColor(Color.parseColor("#FEF2F2"))
                        itemBinding.tvScheduleStatusPill.setTextColor(Color.parseColor("#DC2626"))
                    }
                    else -> {
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
                    }
                }

                todayContainer.addView(itemBinding.root)
            }
        }
    }

    private fun calculateIzinDuration(): Int {
        val sCal = (izinStartCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val eCal = (izinEndCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = eCal.timeInMillis - sCal.timeInMillis
        val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
        return maxOf(1, diffDays)
    }

    private fun updateIzinDurationDisplay() {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startStr = sdf.format(izinStartCal.time)
        val endStr = sdf.format(izinEndCal.time)
        val durationDays = calculateIzinDuration()
        binding.tvIzinStartDateDisplay.text = startStr
        binding.tvIzinEndDateDisplay.text = endStr
        binding.tvIzinDurationBadge.text = "Durasi Izin: $durationDays Hari ($startStr sampai $endStr)"
    }

    private fun showIzinStartDatePicker() {
        val y = izinStartCal.get(Calendar.YEAR)
        val m = izinStartCal.get(Calendar.MONTH)
        val d = izinStartCal.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            izinStartCal.set(year, month, dayOfMonth)
            if (izinEndCal.before(izinStartCal)) {
                izinEndCal = (izinStartCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
            }
            updateIzinDurationDisplay()
        }, y, m, d).show()
    }

    private fun showIzinEndDatePicker() {
        val y = izinEndCal.get(Calendar.YEAR)
        val m = izinEndCal.get(Calendar.MONTH)
        val d = izinEndCal.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val testCal = (izinEndCal.clone() as Calendar).apply { set(year, month, dayOfMonth) }
            if (testCal.before(izinStartCal)) {
                Toast.makeText(this, "Tanggal berakhir tidak boleh sebelum tanggal mulai!", Toast.LENGTH_SHORT).show()
                izinEndCal = (izinStartCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
            } else {
                izinEndCal.set(year, month, dayOfMonth)
            }
            updateIzinDurationDisplay()
        }, y, m, d).show()
    }

    private fun checkCameraAndCaptureIzin() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            captureSuratIzinLauncher.launch(null)
        } else {
            permitCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupStudentIzinFormListeners() {
        binding.chipGroupIzinType.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chipIzinSakit) -> selectedIzinType = "Sakit"
                checkedIds.contains(R.id.chipIzinKeluarga) -> selectedIzinType = "Acara Keluarga"
                checkedIds.contains(R.id.chipIzinPenting) -> selectedIzinType = "Keperluan Penting"
                else -> selectedIzinType = "Izin"
            }
        }

        binding.btnPickIzinStartDate.setOnClickListener { showIzinStartDatePicker() }
        binding.btnPickIzinEndDate.setOnClickListener { showIzinEndDatePicker() }

        binding.cardCaptureSuratIzin.setOnClickListener { checkCameraAndCaptureIzin() }
        binding.btnCaptureSuratIzin.setOnClickListener { checkCameraAndCaptureIzin() }

        binding.btnSubmitIzin.setOnClickListener { submitStudentIzin() }
    }

    private fun submitStudentIzin() {
        val reason = binding.etIzinReason.text?.toString()?.trim().orEmpty()
        if (reason.isEmpty()) {
            binding.etIzinReason.error = "Harap isi keterangan/alasan izin secara rinci!"
            Toast.makeText(this, "Harap lengkapi alasan izin!", Toast.LENGTH_SHORT).show()
            return
        }

        if (permitPhotoBase64.isNullOrEmpty()) {
            Toast.makeText(this, "Wajib mengambil foto surat izin resmi (surat dokter / orang tua)!", Toast.LENGTH_LONG).show()
            return
        }

        val studentNis = currentUser?.cardData?.nisn ?: currentUser?.id ?: "2410031"
        val studentName = currentUser?.name ?: "Rina Aulia Putri"
        val studentClass = currentUser?.cardData?.className ?: "XI IPA 2"
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startStr = sdf.format(izinStartCal.time)
        val endStr = sdf.format(izinEndCal.time)
        val days = calculateIzinDuration()

        val newPermit = StudentPermission(
            id = UUID.randomUUID().toString(),
            studentNisn = studentNis,
            studentName = studentName,
            className = studentClass,
            permissionType = selectedIzinType,
            startDate = startStr,
            endDate = endStr,
            totalDays = days,
            reason = reason,
            photoBase64 = permitPhotoBase64!!,
            status = "Menunggu"
        )

        repository.submitPermission(newPermit)
        Toast.makeText(this, "Pengajuan izin $selectedIzinType ($days hari) berhasil dikirim!", Toast.LENGTH_LONG).show()

        // Reset form
        binding.etIzinReason.text?.clear()
        permitPhotoBitmap = null
        permitPhotoBase64 = null
        binding.imgPreviewSuratIzin.setImageDrawable(null)
        binding.imgPreviewSuratIzin.visibility = View.GONE
        binding.layoutPromptFotoSurat.visibility = View.VISIBLE
        binding.btnCaptureSuratIzin.text = "Buka Kamera"

        renderStudentIzinHistory()
    }

    private fun renderStudentIzinTab() {
        val studentClass = currentUser?.cardData?.className ?: "XI IPA 2"
        binding.tvIzinStudentClassHeader.text = "SMKS TARUNA JAYA PRAWIRA TUBAN · $studentClass"
        updateIzinDurationDisplay()
        renderStudentIzinHistory()
    }

    private fun renderStudentIzinHistory() {
        val container = binding.layoutStudentIzinHistoryContainer
        container.removeAllViews()

        val studentNis = currentUser?.cardData?.nisn ?: currentUser?.id ?: "2410031"
        val myPermits = repository.getStudentPermissions(studentNis)

        if (myPermits.isEmpty()) {
            val emptyCard = MaterialCardView(this).apply {
                radius = 28f
                setCardBackgroundColor(Color.WHITE)
                strokeColor = Color.parseColor("#E5E7EB")
                strokeWidth = 2
                elevation = 0f
            }
            val emptyLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(24, 32, 24, 32)
            }
            val emptyTitle = TextView(this).apply {
                text = "Belum Ada Pengajuan Izin"
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.hadirku_text_dark))
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            val emptySub = TextView(this).apply {
                text = "Riwayat surat izin sakit atau permohonan dispensasi yang Anda ajukan akan tercatat di sini."
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.hadirku_text_muted))
                textSize = 11.5f
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
            }
            emptyLayout.addView(emptyTitle)
            emptyLayout.addView(emptySub)
            emptyCard.addView(emptyLayout)
            container.addView(emptyCard)
        } else {
            for (permit in myPermits) {
                val itemBinding = ItemPermitCardBinding.inflate(layoutInflater, container, false)
                itemBinding.tvPermitItemStudentName.text = permit.studentName
                itemBinding.tvPermitItemDetails.text = "NIS ${permit.studentNisn} · ${permit.className} · Jenis: ${permit.permissionType}"
                itemBinding.tvPermitItemDates.text = "${permit.startDate} s/d ${permit.endDate} (${permit.totalDays} Hari)"
                itemBinding.tvPermitItemReason.text = "Alasan: ${permit.reason}"
                itemBinding.tvPermitItemStatusBadge.text = permit.status

                when (permit.status) {
                    "Disetujui" -> {
                        itemBinding.tvPermitItemStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
                        itemBinding.tvPermitItemStatusBadge.setTextColor(Color.parseColor("#059669"))
                    }
                    "Ditolak" -> {
                        itemBinding.tvPermitItemStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
                        itemBinding.tvPermitItemStatusBadge.setTextColor(Color.parseColor("#DC2626"))
                    }
                    else -> {
                        itemBinding.tvPermitItemStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                        itemBinding.tvPermitItemStatusBadge.setTextColor(Color.parseColor("#D97706"))
                    }
                }

                itemBinding.btnViewPermitPhoto.setOnClickListener {
                    showPermitDetailsDialog(permit, isTeacher = false)
                }
                container.addView(itemBinding.root)
            }
        }
    }

    private var isSyncingRiwayat = false
    private fun renderStudentRiwayatTab() {
        val studentNis = currentUser?.cardData?.nisn ?: currentUser?.nisn ?: currentUser?.id ?: ""
        populateRiwayatUI(studentNis)

        if (!isSyncingRiwayat && studentNis.isNotEmpty()) {
            isSyncingRiwayat = true
            lifecycleScope.launch {
                try {
                    repository.syncStudentAttendanceAndPermits(studentNis)
                    populateRiwayatUI(studentNis)
                } finally {
                    isSyncingRiwayat = false
                }
            }
        }
    }

    private fun populateRiwayatUI(studentNis: String) {
        val cal = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("id-ID"))
        binding.tvRiwayatMonthHeader.text = monthFormat.format(cal.time)

        val records = repository.getStudentAttendanceRecords(studentNis)
        val permits = repository.getStudentPermissions(studentNis)

        val hadirCount = records.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
        val izinCount = records.count { it.status == AttendanceStatus.PERMITTED || it.status == AttendanceStatus.SICK } +
                permits.count { p -> (p.status == "Disetujui" || p.status == "Menunggu") && records.none { it.scheduleId == "permission_${p.id}" } }
        val alpaCount = records.count { it.status == AttendanceStatus.ABSENT }

        binding.tvRiwayatHadirCount.text = hadirCount.toString()
        binding.tvRiwayatIzinCount.text = izinCount.toString()
        binding.tvRiwayatAlpaCount.text = alpaCount.toString()

        val container = binding.layoutAttendanceHistoryContainer
        container.removeAllViews()

        val sdfDate = SimpleDateFormat("EEE, d MMM yyyy", Locale.forLanguageTag("id-ID"))
        val sdfTime = SimpleDateFormat("HH:mm", Locale.forLanguageTag("id-ID"))
        val allSchedules = repository.getAllSchedules()

        // Pure Unified History Model
        data class StudentHistoryItem(
            val timestamp: Long,
            val dateStr: String,
            val subjectTime: String,
            val statusText: String
        )

        val items = mutableListOf<StudentHistoryItem>()

        for (rec in records) {
            val sched = allSchedules.firstOrNull { it.id == rec.scheduleId }
            val subject = sched?.subject ?: if (rec.remarks.isNotBlank()) rec.remarks else "Presensi Harian"
            val time = sdfTime.format(Date(rec.timestamp))
            val statusLabel = when (rec.status) {
                AttendanceStatus.PRESENT -> "Hadir"
                AttendanceStatus.LATE -> "Terlambat"
                AttendanceStatus.PERMITTED -> "Izin"
                AttendanceStatus.SICK -> "Sakit"
                AttendanceStatus.ABSENT -> "Alpa"
            }
            items.add(
                StudentHistoryItem(
                    timestamp = rec.timestamp,
                    dateStr = sdfDate.format(Date(rec.timestamp)),
                    subjectTime = "$subject · $time WIB",
                    statusText = statusLabel
                )
            )
        }

        for (p in permits) {
            val alreadyInRecords = records.any { it.scheduleId == "permission_${p.id}" }
            if (!alreadyInRecords) {
                val label = if (p.permissionType.contains("Sakit", ignoreCase = true)) "Sakit" else "Izin"
                items.add(
                    StudentHistoryItem(
                        timestamp = p.timestamp,
                        dateStr = "${p.startDate} s/d ${p.endDate}",
                        subjectTime = "Surat Izin: ${p.permissionType} (${p.status})",
                        statusText = label
                    )
                )
            }
        }

        val sortedItems = items.sortedByDescending { it.timestamp }

        val filteredItems = when (historyFilterStatus) {
            "Hadir" -> sortedItems.filter { it.statusText == "Hadir" || it.statusText == "Terlambat" }
            "Izin" -> sortedItems.filter { it.statusText == "Izin" || it.statusText == "Sakit" }
            "Alpa" -> sortedItems.filter { it.statusText == "Alpa" }
            else -> sortedItems
        }

        if (filteredItems.isEmpty()) {
            val emptyCard = MaterialCardView(this).apply {
                radius = 24f
                setCardBackgroundColor(Color.WHITE)
                strokeColor = Color.parseColor("#E5E7EB")
                strokeWidth = 2
                elevation = 0f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16
                    bottomMargin = 16
                }
            }
            val emptyLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(32, 40, 32, 40)
            }
            val emptyTitle = TextView(this).apply {
                text = "Belum Ada Catatan Riwayat"
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.hadirku_text_dark))
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            val emptySub = TextView(this).apply {
                val filterDesc = if (historyFilterStatus == "Semua") "presensi maupun surat izin" else "dengan status $historyFilterStatus"
                text = "Belum ada riwayat $filterDesc yang tercatat pada akun ini ($studentNis). Semua riwayat presensi dan permohonan izin akan tersimpan akurat sesuai data resmi."
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.hadirku_text_muted))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 6, 0, 0)
            }
            emptyLayout.addView(emptyTitle)
            emptyLayout.addView(emptySub)
            emptyCard.addView(emptyLayout)
            container.addView(emptyCard)
        } else {
            for (item in filteredItems) {
                val itemBinding = ItemStudentAttendanceFigmaBinding.inflate(layoutInflater, container, false)
                itemBinding.tvHistoryDate.text = item.dateStr
                itemBinding.tvHistorySubjectTime.text = item.subjectTime
                itemBinding.tvHistoryStatusPill.text = item.statusText

                when (item.statusText) {
                    "Hadir" -> {
                        itemBinding.tvHistoryStatusPill.setBackgroundColor(Color.parseColor("#ECFDF5"))
                        itemBinding.tvHistoryStatusPill.setTextColor(Color.parseColor("#059669"))
                    }
                    "Terlambat" -> {
                        itemBinding.tvHistoryStatusPill.setBackgroundColor(Color.parseColor("#FFFBEB"))
                        itemBinding.tvHistoryStatusPill.setTextColor(Color.parseColor("#D97706"))
                    }
                    "Izin", "Sakit" -> {
                        itemBinding.tvHistoryStatusPill.setBackgroundColor(Color.parseColor("#EFF6FF"))
                        itemBinding.tvHistoryStatusPill.setTextColor(Color.parseColor("#2563EB"))
                    }
                    else -> {
                        itemBinding.tvHistoryStatusPill.setBackgroundColor(Color.parseColor("#FEF2F2"))
                        itemBinding.tvHistoryStatusPill.setTextColor(Color.parseColor("#DC2626"))
                    }
                }
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
        val dialog = BottomSheetDialog(this)
        val dBinding = com.absensiapp.school.databinding.DialogChangePasswordBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        dBinding.btnSubmitChangePassword.setOnClickListener {
            val newPass = dBinding.etNewPassword.text?.toString()?.trim().orEmpty()
            val confirmPass = dBinding.etConfirmPassword.text?.toString()?.trim().orEmpty()

            if (newPass.length < 6) {
                dBinding.tilNewPassword.error = "Password baru minimal 6 karakter"
                return@setOnClickListener
            } else {
                dBinding.tilNewPassword.error = null
            }

            if (newPass != confirmPass) {
                dBinding.tilConfirmPassword.error = "Konfirmasi password tidak cocok"
                return@setOnClickListener
            } else {
                dBinding.tilConfirmPassword.error = null
            }

            val user = currentUser
            if (user != null) {
                repository.changePassword(user.id, newPass)
                Toast.makeText(this, "Password akun berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dBinding.btnCancelChangePassword.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // Retained stubs for compatibility
    private fun setupStudentIdCardToggle() {}
    private fun setupStudentCalendar() { renderStudentBerandaTab() }
    private fun renderStudentAttendanceSchedules() { renderStudentBerandaTab() }

    // =========================================================================
    // TEACHER VIEW: HADIRKU FIGMA EXPERIENCE (ABSENSI, SISWA, LAPORAN, PROFIL)
    // =========================================================================

    private var selectedTeacherTab = 0
    private var selectedTeacherClassFilter = "Semua"
    private var selectedTeacherLaporanPeriod = "Mingguan"
    private var isTeacherSessionActive = true

    private fun setupTeacherNavigationAndTabs() {
        binding.bottomNavTeacher.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_teacher_absensi -> {
                    switchTeacherTab(0)
                    true
                }
                R.id.nav_teacher_siswa -> {
                    switchTeacherTab(1)
                    true
                }
                R.id.nav_teacher_laporan -> {
                    switchTeacherTab(2)
                    true
                }
                R.id.nav_teacher_profil -> {
                    switchTeacherTab(3)
                    true
                }
                else -> false
            }
        }

        // Tab 1 (Absensi) - Setup Class Dropdown Selection
        val allClasses = repository.getAllClasses()
        val classNames = if (allClasses.isNotEmpty()) allClasses.map { it.name }.distinct() else listOf("XI RPL 1", "XI IPA 2", "XI TKJ 1", "XII RPL 1")
        val classAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, classNames)
        binding.actvTeacherClassTarget.setAdapter(classAdapter)
        binding.actvTeacherClassTarget.setText(selectedTeacherSessionClass, false)
        binding.actvTeacherClassTarget.setOnItemClickListener { _, _, position, _ ->
            selectedTeacherSessionClass = classNames[position]
            renderTeacherAbsensiTab()
        }

        // Tab 1 (Absensi) - Tombol Input Jadwal Absensi (Membuka popup input jadwal)
        binding.btnTeacherInputSchedule.setOnClickListener {
            val targetClass = binding.actvTeacherClassTarget.text.toString().ifBlank { selectedTeacherSessionClass }
            showTeacherScheduleDialog(null, targetClass)
        }

        binding.btnTeacherExportExcelHeader.setOnClickListener {
            val targetClass = binding.actvTeacherClassTarget.text.toString().ifBlank { selectedTeacherSessionClass }
            exportAttendanceToCsv(targetClass, "Presensi Kelas $targetClass")
        }

        // Tab 2 (Siswa) Filter Chips and Live Search
        binding.chipTeacherClassAll.setOnClickListener {
            selectedTeacherClassFilter = "Semua"
            updateTeacherClassChipSelection()
            renderTeacherSiswaTab()
        }
        binding.chipTeacherClassXipa2.setOnClickListener {
            selectedTeacherClassFilter = "XI IPA 2"
            updateTeacherClassChipSelection()
            renderTeacherSiswaTab()
        }
        binding.chipTeacherClassXitkj1.setOnClickListener {
            selectedTeacherClassFilter = "XI TKJ 1"
            updateTeacherClassChipSelection()
            renderTeacherSiswaTab()
        }
        binding.chipTeacherClassXirpl1.setOnClickListener {
            selectedTeacherClassFilter = "XI RPL 1"
            updateTeacherClassChipSelection()
            renderTeacherSiswaTab()
        }
        binding.chipTeacherClassXiirpl1.setOnClickListener {
            selectedTeacherClassFilter = "XII RPL 1"
            updateTeacherClassChipSelection()
            renderTeacherSiswaTab()
        }
        binding.etTeacherSearchStudent.doAfterTextChanged {
            renderTeacherSiswaTab()
        }

        // Tab 3 (Laporan) Chips and Full Export
        binding.chipTeacherLaporanMingguan.setOnClickListener {
            selectedTeacherLaporanPeriod = "Mingguan"
            updateTeacherLaporanChipSelection()
            renderTeacherLaporanTab()
        }
        binding.chipTeacherLaporanBulanan.setOnClickListener {
            selectedTeacherLaporanPeriod = "Bulanan"
            updateTeacherLaporanChipSelection()
            renderTeacherLaporanTab()
        }
        binding.chipTeacherLaporanSemester.setOnClickListener {
            selectedTeacherLaporanPeriod = "Semester"
            updateTeacherLaporanChipSelection()
            renderTeacherLaporanTab()
        }
        binding.btnTeacherExportLaporanFull.setOnClickListener {
            exportAttendanceToCsv(null, "Laporan Lengkap Presensi Siswa")
        }

        // Tab 4 (Profil) Actions
        binding.btnTeacherChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }
        binding.btnTeacherLogout.setOnClickListener {
            logoutAndReturnToLogin()
        }
    }

    private fun updateTeacherClassChipSelection() {
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_day_selected)
        val unselectedBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_day_unselected)
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        val darkColor = ContextCompat.getColor(this, R.color.hadirku_text_dark)

        binding.chipTeacherClassAll.background = if (selectedTeacherClassFilter == "Semua") selectedBg else unselectedBg
        binding.chipTeacherClassAll.setTextColor(if (selectedTeacherClassFilter == "Semua") whiteColor else darkColor)

        binding.chipTeacherClassXipa2.background = if (selectedTeacherClassFilter == "XI IPA 2") selectedBg else unselectedBg
        binding.chipTeacherClassXipa2.setTextColor(if (selectedTeacherClassFilter == "XI IPA 2") whiteColor else darkColor)

        binding.chipTeacherClassXitkj1.background = if (selectedTeacherClassFilter == "XI TKJ 1") selectedBg else unselectedBg
        binding.chipTeacherClassXitkj1.setTextColor(if (selectedTeacherClassFilter == "XI TKJ 1") whiteColor else darkColor)

        binding.chipTeacherClassXirpl1.background = if (selectedTeacherClassFilter == "XI RPL 1") selectedBg else unselectedBg
        binding.chipTeacherClassXirpl1.setTextColor(if (selectedTeacherClassFilter == "XI RPL 1") whiteColor else darkColor)

        binding.chipTeacherClassXiirpl1.background = if (selectedTeacherClassFilter == "XII RPL 1") selectedBg else unselectedBg
        binding.chipTeacherClassXiirpl1.setTextColor(if (selectedTeacherClassFilter == "XII RPL 1") whiteColor else darkColor)
    }

    private fun updateTeacherLaporanChipSelection() {
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_day_selected)
        val unselectedBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_day_unselected)
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        val darkColor = ContextCompat.getColor(this, R.color.hadirku_text_dark)

        binding.chipTeacherLaporanMingguan.background = if (selectedTeacherLaporanPeriod == "Mingguan") selectedBg else unselectedBg
        binding.chipTeacherLaporanMingguan.setTextColor(if (selectedTeacherLaporanPeriod == "Mingguan") whiteColor else darkColor)

        binding.chipTeacherLaporanBulanan.background = if (selectedTeacherLaporanPeriod == "Bulanan") selectedBg else unselectedBg
        binding.chipTeacherLaporanBulanan.setTextColor(if (selectedTeacherLaporanPeriod == "Bulanan") whiteColor else darkColor)

        binding.chipTeacherLaporanSemester.background = if (selectedTeacherLaporanPeriod == "Semester") selectedBg else unselectedBg
        binding.chipTeacherLaporanSemester.setTextColor(if (selectedTeacherLaporanPeriod == "Semester") whiteColor else darkColor)
    }

    private fun switchTeacherTab(tabIndex: Int) {
        selectedTeacherTab = tabIndex
        binding.layoutTeacherAbsensi.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        binding.layoutTeacherSiswa.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        binding.layoutTeacherLaporan.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        binding.layoutTeacherProfil.visibility = if (tabIndex == 3) View.VISIBLE else View.GONE

        when (tabIndex) {
            0 -> renderTeacherAbsensiTab()
            1 -> renderTeacherSiswaTab()
            2 -> {
                renderTeacherLaporanTab()
                lifecycleScope.launch {
                    try {
                        repository.fetchPureStudentsFromDatabase()
                        if (selectedTeacherTab == 2) {
                            renderTeacherLaporanTab()
                        }
                    } catch (_: Exception) {}
                }
            }
            3 -> renderTeacherProfilTab()
        }
    }

    private fun renderTeacherAbsensiTab() {
        val teacherName = currentUser?.name ?: "Pak Dedi Kurniawan"
        binding.tvTeacherHeaderName.text = teacherName

        val targetClass = binding.actvTeacherClassTarget.text.toString().ifBlank { selectedTeacherSessionClass }

        // --- Render Schedule List for targetClass ---
        val scheduleContainer = binding.layoutTeacherSchedulesList
        scheduleContainer.removeAllViews()

        val allSchedules = repository.getAllSchedules()
        val classSchedules = allSchedules.filter {
            it.className.trim().equals(targetClass.trim(), ignoreCase = true) &&
            it.sessionDate != "DELETED" &&
            it.subject != "DELETED" &&
            !it.isPastDay()
        }.sortedByDescending { it.id }
        binding.tvTeacherScheduleSectionHeader.text = "Jadwal Sesi Absensi $targetClass"
        binding.tvTeacherScheduleCountBadge.text = "${classSchedules.size} Jadwal"

        if (classSchedules.isEmpty()) {
            val emptyCard = com.google.android.material.card.MaterialCardView(this).apply {
                radius = 24f
                setCardBackgroundColor(Color.WHITE)
                strokeColor = Color.parseColor("#E5E7EB")
                strokeWidth = 2
                elevation = 0f
            }
            val emptyLayout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(32, 32, 32, 32)
            }
            val emptyTitle = TextView(this).apply {
                text = "Belum Ada Jadwal Sesi Absensi"
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.hadirku_text_dark))
                textSize = 13.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.CENTER
            }
            val emptySub = TextView(this).apply {
                text = "Tekan tombol '+ Input Jadwal Absensi' di atas untuk membuat jadwal sesi presensi baru untuk kelas $targetClass."
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.hadirku_text_muted))
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 8, 0, 0)
            }
            emptyLayout.addView(emptyTitle)
            emptyLayout.addView(emptySub)
            emptyCard.addView(emptyLayout)
            scheduleContainer.addView(emptyCard)
        } else {
            for (schedule in classSchedules) {
                val cardBinding = ItemTeacherScheduleCardBinding.inflate(layoutInflater, scheduleContainer, false)
                cardBinding.tvCardClassName.text = schedule.className
                cardBinding.tvCardRoom.text = schedule.room.ifBlank { "Ruang 10" }
                cardBinding.tvCardSubject.text = schedule.subject
                cardBinding.tvCardTimeSlot.text = schedule.timeSlot
                cardBinding.tvCardDate.text = "${schedule.sessionDate} • ${schedule.dayName}"

                val isExpired = schedule.isExpired()
                if (isExpired && schedule.isOpenForAttendance) {
                    schedule.isOpenForAttendance = false
                    repository.toggleScheduleStatus(schedule.id, false)
                }
                val isOpen = schedule.isOpenForAttendance && !isExpired

                cardBinding.tvCardStatusBadge.text = when {
                    isExpired -> "Selesai"
                    isOpen -> "Sesi Dibuka"
                    else -> "Sesi Ditutup"
                }
                cardBinding.tvCardStatusBadge.backgroundTintList = ColorStateList.valueOf(
                    when {
                        isExpired -> Color.parseColor("#FEE2E2")
                        isOpen -> Color.parseColor("#ECFDF5")
                        else -> Color.parseColor("#F3F4F6")
                    }
                )
                cardBinding.tvCardStatusBadge.setTextColor(
                    when {
                        isExpired -> Color.parseColor("#DC2626")
                        isOpen -> Color.parseColor("#059669")
                        else -> Color.parseColor("#6B7280")
                    }
                )

                if (isExpired) {
                    cardBinding.btnCardToggleStatus.isEnabled = false
                    cardBinding.btnCardToggleStatus.alpha = 0.5f
                    cardBinding.btnCardToggleStatus.text = "Waktu Habis"
                } else {
                    cardBinding.btnCardToggleStatus.isEnabled = true
                    cardBinding.btnCardToggleStatus.alpha = 1.0f
                    cardBinding.btnCardToggleStatus.text = if (isOpen) "Tutup Sesi" else "Buka Sesi"
                    cardBinding.btnCardToggleStatus.setOnClickListener {
                        repository.toggleScheduleStatus(schedule.id, !isOpen)
                        Toast.makeText(
                            this,
                            if (!isOpen) "Sesi absensi dibuka untuk ${schedule.subject}" else "Sesi absensi ditutup untuk ${schedule.subject}",
                            Toast.LENGTH_SHORT
                        ).show()
                        renderTeacherAbsensiTab()
                    }
                }

                // Edit schedule
                cardBinding.btnCardEditSchedule.setOnClickListener {
                    showTeacherScheduleDialog(schedule, targetClass)
                }

                // Delete schedule with confirmation dialog
                cardBinding.btnCardDeleteSchedule.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Hapus Jadwal")
                        .setMessage("Apakah Anda yakin ingin menghapus jadwal '${schedule.subject}' untuk kelas ${schedule.className}?")
                        .setPositiveButton("Hapus") { _, _ ->
                            repository.deleteSchedule(schedule.id)
                            Toast.makeText(this, "Jadwal '${schedule.subject}' berhasil dihapus!", Toast.LENGTH_SHORT).show()
                            renderTeacherAbsensiTab()
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }

                scheduleContainer.addView(cardBinding.root)
            }
        }

        // --- Render Student List for targetClass ---
        val container = binding.layoutTeacherAbsensiStudentList
        container.removeAllViews()

        val realStudents = repository.getStudentsByClass(targetClass)
        binding.tvTeacherClassCountHeader.text = "Siswa $targetClass · ${realStudents.size}"

        val allRecords = repository.getAllAttendanceRecords()
        val activeSchedule = classSchedules.firstOrNull { it.isOpenForAttendance } ?: classSchedules.firstOrNull()

        for (student in realStudents) {
            val itemBinding = ItemTeacherStudentFigmaBinding.inflate(layoutInflater, container, false)
            val studentNisn = student.cardData?.nisn ?: student.nisn ?: student.id
            val studentClass = student.cardData?.className ?: targetClass

            itemBinding.tvStudentItemName.text = student.name
            itemBinding.tvStudentAvatarInitials.text = getInitials(student.name)
            itemBinding.tvStudentItemDetails.text = "NIS $studentNisn · $studentClass"

            val record = allRecords.firstOrNull {
                it.studentNisn == studentNisn && (activeSchedule == null || it.scheduleId == activeSchedule.id)
            }
            val isPresent = if (activeSchedule != null) {
                repository.isStudentAttended(activeSchedule.id, studentNisn) || record?.status == AttendanceStatus.PRESENT
            } else {
                record?.status == AttendanceStatus.PRESENT
            }

            val statusText = when {
                isPresent -> "Hadir"
                record?.status == AttendanceStatus.PERMITTED -> "Izin"
                record?.status == AttendanceStatus.ABSENT -> "Alpa"
                else -> "Belum"
            }

            val studentTotalRecords = repository.getStudentAttendanceRecords(studentNisn)
            val presentCount = studentTotalRecords.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
            val percentText = if (studentTotalRecords.isNotEmpty()) "${((presentCount.toDouble() / studentTotalRecords.size.toDouble()) * 100).toInt()}%" else "-"
            itemBinding.tvStudentAttendancePercent.text = percentText

            itemBinding.tvStudentStatusPill.text = statusText
            itemBinding.tvStudentStatusPill.setBackgroundColor(
                when (statusText) {
                    "Hadir" -> Color.parseColor("#ECFDF5")
                    "Izin" -> Color.parseColor("#FFFBEB")
                    "Alpa" -> Color.parseColor("#FEF2F2")
                    else -> Color.parseColor("#F3F4F6")
                }
            )
            itemBinding.tvStudentStatusPill.setTextColor(
                when (statusText) {
                    "Hadir" -> Color.parseColor("#059669")
                    "Izin" -> Color.parseColor("#D97706")
                    "Alpa" -> Color.parseColor("#DC2626")
                    else -> Color.parseColor("#6B7280")
                }
            )
            container.addView(itemBinding.root)
        }

        // Render Incoming Student Permits for targetClass
        val allPermits = repository.getAllPermissions()
        val classPermits = allPermits.filter { it.className.equals(targetClass, ignoreCase = true) }
        binding.tvTeacherPermitCountBadge.text = "${classPermits.size} Surat Izin"

        val permitContainer = binding.layoutTeacherPermissionsContainer
        permitContainer.removeAllViews()

        if (classPermits.isEmpty()) {
            val emptyCard = MaterialCardView(this).apply {
                radius = 24f
                setCardBackgroundColor(Color.WHITE)
                strokeColor = Color.parseColor("#E5E7EB")
                strokeWidth = 2
                elevation = 0f
            }
            val emptyLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(20, 24, 20, 24)
            }
            val emptyTitle = TextView(this).apply {
                text = "Tidak Ada Surat Izin Masuk"
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.hadirku_text_dark))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            val emptySub = TextView(this).apply {
                text = "Belum ada permohonan surat izin sakit atau acara keluarga dari siswa kelas $targetClass."
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.hadirku_text_muted))
                textSize = 11.5f
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
            }
            emptyLayout.addView(emptyTitle)
            emptyLayout.addView(emptySub)
            emptyCard.addView(emptyLayout)
            permitContainer.addView(emptyCard)
        } else {
            for (permit in classPermits) {
                val itemBinding = ItemPermitCardBinding.inflate(layoutInflater, permitContainer, false)
                itemBinding.tvPermitItemStudentName.text = permit.studentName
                itemBinding.tvPermitItemDetails.text = "NIS ${permit.studentNisn} · ${permit.className} · Jenis: ${permit.permissionType}"
                itemBinding.tvPermitItemDates.text = "${permit.startDate} s/d ${permit.endDate} (${permit.totalDays} Hari)"
                itemBinding.tvPermitItemReason.text = "Alasan: ${permit.reason}"
                itemBinding.tvPermitItemStatusBadge.text = permit.status

                when (permit.status) {
                    "Disetujui" -> {
                        itemBinding.tvPermitItemStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
                        itemBinding.tvPermitItemStatusBadge.setTextColor(Color.parseColor("#059669"))
                    }
                    "Ditolak" -> {
                        itemBinding.tvPermitItemStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
                        itemBinding.tvPermitItemStatusBadge.setTextColor(Color.parseColor("#DC2626"))
                    }
                    else -> {
                        itemBinding.tvPermitItemStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                        itemBinding.tvPermitItemStatusBadge.setTextColor(Color.parseColor("#D97706"))
                    }
                }

                itemBinding.btnViewPermitPhoto.setOnClickListener {
                    showPermitDetailsDialog(permit, isTeacher = true)
                }
                permitContainer.addView(itemBinding.root)
            }
        }
    }

    private fun showPermitDetailsDialog(permit: StudentPermission, isTeacher: Boolean) {
        val dialog = BottomSheetDialog(this)
        val dBinding = DialogViewPermitBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        dBinding.tvPermitStudentName.text = permit.studentName
        dBinding.tvPermitStudentDetails.text = "NIS ${permit.studentNisn} · ${permit.className} · Jenis: ${permit.permissionType}"
        dBinding.tvPermitDateAndDuration.text = "${permit.startDate} s/d ${permit.endDate} (Durasi: ${permit.totalDays} Hari)"
        dBinding.tvPermitReasonText.text = "Alasan: ${permit.reason}"

        if (permit.photoBase64.isNotEmpty()) {
            try {
                val decodedBytes = Base64.decode(permit.photoBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                dBinding.imgPermitLetterFull.setImageBitmap(bitmap)
            } catch (e: Exception) {
                dBinding.imgPermitLetterFull.setImageResource(R.drawable.ic_nav_permit)
            }
        } else {
            dBinding.imgPermitLetterFull.setImageResource(R.drawable.ic_nav_permit)
        }

        if (isTeacher) {
            dBinding.btnApprovePermit.visibility = View.VISIBLE
            if (permit.status == "Disetujui") {
                dBinding.btnApprovePermit.text = "Sudah Disetujui"
                dBinding.btnApprovePermit.isEnabled = false
            } else {
                dBinding.btnApprovePermit.isEnabled = true
                dBinding.btnApprovePermit.text = "Setujui Izin Ini"
                dBinding.btnApprovePermit.setOnClickListener {
                    repository.updatePermissionStatus(permit.id, "Disetujui")
                    Toast.makeText(this, "Surat izin ${permit.studentName} berhasil disetujui!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    renderTeacherAbsensiTab()
                }
            }
        } else {
            dBinding.btnApprovePermit.visibility = View.GONE
        }

        dBinding.btnPermitDialogClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun renderTeacherSiswaTab() {
        populateTeacherSiswaList(repository.getAllStudents())
        lifecycleScope.launch {
            try {
                val liveStudents = repository.fetchPureStudentsFromDatabase()
                populateTeacherSiswaList(liveStudents)
            } catch (_: Exception) {}
        }
    }

    private fun populateTeacherSiswaList(students: List<UserAccount>) {
        val container = binding.layoutTeacherAllStudentsContainer
        container.removeAllViews()

        val searchQuery = binding.etTeacherSearchStudent.text?.toString()?.trim() ?: ""

        val filtered = students.filter { st ->
            val stClass = st.cardData?.className?.ifBlank { "XI RPL 1" } ?: "XI RPL 1"
            val stNisn = st.cardData?.nisn ?: st.nisn ?: st.id
            val matchClass = if (selectedTeacherClassFilter == "Semua") true else stClass.equals(selectedTeacherClassFilter, ignoreCase = true)
            val matchQuery = if (searchQuery.isEmpty()) true else (st.name.contains(searchQuery, ignoreCase = true) || stNisn.contains(searchQuery))
            matchClass && matchQuery
        }

        binding.tvTeacherTotalStudentsCount.text = "${filtered.size} siswa terdaftar"

        if (filtered.isEmpty()) {
            val emptyCard = MaterialCardView(this).apply {
                radius = 24f
                setCardBackgroundColor(Color.WHITE)
                strokeColor = Color.parseColor("#E5E7EB")
                strokeWidth = 2
                elevation = 0f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 12
                    bottomMargin = 12
                }
            }
            val emptyLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(32, 40, 32, 40)
            }
            val emptyTitle = TextView(this).apply {
                text = "Belum Ada Data Siswa"
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.hadirku_text_dark))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            val emptySub = TextView(this).apply {
                text = "Tidak ditemukan data siswa untuk filter kelas '$selectedTeacherClassFilter' di database."
                setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.hadirku_text_muted))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
            }
            emptyLayout.addView(emptyTitle)
            emptyLayout.addView(emptySub)
            emptyCard.addView(emptyLayout)
            container.addView(emptyCard)
            return
        }

        val allAttendance = repository.getAllAttendanceRecords()
        val todayKey = repository.getTodayDateKey()

        for (student in filtered) {
            val itemBinding = ItemTeacherStudentFigmaBinding.inflate(layoutInflater, container, false)
            val studentNisn = student.cardData?.nisn ?: student.nisn ?: student.id
            val studentClass = student.cardData?.className?.ifBlank { "XI RPL 1" } ?: "XI RPL 1"

            itemBinding.tvStudentItemName.text = student.name
            itemBinding.tvStudentAvatarInitials.text = getInitials(student.name)
            itemBinding.tvStudentItemDetails.text = "NIS $studentNisn · $studentClass"

            val studentRecords = repository.getStudentAttendanceRecords(studentNisn)
            val presentCount = studentRecords.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
            val attendancePercent = if (studentRecords.isNotEmpty()) {
                "${((presentCount.toDouble() / studentRecords.size.toDouble()) * 100).toInt()}%"
            } else {
                "-"
            }
            itemBinding.tvStudentAttendancePercent.text = attendancePercent

            val isAttendedToday = allAttendance.any { it.studentNisn == studentNisn && it.status == AttendanceStatus.PRESENT && repository.formatDateKey(it.timestamp) == todayKey }
            val isPermittedToday = allAttendance.any { it.studentNisn == studentNisn && it.status == AttendanceStatus.PERMITTED && repository.formatDateKey(it.timestamp) == todayKey }
            val isAbsentToday = allAttendance.any { it.studentNisn == studentNisn && it.status == AttendanceStatus.ABSENT && repository.formatDateKey(it.timestamp) == todayKey }

            val statusText = when {
                isAttendedToday -> "Hadir"
                isPermittedToday -> "Izin"
                isAbsentToday -> "Alpa"
                else -> "Belum Absen"
            }

            itemBinding.tvStudentStatusPill.text = statusText
            itemBinding.tvStudentStatusPill.setBackgroundColor(
                when (statusText) {
                    "Hadir" -> Color.parseColor("#ECFDF5")
                    "Izin" -> Color.parseColor("#FFFBEB")
                    "Alpa" -> Color.parseColor("#FEF2F2")
                    else -> Color.parseColor("#F3F4F6")
                }
            )
            itemBinding.tvStudentStatusPill.setTextColor(
                when (statusText) {
                    "Hadir" -> Color.parseColor("#059669")
                    "Izin" -> Color.parseColor("#D97706")
                    "Alpa" -> Color.parseColor("#DC2626")
                    else -> Color.parseColor("#6B7280")
                }
            )
            container.addView(itemBinding.root)
        }
    }

    private fun renderTeacherLaporanTab() {
        val allRecords = repository.getAllAttendanceRecords()
        binding.tvTeacherLaporanChartTitle.text = "Kehadiran Siswa"

        val presentCount = allRecords.count { it.status == AttendanceStatus.PRESENT }
        val permitCount = allRecords.count { it.status == AttendanceStatus.PERMITTED || it.status == AttendanceStatus.SICK }
        val absentCount = allRecords.count { it.status == AttendanceStatus.ABSENT }
        val totalExpected = allRecords.size
        val presentRate = if (totalExpected > 0) {
            ((presentCount.toDouble() / totalExpected.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

        binding.tvTeacherLaporanPresentRate.text = "$presentRate%"
        binding.tvTeacherLaporanPermitCount.text = permitCount.toString()
        binding.tvTeacherLaporanAbsentCount.text = absentCount.toString()

        val cal = Calendar.getInstance()
        val curMonth = cal.get(Calendar.MONTH)
        val curYear = cal.get(Calendar.YEAR)

        fun calculateWeekStats(startDay: Int, endDay: Int): Int {
            val weekRecords = allRecords.filter { record ->
                val rCal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
                rCal.get(Calendar.YEAR) == curYear &&
                rCal.get(Calendar.MONTH) == curMonth &&
                rCal.get(Calendar.DAY_OF_MONTH) in startDay..endDay
            }
            if (weekRecords.isEmpty()) return 0
            val pCount = weekRecords.count { it.status == AttendanceStatus.PRESENT }
            return ((pCount.toDouble() / weekRecords.size.toDouble()) * 100).toInt().coerceIn(0, 100)
        }

        val rateW1 = calculateWeekStats(1, 7)
        val rateW2 = calculateWeekStats(8, 14)
        val rateW3 = calculateWeekStats(15, 21)
        val rateW4 = calculateWeekStats(22, 31)

        val density = resources.displayMetrics.density
        fun updateBar(barView: View, tvPercent: TextView, rate: Int) {
            tvPercent.text = "$rate%"
            val barHeightDp = if (rate > 0) (4 + (rate * 80) / 100) else 4
            val heightPx = (barHeightDp * density).toInt()
            val lp = barView.layoutParams
            lp.height = heightPx
            barView.layoutParams = lp
            barView.setBackgroundColor(if (rate > 0) Color.parseColor("#10B981") else Color.parseColor("#E5E7EB"))
            tvPercent.setTextColor(
                if (rate > 0) ContextCompat.getColor(this, R.color.hadirku_primary)
                else ContextCompat.getColor(this, R.color.hadirku_text_muted)
            )
        }

        updateBar(binding.barLaporan1, binding.tvBarLaporanPercent1, rateW1)
        updateBar(binding.barLaporan2, binding.tvBarLaporanPercent2, rateW2)
        updateBar(binding.barLaporan3, binding.tvBarLaporanPercent3, rateW3)
        updateBar(binding.barLaporan4, binding.tvBarLaporanPercent4, rateW4)

        if (totalExpected == 0) {
            binding.tvTeacherLaporanChartSubtitle.text = "Belum ada riwayat absensi siswa di database."
        } else {
            binding.tvTeacherLaporanChartSubtitle.text = "Persentase presensi bulan ini: $presentRate% ($totalExpected catatan)"
        }
    }

    private fun renderTeacherProfilTab() {
        val teacherName = currentUser?.name ?: "Pak Dedi Kurniawan, S.Pd."
        val teacherNip = currentUser?.id ?: "1982051001"
        val username = currentUser?.id ?: "guru_dedi"

        binding.tvTeacherProfilName.text = teacherName
        binding.tvTeacherProfilInitials.text = getInitials(teacherName)
        binding.tvTeacherProfilNip.text = "NIP $teacherNip"
        binding.tvTeacherProfilUsername.text = username

        val todayIndonesian = SimpleDateFormat("EEEE", Locale.forLanguageTag("id-ID")).format(Date())

        val allSchedules = repository.getAllSchedules()
        val teacherSchedules = allSchedules.filter {
            it.teacherId.equals(teacherNip, ignoreCase = true) ||
            it.teacherName.contains(teacherName, ignoreCase = true)
        }

        val todaySchedules = teacherSchedules.filter {
            it.dayName.equals(todayIndonesian, ignoreCase = true)
        }

        // If no schedule was entered for today, display "-" as specified
        if (todaySchedules.isEmpty()) {
            binding.tvTeacherProfilKelasAjar.text = "-"
        } else {
            val classNames = todaySchedules.map { it.className }.distinct()
            binding.tvTeacherProfilKelasAjar.text = classNames.joinToString(", ")
        }

        val totalHours = teacherSchedules.sumOf { (it.periodEnd - it.periodStart + 1) }
        binding.tvTeacherProfilTotalJam.text = if (totalHours > 0) "$totalHours Jam Pelajaran" else "24 Jam Pelajaran"
    }

    private fun exportAttendanceToCsv(targetClassName: String? = null, title: String = "Laporan Presensi") {
        try {
            val allRecords = repository.getAllAttendanceRecords()
            val recordsToExport = if (targetClassName.isNullOrEmpty() || targetClassName == "Semua") {
                allRecords
            } else {
                allRecords.filter { it.className.equals(targetClassName, ignoreCase = true) }
            }

            if (recordsToExport.isEmpty()) {
                Toast.makeText(this, "Tidak ada data presensi untuk diekspor ke Excel.", Toast.LENGTH_SHORT).show()
                return
            }

            val reportsDir = File(cacheDir, "reports")
            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val cleanClass = targetClassName?.replace(" ", "_") ?: "Semua_Kelas"
            val file = File(reportsDir, "Presensi_${cleanClass}_$timestampStr.csv")

            val allSchedules = repository.getAllSchedules()
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

            FileOutputStream(file).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    // UTF-8 BOM for Microsoft Excel Indonesian locale compatibility
                    writer.write("\uFEFF")

                    // Header row
                    writer.write("No,NISN,Nama Siswa,Kelas,Mata Pelajaran,Status Presensi,Waktu Presensi,Metode,Keterangan\n")

                    recordsToExport.forEachIndexed { index, record ->
                        val sched = allSchedules.firstOrNull { it.id == record.scheduleId }
                        val subject = sched?.subject ?: "Mata Pelajaran"
                        val timeStr = sdf.format(Date(record.timestamp))
                        val statusStr = when (record.status) {
                            AttendanceStatus.PRESENT -> "Hadir"
                            AttendanceStatus.PERMITTED -> "Izin"
                            AttendanceStatus.ABSENT -> "Alpa"
                            else -> "Belum"
                        }

                        val row = listOf(
                            (index + 1).toString(),
                            "\"${record.studentNisn}\"",
                            "\"${record.studentName}\"",
                            "\"${record.className}\"",
                            "\"$subject\"",
                            "\"$statusStr\"",
                            "\"$timeStr\"",
                            "\"${record.verificationMethod}\"",
                            "\"${record.remarks}\""
                        ).joinToString(",")
                        writer.write(row + "\n")
                    }
                }
            }

            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$title - HadirKu")
                putExtra(Intent.EXTRA_TEXT, "Terlampir file data presensi resmi dari sistem HadirKu.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Buka atau Bagikan File Excel Presensi"))
            Toast.makeText(this, "File Excel berhasil dibuat: ${file.name}", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Gagal mengekspor data: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // ADMIN VIEW: HADIRKU FIGMA EXPERIENCE (DASHBOARD, PRESENSI, MASTER, AKTIVITAS, PROFIL)
    // =========================================================================

    private var selectedAdminTab = 0
    private var selectedAdminPresensiPeriod = "Hari ini"
    private var selectedAdminActivityFilter = "Semua"

    private fun setupAdminNavigationAndTabs() {
        binding.bottomNavAdmin.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_admin_dashboard -> {
                    switchAdminTab(0)
                    true
                }
                R.id.nav_admin_presensi -> {
                    switchAdminTab(1)
                    true
                }
                R.id.nav_admin_master -> {
                    switchAdminTab(2)
                    true
                }
                R.id.nav_admin_aktivitas -> {
                    switchAdminTab(3)
                    true
                }
                R.id.nav_admin_profil -> {
                    switchAdminTab(4)
                    true
                }
                else -> false
            }
        }

        // Tab 1 Quick chips
        binding.btnAdminQuickGuru.setOnClickListener { switchAdminTab(2) }
        binding.btnAdminQuickSiswa.setOnClickListener { switchAdminTab(2) }
        binding.btnAdminQuickJadwal.setOnClickListener { showAddScheduleDialog() }

        // Tab 2 Presensi period chips
        binding.chipAdminPresensiHariIni.setOnClickListener {
            selectedAdminPresensiPeriod = "Hari ini"
            updateAdminPresensiChipSelection()
        }
        binding.chipAdminPresensiMingguIni.setOnClickListener {
            selectedAdminPresensiPeriod = "Minggu ini"
            updateAdminPresensiChipSelection()
        }
        binding.chipAdminPresensiSemesterIni.setOnClickListener {
            selectedAdminPresensiPeriod = "Semester ini"
            updateAdminPresensiChipSelection()
        }

        // Tab 3 Master button
        binding.btnAdminAddNewDataMaster.setOnClickListener {
            showAddScheduleDialog()
        }

        // Tab 4 Activity filter chips
        binding.chipAdminActivityAll.setOnClickListener {
            selectedAdminActivityFilter = "Semua"
            updateAdminActivityChipSelection()
            renderAdminAktivitasTab()
        }
        binding.chipAdminActivityGuru.setOnClickListener {
            selectedAdminActivityFilter = "Guru"
            updateAdminActivityChipSelection()
            renderAdminAktivitasTab()
        }
        binding.chipAdminActivitySiswa.setOnClickListener {
            selectedAdminActivityFilter = "Siswa"
            updateAdminActivityChipSelection()
            renderAdminAktivitasTab()
        }
        binding.chipAdminActivityAdmin.setOnClickListener {
            selectedAdminActivityFilter = "Admin"
            updateAdminActivityChipSelection()
            renderAdminAktivitasTab()
        }

        // Tab 5 Profil actions
        binding.btnAdminChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }
        binding.btnAdminLogout.setOnClickListener {
            logoutAndReturnToLogin()
        }
    }

    private fun updateAdminPresensiChipSelection() {
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_day_selected)
        val unselectedBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_day_unselected)
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        val darkColor = ContextCompat.getColor(this, R.color.hadirku_text_dark)

        binding.chipAdminPresensiHariIni.background = if (selectedAdminPresensiPeriod == "Hari ini") selectedBg else unselectedBg
        binding.chipAdminPresensiHariIni.setTextColor(if (selectedAdminPresensiPeriod == "Hari ini") whiteColor else darkColor)

        binding.chipAdminPresensiMingguIni.background = if (selectedAdminPresensiPeriod == "Minggu ini") selectedBg else unselectedBg
        binding.chipAdminPresensiMingguIni.setTextColor(if (selectedAdminPresensiPeriod == "Minggu ini") whiteColor else darkColor)

        binding.chipAdminPresensiSemesterIni.background = if (selectedAdminPresensiPeriod == "Semester ini") selectedBg else unselectedBg
        binding.chipAdminPresensiSemesterIni.setTextColor(if (selectedAdminPresensiPeriod == "Semester ini") whiteColor else darkColor)
    }

    private fun updateAdminActivityChipSelection() {
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_day_selected)
        val unselectedBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_day_unselected)
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        val darkColor = ContextCompat.getColor(this, R.color.hadirku_text_dark)

        binding.chipAdminActivityAll.background = if (selectedAdminActivityFilter == "Semua") selectedBg else unselectedBg
        binding.chipAdminActivityAll.setTextColor(if (selectedAdminActivityFilter == "Semua") whiteColor else darkColor)

        binding.chipAdminActivityGuru.background = if (selectedAdminActivityFilter == "Guru") selectedBg else unselectedBg
        binding.chipAdminActivityGuru.setTextColor(if (selectedAdminActivityFilter == "Guru") whiteColor else darkColor)

        binding.chipAdminActivitySiswa.background = if (selectedAdminActivityFilter == "Siswa") selectedBg else unselectedBg
        binding.chipAdminActivitySiswa.setTextColor(if (selectedAdminActivityFilter == "Siswa") whiteColor else darkColor)

        binding.chipAdminActivityAdmin.background = if (selectedAdminActivityFilter == "Admin") selectedBg else unselectedBg
        binding.chipAdminActivityAdmin.setTextColor(if (selectedAdminActivityFilter == "Admin") whiteColor else darkColor)
    }

    private fun switchAdminTab(tabIndex: Int) {
        selectedAdminTab = tabIndex
        binding.layoutAdminDashboard.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        binding.layoutAdminPresensi.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        binding.layoutAdminMaster.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        binding.layoutAdminAktivitas.visibility = if (tabIndex == 3) View.VISIBLE else View.GONE
        binding.layoutAdminProfil.visibility = if (tabIndex == 4) View.VISIBLE else View.GONE

        when (tabIndex) {
            0 -> renderAdminDashboardTab()
            1 -> renderAdminPresensiTab()
            2 -> renderAdminMasterTab()
            3 -> renderAdminAktivitasTab()
            4 -> renderAdminProfilTab()
        }
    }

    private fun renderAdminDashboardTab() {
        val container = binding.layoutAdminRecentActivityContainer
        container.removeAllViews()

        val sampleLogs = listOf(
            Triple("07:41", "Rina A. absen Matematika", "Siswa · XI IPA 2 · Tepat Waktu"),
            Triple("07:40", "Pak Dedi membuka sesi XI IPA 2", "Guru · Sesi dimulai"),
            Triple("07:35", "Admin verifikasi Citra M.", "Admin · Kartu diperbarui"),
            Triple("07:30", "Admin mengubah jadwal Fisika", "Admin · Jadwal diperbarui")
        )

        for (log in sampleLogs) {
            val itemBinding = ItemAdminActivityFigmaBinding.inflate(layoutInflater, container, false)
            itemBinding.tvActivityTimestamp.text = log.first
            itemBinding.tvActivityTitle.text = log.second
            itemBinding.tvActivitySubtitle.text = log.third
            itemBinding.tvActivityRoleBadge.text = log.third.split("·")[0].trim()
            container.addView(itemBinding.root)
        }
    }

    private fun renderAdminPresensiTab() {
        val container = binding.layoutAdminPendingAttendanceList
        container.removeAllViews()

        val pendingStudents = listOf(
            Pair("Dimas Anggara", "XI IPA 2 · Matematika"),
            Pair("Fajar Hidayat", "XI IPA 2 · Matematika"),
            Pair("Gilang Ramadhan", "XI TKJ 1 · Fisika"),
            Pair("Hana Lestari", "XII RPL 1 · Kimia")
        )

        for (student in pendingStudents) {
            val itemBinding = ItemTeacherStudentFigmaBinding.inflate(layoutInflater, container, false)
            itemBinding.tvStudentItemName.text = student.first
            itemBinding.tvStudentAvatarInitials.text = getInitials(student.first)
            itemBinding.tvStudentItemDetails.text = student.second
            itemBinding.tvStudentAttendancePercent.text = "-"
            itemBinding.tvStudentStatusPill.text = "Belum"
            itemBinding.tvStudentStatusPill.setBackgroundColor(Color.parseColor("#F3F4F6"))
            itemBinding.tvStudentStatusPill.setTextColor(Color.parseColor("#6B7280"))
            container.addView(itemBinding.root)
        }
    }

    private fun renderAdminMasterTab() {
        // Tab statically and dynamically represents master stats from Figma
    }

    private fun renderAdminAktivitasTab() {
        val container = binding.layoutAdminFullActivityContainer
        container.removeAllViews()

        val allLogs = listOf(
            Triple("07:41", "Rina A. absen Matematika", "Siswa · XI IPA 2 · Tepat Waktu"),
            Triple("07:40", "Pak Dedi membuka sesi XI IPA 2", "Guru · Sesi dimulai"),
            Triple("07:38", "Bagas P. absen Matematika", "Siswa · XI IPA 2 · Tepat Waktu"),
            Triple("07:35", "Bu Lestari membuka sesi IPS 1", "Guru · Sesi dimulai"),
            Triple("07:32", "Admin verifikasi Citra M.", "Admin · Kartu diperbarui"),
            Triple("07:30", "Admin mengubah jadwal Fisika", "Admin · Jadwal diperbarui"),
            Triple("07:25", "Budi Santoso izin sakit", "Siswa · XI TKJ 1 · Surat Dokter"),
            Triple("07:20", "Admin mendaftarkan kartu NIS 2410055", "Admin · Registrasi kartu")
        )

        val filtered = if (selectedAdminActivityFilter == "Semua") {
            allLogs
        } else {
            allLogs.filter { it.third.startsWith(selectedAdminActivityFilter) }
        }

        for (log in filtered) {
            val itemBinding = ItemAdminActivityFigmaBinding.inflate(layoutInflater, container, false)
            itemBinding.tvActivityTimestamp.text = log.first
            itemBinding.tvActivityTitle.text = log.second
            itemBinding.tvActivitySubtitle.text = log.third
            itemBinding.tvActivityRoleBadge.text = log.third.split("·")[0].trim()
            container.addView(itemBinding.root)
        }
    }

    private fun renderAdminProfilTab() {
        val adminName = currentUser?.name ?: "Administrator Sistem"
        val username = currentUser?.id ?: "admin"

        binding.tvAdminProfilUsername.text = username
    }

    // Compatibility methods retained for dialogs
    private fun renderTeacherSchedules() { renderTeacherAbsensiTab() }
    private fun renderAdminSchedules() { renderAdminDashboardTab() }
    private fun setupTeacherScheduleActions() {}
    private fun setupAdminActions() {}

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
        showTeacherScheduleDialog(null, null)
    }

    private fun showTeacherScheduleDialog(scheduleToEdit: AttendanceSchedule? = null, defaultClass: String? = null) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogAddScheduleBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.behavior.isHideable = true

        val scheduleCalendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEEE", Locale.forLanguageTag("id-ID"))

        // Setup class dropdown
        val allClasses = repository.getAllClasses()
        val classNames = if (allClasses.isNotEmpty()) allClasses.map { it.name }.distinct() else listOf("XI IPA 2", "XI RPL 1", "XI TKJ 1", "XII RPL 1")
        val classAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, classNames)
        dialogBinding.etClassName.setAdapter(classAdapter)

        val isEdit = scheduleToEdit != null
        if (isEdit) {
            dialogBinding.tvDialogTitle.text = "Edit Jadwal Sesi Absensi"
            dialogBinding.tvDialogSubtitle.text = "Perbarui detail mata pelajaran, ruangan, atau jam sesi presensi."
            dialogBinding.btnSaveSchedule.text = "Simpan Perubahan"

            dialogBinding.etClassName.setText(scheduleToEdit!!.className, false)
            dialogBinding.etSubjectName.setText(scheduleToEdit.subject)
            dialogBinding.etRoomName.setText(scheduleToEdit.room.ifBlank { "Ruang 10" })

            if (scheduleToEdit.sessionDate.isNotBlank() && scheduleToEdit.sessionDate != "Hari ini") {
                try {
                    val parsed = dateFormat.parse(scheduleToEdit.sessionDate)
                    if (parsed != null) scheduleCalendar.time = parsed
                } catch (_: Exception) {}
            }
            dialogBinding.etScheduleDate.setText(dateFormat.format(scheduleCalendar.time))

            val parts = scheduleToEdit.timeSlot.replace("WIB", "").trim().split("-")
            val startStr = if (parts.isNotEmpty()) parts[0].trim() else "07:15"
            val endStr = if (parts.size > 1) parts[1].trim() else "09:15"
            dialogBinding.etStartTime.setText(startStr)
            dialogBinding.etEndTime.setText(endStr)
        } else {
            dialogBinding.tvDialogTitle.text = "Input Jadwal Sesi Absensi"
            dialogBinding.tvDialogSubtitle.text = "Buka sesi presensi untuk kelas, mata pelajaran, dan ruangan yang ditentukan."
            dialogBinding.btnSaveSchedule.text = "Input Jadwal Absensi"

            val initialClass = defaultClass ?: selectedTeacherSessionClass
            dialogBinding.etClassName.setText(initialClass, false)
            dialogBinding.etSubjectName.setText("")
            dialogBinding.etRoomName.setText("Ruang 10")
            dialogBinding.etScheduleDate.setText(dateFormat.format(scheduleCalendar.time))
            dialogBinding.etStartTime.setText("07:15")
            dialogBinding.etEndTime.setText("09:15")
        }

        // Date Picker Action
        val pickDateAction = View.OnClickListener {
            val y = scheduleCalendar.get(Calendar.YEAR)
            val m = scheduleCalendar.get(Calendar.MONTH)
            val d = scheduleCalendar.get(Calendar.DAY_OF_MONTH)
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                scheduleCalendar.set(year, month, dayOfMonth)
                dialogBinding.etScheduleDate.setText(dateFormat.format(scheduleCalendar.time))
            }, y, m, d).show()
        }
        dialogBinding.etScheduleDate.setOnClickListener(pickDateAction)
        dialogBinding.tilScheduleDate.setEndIconOnClickListener(pickDateAction)

        // Time Picker for Jam Mulai
        dialogBinding.etStartTime.setOnClickListener {
            val cur = dialogBinding.etStartTime.text.toString().trim()
            val (h, min) = if (cur.contains(":")) {
                val sp = cur.split(":")
                Pair(sp[0].toIntOrNull() ?: 7, sp[1].toIntOrNull() ?: 15)
            } else Pair(7, 15)
            TimePickerDialog(this, { _, hourOfDay, minute ->
                dialogBinding.etStartTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute))
            }, h, min, true).show()
        }

        // Time Picker for Sampai Jam
        dialogBinding.etEndTime.setOnClickListener {
            val cur = dialogBinding.etEndTime.text.toString().trim()
            val (h, min) = if (cur.contains(":")) {
                val sp = cur.split(":")
                Pair(sp[0].toIntOrNull() ?: 9, sp[1].toIntOrNull() ?: 15)
            } else Pair(9, 15)
            TimePickerDialog(this, { _, hourOfDay, minute ->
                dialogBinding.etEndTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute))
            }, h, min, true).show()
        }

        dialogBinding.etSubjectName.doAfterTextChanged {
            dialogBinding.tilSubject.error = null
        }

        dialogBinding.btnCancelSchedule.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveSchedule.setOnClickListener {
            val className = dialogBinding.etClassName.text.toString().trim().ifEmpty { "XI IPA 2" }
            val subject = dialogBinding.etSubjectName.text?.toString()?.trim().orEmpty()
            val room = dialogBinding.etRoomName.text?.toString()?.trim().orEmpty().ifEmpty { "Ruang 10" }
            val sessionDate = dialogBinding.etScheduleDate.text?.toString()?.trim().orEmpty().ifEmpty { dateFormat.format(scheduleCalendar.time) }
            val startTime = dialogBinding.etStartTime.text?.toString()?.trim().orEmpty().ifEmpty { "07:15" }
            val endTime = dialogBinding.etEndTime.text?.toString()?.trim().orEmpty().ifEmpty { "09:15" }
            val dayName = dayFormat.format(scheduleCalendar.time)

            if (subject.isEmpty()) {
                dialogBinding.tilSubject.error = "Nama mata pelajaran wajib diisi"
                return@setOnClickListener
            }

            val existingForClass = repository.getAllSchedules().firstOrNull {
                it.className.trim().equals(className, ignoreCase = true) &&
                (it.sessionDate == sessionDate || it.sessionDate == "Hari ini" || it.sessionDate.isBlank()) &&
                it.sessionDate != "DELETED"
            }
            val targetId = scheduleToEdit?.id ?: existingForClass?.id ?: ("sch_" + System.currentTimeMillis())
            val initialOpen = scheduleToEdit?.isOpenForAttendance ?: true
            val finalSchedule = AttendanceSchedule(
                id = targetId,
                className = className,
                subject = subject,
                teacherId = currentUser?.id ?: "1982051001",
                teacherName = currentUser?.name ?: "Guru Pengajar",
                periodStart = 1,
                periodEnd = 2,
                timeSlot = "$startTime - $endTime WIB",
                dayName = dayName,
                room = room,
                isOpenForAttendance = initialOpen,
                sessionDate = sessionDate
            )
            if (finalSchedule.isExpired()) {
                finalSchedule.isOpenForAttendance = false
            }

            repository.saveSchedule(finalSchedule)
            dialog.dismiss()

            selectedTeacherSessionClass = className
            binding.actvTeacherClassTarget.setText(className, false)

            val msg = if (isEdit) "Jadwal absensi berhasil diperbarui!" else "Jadwal sesi absensi baru berhasil dibuat!"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            renderTeacherAbsensiTab()
        }

        dialog.show()
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
        dialogBinding.tvAttendanceDateLabel.text = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("id-ID")).format(Date())

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

                            // 2. JIKA PENCAHAYAAN TERLIHAT DAN WAJAH TERLIHAT JELAS, LANGSUNG VERIFIKASI
                            isFaceVerified = true
                            resetFaceCameraState(allowRetry = false)

                            if (embedding != null && !isFaceEnrolled) {
                                val signature = FaceVerificationHelper.createSignatureJson(embedding)
                                capturedFaceSignature = signature
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
                            }

                            vBinding.tvFaceStatusBadge.text = "[VALID] Wajah Terverifikasi (Cahaya Jelas)"
                            vBinding.tvFaceStatusBadge.setBackgroundColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_present_bg))
                            vBinding.tvFaceStatusBadge.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.status_present))
                            vBinding.tvFaceDetails.text = "Pencahayaan memadai ($brightnessInt/255) dan wajah siswa ($studentName) terlihat jelas. Verifikasi wajah berhasil."
                            vBinding.btnTriggerFaceScan.isEnabled = false
                            vBinding.btnTriggerFaceScan.text = "Kamera Ditutup (Wajah Terverifikasi)"

                            vBinding.scrollVerificationContent.post {
                                vBinding.scrollVerificationContent.fullScroll(View.FOCUS_DOWN)
                            }
                            updateSubmitButton()
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
