package com.beauty.camera_plugin

import android.annotation.SuppressLint
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
import android.view.Surface
/**
 * CameraManager - Singleton for CameraX Management
 * 
 * SINGLE RESPONSIBILITY: Quản lý tất cả CameraX operations
 * 
 * Design Pattern: Singleton + Lifecycle-aware
 * Thread Safety: Main thread operations
 */
@SuppressLint("StaticFieldLeak")
object CameraManager {
    private const val TAG = "CameraManager"
    
    // CameraX components
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    
    // State
    private var context: Context? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var currentFacing = CameraSelector.LENS_FACING_BACK
    private var isInitialized = false
    
    private val mainExecutor: Executor get() = ContextCompat.getMainExecutor(context!!)
    
    /**
     * Initialize CameraManager
     * @param context Application context
     * @param lifecycleOwner Activity lifecycle owner
     * @return Success status
     */
    fun initialize(context: Context, lifecycleOwner: LifecycleOwner): Boolean {
        return try {
            this.context = context.applicationContext
            this.lifecycleOwner = lifecycleOwner
            
            // Initialize CameraX
            cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            
            isInitialized = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CameraManager", e)
            false
        }
    }
    
    /**
     * Initialize camera with settings and surface producer
     * @param settings Camera settings from Flutter
     * @param surfaceProducer Flutter surface producer
     * @param enableOpenGL Enable OpenGL filter processing
     * @param onSuccess Success callback
     * @param onError Error callback with message
     */
    fun initializeCamera(
        settings: AdvancedCameraSettings,
        surfaceProducer: FlutterSurfaceProducer,
        enableOpenGL: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isInitialized) {
            onError("CameraManager not initialized")
            return
        }
        
        
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
                
                // Setup preview with Flutter surface producer
                if (enableOpenGL) {
                    setupPreviewWithOpenGL(surfaceProducer)
                } else {
                    setupPreviewWithSurfaceProducer(surfaceProducer)
                }
                
                // Bind camera
                bindCamera()
                
