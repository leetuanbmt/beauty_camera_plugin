package com.beauty.camera_plugin.repository

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.beauty.camera_plugin.FlashMode
import com.beauty.camera_plugin.models.CameraSettings
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine


class CameraRepository(private val context: Context) {
    companion object {
        private const val TAG = "CameraRepository"
    }

    // Callback interface for photo capture
    interface PhotoCaptureCallback {
        fun onSuccess(path: String)
        fun onFailure(exception: Exception)
    }

    // Camera variables
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Configuration
    private var settings = CameraSettings.default()

    // Callbacks
    // var onFaceDetected: ((List<FaceData>) -> Unit)? = null
    private var onCameraSwitched: ((String) -> Unit)? = null

    // Video recording variables
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var currentRecordingPath: String? = null

    private var currentSurface: Surface? = null
    private var currentLifecycleOwner: LifecycleOwner? = null





    /**
     * Initialize the camera system with settings
     * @param lifecycleOwner The lifecycle owner for camera
     * @param settings Camera settings to apply
     */
    suspend fun initialize(lifecycleOwner: LifecycleOwner, settings: CameraSettings) = try {
        // Store settings
        this.settings = settings
        this.currentLifecycleOwner = lifecycleOwner

        // Get camera provider using coroutines
        cameraProvider = suspendCancellableCoroutine { continuation ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    continuation.resume(cameraProviderFuture.run { get() }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get camera provider", e)
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }

        // Update camera selector based on settings
        cameraSelector = if (settings.cameraLensFacing == CameraSettings.CAMERA_FACING_FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Apply initial settings
        setFlashMode(settings.flashMode)
        if (settings.zoom > 1.0) {
            setZoom()
        }
        setDisplayOrientation()

    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize camera", e)
        throw e
    }

    /**
     * Start the camera with the current configuration
     */
    fun startCamera(lifecycleOwner: LifecycleOwner, surface: Surface) {
        try {
            currentLifecycleOwner = lifecycleOwner
            currentSurface = surface

            // Initialize camera provider if needed
            if (cameraProvider == null) {
                ProcessCameraProvider.getInstance(context).apply {
                    addListener({
                        try {
                            cameraProvider = get()
                            initializeCamera(surface)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to get camera provider", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
                return
            }

            initializeCamera(surface)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting camera", e)
        }
    }

    private fun initializeCamera(surface: Surface) {
        try {
            if (cameraProvider == null) {
                Log.e(TAG, "Failed to get camera provider")
                return
            }

            // Unbind previous use cases
            cameraProvider?.unbindAll()

            // Store current surface
            currentSurface = surface

            // Calculate target resolution based on settings
            val targetResolution = settings.resolution
            Log.d(TAG, "Target resolution: ${targetResolution.width}x${targetResolution.height}")

            // Create preview use case with settings
            preview = Preview.Builder()
                .setTargetResolution(targetResolution)
                .setTargetRotation(settings.displayOrientation)
                .build()
                .also { preview: Preview ->
                    preview.setSurfaceProvider { request: SurfaceRequest ->
                        request.provideSurface(
                            surface,
                            ContextCompat.getMainExecutor(context)
                        ) { result: SurfaceRequest.Result ->
                            when (result.resultCode) {
                                SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY -> {
                                    Log.d(TAG, "Surface provided successfully")
                                    // Log preview details
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
                                SurfaceRequest.Result.RESULT_REQUEST_CANCELLED -> 
                                    Log.w(TAG, "Surface request was cancelled")
                                SurfaceRequest.Result.RESULT_INVALID_SURFACE -> 
                                    Log.e(TAG, "Invalid surface provided")
                                else -> 
                                    Log.w(TAG, "Unknown surface result code: ${result.resultCode}")
                            }
                        }
                    }
                }

            // Create image capture use case with settings
            imageCapture = ImageCapture.Builder()
                .setTargetResolution(targetResolution)
                .setTargetRotation(settings.displayOrientation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            
            // Set up face detection if enabled
            if (settings.enableFaceDetection) {
                setupFaceDetection()
            }
            
            // Bind use cases to camera
            val useCases = mutableListOf<UseCase>()
            preview?.let { useCases.add(it) }
            imageCapture?.let { useCases.add(it) }
            imageAnalysis?.let { useCases.add(it) }
            
            if (useCases.isEmpty()) {
                Log.e(TAG, "No use cases to bind")
                return
            }
            
            try {
                camera = cameraProvider!!.bindToLifecycle(
                    currentLifecycleOwner!!,
                    cameraSelector,
                    *useCases.toTypedArray()
                )

                // Apply initial settings
                camera?.cameraControl?.setZoomRatio(settings.zoom.toFloat())
                setFlashMode(settings.flashMode)
                
                // Notify camera switched if callback is set
                val cameraId = camera?.cameraInfo.toString()
                onCameraSwitched?.invoke(cameraId)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error binding use cases", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing camera", e)
        }
    }


    /**
     * Set up face detection
     */
    private fun setupFaceDetection() {
        val analyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analyzer.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
            // Face detection implementation would go here using ML Kit or similar
            // For now, we'll just release the image
            imageProxy.close()
        }

        imageAnalysis = analyzer
    }

    /**
     * Switch camera (front/back)
     */
    fun switchCamera() {
        try {
            val newFacing = if (settings.cameraLensFacing == CameraSettings.CAMERA_FACING_BACK) {
                CameraSettings.CAMERA_FACING_FRONT
            } else {
                CameraSettings.CAMERA_FACING_BACK
            }
            settings = settings.copyWithCameraFacing(newFacing)

            // Restart camera with new settings
            currentLifecycleOwner?.let { owner: LifecycleOwner ->
                currentSurface?.let { surface: Surface ->
                    startCamera(owner, surface)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching camera", e)
        }
    }

    /**
     * Set zoom level
     */
    fun setZoom() {
        settings = settings.copyWithZoom()
        camera?.cameraControl?.setZoomRatio(settings.zoom.toFloat())
    }

    /**
     * Set flash mode
     */
    fun setFlashMode(mode: FlashMode) {
        settings = settings.copyWithFlashMode(mode)

        // Handle torch mode separately
        if (mode == FlashMode.TORCH) {
            camera?.cameraControl?.enableTorch(true)
        } else {
            // Turn off torch if another mode is selected
            camera?.cameraControl?.enableTorch(false)

            // Set the flash mode for image capture
            val flashMode = when (mode) {
                FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }

            imageCapture?.flashMode = flashMode
        }
    }

    /**
     * Focus on a specific point
     */
    fun focusOnPoint(x: Int, y: Int) {
        try {
            // Due to compatibility issues, we'll implement a simplified version
            // In a full implementation, we would use MeteringPoint and proper focus actions
            Log.d(TAG, "Focus requested at point ($x, $y)")

            // Just log the request for now, as the proper implementation
            // requires CameraX-specific classes and methods
        } catch (e: Exception) {
            Log.e(TAG, "Error focusing on point", e)
        }
    }

    /**
     * Set display orientation
     */
    fun setDisplayOrientation() {
        settings = settings.copyWithDisplayOrientation()
    }



    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            cameraExecutor.shutdown()
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    /**
     * Get cache directory for saving images and videos
     */
    fun getCacheDirectory(): String {
        val cacheDir = File(context.cacheDir, "camera_plugin")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir.absolutePath
    }

    /**
     * Get current preview resolution
     */
    fun getPreviewResolution(): Pair<Int, Int> {
        return Pair(settings.resolution.width, settings.resolution.height)
    }

    /**
     * Get current display rotation
     */
    fun getDisplayRotation(): Int {
        return settings.displayOrientation
    }

    /**
     * Check if current camera is front facing
     */
    fun isFrontCamera(): Boolean {
        return settings.cameraLensFacing == CameraSettings.CAMERA_FACING_FRONT
    }

    /**
     * Take a photo with the current settings and effects
     */
    fun takePhoto(callback: PhotoCaptureCallback) {
        if (imageCapture == null) {
            callback.onFailure(Exception("Camera not initialized"))
            return
        }
        
        // Create output file
        val photoFile = File(getCacheDirectory(), "photo_${System.currentTimeMillis()}.jpg")
        
        // Get current preview info for cropping
        val previewInfo = preview?.resolutionInfo
        val cropRect = previewInfo?.cropRect
        
        // Create output options with the same rotation as preview
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .setMetadata(ImageCapture.Metadata().apply {
                // Mirror image for front camera
                isReversedHorizontal = isFrontCamera()
            })
            .build()
        
        // Take the picture
        imageCapture!!.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Log capture details
                    Log.d(TAG, """
                        Photo captured:
                        - File: ${photoFile.absolutePath}
                        - Preview size: ${previewInfo?.resolution?.width}x${previewInfo?.resolution?.height}
                        - Crop rect: $cropRect
                        - Rotation: ${previewInfo?.rotationDegrees}°
                        - Front camera: ${isFrontCamera()}
                    """.trimIndent())
                    
                    callback.onSuccess(photoFile.absolutePath)
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Error capturing photo", exception)
                    callback.onFailure(exception)
                }
            }
        )
    }

    /**
     * Start video recording
     */
    fun startRecording(outputPath: String) {
        if (videoCapture == null) {
            // Initialize video capture if not already done
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Bind video capture use case
            try {
                cameraProvider?.unbind(videoCapture)
                cameraProvider?.bindToLifecycle(
                    context as LifecycleOwner,
                    cameraSelector,
                    videoCapture!!
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error binding video capture use case", e)
                throw e
            }
        }

        // Create output file
        val file = File(outputPath)
        currentRecordingPath = file.absolutePath

        // Configure output options
        val fileOutputOptions = FileOutputOptions.Builder(file).build()

        // Start recording
        activeRecording = videoCapture?.output
            ?.prepareRecording(context, fileOutputOptions)
            ?.apply {
                if (settings.enableAudio) {
                    withAudioEnabled()
                }
            }
            ?.start(ContextCompat.getMainExecutor(context)) { event: VideoRecordEvent ->
                when(event) {
                    is VideoRecordEvent.Start -> {
                        Log.d(TAG, "Video recording started")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (event.hasError()) {
                            Log.e(TAG, "Video recording failed: ${event.cause?.message}")
                            file.delete()
                        } else {
                            Log.d(TAG, "Video recording saved: ${file.absolutePath}")
                        }
                    }
                }
            }
    }

    /**
     * Stop video recording
     */
    fun stopRecording(): String {
        if (activeRecording == null) {
            throw IllegalStateException("No active recording")
        }

        val recording = activeRecording
        val path = currentRecordingPath

        if (recording == null || path == null) {
            throw IllegalStateException("Recording not properly initialized")
        }

        try {
            recording.stop()
            activeRecording = null
            currentRecordingPath = null
            return path
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            throw e
        }
    }

    /**
     * Get the camera sensor's aspect ratio
     * @return The aspect ratio as width/height
     */
    fun getCameraSensorAspectRatio(): Double {
        return try {
            val cameraInfo = camera?.cameraInfo
            if (cameraInfo != null) {
                // Get the actual preview resolution being used
                val resolution = preview?.resolutionInfo?.resolution
                    ?: imageCapture?.resolutionInfo?.resolution
                    ?: settings.resolution

                resolution.width.toDouble() / resolution.height.toDouble()
            } else {
                4.0 / 3.0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting aspect ratio", e)
            4.0 / 3.0
        }
    }

}