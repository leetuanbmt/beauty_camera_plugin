package com.beauty.camera_plugin

import android.annotation.SuppressLint
import android.content.Context
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.util.Log
import android.util.Size
import com.beauty.camera_plugin.models.CameraSettings
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture as VideoCaptureX

@SuppressLint("RestrictedApi")
class CameraHandler(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val settings: CameraSettings
) {
    companion object {
        private const val TAG = "CameraHandler"
    }
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCaptureX<Recorder>? = null

    fun initialize(callback: () -> Unit) {
        Log.d(TAG, "Initializing camera")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            Log.d(TAG, "CameraProvider obtained")
            cameraProvider = cameraProviderFuture.get()
            callback()
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("RestrictedApi")
    fun startCamera(surface: Surface) {
        setupUseCases(surface)
        bindToLifecycle(lifecycleOwner)
    }

    @SuppressLint("RestrictedApi")
    fun getPreviewSize(): PreviewSize? {
        val resolution = preview?.resolutionInfo?.resolution ?: run {
            Log.d(TAG, "getPreviewSize: Resolution info is null")
            return null
        }
        Log.d(TAG, "getPreviewSize: $resolution")
        return PreviewSize(width = resolution.width.toLong(), height = resolution.height.toLong())
    }

    fun dispose() {
        Log.d(TAG, "Disposing camera handler")
        cameraProvider?.unbindAll()
        preview = null
        camera = null
        imageCapture = null
        videoCapture = null
    }

    private fun bindToLifecycle(lifecycleOwner: LifecycleOwner) {
        Log.d(TAG, "Binding camera to lifecycle")
        cameraProvider?.unbindAll()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val useCases = mutableListOf<UseCase>()
        preview?.let { useCases.add(it) }
        imageCapture?.let { useCases.add(it) }
        videoCapture?.let { useCases.add(it) }

        camera = cameraProvider?.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            *useCases.toTypedArray()
        )
        camera?.cameraControl?.setZoomRatio(settings.zoom.toFloat())
        Log.d(TAG, "Camera bound to lifecycle")
    }

    private fun setupUseCases(surface: Surface) {
        Log.d(TAG, "Setting up use cases")

        // Ánh xạ videoQuality từ settings
        val targetResolution = settings.resolution
        val videoQuality = when (settings.videoQuality) { // Giả định videoQuality là enum
            VideoQuality.LOW -> Quality.SD
            VideoQuality.MEDIUM -> Quality.HD
            VideoQuality.HIGH -> Quality.FHD
            else -> Quality.HIGHEST
        }

        // Cấu hình Preview
        preview = Preview.Builder()
            .setTargetRotation(settings.displayOrientation)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            settings.resolution,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                        )
                    )
                    .build()
            ).build()
            .also { preview ->
                preview.setSurfaceProvider { request ->
                    request.provideSurface(
                        surface,
                        ContextCompat.getMainExecutor(context)
                    ) { result ->
                        when (result.resultCode) {
                            SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY -> {
                                Log.d(TAG, "Surface provided successfully")
                                preview.resolutionInfo?.let { info ->
                                    Log.d(TAG, """
                                        Preview configured:
                                        - Resolution: ${info.resolution.width}x${info.resolution.height}
                                        - Crop rect: ${info.cropRect}
                                        - Rotation: ${info.rotationDegrees}°
                                        - Target resolution: ${targetResolution.width}x${targetResolution.height}
                                        - Front camera: ${isFrontCamera()}
                                    """.trimIndent())
                                }
                            }
                            else -> Log.w(TAG, "Surface request failed: ${result.resultCode}")
                        }
                    }
                }
            }

        // Cấu hình ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(targetResolution.width, targetResolution.height),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                        )
                    )
                    .build()
            )
            .build()

        // Cấu hình VideoCapture
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(videoQuality))
            .build()
        videoCapture = VideoCaptureX.withOutput(recorder)

        Log.d(TAG, "Use cases setup complete")
    }
//
//    fun startVideoRecording(outputFile: String, onComplete: (Boolean) -> Unit) {
//        videoCapture?.let { capture ->
//            val fileOptions = androidx.camera.video.FileOutputOptions.Builder(File(outputFile)).build()
//            capture.output.prepareRecording(context, fileOptions)
//                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
//                    when (recordEvent) {
//                        is VideoRecordEvent.Finalize -> {
//                            if (recordEvent.hasError()) {
//                                Log.e(TAG, "Video recording failed: ${recordEvent.error}")
//                                onComplete(false)
//                            } else {
//                                Log.d(TAG, "Video recording completed: $outputFile")
//                                onComplete(true)
//                            }
//                        }
//                    }
//                }
//        } ?: Log.e(TAG, "VideoCapture not initialized")
//    }
//
//    fun stopVideoRecording() {
//        videoCapture?.output?.stopRecording()
//        Log.d(TAG, "Video recording stopped")
//    }

    fun isFrontCamera(): Boolean {
        return settings.cameraLensFacing == CameraSettings.CAMERA_FACING_FRONT
    }
}

