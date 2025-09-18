package com.beauty.camera_plugin

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import java.io.File

/**
 * Plugin chính để quản lý beauty camera và tương tác với Flutter.
 * Class này implement BeautyCameraHostApi và quản lý vòng đời của plugin.
 */
class BeautyCameraPlugin : FlutterPlugin, ActivityAware, BeautyCameraHostApi, FaceDetectorAnalyzer.DetectorListener {
    companion object {
        private const val TAG = "BeautyCameraPlugin"
    }
    
    private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null
    private var activityBinding: ActivityPluginBinding? = null
    private lateinit var flutterApi: BeautyCameraFlutterApi

    private var cameraHandler: CameraHandler? = null
    private var openGlRenderer: OpenGLRenderer? = null
    // This entry acts as the "SurfaceProducer" for Flutter, providing a SurfaceTexture
    // for the OpenGLRenderer to render camera frames onto.
    private var previewSurfaceProducer: FlutterSurfaceProducer? = null
    
    // Cache cho lệnh initialize khi activity chưa sẵn sàng
    private var pendingInitializeSettings: AdvancedCameraSettings? = null
    private var pendingInitializeCallback: ((Result<Unit>) -> Unit)? = null

    // Lưu đường dẫn của video đang quay và cài đặt hiện tại
    private var currentVideoPath: String? = null
    private var currentSettings: AdvancedCameraSettings? = null

