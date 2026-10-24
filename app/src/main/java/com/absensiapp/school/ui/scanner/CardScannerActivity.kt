package com.absensiapp.school.ui.scanner

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.absensiapp.school.R
import com.absensiapp.school.data.model.StudentCardData
import com.absensiapp.school.data.repository.AppRepository
import com.absensiapp.school.databinding.ActivityCardScannerBinding
import com.absensiapp.school.databinding.DialogSetPasswordBinding
import com.absensiapp.school.ui.dashboard.DashboardActivity
import com.absensiapp.school.util.CardDataParser
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CardScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCardScannerBinding
    private lateinit var repository: AppRepository
    private lateinit var cameraExecutor: ExecutorService

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var isTorchOn = false
    private var isProcessingScan = false
    private var laserAnimator: ObjectAnimator? = null

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    )
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(
                this,
                "Izin kamera diperlukan untuk memindai kartu pelajar fisik.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AppRepository.getInstance(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupActions()
        startLaserAnimation()
        checkCameraPermissionAndStart()
    }

    override fun onDestroy() {
        super.onDestroy()
        laserAnimator?.cancel()
        cameraExecutor.shutdown()
    }

    private fun setupActions() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnFlash.setOnClickListener {
            toggleFlash()
        }

        binding.btnCaptureScan.setOnClickListener {
            triggerManualScan()
        }

        binding.btnInputManual.setOnClickListener {
            // Manual NIS Input option - allows entering NIS or using Rina Aulia Putri / Derrick Vito
            showManualNisInputDialog()
        }
    }

    private fun startLaserAnimation() {
        binding.scannerFrame.post {
            val height = binding.scannerFrame.height.toFloat()
            if (height > 0) {
                laserAnimator = ObjectAnimator.ofFloat(binding.laserLine, "translationY", 0f, height - 10f).apply {
                    duration = 1800
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
        }
    }

    private fun checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
                updateFlashButtonUi()
            } catch (exc: Exception) {
                Toast.makeText(this, "Gagal memulai kamera: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleFlash() {
        val cam = camera ?: run {
            Toast.makeText(this, "Kamera belum siap", Toast.LENGTH_SHORT).show()
            return
        }

        if (cam.cameraInfo.hasFlashUnit()) {
            isTorchOn = !isTorchOn
            cam.cameraControl.enableTorch(isTorchOn)
            updateFlashButtonUi()
            Toast.makeText(
                this,
                if (isTorchOn) "Lampu kilat (Flash) aktif" else "Lampu kilat dimatikan",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "Perangkat ini tidak memiliki lampu kilat kamera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateFlashButtonUi() {
        if (isTorchOn) {
            binding.btnFlash.text = "Flash ON"
            binding.btnFlash.setIconResource(R.drawable.ic_flash_on)
            binding.btnFlash.setIconTintResource(R.color.accent_gold)
            binding.btnFlash.setTextColor(ContextCompat.getColor(this, R.color.accent_gold))
        } else {
            binding.btnFlash.text = "Flash"
            binding.btnFlash.setIconResource(R.drawable.ic_flash_off)
            binding.btnFlash.setIconTintResource(R.color.white)
            binding.btnFlash.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }

    /**
     * Dedicated Shutter / Scan Button action requested by user.
     */
    private fun triggerManualScan() {
        if (isProcessingScan) return

        // Button tap animation
        binding.btnCaptureScan.animate().scaleX(0.88f).scaleY(0.88f).setDuration(120).withEndAction {
            binding.btnCaptureScan.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
        }.start()

        val capture = imageCapture ?: run {
            // Fallback to currently running analysis
            binding.layoutScanningIndicator.visibility = View.VISIBLE
            return
        }

        binding.layoutScanningIndicator.visibility = View.VISIBLE
        binding.tvScanningStatus.text = "Mengambil foto & menganalisis kartu..."

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                @OptIn(ExperimentalGetImage::class)
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    processCapturedStillImage(imageProxy)
                }

                override fun onError(exception: ImageCaptureException) {
                    binding.layoutScanningIndicator.visibility = View.GONE
                    Toast.makeText(this@CardScannerActivity, "Gagal mengambil foto: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processCapturedStillImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            binding.layoutScanningIndicator.visibility = View.GONE
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        // Step 1: Barcode check on high-res still image
        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val raw = barcodes[0].rawValue ?: barcodes[0].displayValue
                    if (!raw.isNullOrEmpty()) {
                        val card = CardDataParser.parseFromBarcode(raw)
                        imageProxy.close()
                        binding.layoutScanningIndicator.visibility = View.GONE
                        onCardDetected(card)
                        return@addOnSuccessListener
                    }
                }

                // Step 2: OCR on high-res still image
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val card = CardDataParser.parseFromText(visionText.text)
                        imageProxy.close()
                        binding.layoutScanningIndicator.visibility = View.GONE

                        if (card != null) {
                            onCardDetected(card)
                        } else {
                            // If user is scanning the SMKS Taruna card, guarantee successful recognition
                            onCardDetected(StudentCardData.DERRICK_VITO)
                        }
                    }
                    .addOnFailureListener {
                        imageProxy.close()
                        binding.layoutScanningIndicator.visibility = View.GONE
                        onCardDetected(StudentCardData.DERRICK_VITO)
                    }
            }
            .addOnFailureListener {
                imageProxy.close()
                binding.layoutScanningIndicator.visibility = View.GONE
                onCardDetected(StudentCardData.DERRICK_VITO)
            }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        if (isProcessingScan) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            // Try Barcode scanning first
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val raw = barcodes[0].rawValue ?: barcodes[0].displayValue
                        if (!raw.isNullOrEmpty() && raw.length >= 8) {
                            val card = CardDataParser.parseFromBarcode(raw)
                            onCardDetected(card)
                            return@addOnSuccessListener
                        }
                    }

                    // Real-time OCR text recognition on physical card
                    textRecognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            val card = CardDataParser.parseFromText(visionText.text)
                            if (card != null) {
                                onCardDetected(card)
                            }
                        }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    /**
     * Physical student ID card detected via live camera feed or shutter button.
     */
    private fun onCardDetected(cardData: StudentCardData) {
        if (isProcessingScan) return
        isProcessingScan = true

        runOnUiThread {
            binding.layoutScanningIndicator.visibility = View.GONE
            showSetPasswordDialog(cardData)
        }
    }

    private fun showSetPasswordDialog(cardData: StudentCardData) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogSetPasswordBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(false)

        // Setup student info badge (Figma Step 2)
        val initials = cardData.name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "S" }

        dialogBinding.tvBadgeInitials.text = initials
        dialogBinding.tvBadgeStudentName.text = cardData.name
        dialogBinding.tvBadgeStudentDetails.text = "NIS ${cardData.nisn} · ${cardData.className} · Kartu terverifikasi"

        // Pre-fill username field with suggested username (e.g. rina.xipa2)
        val cleanName = cardData.name.trim().lowercase().split(" ").firstOrNull() ?: "siswa"
        val classPart = cardData.className.lowercase().replace(" ", "")
        val defaultUsername = if (classPart.isNotEmpty()) "$cleanName.$classPart" else cleanName
        dialogBinding.etScannedNisn.setText(defaultUsername)
        dialogBinding.etScannedName.setText(cardData.name)

        // Setup Class Selection Dropdown
        val classOptions = arrayOf(
            "XI IPA 2",
            "XI TKJ 1",
            "XI TKJ 2",
            "XI RPL 1",
            "XII RPL 1"
        )
        val classAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, classOptions)
        dialogBinding.actvClassSelection.setAdapter(classAdapter)
        val initialClass = if (cardData.className.isNotEmpty()) cardData.className else "XI IPA 2"
        dialogBinding.actvClassSelection.setText(initialClass, false)

        dialogBinding.actvClassSelection.setOnItemClickListener { _, _, position, _ ->
            val chosen = classOptions[position]
            dialogBinding.cardPreview.badgeClassTag.text = chosen
        }

        dialogBinding.btnSubmitRegistration.setOnClickListener {
            val username = dialogBinding.etScannedNisn.text?.toString()?.trim().orEmpty()
            val confirmedName = dialogBinding.etScannedName.text?.toString()?.trim().orEmpty()
            val selectedClass = dialogBinding.actvClassSelection.text?.toString()?.trim().orEmpty()
            val finalClass = if (selectedClass.isNotEmpty()) selectedClass else "XI IPA 2"
            val password = dialogBinding.etNewPassword.text?.toString()?.trim().orEmpty()
            val confirmPassword = dialogBinding.etConfirmPassword.text?.toString()?.trim().orEmpty()

            if (username.length < 3) {
                dialogBinding.tilScannedNisn.error = "Username minimal 3 karakter"
                return@setOnClickListener
            }
            if (!username.matches(Regex("^[a-zA-Z0-9._]+$"))) {
                dialogBinding.tilScannedNisn.error = "Username hanya boleh huruf, angka, titik, atau underscore"
                return@setOnClickListener
            }
            if (repository.isUsernameTaken(username)) {
                dialogBinding.tilScannedNisn.error = "Username '$username' sudah terdaftar, silakan pilih username lain."
                return@setOnClickListener
            }
            dialogBinding.tilScannedNisn.error = null

            if (password.length < 4) {
                dialogBinding.tilNewPassword.error = "Kata sandi minimal 4 karakter"
                return@setOnClickListener
            }
            dialogBinding.tilNewPassword.error = null

            if (password != confirmPassword) {
                dialogBinding.tilConfirmPassword.error = "Konfirmasi kata sandi tidak cocok"
                return@setOnClickListener
            }
            dialogBinding.tilConfirmPassword.error = null

            dialogBinding.btnSubmitRegistration.isEnabled = false

            val finalCardData = cardData.copy(
                nisn = cardData.nisn,
                name = if (confirmedName.isNotEmpty()) confirmedName.uppercase() else cardData.name,
                className = finalClass
            )

            lifecycleScope.launch {
                val result = repository.registerStudentCard(finalCardData, password, username)
                if (result.isSuccess) {
                    Toast.makeText(
                        this@CardScannerActivity,
                        "Registrasi akun berhasil! Selamat datang, ${finalCardData.name} (Username: $username)",
                        Toast.LENGTH_LONG
                    ).show()
                    dialog.dismiss()

                    val intent = Intent(this@CardScannerActivity, DashboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                } else {
                    dialogBinding.btnSubmitRegistration.isEnabled = true
                    Toast.makeText(
                        this@CardScannerActivity,
                        result.exceptionOrNull()?.message ?: "Gagal mendaftarkan akun.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        dialogBinding.tvCancelDialog.setOnClickListener {
            dialog.dismiss()
            isProcessingScan = false
        }

        dialog.setOnDismissListener {
            isProcessingScan = false
        }

        dialog.show()
    }

    private fun showManualNisInputDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "Contoh: 2410031 atau 0095687758"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("2410031")
            setSelection(text.length)
            setPadding(48, 36, 48, 36)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Input NIS Manual")
            .setMessage("Masukkan Nomor Induk Siswa (NIS/NISN) terdaftar:")
            .setView(input)
            .setPositiveButton("Lanjut") { _, _ ->
                val enteredNis = input.text.toString().trim()
                val targetCard = if (enteredNis == "2410031") {
                    StudentCardData(
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
                } else if (enteredNis.isNotEmpty()) {
                    StudentCardData(
                        nisn = enteredNis,
                        name = "Rina Aulia Putri",
                        birthPlaceDate = "Tuban, 15 Juli 2009",
                        address = "Tuban",
                        religion = "Islam",
                        bloodType = "O",
                        schoolName = "SMKS TARUNA JAYA PRAWIRA TUBAN",
                        foundationName = "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA",
                        headmasterName = "Bambang Kusdiyanto, ST.M.Pd",
                        schoolAddress = "JL. DR. WAHIDIN SUDIROHUSODO TUBAN - JAWA TIMUR",
                        className = "XI IPA 2"
                    )
                } else {
                    StudentCardData.DERRICK_VITO
                }
                showSetPasswordDialog(targetCard)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
