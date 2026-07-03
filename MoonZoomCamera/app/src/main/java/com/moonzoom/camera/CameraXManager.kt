package com.moonzoom.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Wraps CameraX setup.
 *
 * Key design decision: rather than manually enumerating and switching between the
 * ultra-wide / wide / telephoto physical cameras, we bind ONE logical CameraSelector
 * (DEFAULT_BACK_CAMERA) and drive zoom purely through Camera.cameraControl.setZoomRatio().
 *
 * On Samsung S21-and-up (and most flagships since ~2020), the back camera is exposed
 * to Camera2/CameraX as a single LOGICAL_MULTI_CAMERA. The camera HAL automatically
 * hands off between the ultra-wide, wide, and tele sensors as the requested zoom ratio
 * crosses their respective thresholds -- which is exactly how Samsung's own Camera app
 * achieves "seamless" zoom. This is more reliable than manually targeting physical
 * camera IDs, which vary by OEM and aren't guaranteed to be exposed at all.
 */
class CameraXManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    var onZoomRangeReady: ((min: Float, max: Float) -> Unit)? = null

    fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bind(cameraProvider)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bind(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        cameraProvider.unbindAll()
        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture
        )

        camera?.cameraInfo?.zoomState?.value?.let { zoomState ->
            onZoomRangeReady?.invoke(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        }
    }

    fun toggleLensFacing() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            bind(cameraProviderFuture.get())
        }, ContextCompat.getMainExecutor(context))
    }

    /** Clamp the requested ratio to what this device's active camera actually supports. */
    fun setZoomRatio(requestedRatio: Float) {
        val zoomState = camera?.cameraInfo?.zoomState?.value ?: return
        val clamped = requestedRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    fun currentZoomRatio(): Float =
        camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

    fun zoomRange(): Pair<Float, Float> {
        val zs = camera?.cameraInfo?.zoomState?.value
        return Pair(zs?.minZoomRatio ?: 1f, zs?.maxZoomRatio ?: 1f)
    }

    fun capturePhoto(onSaved: (File) -> Unit, onError: (ImageCaptureException) -> Unit) {
        val capture = imageCapture ?: return
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val photoFile = File(outputDir, "MOONZOOM_$name.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onSaved(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXManager", "Capture failed", exception)
                    onError(exception)
                }
            }
        )
    }
}
