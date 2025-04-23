package com.beauty.camera_plugin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import io.flutter.view.TextureRegistry
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Enum representing camera lens facing
 */
enum class CameraLens {
    FRONT,
    BACK,
    EXTERNAL
}

/**
 * Manager class for handling camera operations using CameraX library
 */
class CameraXManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val flutterApi: BeautyCameraFlutterApi,
    private val textureRegistry: TextureRegistry
) : BeautyCameraHostApi {
    
    companion object {
        private const val TAG = "CameraXManager"
    }
    
    // Camera executor for background operations
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    
    // CameraX objects
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: androidx.camera.video.VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraSelector: CameraSelector? = null
    
    // Current camera state
    private var isRecording = false
    private var currentSettings: CameraSettings? = null
    private var previewHandler: CameraPreviewTextureHandler? = null
    
    // Lens and rotation settings
    private var cameraLens = CameraLens.BACK
    private var defaultRotation = Surface.ROTATION_0
    
    // Camera control
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    
    // Image processing
    private var imageProcessor: ImageFilterProcessor? = null
    private var currentFilterConfig: FilterConfig? = null
    
    override fun initializeCamera(settings: CameraSettings, callback: (Result<Unit>) -> Unit) {
        try {
            // Check camera permission first
            if (!hasCameraPermission()) {
                Log.e(TAG, "Camera permission not granted")
                flutterApi.onCameraError(
                    CameraError(
                        CameraErrorType.INITIALIZATION_FAILED,
                        "Camera permission not granted"
                    )
                ) {}
                callback(Result.failure(FlutterError(
                    "permission_error",
                    "Camera permission not granted",
                    null
                )))
                return
            }
            
            // Verify lifecycleOwner is valid
            if (!isLifecycleValid()) {
                Log.e(TAG, "Lifecycle owner is not valid")
                callback(Result.failure(FlutterError(
                    "lifecycle_error",
                    "Lifecycle owner is not valid or in wrong state",
                    null
                )))
                return
            }
            
            // Check if executor is terminated and reinitialize if needed
            if (cameraExecutor.isShutdown || cameraExecutor.isTerminated) {
                Log.d(TAG, "Camera executor is shutdown or terminated, reinitializing")
                cameraExecutor = Executors.newSingleThreadExecutor()
            }
            
            currentSettings = settings
            
            // Initialize image processor
            if (imageProcessor == null) {
                imageProcessor = ImageFilterProcessor(context)
            }
            
            // Initialize CameraX provider
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    cameraProvider = providerFuture.get()
                    
                    // Set up camera selector based on settings
                    val cameraId = settings.cameraId
                    cameraSelector = if (cameraId != null) {
                        CameraSelector.Builder().requireLensFacing(
                            if (cameraId == "1") CameraSelector.LENS_FACING_FRONT 
                            else CameraSelector.LENS_FACING_BACK
                        ).build()
                    } else {
                        // Default to back camera
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    
                    // Set camera lens enum based on selector
                    cameraLens = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                        CameraLens.FRONT
                    } else {
                        CameraLens.BACK
                    }
                    
                    // Initialize use cases that will be used later
                    // This ensures they're ready when needed
                    initializeUseCases(settings)
                    
                    Log.d(TAG, "Camera initialized successfully")
                    callback(Result.success(Unit))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize camera", e)
                    flutterApi.onCameraError(
                        CameraError(
                            CameraErrorType.INITIALIZATION_FAILED,
                            "Failed to initialize camera: ${e.message}"
                        )
                    ) {}
                    callback(Result.failure(FlutterError(
                        "camera_init_error",
                        "Failed to initialize camera: ${e.message}",
                        null
                    )))
                }
            }, mainExecutor)
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing camera", e)
            flutterApi.onCameraError(
                CameraError(
                    CameraErrorType.INITIALIZATION_FAILED,
                    "Exception initializing camera: ${e.message}"
                )
            ) {}
            callback(Result.failure(FlutterError(
                "camera_init_error",
                "Exception initializing camera: ${e.message}",
                null
            )))
        }
    }
    
    /**
     * Check if lifecycleOwner is valid and in a good state to use
     */
    private fun isLifecycleValid(): Boolean {
        return lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)
    }
    
    /**
     * Checks if camera permission is granted
     */
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Initialize camera use cases (ImageCapture, VideoCapture)
     */
    private fun initializeUseCases(settings: CameraSettings) {
        try {
            // Get the current device rotation
            val rotation = getOutputRotation()
            Log.d(TAG, "Initializing use cases with rotation: $rotation")
            
            // Create ImageCapture use case
            val imageCaptureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(rotation)
                
            // Set target resolution if specified
            val width = settings.width
            val height = settings.height
            if (width != null && height != null && width > 0 && height > 0) {
                imageCaptureBuilder.setTargetResolution(Size(width.toInt(), height.toInt()))
            }
            
            imageCapture = imageCaptureBuilder.build()
            
            // Create VideoCapture use case
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    androidx.camera.video.QualitySelector.from(
                        androidx.camera.video.Quality.HIGHEST
                    )
                )
                .build()
                
            videoCapture = androidx.camera.video.VideoCapture.withOutput(recorder)
            
            Log.d(TAG, "Camera use cases initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera use cases", e)
        }
    }
    
    override fun createPreviewTexture(callback: (Result<Long?>) -> Unit) {
        Log.d(TAG, "Creating preview texture")
        try {
            // Initialize the preview handler
            if (previewHandler == null) {
                previewHandler = CameraPreviewTextureHandler(textureRegistry, mainExecutor)
            }
            
            // Initialize the texture
            val textureId = previewHandler!!.init()
            Log.d(TAG, "Created preview texture with ID: $textureId")
            
            if (textureId < 0) {
                Log.e(TAG, "Failed to create texture - invalid ID: $textureId")
                callback(Result.failure(FlutterError(
                    "texture_creation_error",
                    "Failed to create texture - invalid ID",
                    null
                )))
                return
            }
            
            callback(Result.success(textureId))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create preview texture", e)
            callback(Result.failure(FlutterError(
                "texture_creation_error",
                "Failed to create preview texture: ${e.message}",
                null
            )))
        }
    }
    
    /**
     * Start the preview
     * @param textureId The ID of the texture to use for the preview
     * @param result Callback to handle the result
     */
    fun startPreview(textureId: Long, result: (isSuccess: Boolean, errorMessage: String?) -> Unit) {
        Log.d(TAG, "Starting preview for texture ID: $textureId")
        
        try {
            if (cameraProvider == null) {
                val error = "Camera provider is null in startPreview"
                Log.e(TAG, error)
                result(false, error)
                return
            }
            
            if (previewHandler == null) {
                val error = "Preview handler is null in startPreview"
                Log.e(TAG, error)
                result(false, error)
                return
            }
            
            Log.d(TAG, "Handler texture ID: ${previewHandler!!.getTextureId()}, Requested texture ID: $textureId")
            
            // Unbind any previous use cases
            Log.d(TAG, "Unbinding any previous use cases")
            cameraProvider?.unbindAll()
            
            // Get the current device rotation
            val rotation = getOutputRotation()
            Log.d(TAG, "Setting preview rotation to: $rotation")
            
            // Create the preview use case
            val previewBuilder = Preview.Builder()
                .setTargetRotation(rotation)
            
            // Apply any necessary configurations to the preview
            val previewResolution = getOptimalPreviewSize()
            if (previewResolution != null) {
                Log.d(TAG, "Setting target resolution to: ${previewResolution.width}x${previewResolution.height}")
                previewBuilder.setTargetResolution(previewResolution)
                // Update the preview handler with the resolution
                previewHandler!!.setResolution(previewResolution)
            } else if (previewHandler!!.getResolution() != null) {
                val resolution = previewHandler!!.getResolution()!!
                Log.d(TAG, "Using existing resolution: ${resolution.width}x${resolution.height}")
                previewBuilder.setTargetResolution(resolution)
            } else {
                Log.d(TAG, "Using default target resolution")
            }
            
            // Build the preview
            val preview = previewBuilder.build()
            
            // Set the surface provider
            Log.d(TAG, "Setting surface provider for preview")
            preview.setSurfaceProvider(previewHandler)
            
            // Select camera to use
            val cameraSelector = when (cameraLens) {
                CameraLens.FRONT -> {
                    Log.d(TAG, "Using front camera")
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
                CameraLens.BACK -> {
                    Log.d(TAG, "Using back camera")
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                CameraLens.EXTERNAL -> {
                    Log.d(TAG, "Using external camera")
                    CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_EXTERNAL)
                        .build()
                }
            }
            
            // Create a list of use cases to bind
            val useCaseList = mutableListOf<UseCase>(preview)
            
            // Add ImageCapture if initialized
            if (imageCapture != null) {
                // Update the rotation for image capture
                imageCapture!!.targetRotation = rotation
                
                Log.d(TAG, "Adding ImageCapture use case with rotation: $rotation")
                useCaseList.add(imageCapture!!)
            }
            
            // Add VideoCapture if initialized - VideoCapture extends UseCase indirectly
            // Need to check compatibility with lifecycle binding
            try {
                if (videoCapture != null) {
                    Log.d(TAG, "Adding VideoCapture use case")
                    // Note: VideoCapture<Recorder> isn't a direct UseCase, so we can't add it directly
                    // For now, we'll just bind the preview and image capture
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add VideoCapture use case", e)
            }
            
            // Bind the camera to the lifecycle with all the use cases
            Log.d(TAG, "Binding camera use cases to lifecycle, count: ${useCaseList.size}")
            this.camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCaseList.toTypedArray()
            )
            
            if (this.camera == null) {
                val error = "Failed to bind camera to lifecycle"
                Log.e(TAG, error)
                result(false, error)
                return
            }
            
            cameraControl = this.camera!!.cameraControl
            cameraInfo = this.camera!!.cameraInfo
            
            // Get the actual preview size from the camera info
            val actualPreviewSize = getActualPreviewSize(this.camera!!)
            if (actualPreviewSize != null) {
                Log.d(TAG, "Actual preview size: ${actualPreviewSize.width}x${actualPreviewSize.height}")
                previewHandler!!.setResolution(actualPreviewSize)
            }
            
            Log.d(TAG, "Camera preview started successfully")
            result(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start preview", e)
            result(false, e.message)
        }
    }
    
    /**
     * Gets the optimal preview size based on the camera capabilities
     */
    private fun getOptimalPreviewSize(): Size? {
        try {
            val cameraProvider = cameraProvider ?: return null
            
            // Get the camera selector
            val selector = when (cameraLens) {
                CameraLens.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                CameraLens.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                CameraLens.EXTERNAL -> CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_EXTERNAL)
                    .build()
            }
            
            // Try to get camera info
            val cameraInfo = cameraProvider.availableCameraInfos.firstOrNull { 
                selector.filter(listOf(it)).isNotEmpty()
            } ?: return null
            
            // Convert to Camera2CameraInfo to access needed properties
            val camera2CameraInfo = Camera2CameraInfo.from(cameraInfo)
            
            // Get the camera capabilities
            val cameraCharacteristics = camera2CameraInfo.getCameraCharacteristic(
                android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            ) ?: return null
            
            // Get supported sizes for preview
            @Suppress("UNCHECKED_CAST")
            val configMap = cameraCharacteristics as android.hardware.camera2.params.StreamConfigurationMap
            val sizes = configMap.getOutputSizes(SurfaceTexture::class.java)
            if (sizes == null || sizes.isEmpty()) return null
            
            // Get target aspect ratio from settings
            val targetWidth = currentSettings?.width?.toInt() ?: 0
            val targetHeight = currentSettings?.height?.toInt() ?: 0
            val targetRatio = if (targetWidth > 0 && targetHeight > 0) {
                targetWidth.toDouble() / targetHeight.toDouble()
            } else {
                // Default to screen aspect ratio if not specified
                val displayMetrics = context.resources.displayMetrics
                displayMetrics.widthPixels.toDouble() / displayMetrics.heightPixels.toDouble()
            }
            
            // Find closest matching size
            var optimalSize: Size? = null
            var minDiff = Double.MAX_VALUE
            
            for (size in sizes) {
                val ratio = size.width.toDouble() / size.height.toDouble()
                val diff = Math.abs(ratio - targetRatio)
                
                if (diff < minDiff) {
                    optimalSize = Size(size.width, size.height)
                    minDiff = diff
                } else if (diff == minDiff && size.width > (optimalSize?.width ?: 0)) {
                    // If same ratio, choose the larger one
                    optimalSize = Size(size.width, size.height)
                }
            }
            
            return optimalSize
        } catch (e: Exception) {
            Log.e(TAG, "Error getting optimal preview size", e)
            return null
        }
    }
    
    /**
     * Gets the actual preview size from camera info
     */
    private fun getActualPreviewSize(camera: Camera): Size? {
        try {
            // Sử dụng kích thước từ camera parameters thay vì previewResolution
            val cameraInfo = camera.cameraInfo
            
            // Thử lấy từ camera capabilities
            val camera2CameraInfo = Camera2CameraInfo.from(cameraInfo)
            
            // Fallback to optimal size nếu không lấy được
            return getOptimalPreviewSize()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting actual preview size", e)
            return null
        }
    }
    
    // Implementation for BeautyCameraHostApi interface
    override fun startPreview(textureId: Long, callback: (Result<Unit>) -> Unit) {
        startPreview(textureId) { isSuccess, errorMessage ->
            if (isSuccess) {
                try {
                    // Notify Flutter that camera preview is ready with the resolution
                    val resolution = previewHandler?.getResolution()
                    if (resolution != null) {
                        Log.d(TAG, "Notifying Flutter about initialized camera with resolution: ${resolution.width}x${resolution.height}")
                        flutterApi.onCameraInitialized(
                            textureId,
                            resolution.width.toLong(),
                            resolution.height.toLong()
                        ) { initResult ->
                            if (initResult.isSuccess) {
                                Log.d(TAG, "Flutter notified of camera initialization success")
                                callback(Result.success(Unit))
                            } else {
                                Log.e(TAG, "Failed to notify Flutter of camera initialization", initResult.exceptionOrNull())
                                callback(Result.success(Unit)) // Continue anyway
                            }
                        }
                    } else {
                        Log.w(TAG, "Resolution is null, notifying with default values")
                        flutterApi.onCameraInitialized(
                            textureId,
                            1920L, // Default width
                            1080L  // Default height
                        ) { _ ->
                            callback(Result.success(Unit))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception while notifying Flutter", e)
                    callback(Result.success(Unit)) // Continue anyway
                }
            } else {
                callback(Result.failure(FlutterError(
                    "preview_error",
                    "Failed to start preview: $errorMessage",
                    null
                )))
            }
        }
    }
    
    override fun stopPreview(callback: (Result<Unit>) -> Unit) {
        try {
            cameraProvider?.unbindAll()
            Log.d(TAG, "Preview stopped successfully")
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop preview", e)
            callback(Result.failure(FlutterError(
                "preview_error",
                "Failed to stop preview: ${e.message}",
                null
            )))
        }
    }
    
    override fun disposeCamera(callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Disposing camera - starting disposal process")
        try {
            // Stop recording if necessary
            if (isRecording) {
                Log.d(TAG, "Stopping recording during disposal")
                try {
                    val currentRecording = recording
                    if (currentRecording != null) {
                        currentRecording.stop()
                        recording = null
                    }
                    isRecording = false
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping recording during disposal", e)
                    // Continue with disposal anyway
                }
            }
            
            // Unbind all camera use cases
            if (cameraProvider != null) {
                Log.d(TAG, "Unbinding all camera use cases")
                cameraProvider?.unbindAll()
            } else {
                Log.d(TAG, "Camera provider is null - skipping unbind")
            }
            
            // Clean up the executor if it's still running
            if (!cameraExecutor.isShutdown) {
                Log.d(TAG, "Shutting down camera executor")
                try {
                    // Shutdown the executor gracefully
                    cameraExecutor.shutdown()
                    
                    // If needed in the future, we could add a timeout:
                    // if (!cameraExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    //     cameraExecutor.shutdownNow()
                    // }
                } catch (e: Exception) {
                    Log.w(TAG, "Error shutting down camera executor", e)
                    // Try a more forceful shutdown
                    try {
                        cameraExecutor.shutdownNow()
                    } catch (e2: Exception) {
                        Log.w(TAG, "Error forcefully shutting down camera executor", e2)
                    }
                }
            } else {
                Log.d(TAG, "Camera executor already shut down")
            }
            
            // Clean up the preview handler
            if (previewHandler != null) {
                Log.d(TAG, "Cleaning up preview handler")
                previewHandler?.cleanup()
                previewHandler = null
            } else {
                Log.d(TAG, "Preview handler is null - skipping cleanup")
            }
            
            // Clean up the image processor
            if (imageProcessor != null) {
                Log.d(TAG, "Disposing image processor")
                imageProcessor?.dispose()
                imageProcessor = null
            } else {
                Log.d(TAG, "Image processor is null - skipping disposal")
            }
            
            // Clear other references
            camera = null
            preview = null
            imageCapture = null
            videoCapture = null
            cameraProvider = null
            
            // Create a new executor for future use
            cameraExecutor = Executors.newSingleThreadExecutor()
            
            Log.d(TAG, "Camera disposed successfully")
            
            // Execute callback on main thread
            mainExecutor.execute {
                callback(Result.success(Unit))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispose camera", e)
            
            // Clean up as much as possible
            cameraProvider = null
            camera = null
            preview = null
            imageCapture = null
            videoCapture = null
            recording = null
            isRecording = false
            
            // Create a new executor
            try {
                if (!cameraExecutor.isShutdown) {
                    cameraExecutor.shutdownNow()
                }
                cameraExecutor = Executors.newSingleThreadExecutor()
            } catch (e2: Exception) {
                Log.e(TAG, "Error recreating camera executor", e2)
            }
            
            // Execute callback on main thread
            mainExecutor.execute {
                callback(Result.failure(FlutterError(
                    "camera_dispose_error",
                    "Failed to dispose camera: ${e.message}",
                    null
                )))
            }
        }
    }
    
    override fun takePicture(path: String, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Taking picture to path: $path")
        
        val imageCapture = this.imageCapture ?: run {
            Log.e(TAG, "Cannot take picture, image capture not initialized")
            callback(Result.failure(FlutterError(
                "capture_error",
                "Cannot take picture, image capture not initialized",
                null
            )))
            return
        }
        
        try {
            // Check executor is not terminated
            if (cameraExecutor.isShutdown || cameraExecutor.isTerminated) {
                Log.d(TAG, "Camera executor is shutdown or terminated, reinitializing")
                cameraExecutor = Executors.newSingleThreadExecutor()
            }
            
            // Check path is valid
            if (path.isEmpty()) {
                Log.e(TAG, "Cannot take picture, path is empty")
                callback(Result.failure(FlutterError(
                    "capture_error", 
                    "Picture path cannot be empty",
                    null
                )))
                return
            }
            
            // Ensure parent directory exists
            val photoFile = File(path)
            val parentDir = photoFile.parentFile
            
            if (parentDir != null && !parentDir.exists()) {
                Log.d(TAG, "Creating parent directory: ${parentDir.absolutePath}")
                try {
                    if (!parentDir.mkdirs()) {
                        Log.w(TAG, "Failed to create parent directory but continuing anyway")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating parent directory", e)
                    // Continue anyway, the ImageCapture might still succeed
                }
            }
            
            Log.d(TAG, "Starting image capture to file: ${photoFile.absolutePath}")
            
            // Get the current device rotation
            val rotation = getOutputRotation()
            Log.d(TAG, "Current device rotation for output: $rotation")
            
            // Set output options with correct rotation
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                .setMetadata(ImageCapture.Metadata().apply {
                    // Set JPEG orientation based on device rotation
                    isReversedHorizontal = cameraLens == CameraLens.FRONT
                })
                .build()
            
            // Update the target rotation for the imageCapture
            imageCapture.targetRotation = rotation
            
            // Capture the image
            imageCapture.takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        try {
                            Log.d(TAG, "Photo saved successfully at $path")
                            
                            // Verify the file exists
                            if (!photoFile.exists() || photoFile.length() == 0L) {
                                Log.e(TAG, "Photo file does not exist or is empty after saved callback")
                                callback(Result.failure(FlutterError(
                                    "capture_error",
                                    "Photo file missing or empty after capture",
                                    null
                                )))
                                return
                            }
                            
                            // Apply filters to the saved image if needed
                            val filterConfig = currentFilterConfig
                            if (filterConfig != null) {
                                try {
                                    Log.d(TAG, "Applying filter to saved image: ${filterConfig.filterType}")
                                    processImageWithFilters(path, filterConfig)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to apply filters to saved image", e)
                                    // Continue anyway, at least we have the original photo
                                }
                            }
                            
                            // Fix image orientation if needed
                            try {
                                fixImageOrientation(path)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to fix image orientation", e)
                                // Continue anyway, the image was captured
                            }
                            
                            // Notify Flutter that picture was taken
                            Log.d(TAG, "Notifying Flutter of picture completion")
                            
                            // Execute the Flutter API call on the main UI thread
                            mainExecutor.execute {
                                flutterApi.onTakePictureCompleted(path) {
                                    if (it.isSuccess) {
                                        Log.d(TAG, "Flutter notified of picture completion")
                                    } else {
                                        Log.e(TAG, "Failed to notify Flutter of picture completion", it.exceptionOrNull())
                                    }
                                    
                                    // Return success to Flutter
                                    callback(Result.success(Unit))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onImageSaved callback", e)
                            callback(Result.failure(FlutterError(
                                "capture_error",
                                "Error processing saved image: ${e.message}",
                                null
                            )))
                        }
                    }
                    
                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Failed to take picture", exception)
                        
                        // Check if the file exists despite the error
                        if (photoFile.exists() && photoFile.length() > 0) {
                            Log.w(TAG, "Image file exists despite capture error, trying to use it anyway")
                            
                            // Execute on main thread
                            mainExecutor.execute {
                                // Try to notify Flutter of completion anyway
                                flutterApi.onTakePictureCompleted(path) { _ -> 
                                    // Just log any errors here, we'll return the original error below
                                }
                                
                                // Return success since we do have an image file
                                callback(Result.success(Unit))
                            }
                            return
                        }
                        
                        // Make these Flutter API calls on the main thread too
                        mainExecutor.execute {
                            flutterApi.onCameraError(
                                CameraError(
                                    CameraErrorType.CAPTURE_FAILED,
                                    "Failed to take picture: ${exception.message}"
                                )
                            ) {}
                            callback(Result.failure(FlutterError(
                                "capture_error",
                                "Failed to take picture: ${exception.message}",
                                null
                            )))
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during takePicture", e)
            flutterApi.onCameraError(
                CameraError(
                    CameraErrorType.CAPTURE_FAILED,
                    "Exception during takePicture: ${e.message}"
                )
            ) {}
            callback(Result.failure(FlutterError(
                "capture_error",
                "Exception during takePicture: ${e.message}",
                null
            )))
        }
    }
    
    /**
     * Gets the correct rotation value based on the current device orientation
     * and camera lens facing direction
     */
    private fun getOutputRotation(): Int {
        try {
            // Get the current device rotation from the activity context if possible
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            
            @Suppress("DEPRECATION")
            val rotation = windowManager.defaultDisplay.rotation
            
            Log.d(TAG, "Device display rotation from WindowManager: $rotation")
            
            // If using front camera, we need to adjust rotation
            return when (cameraLens) {
                CameraLens.FRONT -> rotation
                else -> rotation
            }
        } catch (e: Exception) {
            // Fallback to default rotation if we can't get the display
            Log.e(TAG, "Error getting device rotation, using default", e)
            return Surface.ROTATION_0
        }
    }
    
    /**
     * Fix the image orientation using EXIF data if needed
     */
    private fun fixImageOrientation(imagePath: String) {
        try {
            val exifInterface = ExifInterface(imagePath)
            
            val orientation = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            Log.d(TAG, "Original EXIF orientation: $orientation")
            
            // Check if orientation needs correction based on camera lens
            val correctedOrientation = when (cameraLens) {
                CameraLens.FRONT -> {
                    // For front camera, we often need to flip horizontally
                    if (orientation == ExifInterface.ORIENTATION_NORMAL ||
                        orientation == ExifInterface.ORIENTATION_UNDEFINED) {
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL
                    } else {
                        orientation
                    }
                }
                else -> orientation
            }
            
            // If orientation changed, update the EXIF data
            if (correctedOrientation != orientation) {
                Log.d(TAG, "Updating EXIF orientation to: $correctedOrientation")
                exifInterface.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    correctedOrientation.toString()
                )
                exifInterface.saveAttributes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fixing image orientation", e)
        }
    }
    
    override fun startRecording(path: String, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Start recording to path: $path")
        
        // Check permissions
        if (!hasCameraPermission()) {
            Log.e(TAG, "Camera permission not granted")
            callback(Result.failure(FlutterError(
                "permission_error",
                "Camera permission not granted",
                null
            )))
            return
        }
        
        if (!hasMicrophonePermission()) {
            Log.e(TAG, "Microphone permission not granted")
            callback(Result.failure(FlutterError(
                "permission_error",
                "Microphone permission not granted",
                null
            )))
            return
        }
        
        if (isRecording) {
            Log.w(TAG, "Already recording")
            callback(Result.failure(FlutterError(
                "recording_error",
                "Already recording",
                null
            )))
            return
        }
        
        val videoCapture = this.videoCapture ?: run {
            Log.e(TAG, "Cannot start recording, video capture not initialized")
            callback(Result.failure(FlutterError(
                "recording_error",
                "Cannot start recording, video capture not initialized",
                null
            )))
            return
        }
        
        try {
            // Check executor is not terminated
            if (cameraExecutor.isShutdown || cameraExecutor.isTerminated) {
                Log.d(TAG, "Camera executor is shutdown or terminated, reinitializing")
                cameraExecutor = Executors.newSingleThreadExecutor()
            }
            
            // Create output file
            val videoFile = File(path)
            if (videoFile.exists()) {
                videoFile.delete()
            }
            
            // Ensure parent directory exists
            val parentDir = videoFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                Log.d(TAG, "Creating parent directory: ${parentDir.absolutePath}")
                try {
                    if (!parentDir.mkdirs()) {
                        Log.w(TAG, "Failed to create parent directory but continuing anyway")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating parent directory", e)
                    // Continue anyway, the recording might still succeed
                }
            }
            
            // Create output options
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
                put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, videoFile.parent)
            }
            
            val outputOptions = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
                .setContentValues(contentValues)
                .build()
            
            // Start recording
            recording = videoCapture.output
                .prepareRecording(context, outputOptions)
                .apply {
                    // Enable audio if needed
                    withAudioEnabled()
                }
                .start(cameraExecutor) { event ->
                    when (event) {
                        is androidx.camera.video.VideoRecordEvent.Start -> {
                            isRecording = true
                            Log.d(TAG, "Recording started to: $path")
                            
                            // Notify Flutter that recording has started - Execute on main thread
                            mainExecutor.execute {
                                flutterApi.onRecordingStarted {
                                    if (it.isSuccess) {
                                        Log.d(TAG, "Flutter notified of recording start")
                                    } else {
                                        Log.e(TAG, "Failed to notify Flutter of recording start", it.exceptionOrNull())
                                    }
                                }
                            }
                        }
                        is androidx.camera.video.VideoRecordEvent.Finalize -> {
                            isRecording = false
                            Log.d(TAG, "Recording finished: ${event.outputResults.outputUri}")
                            
                            // Check if the file exists
                            if (!videoFile.exists()) {
                                Log.w(TAG, "Video file doesn't exist at expected path: $path")
                            }
                            
                            // Notify Flutter that recording was stopped - Execute on main thread
                            mainExecutor.execute {
                                flutterApi.onRecordingStopped(path) {
                                    if (it.isSuccess) {
                                        Log.d(TAG, "Flutter notified of recording completion")
                                    } else {
                                        Log.e(TAG, "Failed to notify Flutter of recording completion", it.exceptionOrNull())
                                    }
                                }
                            }
                            
                            if (event.hasError()) {
                                val exception = event.cause
                                Log.e(TAG, "Recording error: ${exception?.message}", exception)
                                
                                // Execute on main thread
                                mainExecutor.execute {
                                    flutterApi.onCameraError(
                                        CameraError(
                                            CameraErrorType.RECORDING_FAILED,
                                            "Failed to record video: ${exception?.message}"
                                        )
                                    ) {}
                                }
                            }
                        }
                    }
                }
            
            isRecording = true
            
            // Return success to Flutter
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            flutterApi.onCameraError(
                CameraError(
                    CameraErrorType.RECORDING_FAILED,
                    "Failed to start recording: ${e.message}"
                )
            ) {}
            callback(Result.failure(FlutterError(
                "recording_error",
                "Failed to start recording: ${e.message}",
                null
            )))
        }
    }
    
    /**
     * Checks if microphone permission is granted
     */
    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    override fun stopRecording(callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Stopping recording")
        
        if (!isRecording) {
            Log.w(TAG, "Not currently recording")
            callback(Result.success(Unit))
            return
        }
        
        try {
            val currentRecording = recording
            if (currentRecording != null) {
                // Stop the recording
                currentRecording.stop()
                recording = null
            } else {
                Log.w(TAG, "Recording is null despite isRecording flag being true")
            }
            
            isRecording = false
            Log.d(TAG, "Recording stopped successfully")
            
            // Execute callback on main thread to avoid threading issues
            mainExecutor.execute {
                callback(Result.success(Unit))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            
            // Reset recording state
            isRecording = false
            recording = null
            
            // Execute callback on main thread
            mainExecutor.execute {
                callback(Result.failure(FlutterError(
                    "recording_error",
                    "Failed to stop recording: ${e.message}",
                    null
                )))
            }
        }
    }
    
    override fun applyFilter(textureId: Long, filterConfig: FilterConfig, callback: (Result<Unit>) -> Unit) {
        try {
            // Store the filter configuration
            currentFilterConfig = filterConfig
            
            // Apply the filter to the camera
            applyFilterToCamera(filterConfig)
            
            Log.d(TAG, "Filter applied: ${filterConfig.filterType}")
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply filter", e)
            callback(Result.failure(FlutterError(
                "filter_error",
                "Failed to apply filter: ${e.message}",
                null
            )))
        }
    }
    
    /**
     * Applies the current filter configuration to the camera
     */
    private fun applyFilterToCamera(filterConfig: FilterConfig) {
        val camera = camera ?: return
        
        try {
            // Apply camera effects based on the configuration
            val cameraControl = camera.cameraControl
            
            // Apply white balance if provided
            filterConfig.whiteBalance?.let { whiteBalance ->
                val awbMode = when (whiteBalance) {
                    WhiteBalanceMode.AUTO -> CameraMetadata.CONTROL_AWB_MODE_AUTO
                    WhiteBalanceMode.INCANDESCENT -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                    WhiteBalanceMode.FLUORESCENT -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
                    WhiteBalanceMode.WARM_FLUORESCENT -> CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT
                    WhiteBalanceMode.DAYLIGHT -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                    WhiteBalanceMode.CLOUDY_DAYLIGHT -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                    WhiteBalanceMode.TWILIGHT -> CameraMetadata.CONTROL_AWB_MODE_TWILIGHT
                    WhiteBalanceMode.SHADE -> CameraMetadata.CONTROL_AWB_MODE_SHADE
                }
                
                try {
                    // Note: Setting AWB mode directly is not available through the standard
                    // CameraX API without Camera2 interop. We'll log the info but can't set it
                    // without setCaptureRequestOption
                    Log.d(TAG, "White balance mode $awbMode selected, but AWB mode can't be set directly without Camera2 interop")
                    
                    // In a production app, you could register an extension to handle this
                    // For now, we'll just log the request
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process white balance mode: ${e.message}")
                }
            }
            
            // Apply brightness via exposure compensation (standard CameraX API)
            filterConfig.brightness?.let { brightness ->
                val scaledBrightness = brightness.toFloat()
                try {
                    val exposureState = camera.cameraInfo.exposureState
                    if (exposureState.isExposureCompensationSupported) {
                        val range = exposureState.exposureCompensationRange
                        val index = (range.lower + (scaledBrightness * (range.upper - range.lower))).toInt()
                        val clampedIndex = index.coerceIn(range.lower, range.upper)
                        cameraControl.setExposureCompensationIndex(clampedIndex)
                        Log.d(TAG, "Set exposure compensation index: $clampedIndex")
                    } else {
                        Log.w(TAG, "Exposure compensation not supported")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set exposure compensation: ${e.message}")
                }
            }
            
            // Apply ISO if provided
            filterConfig.iso?.let { iso ->
                try {
                    // Note: Setting ISO directly is not available through the standard
                    // CameraX API without Camera2 interop. We'll log the info but can't set it
                    // without setCaptureRequestOption
                    Log.d(TAG, "ISO value ${iso.toInt()} selected, but ISO can't be set directly without Camera2 interop")
                    
                    // In a production app, you could register an extension to handle this
                    // For now, we'll just log the request
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process ISO request: ${e.message}")
                }
            }
            
            // Other real-time adjustments would typically need to be applied to the preview frames
            // through image analysis or post-processing
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply camera settings", e)
        }
    }
    
    /**
     * Processes a saved image with filters
     */
    private fun processImageWithFilters(imagePath: String, filterConfig: FilterConfig) {
        // Load the image from the file
        val imageProcessor = imageProcessor ?: return
        
        try {
            // Load bitmap from file
            val options = android.graphics.BitmapFactory.Options()
            options.inMutable = true
            var bitmap = android.graphics.BitmapFactory.decodeFile(imagePath, options)
            
            // Apply filters based on the configuration
            if (bitmap != null) {
                when (filterConfig.filterType) {
                    "beauty" -> {
                        val parameters = filterConfig.parameters
                        val smoothness = (parameters?.get("smoothness") as? Double)?.toFloat() ?: 0.5f
                        val brightness = (parameters?.get("brightness") as? Double)?.toFloat() ?: 0.5f
                        bitmap = imageProcessor.applyBeautyFilter(bitmap, smoothness, brightness)
                    }
                    
                    "vintage" -> {
                        val parameters = filterConfig.parameters
                        val intensity = (parameters?.get("intensity") as? Double)?.toFloat() ?: 0.7f
                        bitmap = imageProcessor.applyVintageFilter(bitmap, intensity)
                    }
                    
                    "black_and_white" -> {
                        val contrast = filterConfig.contrast?.toFloat() ?: 1.2f
                        bitmap = imageProcessor.applyBlackAndWhiteFilter(bitmap, contrast)
                    }
                    
                    // Apply camera effect modes
                    else -> {
                        filterConfig.effectMode?.let { effectMode ->
                            bitmap = imageProcessor.applyCameraEffect(bitmap, effectMode)
                        }
                    }
                }
                
                // Apply additional adjustments if specified
                filterConfig.brightness?.let { brightness ->
                    bitmap = imageProcessor.adjustBrightness(bitmap, brightness.toFloat())
                }
                
                filterConfig.saturation?.let { saturation ->
                    bitmap = imageProcessor.adjustSaturation(bitmap, saturation.toFloat())
                }
                
                filterConfig.contrast?.let { contrast ->
                    bitmap = imageProcessor.adjustContrast(bitmap, contrast.toFloat())
                }
                
                // Save the processed bitmap back to the file
                val outputStream = java.io.FileOutputStream(imagePath)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, outputStream)
                outputStream.flush()
                outputStream.close()
                
                // Recycle bitmap to free memory
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image with filters", e)
        }
    }
    
    /**
     * Ensures the preview handler is initialized
     * This is called before startPreview to make sure we have a valid preview handler
     */
    fun ensurePreviewHandlerInitialized(textureId: Long, callback: (Result<Unit>) -> Unit) {
        try {
            // If we already have a valid previewHandler with matching ID, just return success
            if (previewHandler != null && previewHandler!!.getTextureId() == textureId) {
                Log.d(TAG, "Preview handler already initialized with ID: $textureId")
                callback(Result.success(Unit))
                return
            }
            
            Log.d(TAG, "Initializing preview handler for texture ID: $textureId")
            
            // Create the preview handler if it doesn't exist
            if (previewHandler == null) {
                previewHandler = CameraPreviewTextureHandler(textureRegistry, mainExecutor)
                val newTextureId = previewHandler!!.init()
                
                if (newTextureId != textureId) {
                    Log.w(TAG, "Created texture ID ($newTextureId) does not match requested ID ($textureId)")
                    // We'll continue anyway and let the caller decide what to do
                }
            }
            
            val actualTextureId = previewHandler!!.getTextureId()
            Log.d(TAG, "Preview handler initialized with texture ID: $actualTextureId")
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize preview handler", e)
            callback(Result.failure(FlutterError(
                "preview_handler_init_error",
                "Failed to initialize preview handler: ${e.message}",
                null
            )))
        }
    }
} 