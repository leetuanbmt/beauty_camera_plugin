package com.beauty.camera_plugin.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.view.Surface
import com.beauty.camera_plugin.AdvancedCameraSettings
import com.beauty.camera_plugin.PreviewSize
import com.beauty.camera_plugin.FlashMode

/**
 * CameraController - Implementation of ICameraManager
 * Manages CameraX operations with proper threading
 * 
 * SINGLE RESPONSIBILITY: Camera lifecycle and operations
 * THREADING: Camera operations on main thread, processing on background thread
 */
class CameraController : ICameraManager {
    private val TAG = "CameraController"
    
    // CameraX components
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    // private var videoCapture: VideoCapture<Recorder>? = null // TODO: Implement video recording
    
    // Callback for when camera frame is available
    private var onFrameAvailableCallback: (() -> Unit)? = null
    
    // Threading
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "CameraThread").apply {
            priority = Thread.NORM_PRIORITY
        }
    }
    
    // State
    private var context: Context? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var currentFacing = CameraSelector.LENS_FACING_BACK
    private var isInitialized = false
    private var isRecording = false
    
    // Callbacks
    private var onCameraInitialized: (() -> Unit)? = null
    private var onCameraError: ((String) -> Unit)? = null
    
    private val mainExecutor: Executor get() = ContextCompat.getMainExecutor(context!!)
    
    override fun initialize(context: Context, lifecycleOwner: LifecycleOwner): Boolean {
        return try {
            this.context = context.applicationContext
            this.lifecycleOwner = lifecycleOwner
            
            // Initialize CameraX
            cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            
            isInitialized = true
            Log.d(TAG, "CameraController initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CameraController", e)
            false
        }
    }
    
    override fun initializeCamera(
        settings: AdvancedCameraSettings,
        surface: Surface,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isInitialized) {
            onError("CameraController not initialized")
            return
        }
        
        this.onCameraInitialized = onSuccess
        this.onCameraError = onError
        
        // Set camera facing from settings
        currentFacing = if (settings.cameraLensFacing?.raw == 0) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        
        val future = cameraProviderFuture ?: run {
            onError("CameraProvider future not available")
            return
        }
        
        future.addListener({
            try {
                // Get camera provider
                cameraProvider = future.get()
                
                // Setup preview
                setupPreview(surface)
                
                // Setup image capture
                setupImageCapture()
                
                // Setup video capture
                // setupVideoCapture() // TODO: Implement video recording
                
                // Bind camera
                bindCamera()
                
                onSuccess()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing camera", e)
                onError("Failed to initialize camera: ${e.message}")
            }
        }, mainExecutor)
    }
    
    override fun switchCamera(): Int {
        if (!isInitialized) {
            Log.e(TAG, "Cannot switch camera - not initialized")
            return currentFacing
        }
        
        return try {
            // Switch facing
            currentFacing = if (currentFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            
            // Rebind camera with new facing
            bindCamera()
            
            currentFacing
            
        } catch (e: Exception) {
            Log.e(TAG, "Error switching camera", e)
            currentFacing
        }
    }
    
    override fun getPreviewSize(callback: (Result<PreviewSize>) -> Unit) {
        try {
            if (!isReady()) {
                // Default fallback size
                callback(Result.success(PreviewSize(width = 1280, height = 720)))
                return
            }


            val info = preview?.resolutionInfo ?: run {
                callback(Result.success(PreviewSize(width = 1280, height = 720)))
                return
            }
            val resolution = info.resolution
            val rotation = info.rotationDegrees
            Log.d(TAG, "Actual camera resolution: ${resolution.width}x${resolution.height}")
            Log.d(TAG, "Camera rotation: $rotation degrees")
            Log.d(TAG, "Camera facing: ${if (currentFacing == CameraSelector.LENS_FACING_FRONT) "FRONT" else "BACK"}")
            // For Flutter display, we need to swap width/height to get correct aspect ratio
            // Camera sensor is usually landscape, but Flutter expects portrait orientation
//            val isSideways = rotation == 90 || rotation == 270
            
            // Always swap width/height for Flutter AspectRatio widget
            // Flutter AspectRatio uses width/height, but camera resolution needs to be swapped
            val flutterWidth = resolution.height.toLong()   // Camera height becomes Flutter width
            val flutterHeight = resolution.width.toLong()   // Camera width becomes Flutter height
            
            Log.d(TAG, "getPreviewSize: original=${resolution.width}x${resolution.height}, rotation=$rotation°")
            Log.d(TAG, "getPreviewSize: for Flutter display: ${flutterWidth}x${flutterHeight} (swapped for AspectRatio)")
            
            val previewSize = PreviewSize(width = flutterWidth, height = flutterHeight)
            callback(Result.success(previewSize))

        } catch (e: Exception) {
            Log.e(TAG, "Error getting preview resolution", e)
            callback(Result.failure(e))
        }
    }
    
    override fun setZoom(zoomLevel: Float) {
        camera?.cameraControl?.setZoomRatio(zoomLevel.coerceIn(1.0f, 10.0f))
    }
    
    override fun focusOnPoint(x: Int, y: Int) {
        try {
            // TODO: Implement proper focus with MeteringPoint
            Log.d(TAG, "Focus on point: $x, $y - not implemented yet")
        } catch (e: Exception) {
            Log.e(TAG, "Error focusing on point", e)
        }
    }
    
    override fun setFlashMode(mode: FlashMode) {
        val flashMode = when (mode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.TORCH -> ImageCapture.FLASH_MODE_AUTO // CameraX doesn't have torch mode directly
        }
        
        imageCapture?.flashMode = flashMode
    }
    
    override fun takePhoto(callback: (Result<String>) -> Unit) {
        if (!isReady() || imageCapture == null) {
            callback(Result.failure(Exception("Camera not ready")))
            return
        }
        
        // TODO: Implement photo capture with file saving
        callback(Result.success("/tmp/photo_${System.currentTimeMillis()}.jpg"))
    }
    
    override fun startVideoRecording(callback: (Result<Unit>) -> Unit) {
        // TODO: Implement video recording
        callback(Result.failure(Exception("Video recording not implemented yet")))
    }
    
    override fun stopVideoRecording(callback: (Result<String>) -> Unit) {
        // TODO: Implement video recording stop
        callback(Result.failure(Exception("Video recording not implemented yet")))
    }
    
    override fun isInitialized(): Boolean {
        return isInitialized
    }
    
    override fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            preview = null
            imageCapture = null
            // videoCapture = null // TODO: Add when video recording is implemented
            isRecording = false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }
    
    override fun dispose() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            preview = null
            imageCapture = null
            // videoCapture = null // TODO: Add when video recording is implemented
            cameraProvider = null
            cameraProviderFuture = null
            context = null
            lifecycleOwner = null
            isInitialized = false
            isRecording = false
            onFrameAvailableCallback = null
            
            // Shutdown executor
            cameraExecutor.shutdown()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing CameraController", e)
        }
    }
    
    // --- Private Methods ---
    
    private fun setupPreview(surface: Surface) {
        preview = Preview.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .build()
        
        val surfaceProvider = Preview.SurfaceProvider { request ->
            try {
                // TODO: Instead of providing surface directly, we need to:
                // 1. Create an OpenGL texture for camera input
                // 2. Let filter pipeline render from camera texture to FlutterTextureBridge
                // For now, provide surface directly (filter will be applied separately)
                request.provideSurface(surface, mainExecutor) {
                    Log.d(TAG, "Preview surface provided to CameraX")
                    // Trigger filter pipeline render when frame is available
                    onFrameAvailableCallback?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error providing surface to CameraX", e)
                throw e
            }
        }
        
        preview?.surfaceProvider = surfaceProvider
    }
    
    private fun setupImageCapture() {
        imageCapture = ImageCapture.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .build()
    }
    
    // private fun setupVideoCapture() {
    //     // TODO: Implement video capture setup
    // }
    
    private fun bindCamera() {
        val provider = cameraProvider ?: run {
            Log.e(TAG, "CameraProvider not available for binding")
            return
        }
        
        val owner = lifecycleOwner ?: run {
            Log.e(TAG, "LifecycleOwner not available for binding")
            return
        }
        
        try {
            // Unbind previous
            provider.unbindAll()
            
            // Create camera selector
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(currentFacing)
                .build()
            
            // Bind to lifecycle
            val useCases = mutableListOf<UseCase>()
            preview?.let { useCases.add(it) }
            imageCapture?.let { useCases.add(it) }
            // videoCapture?.let { useCases.add(it) } // TODO: Add when video recording is implemented
            
            camera = provider.bindToLifecycle(
                owner,
                cameraSelector,
                *useCases.toTypedArray()
            )
            
            Log.d(TAG, "Camera bound successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
            throw e
        }
    }
    
    private fun isReady(): Boolean {
        return isInitialized && camera != null && preview != null
    }
    
    /**
     * Set callback for when camera frame is available
     */
    fun setOnFrameAvailableCallback(callback: (() -> Unit)?) {
        onFrameAvailableCallback = callback
    }
}