                onSuccess()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing camera", e)
                onError("Failed to initialize camera: ${e.message}")
            }
        }, mainExecutor)
    }

    
    /**
     * Switch camera between front and back
     * @return New camera facing (LENS_FACING_FRONT or LENS_FACING_BACK)
     */
    fun switchCamera(): Int {
        if (!isInitialized) {
            Log.e(TAG, "Cannot switch camera - not initialized")
            return currentFacing
        }
        
        try {
            // Switch facing
            currentFacing = if (currentFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            
            
            // Rebind camera with new facing
            bindCamera()
            
            return currentFacing
            
        } catch (e: Exception) {
            Log.e(TAG, "Error switching camera", e)
            return currentFacing
        }
    }
    
    
    /**
     * Get preview resolution with callback
     * @param callback Callback with PreviewSize result
     */
    fun getPreviewSize(callback: (Result<PreviewSize>) -> Unit) {
        try {
            if (!isReady()) {
                // Default fallback size
                callback(Result.success(PreviewSize(width = 1280, height = 720)))
                return
            }
            
            val resolutionInfo = preview?.resolutionInfo
            val resolution = resolutionInfo?.resolution
            val rotation = resolutionInfo?.rotationDegrees ?: 0
            
            if (resolution != null) {
                // Adjust for device rotation
                val isSideways = rotation == 90 || rotation == 270
                val width = if (isSideways) resolution.height else resolution.width
                val height = if (isSideways) resolution.width else resolution.height
                
                val previewSize = PreviewSize(width = width.toLong(), height = height.toLong())
                callback(Result.success(previewSize))
            } else {
                callback(Result.success(PreviewSize(width = 1280, height = 720)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting preview resolution", e)
            callback(Result.failure(e))
        }
    }

    
    /**
     * Check if camera is ready
     */
   private fun isReady(): Boolean {
        return isInitialized && camera != null && preview != null
    }
    
    /**
     * Check if CameraManager is initialized
     */
    fun isInitialized(): Boolean {
        return isInitialized
    }
    
    /**
     * Stop camera without disposing CameraManager
     * Used when Flutter dispose is called but Activity is still alive
     */
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            preview = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }
    
    /**
     * Dispose camera resources completely
     * Used when Activity is destroyed
     */
    fun dispose() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            preview = null
            cameraProvider = null
            cameraProviderFuture = null
            context = null
            lifecycleOwner = null
            isInitialized = false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing CameraManager", e)
        }
    }
    
    // --- Private Methods ---
    
    private fun setupPreviewWithOpenGL(surfaceProducer: FlutterSurfaceProducer) {
        
        // Initialize OpenGL Manager
        val context = this.context ?: run {
            Log.e(TAG, "Context is null, cannot initialize OpenGL")
            return
        }
        
        val openGLInitialized = OpenGLManager.initialize(surfaceProducer.getSurface())
        
        if (!openGLInitialized) {
            Log.e(TAG, "Failed to initialize OpenGL Manager")
            return
        }
        
        
        preview = Preview.Builder()
            .setTargetRotation(Surface.ROTATION_90)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .build()
            .also { 
                // Create surface provider that uses OpenGL Manager
                val surfaceProvider = Preview.SurfaceProvider { request ->
                    val resolution = request.resolution
                    
                    // Set output size for OpenGL
                    OpenGLManager.setOutputSize(resolution.width, resolution.height)
                    
                    // For OpenGL rendering, we need to create SurfaceTexture first
                    // CameraX will provide the texture ID when we create SurfaceTexture
                    try {
                        // Create a temporary texture to get texture ID
                        val textures = IntArray(1)
                        android.opengl.GLES20.glGenTextures(1, textures, 0)
                        val textureId = textures[0]
                        
                        
                        // Set camera input with the generated texture ID
                        OpenGLManager.setCameraInput(textureId, resolution.width, resolution.height)
                        
                        // Initialize renderer when OpenGL context is ready
                        OpenGLManager.initializeRenderer()
                        
                        // Get the SurfaceTexture created by OpenGLManager
                        val cameraSurfaceTexture = OpenGLManager.getCameraSurfaceTexture()
                        if (cameraSurfaceTexture != null) {
                            
                            // Provide OpenGL surface to CameraX
                            request.provideSurface(Surface(cameraSurfaceTexture), mainExecutor) {
                                // Frame callback is already set up in OpenGLManager.setCameraInput()
                            }
                        } else {
                            Log.e(TAG, "Failed to get OpenGL SurfaceTexture after setup")
                            // Fallback to direct surface
                            request.provideSurface(surfaceProducer.getSurface(), mainExecutor) {
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting up OpenGL camera input", e)
                        // Fallback to direct surface
                        request.provideSurface(surfaceProducer.getSurface(), mainExecutor) {
                            Log.d(TAG, "Fallback: Direct surface provided to CameraX")
                        }
                    }
                }

                it.surfaceProvider = surfaceProvider
            }
    }

    private fun setupPreviewWithSurfaceProducer(surfaceProducer: FlutterSurfaceProducer) {
        
        preview = Preview.Builder()
            .setTargetRotation(Surface.ROTATION_90)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .build()
            .also { 
                // Create surface provider that uses the Flutter surface
                val surfaceProvider = Preview.SurfaceProvider { request ->
                    val resolution = request.resolution
                    
                    try {
                        // Set surface size in Flutter texture
                        surfaceProducer.setSize(resolution.width, resolution.height)
                        
                        // Get Flutter surface
                        val flutterSurface = surfaceProducer.getSurface()
                        
                        // Provide Flutter surface to CameraX
                        request.provideSurface(flutterSurface, mainExecutor) {
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting Flutter surface", e)
                        // This will cause the app to crash, but we need to see the error
                        throw e
                    }
                }

                it.surfaceProvider = surfaceProvider
            }
    }

    
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
            camera = provider.bindToLifecycle(
                owner,
                cameraSelector,
                preview
            )
            
            
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
            throw e
        }
    }
}
