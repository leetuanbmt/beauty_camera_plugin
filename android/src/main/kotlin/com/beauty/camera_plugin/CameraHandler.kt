package com.beauty.camera_plugin

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.video.*
import com.beauty.camera_plugin.models.CameraSettings
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executor

@SuppressLint("RestrictedApi")
class CameraHandler(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val settings: CameraSettings,
    private val faceDetectorListener: FaceDetectorAnalyzer.DetectorListener? // Add this
) {
    companion object {
        private const val TAG = "CameraHandler"
    }

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private var imageAnalysis: ImageAnalysis? = null
    private var faceDetectorAnalyzer: FaceDetectorAnalyzer? = null

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    /**
     * Khởi tạo và bắt đầu hiển thị camera preview.
     * @param surfaceProvider Một lambda cung cấp Surface cho CameraX khi được yêu cầu.
     * @param onInitialized Callback được gọi khi camera đã sẵn sàng.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun startCameraPreview(surfaceProvider: Preview.SurfaceProvider, onInitialized: () -> Unit) {
        Log.d(TAG, "Starting camera preview...")
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                Log.d(TAG, "CameraProvider obtained successfully")
                setupAndBindUseCases(surfaceProvider)
                onInitialized()
                Log.d(TAG, "Camera preview started successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting camera preview", e)
            }
        }, mainExecutor)
    }

    @SuppressLint("WrongConstant")
    @ExperimentalCamera2Interop
    private fun setupAndBindUseCases(surfaceProvider: Preview.SurfaceProvider) {
        val cameraProvider = cameraProvider ?: run {
            Log.e(TAG, "CameraProvider is not available.")
            return
        }

        Log.d(TAG, "Setting up camera use cases...")
        cameraProvider.unbindAll()

        preview = createPreviewUseCase(surfaceProvider)
        imageCapture = createImageCaptureUseCase()
        videoCapture = createVideoCaptureUseCase()
        Log.d(TAG, "Use cases created successfully")

        if (settings.enableFaceDetection && faceDetectorListener != null) {
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetRotation(settings.displayOrientation)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            faceDetectorAnalyzer = FaceDetectorAnalyzer(context, faceDetectorListener, settings.cameraLensFacing)
            imageAnalysis?.setAnalyzer(mainExecutor, faceDetectorAnalyzer!!)
        } else {
            imageAnalysis = null
            faceDetectorAnalyzer = null
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(settings.cameraLensFacing)
            .build()

        Log.d(TAG, "Open camera with lens facing: ${settings.cameraLensFacing}")

        try {
            val useCases = listOfNotNull(preview, imageCapture, videoCapture, imageAnalysis)
            Log.d(TAG, "Binding ${useCases.size} use cases to lifecycle...")
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )
            camera?.cameraControl?.setZoomRatio(settings.zoom.toFloat())
            Log.d(TAG, "Use cases bound to lifecycle successfully. Camera: $camera")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind use cases", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording(outputFile: String, onResult: (Boolean) -> Unit) {
        val currentVideoCapture = videoCapture ?: run {
            Log.e(TAG, "VideoCapture is not initialized.")
            onResult(false)
            return
        }
        stopVideoRecording()
        val file = File(outputFile)
        val outputOptions = FileOutputOptions.Builder(file).build()

        activeRecording = currentVideoCapture.output
            .prepareRecording(context, outputOptions)
            .start(mainExecutor) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> Log.d(TAG, "Video recording started.")
                    is VideoRecordEvent.Finalize -> {
                        if (recordEvent.hasError()) {
                            Log.e(TAG, "Video recording failed: ${recordEvent.error} - ${recordEvent.cause?.message}")
                            activeRecording = null
                            onResult(false)
                        } else {
                            Log.d(TAG, "Video recording completed: ${recordEvent.outputResults.outputUri}")
                            onResult(true)
                        }
                    }
                }
            }
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
        Log.d(TAG, "Video recording stopped.")
    }

    fun getPreviewSize(): PreviewSize? {
        val info = preview?.resolutionInfo ?: return null
        val resolution = info.resolution
        val rotation = info.rotationDegrees
        val isSideways = rotation == 90 || rotation == 270
        val adjustedWidth = if (isSideways) resolution.height.toLong() else resolution.width.toLong()
        val adjustedHeight = if (isSideways) resolution.width.toLong() else resolution.height.toLong()
        Log.d(TAG, "getPreviewSize: original=${resolution.width}x${resolution.height}, rotation=$rotation°, adjusted=${adjustedWidth}x${adjustedHeight}")
        return PreviewSize(width = adjustedWidth, height = adjustedHeight)
    }

    fun dispose() {
        Log.d(TAG, "Disposing camera handler.")
        stopVideoRecording()
        cameraProvider?.unbindAll()
        camera = null
        preview = null
        imageCapture = null
        videoCapture = null
        imageAnalysis?.clearAnalyzer() // Clear analyzer
        imageAnalysis = null
        faceDetectorAnalyzer?.close() // Close MediaPipe detector
        faceDetectorAnalyzer = null
    }

    private fun createPreviewUseCase(surfaceProvider: Preview.SurfaceProvider): Preview {
        Log.d(TAG, "Creating preview use case...")
        return Preview.Builder()
            .setTargetRotation(settings.displayOrientation)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .build()
            .also { 
                it.surfaceProvider = surfaceProvider
                Log.d(TAG, "Preview use case created with surface provider")
            }
    }

    private fun createImageCaptureUseCase(): ImageCapture {
        return ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(settings.displayOrientation)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .build()
    }

    private fun createVideoCaptureUseCase(): VideoCapture<Recorder> {
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()

        val recorder = Recorder.Builder()
            .build()

        return VideoCapture.Builder(recorder)
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(settings.displayOrientation)
            .build()
    }
}
