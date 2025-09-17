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
    private var previewSurfaceProducer: SurfaceProducer? = null
    
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
            cameraHandler?.dispose()
            openGlRenderer?.release()
            previewSurfaceProducer?.release()

            val textureRegistry = flutterPluginBinding?.textureRegistry ?: run {
                callback(Result.failure(Exception("TextureRegistry not available")))
                return
            }

            Log.d(TAG, "Executing initialize with OpenGL")

            // 1. Tạo FlutterSurfaceProducer trước, nhưng chưa set size
            val producer = FlutterSurfaceProducer(textureRegistry)
            this.previewSurfaceProducer = producer

            // 2. Tạo SurfaceProvider sẽ khởi tạo OpenGL khi có resolution
            val surfaceProvider = androidx.camera.core.Preview.SurfaceProvider { request ->
                val resolution = request.resolution
                Log.d(TAG, "CameraX selected resolution for OpenGL mode: ${resolution.width}x${resolution.height}")

                // 3. Khởi tạo OpenGL Renderer khi đã có resolution
                if (openGlRenderer == null) {
                    Log.d(TAG, "Creating OpenGL Renderer with resolution...")
                    openGlRenderer = OpenGLRenderer(activity.applicationContext).apply {
                        start()
                        waitUntilReady()
                        setCameraResolution(resolution)
                    }
                    Log.d(TAG, "OpenGL Renderer created and ready")
                }

                // 4. Set size cho FlutterSurfaceProducer với camera resolution
                producer.setSize(resolution.width, resolution.height)
                Log.d(TAG, "FlutterSurfaceProducer size set to: ${resolution.width}x${resolution.height}")

                // 5. Set output surface cho OpenGL
                openGlRenderer?.setOutputSurface(producer.getSurface())
                Log.d(TAG, "OpenGL output surface set")

                // 6. Cung cấp input surface cho CameraX
                val rendererInputSurface = openGlRenderer?.cameraInputSurface ?: run {
                    Log.e(TAG, "OpenGL renderer input surface is null")
                    return@SurfaceProvider
                }
                
                request.provideSurface(rendererInputSurface, ContextCompat.getMainExecutor(activity)) {
                    Log.d(TAG, "Surface provided to CameraX and callback received.")
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
    
    fun setBeautyFilter(parameters: BeautyFilterParameters, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "setBeautyFilter: type=${parameters.type}, smoothing=${parameters.smoothingStrength}, brightening=${parameters.brighteningStrength}")
        
        // Convert parameters và gửi xuống OpenGLRenderer
        val smoothing = parameters.smoothingStrength.toFloat()
        val brightening = parameters.brighteningStrength.toFloat()
        
        openGlRenderer?.setBeautyFilterParameters(smoothing, brightening)
        
        // Tự động bật filter nếu type không phải none
        val shouldEnableFilter = parameters.type != BeautyFilterType.NONE
        openGlRenderer?.setFilterEnabled(shouldEnableFilter)
        
        Log.d(TAG, "Beauty filter applied successfully - filter enabled: $shouldEnableFilter")
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
                else -> {
                    Log.w(TAG, "Unknown filter category: $category")
                    callback(Result.failure(Exception("Unknown filter category: $category")))
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
        
        // Apply beauty-specific parameters
        renderer.setBeautyFilterParameters(
            parameters.skinSmoothing.toFloat(),
            parameters.skinBrightening.toFloat()
        )
        
        // Enable filter
        renderer.setFilterEnabled(true)
        
        Log.d(TAG, "Beauty filter applied: $type")
        callback(Result.success(Unit))
    }

    private fun applyPortraitFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        // TODO: Implement portrait filters
        Log.d(TAG, "Portrait filter not implemented yet: $type")
        callback(Result.success(Unit))
    }

    private fun applyFoodFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        // TODO: Implement food filters
        Log.d(TAG, "Food filter not implemented yet: $type")
        callback(Result.success(Unit))
    }

    private fun applyLandscapeFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        // TODO: Implement landscape filters
        Log.d(TAG, "Landscape filter not implemented yet: $type")
        callback(Result.success(Unit))
    }

    private fun applyVintageFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        // TODO: Implement vintage filters
        Log.d(TAG, "Vintage filter not implemented yet: $type")
        callback(Result.success(Unit))
    }

    private fun applyVibrantFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        // TODO: Implement vibrant filters
        Log.d(TAG, "Vibrant filter not implemented yet: $type")
        callback(Result.success(Unit))
    }

    private fun applyMoodyFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        // TODO: Implement moody filters
        Log.d(TAG, "Moody filter not implemented yet: $type")
        callback(Result.success(Unit))
    }

    private fun applyFilmFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        // TODO: Implement film filters
        Log.d(TAG, "Film filter not implemented yet: $type")
        callback(Result.success(Unit))
    }

    private fun applyArtFilter(type: FilterType, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        // TODO: Implement art filters
        Log.d(TAG, "Art filter not implemented yet: $type")
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

    private fun createFlutterSurfaceWithResolution(resolution: android.util.Size) {
        try {
            val textureRegistry = flutterPluginBinding?.textureRegistry
            if (textureRegistry != null) {
                Log.d(TAG, "Creating FlutterSurfaceProducer with camera resolution: ${resolution.width}x${resolution.height}")
                
                // Create surface producer
                val surfaceProducer = FlutterSurfaceProducer(textureRegistry)
                val flutterSurface = surfaceProducer.getSurface()
                Log.d(TAG, "FlutterSurfaceProducer created successfully")
                
                // Store the producer reference first
                this.previewSurfaceProducer = surfaceProducer
                
                // Set output surface with a small delay to ensure everything is ready
                mainHandler.postDelayed({
                    openGlRenderer?.setOutputSurface(flutterSurface)
                    Log.d(TAG, "Output surface set with camera resolution: ${resolution.width}x${resolution.height}")
                    
                    // Check the actual surface size after setting
                    mainHandler.postDelayed({
                        Log.d(TAG, "FlutterSurfaceProducer texture ID: ${surfaceProducer.getTextureId()}")
                    }, 100)
                }, 300) // 300ms delay to ensure Flutter widget is ready
                
            } else {
                Log.e(TAG, "TextureRegistry not available for FlutterSurfaceProducer creation")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating FlutterSurfaceProducer with resolution", e)
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