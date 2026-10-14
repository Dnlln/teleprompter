package com.teleprompter.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.teleprompter.app.databinding.ActivityMainBinding
import com.teleprompter.app.databinding.DialogSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var prompterController: TeleprompterController
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    // Explicit Front Camera state (Default = TRUE)
    private var isUsingFrontCamera: Boolean = true

    // Default Zoom 1.2x on front camera removes wide-angle "fish-eye" facial distortion
    private var currentZoomRatio: Float = 1.2f

    // Prompter Visual Appearance (Adjustable in Settings)
    private var prompterOpacity: Float = 0.85f
    private var prompterHeightDp: Int = 225

    // Screen Backlight Frame (White 1/5 screen width border fill light)
    private var isBacklightEnabled: Boolean = false

    // File Picker for .docx
    private val openDocxLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            loadDocxFromUri(uri)
        }
    }

    // Permissions Launcher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true

        if (cameraGranted && audioGranted) {
            startCamera()
        } else {
            Toast.makeText(
                this,
                "Camera and Microphone permissions are required to record video",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make activity fullscreen & draw into notch / cutout area
        setupWindowCutout()

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Compatible texture view mode for reliable front camera rendering
        binding.viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        initPinchToZoom()
        initTeleprompter()
        setupListeners()
        updateCameraModeLabel()

        applyPrompterOpacity(prompterOpacity)
        applyPrompterHeight(prompterHeightDp)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionsLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun setupWindowCutout() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Keep the exact same vertical position for prompterContainer
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val cutoutHeight = insets.displayCutout?.safeInsetTop ?: 0
            val topOffset = maxOf(statusBarHeight, cutoutHeight)

            val density = resources.displayMetrics.density
            val horizontalPadding = (12 * density).toInt()
            val bottomPadding = (6 * density).toInt()
            val topPadding = topOffset + (4 * density).toInt()

            binding.prompterContainer.setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding)
            insets
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initPinchToZoom() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cam = camera ?: return false
                val zoomState = cam.cameraInfo.zoomState.value ?: return false
                val newRatio = (zoomState.zoomRatio * detector.scaleFactor).coerceIn(
                    zoomState.minZoomRatio,
                    zoomState.maxZoomRatio
                )
                currentZoomRatio = newRatio
                cam.cameraControl.setZoomRatio(newRatio)
                updateZoomLabel(newRatio)
                return true
            }
        })

        binding.viewFinder.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun initTeleprompter() {
        prompterController = TeleprompterController(
            scrollView = binding.prompterScrollView,
            textView = binding.prompterTextView
        ) { isPlaying, speed ->
            updatePrompterUi(isPlaying, speed)
        }

        // Preload default welcome text in English
        val defaultLines = listOf(
            "👋 Welcome to Teleprompter PRO!",
            "This text is docked right at the top of the screen, directly beneath your front camera lens.",
            "Reading here ensures your eyes remain focused straight at your viewers for natural eye contact.",
            "Tap 'DOCX' below to import your Microsoft Word document script.",
            "Use 'Slower' and 'Faster' buttons to adjust the scrolling speed to your pace.",
            "Tap the red Record button to start recording video and audio while scrolling the text.",
            "Enjoy your recording!"
        )
        prompterController.setText(defaultLines)
    }

    private fun setupListeners() {
        // Play / Pause Prompter
        binding.btnPlayPause.setOnClickListener {
            prompterController.togglePlayPause()
        }

        // Tap directly on prompter text to toggle play/pause
        binding.prompterScrollView.setOnClickListener {
            prompterController.togglePlayPause()
        }

        // Reset scroll to top
        binding.btnResetScroll.setOnClickListener {
            prompterController.resetToTop()
            Toast.makeText(this, "Rewound to start", Toast.LENGTH_SHORT).show()
        }

        // Slower and Faster buttons
        binding.btnSpeedMinus.setOnClickListener {
            prompterController.decreaseSpeed(0.5f)
        }
        binding.btnSpeedPlus.setOnClickListener {
            prompterController.increaseSpeed(0.5f)
        }

        // DOCX File Picker
        binding.btnImportDocx.setOnClickListener {
            try {
                openDocxLauncher.launch(
                    arrayOf(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/msword",
                        "application/octet-stream",
                        "*/*"
                    )
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening file picker: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Video Recording Button
        binding.btnRecord.setOnClickListener {
            toggleVideoRecording()
        }

        // Camera Switch Chip (Front / Back)
        binding.btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        // Zoom / Fish-eye removal toggle chip
        binding.btnZoomToggle.setOnClickListener {
            toggleZoomMode()
        }

        // Settings Dialog
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun updatePrompterUi(isPlaying: Boolean, speed: Float) {
        if (isPlaying) {
            binding.icPlayPause.setImageResource(R.drawable.ic_pause)
            binding.tvPlayPauseLabel.text = getString(R.string.pause)
            binding.btnPlayPause.setBackgroundResource(R.drawable.bg_hud_btn_active)
        } else {
            binding.icPlayPause.setImageResource(R.drawable.ic_play)
            binding.tvPlayPauseLabel.text = getString(R.string.play)
            binding.btnPlayPause.setBackgroundResource(R.drawable.bg_hud_btn)
        }
        binding.tvSpeedBadge.text = String.format(Locale.US, "%.1fx", speed)
    }

    private fun applyPrompterOpacity(opacity: Float) {
        prompterOpacity = opacity.coerceIn(0.1f, 1.0f)
        binding.prompterContainer.background?.mutate()?.alpha = (prompterOpacity * 255).toInt()
    }

    private fun applyPrompterHeight(heightDp: Int) {
        prompterHeightDp = heightDp.coerceIn(120, 450)
        val heightPx = (prompterHeightDp * resources.displayMetrics.density).toInt()
        val params = binding.prompterContainer.layoutParams
        params.height = heightPx
        binding.prompterContainer.layoutParams = params
    }

    private fun setBacklightEnabled(enabled: Boolean) {
        isBacklightEnabled = enabled
        binding.backlightOverlay.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            updateBacklightDimensions()
        }
        val lp = window.attributes
        lp.screenBrightness = if (enabled) 1.0f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    private fun updateBacklightDimensions() {
        val screenWidth = resources.displayMetrics.widthPixels
        val borderWidth = (screenWidth * 0.20f).toInt() // Exactly 1/5 of the screen width!
        binding.backlightTop.layoutParams.height = borderWidth
        binding.backlightBottom.layoutParams.height = borderWidth
        binding.backlightLeft.layoutParams.width = borderWidth
        binding.backlightRight.layoutParams.width = borderWidth
        binding.backlightOverlay.requestLayout()
    }

    private fun updateCameraModeLabel() {
        if (isUsingFrontCamera) {
            binding.tvCameraModeLabel.text = getString(R.string.front_camera)
            binding.tvCameraModeLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan))
        } else {
            binding.tvCameraModeLabel.text = getString(R.string.back_camera)
            binding.tvCameraModeLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
        }
    }

    private fun toggleZoomMode() {
        currentZoomRatio = when {
            currentZoomRatio < 1.15f -> 1.2f  // 1.2x: Natural portrait, fish-eye removed!
            currentZoomRatio < 1.35f -> 1.5f  // 1.5x: Medium close-up
            else -> 1.0f                      // 1.0x: Full wide
        }
        applyCurrentZoom()

        val label = when (currentZoomRatio) {
            1.2f -> "1.2x (Portrait, natural)"
            1.5f -> "1.5x (Close-up)"
            else -> "1.0x (Wide angle)"
        }
        Toast.makeText(this, "Zoom: $label", Toast.LENGTH_SHORT).show()
    }

    private fun applyCurrentZoom() {
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value ?: return
        val clampedRatio = currentZoomRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clampedRatio)
        updateZoomLabel(clampedRatio)
    }

    private fun updateZoomLabel(ratio: Float) {
        val text = when {
            ratio >= 1.45f -> "1.5x Close-up"
            ratio >= 1.15f -> "1.2x Portrait"
            else -> "1.0x Wide"
        }
        binding.tvZoomLabel.text = text
    }

    /* ==========================================================================
       CameraX Setup & Video Recording
       ========================================================================== */
    private fun getSelectedCameraSelector(): CameraSelector {
        val targetFacing = if (isUsingFrontCamera) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

        val selector = CameraSelector.Builder().requireLensFacing(targetFacing).build()

        val provider = cameraProvider
        if (provider != null && !provider.hasCamera(selector)) {
            return if (isUsingFrontCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
        }

        return selector
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // 16:9 target aspect ratio avoids warping/stretching
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build().also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val selector = getSelectedCameraSelector()

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    videoCapture
                )

                // Apply initial zoom (1.2x portrait zoom removes front camera fish-eye)
                binding.viewFinder.post {
                    applyCurrentZoom()
                }

                updateCameraModeLabel()
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera error: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {
        if (recording != null) {
            Toast.makeText(this, "Cannot switch camera during recording", Toast.LENGTH_SHORT).show()
            return
        }

        isUsingFrontCamera = !isUsingFrontCamera
        // When switching to front camera, default to 1.2x portrait zoom to kill fish-eye
        currentZoomRatio = if (isUsingFrontCamera) 1.2f else 1.0f
        updateCameraModeLabel()
        startCamera()

        val msg = if (isUsingFrontCamera) "Front camera enabled" else "Back camera enabled"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun toggleVideoRecording() {
        val currentRecording = recording
        if (currentRecording != null) {
            // Stop recording
            currentRecording.stop()
            recording = null
            return
        }

        val capture = videoCapture ?: return

        val fileName = "Teleprompter_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Teleprompter")
            }
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        recording = capture.output
            .prepareRecording(this, mediaStoreOutput)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        // 1. Show floating recording badge and timer
                        binding.recBadgeFloating.visibility = View.VISIBLE
                        binding.recChronometerFloating.base = SystemClock.elapsedRealtime()
                        binding.recChronometerFloating.start()

                        // 2. Clean single-layer record button update
                        binding.ivRecordCircle.setBackgroundResource(R.drawable.bg_record_btn_recording)
                        binding.tvRecordLabel.text = getString(R.string.stop)

                        // 3. Auto-start teleprompter when recording begins
                        if (!prompterController.isPlaying) {
                            prompterController.start()
                        }
                        Toast.makeText(this, "Video recording started", Toast.LENGTH_SHORT).show()
                    }
                    is VideoRecordEvent.Finalize -> {
                        // 1. Hide recording badge and timer
                        binding.recBadgeFloating.visibility = View.GONE
                        binding.recChronometerFloating.stop()

                        // 2. Reset record button
                        binding.ivRecordCircle.setBackgroundResource(R.drawable.bg_record_btn)
                        binding.tvRecordLabel.text = getString(R.string.record)

                        // 3. Pause prompter
                        if (prompterController.isPlaying) {
                            prompterController.pause()
                        }

                        if (!recordEvent.hasError()) {
                            val msg = "Video saved to gallery (Movies/Teleprompter)"
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        } else {
                            recording?.close()
                            recording = null
                            Toast.makeText(this, "Recording error: ${recordEvent.cause?.message}", Toast.LENGTH_SHORT).show()
                        }
                        recording = null
                    }
                }
            }
    }

    /* ==========================================================================
       DOCX File Loading
       ========================================================================== */
    private fun loadDocxFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = DocxReader.readDocxFromUri(this@MainActivity, uri)
            withContext(Dispatchers.Main) {
                result.onSuccess { paragraphs ->
                    prompterController.setText(paragraphs)
                    Toast.makeText(this@MainActivity, "DOCX loaded successfully!", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(
                        this@MainActivity,
                        "Error reading DOCX: ${error.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /* ==========================================================================
       Settings Bottom Sheet Dialog
       ========================================================================== */
    private fun showSettingsDialog() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val dialogBinding = DialogSettingsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Speed SeekBar (1 to 10)
        dialogBinding.speedSeekBar.max = 90
        dialogBinding.speedSeekBar.progress = ((prompterController.speed - 1f) * 10).toInt()
        dialogBinding.tvSpeedValue.text = String.format(Locale.US, "%.1fx", prompterController.speed)
        dialogBinding.speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newSpeed = 1f + (progress / 10f)
                prompterController.speed = newSpeed
                dialogBinding.tvSpeedValue.text = String.format(Locale.US, "%.1fx", newSpeed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Font Size SeekBar (14sp to 36sp)
        dialogBinding.fontSizeSeekBar.max = 22
        dialogBinding.fontSizeSeekBar.progress = (prompterController.fontSizeSp - 14f).toInt()
        dialogBinding.tvFontSizeValue.text = "${prompterController.fontSizeSp.toInt()} sp"
        dialogBinding.fontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newSize = 14f + progress
                prompterController.fontSizeSp = newSize
                dialogBinding.tvFontSizeValue.text = "${newSize.toInt()} sp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Window Opacity SeekBar (10% to 100%)
        dialogBinding.opacitySeekBar.max = 90
        dialogBinding.opacitySeekBar.progress = ((prompterOpacity - 0.1f) * 100).toInt()
        dialogBinding.tvOpacityValue.text = "${(prompterOpacity * 100).toInt()}%"
        dialogBinding.opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newOpacity = 0.1f + (progress / 100f)
                applyPrompterOpacity(newOpacity)
                dialogBinding.tvOpacityValue.text = "${(newOpacity * 100).toInt()}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Prompter Height SeekBar (140dp to 380dp - Expands Downwards)
        dialogBinding.heightSeekBar.max = 240
        dialogBinding.heightSeekBar.progress = prompterHeightDp - 140
        dialogBinding.tvHeightValue.text = "$prompterHeightDp dp"
        dialogBinding.heightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newHeight = 140 + progress
                applyPrompterHeight(newHeight)
                dialogBinding.tvHeightValue.text = "$newHeight dp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Screen Backlight Frame Switch (1/5 screen width fill light)
        dialogBinding.switchBacklight.isChecked = isBacklightEnabled
        dialogBinding.switchBacklight.setOnCheckedChangeListener { _, isChecked ->
            setBacklightEnabled(isChecked)
        }

        // Apply manual text
        dialogBinding.btnApplyText.setOnClickListener {
            val text = dialogBinding.etManualText.text.toString().trim()
            if (text.isNotEmpty()) {
                val lines = text.split("\n").filter { it.isNotBlank() }
                prompterController.setText(lines)
                dialog.dismiss()
                Toast.makeText(this, "Script updated", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        prompterController.destroy()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
}
