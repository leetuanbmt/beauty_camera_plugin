package com.beauty.camera_plugin

import android.util.Log
import android.view.Surface
import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

/**
 * Plugin chính để quản lý beauty camera và tương tác với Flutter.
 * Class này implement BeautyCameraHostApi và quản lý vòng đời của plugin.
 */
class BeautyCameraPlugin : FlutterPlugin, ActivityAware, BeautyCameraHostApi {
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
        Log.d(TAG, "onAttachedToActivity - Activity class: ${binding.activity?.javaClass?.simpleName}")
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
        activityBinding = null
        cameraHandler = null
        openGlRenderer = null
        previewSurfaceProducer = null
        
        // Clear cache
        pendingInitializeSettings = null
        pendingInitializeCallback = null
    }

    // --- BeautyCameraHostApi Implementation ---

    override fun initialize(settings: AdvancedCameraSettings, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Initialize with settings: $settings")
        Log.d(TAG, "Activity binding: $activityBinding")
        Log.d(TAG, "Activity from binding: ${activityBinding?.activity}")
        
        // Kiểm tra xem activity đã sẵn sàng chưa
        val activity = activityBinding?.activity
        if (activity == null) {
            Log.w(TAG, "Activity not attached - caching initialize command")
            // Cache lệnh initialize để thực thi sau khi activity sẵn sàng
            pendingInitializeSettings = settings
            pendingInitializeCallback = callback
            return
        }
        
        // Activity đã sẵn sàng, thực thi ngay
        executeInitialize(settings, callback)
    }

    override fun initializeForTest(settings: AdvancedCameraSettings, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "InitializeForTest with settings: $settings")
        val activity = activityBinding?.activity
        if (activity == null) {
            Log.w(TAG, "Activity not attached - caching initializeForTest command")
            pendingInitializeSettings = settings
            pendingInitializeCallback = callback
            return
        }
        executeInitializeForTest(settings, callback)
    }
    
    private fun executeInitialize(settings: AdvancedCameraSettings, callback: (Result<Unit>) -> Unit) {
        val activity = activityBinding?.activity ?: run {
            Log.e(TAG, "Activity is null during executeInitialize")
            callback(Result.failure(Exception("Activity is null")))
            return
        }

        try {
            // Release previous resources if they exist, to prevent leaks on re-initialization.
            cameraHandler?.dispose()
            openGlRenderer?.release()
            previewSurfaceProducer?.release()

            Log.d(TAG, "Executing initialize with activity: ${activity.javaClass.simpleName}")

            // 1. Khởi tạo OpenGL Renderer trên một thread riêng
            openGlRenderer = OpenGLRenderer(activity.applicationContext)
            openGlRenderer?.start()
            openGlRenderer?.waitUntilReady()
            Log.d(TAG, "OpenGL Renderer initialized")

            // 2. Khởi tạo CameraHandler
            val cameraSettings = com.beauty.camera_plugin.models.CameraSettings.fromAdvancedSettings(settings)
            cameraHandler = CameraHandler(activity.applicationContext, activity as LifecycleOwner, cameraSettings)
            cameraHandler?.initialize {
                // If dispose was called while camera was initializing, handlers will be null.
                if (activityBinding == null) {
                    Log.w(TAG, "Initialization callback fired after plugin was disposed. Ignoring.")
                    callback(Result.failure(Exception("Plugin disposed during initialization.")))
                    return@initialize
                }

                Log.d(TAG, "CameraHandler initialized")

                // 3. Nối CameraX output với OpenGL input
                val rendererInputSurface = openGlRenderer?.cameraInputSurface
                if (rendererInputSurface != null) {
                    cameraHandler?.startCamera(rendererInputSurface)
                    Log.d(TAG, "Camera started with OpenGL input surface")
                    callback(Result.success(Unit))
                } else {
                    Log.e(TAG, "OpenGL renderer input surface is null")
                    callback(Result.failure(Exception("OpenGL renderer input surface is null")))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing camera", e)
            callback(Result.failure(e))
        }
    }

    private fun executeInitializeForTest(settings: AdvancedCameraSettings, callback: (Result<Unit>) -> Unit) {
        val activity = activityBinding?.activity ?: run {
            Log.e(TAG, "Activity is null during executeInitializeForTest")
            callback(Result.failure(Exception("Activity is null")))
            return
        }

        try {
            cameraHandler?.dispose()
            openGlRenderer?.release()
            previewSurfaceProducer?.release()

            Log.d(TAG, "Executing initializeForTest with activity: ${activity.javaClass.simpleName}")

            // 1. Create a SurfaceProducer directly without OpenGLRenderer
            val textureRegistry = flutterPluginBinding?.textureRegistry ?: run {
                Log.e(TAG, "TextureRegistry not available")
                callback(Result.failure(Exception("TextureRegistry not available")))
                return
            }
            val surfaceProducer = FlutterSurfaceProducer(textureRegistry)
            this.previewSurfaceProducer = surfaceProducer
            val surface = surfaceProducer.getSurface()

            // 2. Initialize CameraHandler
            val cameraSettings = com.beauty.camera_plugin.models.CameraSettings.fromAdvancedSettings(settings)
            cameraHandler = CameraHandler(activity.applicationContext, activity as LifecycleOwner, cameraSettings)
            cameraHandler?.initialize {
                if (activityBinding == null) {
                    Log.w(TAG, "Initialization callback fired after plugin was disposed. Ignoring.")
                    callback(Result.failure(Exception("Plugin disposed during initialization.")))
                    return@initialize
                }

                Log.d(TAG, "CameraHandler initialized for test")

                // 3. Connect CameraX output directly to the Flutter texture surface
                cameraHandler?.startCamera(surface)
                Log.d(TAG, "Camera started with direct SurfaceTexture surface")
                callback(Result.success(Unit))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing camera for test", e)
            callback(Result.failure(e))
        }
    }

    override fun getPreviewTexture(callback: (Result<Long>) -> Unit) {
        // In test mode, the texture entry is already created during initialization.
        if (openGlRenderer == null) {
            val producer = previewSurfaceProducer ?: run {
                Log.e(TAG, "previewSurfaceProducer not available in test mode")
                callback(Result.failure(Exception("previewSurfaceProducer not available in test mode")))
                return
            }
            Log.d(TAG, "Returning existing preview texture for test mode with ID: ${producer.getTextureId()}")
            callback(Result.success(producer.getTextureId()))
            return
        }

        val renderer = openGlRenderer ?: run {
            Log.e(TAG, "Renderer not initialized")
            callback(Result.failure(Exception("Renderer not initialized")))
            return
        }
        val textureRegistry = flutterPluginBinding?.textureRegistry ?: run {
            Log.e(TAG, "TextureRegistry not available")
            callback(Result.failure(Exception("TextureRegistry not available")))
            return
        }

        try {
            // 4. Tạo Flutter Texture và nối OpenGL output với nó
            val surfaceProducer = FlutterSurfaceProducer(textureRegistry)
            val flutterSurface = surfaceProducer.getSurface()
            renderer.setOutputSurface(flutterSurface)

            this.previewSurfaceProducer = surfaceProducer
            Log.d(TAG, "Preview texture created with ID: ${surfaceProducer.getTextureId()}")
            callback(Result.success(surfaceProducer.getTextureId()))
        } catch (e: Exception) {
            Log.e(TAG, "Error creating preview texture", e)
            callback(Result.failure(e))
        }
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
         Log.d(TAG, "switchCamera not implemented yet")
         callback(Result.success(Unit)) 
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
         Log.d(TAG, "takePhoto not implemented yet")
         callback(Result.success("/tmp/photo.jpg")) 
     }
     override fun startVideoRecording(callback: (Result<Unit>) -> Unit) { 
         Log.d(TAG, "startVideoRecording not implemented yet")
         callback(Result.success(Unit)) 
     }
     override fun stopVideoRecording(callback: (Result<String>) -> Unit) { 
         Log.d(TAG, "stopVideoRecording not implemented yet")
         callback(Result.success("/tmp/video.mp4")) 
     }
     override fun getCameraSensorAspectRatio(callback: (Result<Double>) -> Unit) { 
         Log.d(TAG, "getCameraSensorAspectRatio not implemented yet")
         callback(Result.success(16.0/9.0)) 
     }
     override fun setFilterMode(mode: CameraFilterMode, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) { 
         Log.d(TAG, "setFilterMode not implemented yet")
         callback(Result.success(Unit)) 
     }
     override fun setScaleType(scaleType: ScaleType, callback: (Result<Unit>) -> Unit) { 
         Log.d(TAG, "setScaleType not implemented yet")
         callback(Result.success(Unit)) 
     }
     override fun getAvailableFilters(callback: (Result<List<FilterInfo>>) -> Unit) { 
         Log.d(TAG, "getAvailableFilters not implemented yet")
         callback(Result.success(emptyList())) 
     }
     override fun adjustFilterParameters(parameters: FilterParameters, callback: (Result<Unit>) -> Unit) { 
         Log.d(TAG, "adjustFilterParameters not implemented yet")
         callback(Result.success(Unit)) 
     }
     override fun getAvailableCameras(callback: (Result<List<CameraInfo>>) -> Unit) { 
         Log.d(TAG, "getAvailableCameras not implemented yet")
         callback(Result.success(emptyList())) 
     }
}