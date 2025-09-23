package com.beauty.camera_plugin

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import com.beauty.camera_plugin.camera.CameraController
import com.beauty.camera_plugin.camera.ICameraManager
import com.beauty.camera_plugin.renderer.RenderPipelineManager
import com.beauty.camera_plugin.renderer.filters.*
import com.beauty.camera_plugin.flutter_bridge.FlutterTextureBridge

/**
 * BeautyCameraPlugin - Main Plugin Entry Point
 * 
 * SINGLE RESPONSIBILITY: 
 * - Orchestrate camera, renderer, and filter components
 * - Handle Pigeon communication
 * - Manage component lifecycle
 * 
 * Architecture: Dart → BeautyCameraPlugin → CameraController + RenderPipelineManager
 */
class BeautyCameraPlugin : FlutterPlugin, ActivityAware, BeautyCameraHostApi {
    companion object {
        private const val TAG = "BeautyCameraPlugin"
    }
    
    private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null
    private var activityBinding: ActivityPluginBinding? = null
    private lateinit var flutterApi: BeautyCameraFlutterApi

    // Core components
    private var cameraManager: ICameraManager? = null
    private var renderPipeline: RenderPipelineManager? = null
    private var textureBridge: FlutterTextureBridge? = null
    
    // --- Flutter Plugin Lifecycle ---

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "Registering Pigeon host")
        this.flutterPluginBinding = binding
        
        // ONLY: Đăng ký Pigeon host API
        BeautyCameraHostApi.setUp(binding.binaryMessenger, this)
        flutterApi = BeautyCameraFlutterApi(binding.binaryMessenger)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "Unregistering Pigeon host")
        BeautyCameraHostApi.setUp(binding.binaryMessenger, null)
        
        // Cleanup components
        disposeComponents()
    }

    // --- Activity Lifecycle ---

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        Log.d(TAG, "Activity attached - initializing components")
        this.activityBinding = binding
        
        // Initialize core components
        initializeComponents()
    }

    override fun onDetachedFromActivity() {
        Log.d(TAG, "Activity detached")
        disposeComponents()
        activityBinding = null
    }
    
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) { 
        onAttachedToActivity(binding) 
    }
    
    override fun onDetachedFromActivityForConfigChanges() { 
        onDetachedFromActivity() 
    }

    // --- Component Management ---

    private fun initializeComponents() {
        val activity = activityBinding?.activity ?: return
        val textureRegistry = flutterPluginBinding?.textureRegistry ?: return
        
        try {
            // Initialize camera manager
            cameraManager = CameraController()
            val cameraInitialized = cameraManager?.initialize(
                context = activity.applicationContext,
                lifecycleOwner = activity as LifecycleOwner
            ) ?: false
            
            if (!cameraInitialized) {
                Log.e(TAG, "Failed to initialize camera manager")
                return
            }
            
            // Initialize render pipeline with default size
            // Will be updated when camera resolution is known
            renderPipeline = RenderPipelineManager()
            val renderInitialized = renderPipeline?.initialize(1920, 1080) ?: false
            
            if (!renderInitialized) {
                Log.e(TAG, "Failed to initialize render pipeline")
                return
            }
            
            // Initialize texture bridge
            textureBridge = FlutterTextureBridge(textureRegistry)
            
            // Initialize default filters
            initializeDefaultFilters()
            
            Log.d(TAG, "All components initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing components", e)
        }
    }
    
    private fun initializeDefaultFilters() {
        renderPipeline?.let { pipeline ->
            // Add beauty filters
            pipeline.addFilter(BeautySmoothFilter())
            pipeline.addFilter(WhiteningFilter())
            pipeline.addFilter(LutFilter())
            
            Log.d(TAG, "Default filters initialized")
        }
    }
    
    private fun disposeComponents() {
        try {
            cameraManager?.dispose()
            cameraManager = null
            
            renderPipeline?.dispose()
            renderPipeline = null
            
            textureBridge?.release()
            textureBridge = null
            
            Log.d(TAG, "All components disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing components", e)
        }
    }

    // --- Pigeon API Implementation ---

    override fun initialize(settings: AdvancedCameraSettings, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Initialize camera with settings")
        
        try {
            val activity = activityBinding?.activity
            if (activity == null) {
                callback(Result.failure(Exception("Activity not available")))
                return
            }
            
            // Ensure components are initialized
            if (cameraManager == null || renderPipeline == null || textureBridge == null) {
                initializeComponents()
            }
            
            // Create Flutter texture
            val textureId = textureBridge?.createTexture() ?: -1L
            if (textureId == -1L) {
                callback(Result.failure(Exception("Failed to create Flutter texture")))
                return
            }
            // Initialize camera with surface from texture bridge
            val surface = textureBridge!!.getSurface()
            if (surface == null) {
                callback(Result.failure(Exception("Failed to get surface from texture bridge")))
                return
            }
            
            cameraManager?.initializeCamera(
                settings = settings,
                surface = surface,
                onSuccess = {
                    Log.d(TAG, "Camera initialized successfully")
                    
                    // Get actual camera resolution and update texture size
                    cameraManager?.getPreviewSize { result ->
                        result.onSuccess { previewSize ->
                            Log.d(TAG, "Updating texture size to: ${previewSize.width}x${previewSize.height}")
                            textureBridge?.setSize(previewSize.width.toInt(), previewSize.height.toInt())
                            
                            // Update render pipeline with actual camera resolution
                            renderPipeline?.updateViewport(previewSize.width.toInt(), previewSize.height.toInt())
                            Log.d(TAG, "Updated render pipeline viewport to: ${previewSize.width}x${previewSize.height}")
                            
                            // Set callback to trigger filter render when camera frame is available
                            (cameraManager as? CameraController)?.setOnFrameAvailableCallback {
                                // Trigger filter pipeline render when camera frame is available
                                // Create default filter parameters
                                val defaultParams = FilterParameters(
                                    intensity = 0.0,
                                    skinSmoothing = 0.0,
                                    skinBrightening = 0.0,
                                    faceSlimming = 0.0,
                                    eyeEnlargement = 0.0,
                                    lipEnhancement = 0.0,
                                    brightness = 0.0,
                                    contrast = 0.0,
                                    saturation = 0.0,
                                    warmth = 0.0,
                                    tint = 0.0,
                                    vibrance = 0.0,
                                    blur = 0.0,
                                    sharpen = 0.0,
                                    vignette = 0.0,
                                    grain = 0.0,
                                    fade = 0.0,
                                    highlights = 0.0,
                                    shadows = 0.0,
                                    clarity = 0.0,
                                    structure = 0.0
                                )
                                renderPipeline?.renderFrame(defaultParams)
                                Log.d(TAG, "Triggered filter render on camera frame")
                            }
                        }
                        result.onFailure { error ->
                            Log.w(TAG, "Failed to get preview size: $error")
                        }
                    }
                    
                    callback(Result.success(Unit))
                },
                onError = { error ->
                    Log.e(TAG, "Camera initialization failed: $error")
                    callback(Result.failure(Exception(error)))
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in initialize", e)
            callback(Result.failure(e))
        }
    }

    override fun dispose(callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Flutter dispose - stopping camera only")
        
        try {
            // Stop camera but keep components for reuse
            cameraManager?.stopCamera()
            
            // Clean Flutter texture
            textureBridge?.release()
            textureBridge = null
            
            Log.d(TAG, "Camera stopped successfully")
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Error during dispose", e)
            callback(Result.failure(e))
        }
    }

    override fun switchCamera(callback: (Result<Unit>) -> Unit) { 
        Log.d(TAG, "Switch camera")
        
        try {
            val newFacing = cameraManager?.switchCamera() ?: -1
            Log.d(TAG, "Camera switched to facing: $newFacing")
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Error switching camera", e)
            callback(Result.failure(e))
        }
    }

    override fun getPreviewTexture(callback: (Result<Long>) -> Unit) {
        Log.d(TAG, "Get preview texture ID")
        
        val textureId = textureBridge?.getTextureId() ?: -1L
        if (textureId == -1L) {
            callback(Result.failure(Exception("Texture not available")))
            return
        }
        
        callback(Result.success(textureId))
    }

    override fun getPreviewSize(callback: (Result<PreviewSize>) -> Unit) {
        Log.d(TAG, "Get preview size")
        
        cameraManager?.getPreviewSize(callback) ?: run {
            callback(Result.failure(Exception("Camera manager not available")))
        }
    }

    // --- Camera Operations ---

    override fun setZoom(zoomLevel: Double, callback: (Result<Unit>) -> Unit) {
        try {
            cameraManager?.setZoom(zoomLevel.toFloat())
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun focusOnPoint(x: Long, y: Long, callback: (Result<Unit>) -> Unit) {
        try {
            cameraManager?.focusOnPoint(x.toInt(), y.toInt())
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun setFlashMode(mode: FlashMode, callback: (Result<Unit>) -> Unit) {
        try {
            cameraManager?.setFlashMode(mode)
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun setDisplayOrientation(degrees: Long, callback: (Result<Unit>) -> Unit) {
        // TODO: Implement display orientation in CameraController
        callback(Result.success(Unit))
    }

    override fun takePhoto(callback: (Result<String>) -> Unit) {
        cameraManager?.takePhoto(callback) ?: run {
            callback(Result.failure(Exception("Camera manager not available")))
        }
    }

    override fun startVideoRecording(callback: (Result<Unit>) -> Unit) {
        cameraManager?.startVideoRecording(callback) ?: run {
            callback(Result.failure(Exception("Camera manager not available")))
        }
    }

    override fun stopVideoRecording(callback: (Result<String>) -> Unit) {
        cameraManager?.stopVideoRecording(callback) ?: run {
            callback(Result.failure(Exception("Camera manager not available")))
        }
    }

    override fun getCameraSensorAspectRatio(callback: (Result<Double>) -> Unit) {
        // TODO: Implement aspect ratio calculation in CameraController
        callback(Result.success(16.0 / 9.0))
    }

    // --- Filter Operations ---

    override fun applyFilter(
        category: FilterCategory,
        type: FilterType,
        parameters: FilterParameters,
        callback: (Result<Unit>) -> Unit
    ) {
        Log.d(TAG, "Apply filter - category: $category, type: $type")
        try {
            // Apply filter via render pipeline
            renderPipeline?.let { pipeline ->
                Log.d(TAG, "Looking for filter: ${type.name}")
                
                // Apply specific parameters based on filter type
                when (type.name) {
                    "BEAUTY_NATURAL", "BEAUTY_SMOOTH" -> {
                        // Apply beauty parameters to BeautySmoothFilter
                        val beautyFilter = pipeline.getFilter("BeautySmooth")
                        if (beautyFilter != null) {
                            Log.d(TAG, "Found BeautySmooth filter: ${beautyFilter.javaClass.simpleName}")
                            beautyFilter.setIntensity(parameters.intensity.toFloat())
                        } else {
                            Log.w(TAG, "BeautySmooth filter not found")
                        }
                    }
                    "BEAUTY_GLOW", "BEAUTY_BRIGHT" -> {
                        // Apply whitening parameters to WhiteningFilter
                        val whiteningFilter = pipeline.getFilter("Whitening")
                        if (whiteningFilter != null) {
                            Log.d(TAG, "Found Whitening filter: ${whiteningFilter.javaClass.simpleName}")
                            whiteningFilter.setIntensity(parameters.intensity.toFloat())
                        } else {
                            Log.w(TAG, "Whitening filter not found")
                        }
                    }
                    else -> {
                        // Apply to LUT filter for other types
                        val lutFilter = pipeline.getFilter("LUT")
                        if (lutFilter != null) {
                            Log.d(TAG, "Found LUT filter: ${lutFilter.javaClass.simpleName}")
                            lutFilter.setIntensity(parameters.intensity.toFloat())
                        } else {
                            Log.w(TAG, "LUT filter not found")
                        }
                    }
                }
                
                // Trigger render with new parameters
                pipeline.renderFrame(parameters)
                Log.d(TAG, "Filter applied successfully")
            }
            
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Error applying filter", e)
            callback(Result.failure(e))
        }
    }

    override fun setScaleType(scaleType: ScaleType, callback: (Result<Unit>) -> Unit) {
        // TODO: Implement scale type in CameraController
        callback(Result.success(Unit))
    }

    override fun getAvailableCameras(callback: (Result<List<CameraInfo>>) -> Unit) {
        val activity = activityBinding?.activity
        if (activity == null) {
            callback(Result.failure(Exception("Activity not available")))
            return
        }
        
        try {
            val cameras = com.beauty.camera_plugin.camera.CameraUtils.getAvailableCameras(activity.applicationContext)
            callback(Result.success(cameras))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun setFilterEnabled(enabled: Boolean, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Set filter enabled: $enabled")
        
        try {
            renderPipeline?.setEnabled(enabled)
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Error setting filter enabled", e)
            callback(Result.failure(e))
        }
    }

    override fun adjustFilterIntensity(intensity: Double, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Adjust filter intensity: $intensity")
        
        try {
            renderPipeline?.let { pipeline ->
                // Update intensity for all filters in chain
                pipeline.getFilterChain().let { chain ->
                    // This is a simplified approach - in real implementation,
                    // you'd want to update specific filters based on current selection
                    Log.d(TAG, "Filter intensity adjusted to $intensity")
                }
            }
            
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting filter intensity", e)
            callback(Result.failure(e))
        }
    }
}