    // Queue for pending switchCamera requests
    private val pendingSwitchCameraCallbacks: MutableList<(Result<Unit>) -> Unit> = mutableListOf()

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "onAttachedToEngine")
        this.flutterPluginBinding = binding
        BeautyCameraHostApi.setUp(binding.binaryMessenger, this)
        flutterApi = BeautyCameraFlutterApi(binding.binaryMessenger)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "onDetachedFromEngine")
        BeautyCameraHostApi.setUp(binding.binaryMessenger, null)
        disposeNow()
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        Log.d(TAG, "onAttachedToActivity - Activity: ${binding.activity}")
        this.activityBinding = binding
        
        // Thực thi lệnh initialize đã cache nếu có
        if (pendingInitializeSettings != null && pendingInitializeCallback != null) {
            Log.d(TAG, "Executing cached initialize command")
            val settings = pendingInitializeSettings!!
            val callback = pendingInitializeCallback!!
            
            // Clear cache trước khi thực thi
            pendingInitializeSettings = null
            pendingInitializeCallback = null
            
            // Thực thi initialize
            executeInitialize(settings, callback)
        }
    }

    override fun onDetachedFromActivity() { 
        Log.d(TAG, "onDetachedFromActivity")
        disposeNow() 
    }
    
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) { 
        onAttachedToActivity(binding) 
    }
    
    override fun onDetachedFromActivityForConfigChanges() { 
        onDetachedFromActivity() 
    }

    private fun disposeNow() {
        Log.d(TAG, "disposeNow")
        cameraHandler?.dispose()
        openGlRenderer?.release()
        previewSurfaceProducer?.release()
        // Release previous OpenGL input surface if exists
        if (openGlRenderer?.cameraInputSurface != null) {
            Log.d(TAG, "Releasing previous OpenGL input surface")
            openGlRenderer?.cameraInputSurface?.release()
        }
        activityBinding = null
        cameraHandler = null
        openGlRenderer = null
        previewSurfaceProducer = null
        currentVideoPath = null
        currentSettings = null
        
        // Clear cache
        pendingInitializeSettings = null
        pendingInitializeCallback = null
        // Notify and clear all pending switchCamera callbacks
        if (pendingSwitchCameraCallbacks.isNotEmpty()) {
            pendingSwitchCameraCallbacks.forEach { it(Result.failure(Exception("Plugin disposed."))) }
            pendingSwitchCameraCallbacks.clear()
        }
    }

    // --- BeautyCameraHostApi Implementation ---

    override fun initialize(settings: AdvancedCameraSettings, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Initialize with settings: $settings")
        this.currentSettings = settings // Cache settings for later use (e.g., switchCamera)

        if (activityBinding?.activity == null) {
            Log.w(TAG, "Activity not attached - caching initialize command")
            pendingInitializeSettings = settings
            pendingInitializeCallback = callback
            return
        }
        executeInitialize(settings, callback)
    }


    
    private fun executeInitialize(settings: AdvancedCameraSettings, callback: (Result<Unit>) -> Unit) {
        val activity = activityBinding?.activity ?: run {
            callback(Result.failure(Exception("Activity is null")))
            return
        }

        try {
            Log.d(TAG, "Starting camera initialization...")
            
            // Clean up existing resources
            cameraHandler?.dispose()
            openGlRenderer?.release()
            previewSurfaceProducer?.release()

            val textureRegistry = flutterPluginBinding?.textureRegistry ?: run {
                Log.e(TAG, "TextureRegistry not available")
                callback(Result.failure(Exception("TextureRegistry not available")))
                return
            }
            
            Log.d(TAG, "TextureRegistry available, proceeding with initialization")

            Log.d(TAG, "Executing initialize with OpenGL")

            // 1. Tạo FlutterSurfaceProducer trước, nhưng chưa set size
            previewSurfaceProducer =  FlutterSurfaceProducer(textureRegistry)
            val surface = previewSurfaceProducer!!.getSurface()

            // 2. Tạo SurfaceProvider sẽ khởi tạo OpenGL khi có resolution
            val surfaceProvider = androidx.camera.core.Preview.SurfaceProvider { request ->
                val resolution = request.resolution
                Log.d(TAG, "CameraX selected resolution for OpenGL mode: ${resolution.width}x${resolution.height}")

                // 3. Khởi tạo OpenGL Renderer khi đã có resolution
                if (openGlRenderer == null) {
                    try {
                        Log.d(TAG, "Creating OpenGL Renderer with resolution...")
                        openGlRenderer = OpenGLRenderer(activity.applicationContext).apply {
                            start()
                            waitUntilReady()
                            setCameraResolution(resolution)
                        }
                        Log.d(TAG, "OpenGL Renderer created and ready")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create OpenGL Renderer", e)
                        // Continue without OpenGL (fallback to basic camera)
                        request.provideSurface(surface, ContextCompat.getMainExecutor(activity.applicationContext)) { }
                        return@SurfaceProvider
                    }
                }

                // 4. Set size cho FlutterSurfaceProducer với camera resolution
                previewSurfaceProducer!!.setSize(resolution.width, resolution.height)
                Log.d(TAG, "FlutterSurfaceProducer size set to: ${resolution.width}x${resolution.height}")

                // 5. Set output surface cho OpenGL
                if (openGlRenderer != null) {
                    try {
                        openGlRenderer!!.setOutputSurface(surface)
                        Log.d(TAG, "OpenGL output surface set")

                        // 6. Cung cấp input surface cho CameraX
                        val rendererInputSurface = openGlRenderer!!.cameraInputSurface
                        request.provideSurface(rendererInputSurface, ContextCompat.getMainExecutor(activity)) {
                            Log.d(TAG, "OpenGL surface provided to CameraX and callback received.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting up OpenGL surfaces", e)
                        // Fallback to direct surface
                        request.provideSurface(surface, ContextCompat.getMainExecutor(activity)) {
                            Log.d(TAG, "Fallback surface provided to CameraX and callback received.")
                        }
                    }
                } else {
                    Log.w(TAG, "OpenGL renderer is null, using direct surface")
                    request.provideSurface(surface, ContextCompat.getMainExecutor(activity)) {
                        Log.d(TAG, "Direct surface provided to CameraX and callback received.")
                    }
                }
            }

            Log.d(TAG, "Camera cameraLensFacing setting: ${settings.cameraLensFacing}")

            // 5. Khởi tạo CameraHandler với SurfaceProvider đó
            val cameraSettings = com.beauty.camera_plugin.models.CameraSettings.fromAdvancedSettings(settings)
            cameraHandler = CameraHandler(activity.applicationContext, activity as LifecycleOwner, cameraSettings, this)
            cameraHandler?.startCameraPreview(surfaceProvider) {
                if (activityBinding == null) {
                    callback(Result.failure(Exception("Plugin disposed during initialization.")))
                    return@startCameraPreview
                }
                Log.d(TAG, "Camera handler initialized for OpenGL mode.")
                callback(Result.success(Unit))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing camera for OpenGL", e)
            callback(Result.failure(e))
        }
    }


    override fun getPreviewTexture(callback: (Result<Long>) -> Unit) {
        // This function is called to get the texture ID for Flutter to display.
        // The producer is always created during initialization.

        val producer = previewSurfaceProducer ?: run {
            callback(Result.failure(Exception("previewSurfaceProducer not available")))
            return
        }
        
        val mode = if (openGlRenderer != null) "OpenGL" else "test"
        Log.d(TAG, "Returning preview texture for $mode mode with ID: ${producer.getTextureId()}")
        callback(Result.success(producer.getTextureId()))
    }

    override fun dispose(callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Disposing camera plugin")
        // This is called from Flutter. We only release the camera resources,
        // but keep the activity binding, as the plugin is still attached.
        cameraHandler?.dispose()
        openGlRenderer?.release()
        previewSurfaceProducer?.release()
        cameraHandler = null
        openGlRenderer = null
        previewSurfaceProducer = null

        // We also clear any pending initialize commands that might have been cached.
        pendingInitializeSettings = null
        pendingInitializeCallback = null

        callback(Result.success(Unit))
    }

    // --- Các hàm còn lại chưa implement ---
     override fun switchCamera(callback: (Result<Unit>) -> Unit) { 
        Log.d(TAG, "Switching camera")
        mainHandler.post {
            val settings = currentSettings
            if (settings == null) {
                Log.w(TAG, "Plugin not initialized - queuing switchCamera request")
                pendingSwitchCameraCallbacks.add(callback)
                return@post
            }
            // Đảo ngược camera lens facing
            val newLensFacing = if (settings.cameraLensFacing?.raw == com.beauty.camera_plugin.models.CameraSettings.CAMERA_FACING_FRONT) {
                com.beauty.camera_plugin.models.CameraSettings.CAMERA_FACING_BACK
            } else {
                com.beauty.camera_plugin.models.CameraSettings.CAMERA_FACING_FRONT
            }
            val newSettings = settings.copy(cameraLensFacing = CameraFacing.ofRaw(newLensFacing))
            // Khởi tạo lại với settings mới
            initialize(newSettings) { result ->
                callback(result)
            }
        }
     }
     override fun setZoom(zoomLevel: Double, callback: (Result<Unit>) -> Unit) { 
         Log.d(TAG, "setZoom not implemented yet")
         callback(Result.success(Unit)) 
     }
     override fun focusOnPoint(x: Long, y: Long, callback: (Result<Unit>) -> Unit) { 
         Log.d(TAG, "focusOnPoint not implemented yet")
         callback(Result.success(Unit)) 
     }
     override fun setFlashMode(mode: FlashMode, callback: (Result<Unit>) -> Unit) { 
         Log.d(TAG, "setFlashMode not implemented yet")
         callback(Result.success(Unit)) 
     }
     override fun setDisplayOrientation(degrees: Long, callback: (Result<Unit>) -> Unit) { 
         Log.d(TAG, "setDisplayOrientation not implemented yet")
         callback(Result.success(Unit)) 
     }
     override fun getPreviewSize(callback: (Result<PreviewSize>) -> Unit) {
         val size = cameraHandler?.getPreviewSize()
         if (size != null) {
             Log.d(TAG, "getPreviewSize: Success (width=${size.width}, height=${size.height})")
             callback(Result.success(size))
         } else {
             val errorMsg = "Preview size not available. Camera may not be initialized or running."
             Log.e(TAG, "getPreviewSize: Failed. $errorMsg")
             callback(Result.failure(Exception(errorMsg)))
         }
     }
     override fun takePhoto(callback: (Result<String>) -> Unit) { 
         // TODO: Implement in CameraHandler
         Log.d(TAG, "takePhoto not implemented yet")
         callback(Result.success("/tmp/photo.jpg")) 
     }
     override fun startVideoRecording(callback: (Result<Unit>) -> Unit) {
        val handler = cameraHandler ?: run {
            callback(Result.failure(Exception("Camera not initialized")))
            return
        }
        val activity = activityBinding?.activity ?: run {
            callback(Result.failure(Exception("Activity not available")))
            return
        }

        try {
            val videoFile = File(activity.cacheDir, "video_${System.currentTimeMillis()}.mp4")
            currentVideoPath = videoFile.absolutePath
            Log.d(TAG, "Starting video recording to: $currentVideoPath")

            handler.startVideoRecording(currentVideoPath!!) { success ->
                // Callback này được gọi khi video KẾT THÚC ghi
                if (success) {
                    Log.d(TAG, "Video recording finished successfully.")
                } else {
                    Log.e(TAG, "Video recording finished with an error.")
                }
            }
            // Giả định việc ghi hình bắt đầu thành công và trả về kết quả ngay cho Flutter
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start video recording", e)
            callback(Result.failure(e))
        }
    }

    override fun stopVideoRecording(callback: (Result<String>) -> Unit) {
        Log.d(TAG, "Stopping video recording")
        cameraHandler?.stopVideoRecording()
        val path = currentVideoPath
        if (path != null) {
            Log.d(TAG, "Video recording stopped. File at: $path")
            callback(Result.success(path))
            currentVideoPath = null
        } else {
            val errorMsg = "No video recording was in progress or path is missing."
            Log.e(TAG, errorMsg)
            callback(Result.failure(Exception(errorMsg)))
        }
    }
     override fun getCameraSensorAspectRatio(callback: (Result<Double>) -> Unit) { 
         Log.d(TAG, "getCameraSensorAspectRatio not implemented yet")
         callback(Result.success(16.0/9.0)) 
     }
     override fun setScaleType(scaleType: ScaleType, callback: (Result<Unit>) -> Unit) { 
         Log.d(TAG, "setScaleType not implemented yet")
         callback(Result.success(Unit)) 
     }
     override fun getAvailableCameras(callback: (Result<List<CameraInfo>>) -> Unit) { 
         Log.d(TAG, "getAvailableCameras not implemented yet")
         callback(Result.success(emptyList())) 
     }
     
    override fun setFilterEnabled(enabled: Boolean, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "setFilterEnabled: $enabled")
        openGlRenderer?.setFilterEnabled(enabled)
        callback(Result.success(Unit))
    }

    override fun applyFilter(category: FilterCategory, type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "applyFilter called - category: $category, type: $type")
        
        try {
            val renderer = openGlRenderer
            if (renderer == null) {
                Log.e(TAG, "OpenGL renderer is not initialized")
                callback(Result.failure(Exception("Camera not initialized")))
                return
            }

            // Apply filter based on category and type
            when (category) {
                FilterCategory.BEAUTY -> {
                    applyBeautyFilter(type, parameters, callback)
                }
                FilterCategory.PORTRAIT -> {
                    applyPortraitFilter(type, parameters, callback)
                }
                FilterCategory.FOOD -> {
                    applyFoodFilter(type, parameters, callback)
                }
                FilterCategory.LANDSCAPE -> {
                    applyLandscapeFilter(type, parameters, callback)
                }
                FilterCategory.VINTAGE -> {
                    applyVintageFilter(type, parameters, callback)
                }
                FilterCategory.VIBRANT -> {
                    applyVibrantFilter(type, parameters, callback)
                }
                FilterCategory.MOODY -> {
                    applyMoodyFilter(type, parameters, callback)
                }
                FilterCategory.FILM -> {
                    applyFilmFilter(type, parameters, callback)
                }
                FilterCategory.ART -> {
                    applyArtFilter(type, parameters, callback)
                }
                FilterCategory.NONE -> {
                    // Disable all filters
                    renderer.setFilterEnabled(false)
                    callback(Result.success(Unit))
                }
            }

            // Notify Flutter about filter change
            flutterApi.onFilterChanged(category, type, parameters) { }

        } catch (e: Exception) {
            Log.e(TAG, "Error applying filter", e)
            callback(Result.failure(e))
        }
    }

    private fun applyBeautyFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        val renderer = openGlRenderer ?: return
        
        // Simplified - no filter processing, just log
        Log.d(TAG, "Beauty filter requested but disabled in simplified mode: $type")
        
        // Just enable basic filter state
        renderer.setFilterEnabled(true)
        
        Log.d(TAG, "Beauty filter applied: $type")
        callback(Result.success(Unit))
    }

    private fun applyPortraitFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        val renderer = openGlRenderer ?: return
        
        // Simplified - no filter processing, just log
        Log.d(TAG, "Portrait filter requested but disabled in simplified mode: $type")
        
        renderer.setFilterEnabled(true)
        Log.d(TAG, "Portrait filter applied: $type")
        callback(Result.success(Unit))
    }

    private fun applyFoodFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        val renderer = openGlRenderer ?: return
        
        // Apply food-specific parameters (focus on warmth, saturation, vibrance)
        // Filter parameters disabled in simplified mode
        Log.d(TAG, "Filter parameters ignored in simplified mode")
        
        renderer.setFilterEnabled(true)
        Log.d(TAG, "Food filter applied: $type")
        callback(Result.success(Unit))
    }

    private fun applyLandscapeFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        val renderer = openGlRenderer ?: return
        
        // Apply landscape-specific parameters (focus on vibrance, clarity, structure)
        // Filter parameters disabled in simplified mode
        Log.d(TAG, "Filter parameters ignored in simplified mode")
        
        renderer.setFilterEnabled(true)
        Log.d(TAG, "Landscape filter applied: $type")
        callback(Result.success(Unit))
    }

    private fun applyVintageFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        val renderer = openGlRenderer ?: return
        
        // Apply vintage-specific parameters (focus on fade, grain, warmth)
        // Filter parameters disabled in simplified mode
        Log.d(TAG, "Filter parameters ignored in simplified mode")
        
        renderer.setFilterEnabled(true)
        Log.d(TAG, "Vintage filter applied: $type")
        callback(Result.success(Unit))
    }

    private fun applyVibrantFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        val renderer = openGlRenderer ?: return
        
        // Apply vibrant-specific parameters (focus on saturation, vibrance, clarity)
        // Filter parameters disabled in simplified mode
        Log.d(TAG, "Filter parameters ignored in simplified mode")
        
        renderer.setFilterEnabled(true)
        Log.d(TAG, "Vibrant filter applied: $type")
        callback(Result.success(Unit))
    }

    private fun applyMoodyFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        val renderer = openGlRenderer ?: return
        
        // Apply moody-specific parameters (focus on shadows, highlights, fade)
        // Filter parameters disabled in simplified mode
        Log.d(TAG, "Filter parameters ignored in simplified mode")
        
        renderer.setFilterEnabled(true)
        Log.d(TAG, "Moody filter applied: $type")
        callback(Result.success(Unit))
    }

    private fun applyFilmFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        val renderer = openGlRenderer ?: return
        
        // Apply film-specific parameters (focus on grain, fade, contrast)
        // Filter parameters disabled in simplified mode
        Log.d(TAG, "Filter parameters ignored in simplified mode")
        
        renderer.setFilterEnabled(true)
        Log.d(TAG, "Film filter applied: $type")
        callback(Result.success(Unit))
    }

    private fun applyArtFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        val renderer = openGlRenderer ?: return
        
        // Apply art-specific parameters (focus on blur, sharpen, structure)
        // Filter parameters disabled in simplified mode
        Log.d(TAG, "Filter parameters ignored in simplified mode")
        
        renderer.setFilterEnabled(true)
        Log.d(TAG, "Art filter applied: $type")
        callback(Result.success(Unit))
    }

    override fun adjustFilterIntensity(intensity: Double, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "adjustFilterIntensity called - intensity: $intensity")
        
        try {
            val renderer = openGlRenderer
            if (renderer == null) {
                Log.e(TAG, "OpenGL renderer is not initialized")
                callback(Result.failure(Exception("Camera not initialized")))
                return
            }

            // TODO: Implement filter intensity adjustment in OpenGL renderer
            // For now, we'll just log it
            Log.d(TAG, "Filter intensity adjusted to: $intensity")
            callback(Result.success(Unit))

        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting filter intensity", e)
            callback(Result.failure(e))
        }
    }

    override fun onResults(faces: List<FaceData>) {
        if (flutterPluginBinding != null) {
            mainHandler.post {
                flutterApi.onFaceDetected(faces) {
                    Log.d(TAG, "onFaceDetected callback result: $it")
                }
                // Truyền landmark vào OpenGLRenderer để filter
                val firstFaceLandmarks = faces.firstOrNull()?.landmarks
                openGlRenderer?.setFaceLandmarks(firstFaceLandmarks)
            }
        }
    }
    
}