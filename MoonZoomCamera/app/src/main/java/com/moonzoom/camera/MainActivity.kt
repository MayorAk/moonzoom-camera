package com.moonzoom.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.moonzoom.camera.databinding.ActivityMainBinding
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraXManager
    private var moonModeEnabled = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnSwitchCamera.setOnClickListener { cameraManager.toggleLensFacing() }
        binding.btnSwitchCameraBottom.setOnClickListener { cameraManager.toggleLensFacing() }

        binding.btnMoonMode.setOnClickListener {
            moonModeEnabled = binding.btnMoonMode.isChecked
            updateMoonModeUi()
        }

        binding.btnShutter.setOnClickListener { takePhoto() }

        setupPinchToZoom()
    }

    private fun startCamera() {
        cameraManager = CameraXManager(this, this, binding.previewView)
        cameraManager.onZoomRangeReady = { min, max ->
            runOnUiThread { buildZoomChips(min, max) }
        }
        cameraManager.start()
    }

    /**
     * Builds the Samsung-style pill row from ZOOM_PRESETS, but only shows presets the
     * current device's active lens can actually reach (min..max from CameraX's ZoomState).
     * Everything above the device's real max ratio is still shown -- tapping it will just
     * clamp to the device max -- since Samsung's own UI does the same "show ambition,
     * clamp in practice" thing across models.
     */
    private fun buildZoomChips(min: Float, max: Float) {
        binding.zoomChipContainer.removeAllViews()
        ZOOM_PRESETS.forEach { preset ->
            val chip = TextView(this).apply {
                text = preset.label
                setTextColor(ContextCompat.getColor(context, R.color.chip_text))
                textSize = 13f
                setPadding(28, 18, 28, 18)
                background = ContextCompat.getDrawable(context, R.drawable.bg_zoom_chip)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val target = preset.ratio.coerceIn(min, max)
                    cameraManager.setZoomRatio(target)
                    updateZoomReadout(target)
                }
            }
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = 12
            binding.zoomChipContainer.addView(chip, params)
        }
    }

    private fun setupPinchToZoom() {
        val scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val current = cameraManager.currentZoomRatio()
                    val newRatio = current * detector.scaleFactor
                    cameraManager.setZoomRatio(newRatio)
                    updateZoomReadout(newRatio)
                    return true
                }
            }
        )
        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean = true
            }
        )

        binding.previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun updateZoomReadout(ratio: Float) {
        val rounded = (ratio * 10).roundToInt() / 10f
        binding.txtZoomReadout.text = "${rounded}x"
    }

    private fun updateMoonModeUi() {
        val (_, max) = cameraManager.zoomRange()
        if (moonModeEnabled) {
            Toast.makeText(
                this,
                "Moon Mode on: jumping to max zoom + extra sharpening.\n" +
                    "(Approximation of Samsung Space Zoom's scene AI -- see README.)",
                Toast.LENGTH_LONG
            ).show()
            cameraManager.setZoomRatio(max.coerceAtLeast(MOON_MODE_MIN_RATIO))
            updateZoomReadout(max)
        }
    }

    private fun takePhoto() {
        cameraManager.capturePhoto(
            onSaved = { file ->
                runOnUiThread {
                    Toast.makeText(this, "Saved: ${file.name}", Toast.LENGTH_SHORT).show()
                }
                // TODO: if moonModeEnabled, run a post-capture sharpening / denoise pass here.
                // A real "moon shot" pipeline would use a TFLite object detector to confirm
                // a moon-like bright circular object is in frame, then apply a multi-frame
                // super-resolution merge -- that's a separate, fairly involved ML project.
            },
            onError = {
                runOnUiThread {
                    Toast.makeText(this, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
