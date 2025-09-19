package com.beauty.camera_plugin

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

/**
 * BeautyCameraPlugin - Pure Pigeon Host Registration & Delegation
 * 
 * SINGLE RESPONSIBILITY: 
 * - Đăng ký Pigeon host từ Dart
 * - Delegate ALL operations trực tiếp cho CameraManager
 * - Không có business logic gì cả
 * 
 * Architecture: Dart → BeautyCameraPlugin → CameraManager
 */
class BeautyCameraPlugin : FlutterPlugin, ActivityAware, BeautyCameraHostApi {
    companion object {
        private const val TAG = "BeautyCameraPlugin"
    }
    
    private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null
    private var activityBinding: ActivityPluginBinding? = null
    private lateinit var flutterApi: BeautyCameraFlutterApi

    // Flutter texture - chỉ tạo và cung cấp surface
    private var previewSurfaceProducer: FlutterSurfaceProducer? = null
    
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
        
        // Cleanup
        CameraManager.dispose()
        OpenGLManager.dispose()
        previewSurfaceProducer?.release()
        previewSurfaceProducer = null
    }

    // --- Activity Lifecycle ---

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        Log.d(TAG, "Activity attached - initializing CameraManager")
        this.activityBinding = binding
        
        // ONLY: Initialize CameraManager
        val activity = binding.activity
        CameraManager.initialize(
            context = activity.applicationContext,
            lifecycleOwner = activity as LifecycleOwner
        )
    }

    override fun onDetachedFromActivity() {
        Log.d(TAG, "Activity detached")
        CameraManager.dispose()
        OpenGLManager.dispose()
        previewSurfaceProducer?.release()
        previewSurfaceProducer = null
        activityBinding = null
    }
    
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) { 
        onAttachedToActivity(binding) 
    }
    
    override fun onDetachedFromActivityForConfigChanges() { 
        onDetachedFromActivity() 
    }

    // --- Pure Delegation to CameraManager ---

    override fun initialize(settings: AdvancedCameraSettings, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Delegate initialize to CameraManager")
        
        try {
            // Ensure CameraManager is initialized (re-initialize if needed)
            val activity = activityBinding?.activity
            if (activity != null && !CameraManager.isInitialized()) {
                Log.d(TAG, "Re-initializing CameraManager")
                CameraManager.initialize(
                    context = activity.applicationContext,
                    lifecycleOwner = activity as LifecycleOwner
                )
            }
            
            // ONLY: Create Flutter texture and get surface
            val textureRegistry = flutterPluginBinding?.textureRegistry ?: run {
                callback(Result.failure(Exception("TextureRegistry not available")))
                return
            }
            
            previewSurfaceProducer?.release()
            previewSurfaceProducer = FlutterSurfaceProducer(textureRegistry)
            
            // DELEGATE: All logic to CameraManager
            CameraManager.initializeCamera(
                settings = settings,
                surfaceProducer = previewSurfaceProducer!!,
                enableOpenGL = settings.isFilterEnabled ?: false, // Use settings from Flutter
                onSuccess = {
                    Log.d(TAG, "CameraManager.initializeCamera success")
                    callback(Result.success(Unit))
                },
                onError = { error ->
                    Log.e(TAG, "CameraManager.initializeCamera error: $error")
                    callback(Result.failure(Exception(error)))
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in initialize delegation", e)
            callback(Result.failure(e))
        }
    }

    override fun dispose(callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Flutter dispose - stopping camera only, keeping CameraManager initialized")
        
        try {
            // ONLY stop camera, don't dispose CameraManager (Activity will handle that)
            CameraManager.stopCamera()
            
            // DELEGATE: Dispose OpenGLManager
            OpenGLManager.dispose()
            
            // Clean Flutter texture
            previewSurfaceProducer?.release()
            previewSurfaceProducer = null
            
            Log.d(TAG, "Camera stopped successfully")
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Error during dispose", e)
            callback(Result.failure(e))
        }
    }

     override fun switchCamera(callback: (Result<Unit>) -> Unit) { 
        Log.d(TAG, "Delegate switchCamera to CameraManager")
        
        try {
            // DELEGATE: Direct to CameraManager
            val newFacing = CameraManager.switchCamera()
            Log.d(TAG, "CameraManager.switchCamera success: $newFacing")
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "CameraManager.switchCamera error", e)
            callback(Result.failure(e))
        }
    }

    override fun getPreviewTexture(callback: (Result<Long>) -> Unit) {
        Log.d(TAG, "Return Flutter texture ID")
        
        val producer = previewSurfaceProducer ?: run {
            callback(Result.failure(Exception("Preview not available")))
                return
            }

        // ONLY: Return texture ID
        callback(Result.success(producer.getTextureId()))
    }

    override fun getPreviewSize(callback: (Result<PreviewSize>) -> Unit) {
        Log.d(TAG, "Delegate getPreviewSize to CameraManager")
        
        // DELEGATE: Direct to CameraManager with callback
        CameraManager.getPreviewSize(callback)
    }

    // --- TODO: All other methods delegate to CameraManager ---

    override fun setZoom(zoomLevel: Double, callback: (Result<Unit>) -> Unit) {
        TODO("Delegate to CameraManager")
    }

    override fun focusOnPoint(x: Long, y: Long, callback: (Result<Unit>) -> Unit) {
        TODO("Delegate to CameraManager")
    }

    override fun setFlashMode(mode: FlashMode, callback: (Result<Unit>) -> Unit) {
        TODO("Delegate to CameraManager")
    }

    override fun setDisplayOrientation(degrees: Long, callback: (Result<Unit>) -> Unit) {
        TODO("Delegate to CameraManager")
    }

    override fun takePhoto(callback: (Result<String>) -> Unit) {
        TODO("Delegate to CameraManager")
    }

    override fun startVideoRecording(callback: (Result<Unit>) -> Unit) {
        TODO("Delegate to CameraManager")
    }

    override fun stopVideoRecording(callback: (Result<String>) -> Unit) {
        TODO("Delegate to CameraManager")
    }

    override fun getCameraSensorAspectRatio(callback: (Result<Double>) -> Unit) {
        TODO("Delegate to CameraManager")
    }

    override fun applyFilter(
        category: FilterCategory,
        type: FilterType,
        parameters: FilterParameters,
        callback: (Result<Unit>) -> Unit
    ) {
        Log.d(TAG, "Delegate applyFilter to OpenGLManager - type: $type")
        try {
            // DELEGATE: Apply filter via OpenGLManager
            OpenGLManager.applyGLFilter(type)
            OpenGLManager.setFilterIntensity(parameters.intensity.toFloat())
            
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "OpenGLManager.applyFilter error", e)
            callback(Result.failure(e))
        }
    }

    override fun setScaleType(scaleType: ScaleType, callback: (Result<Unit>) -> Unit) {
        TODO("Delegate to CameraManager")
    }

    override fun getAvailableCameras(callback: (Result<List<CameraInfo>>) -> Unit) {
        TODO("Delegate to CameraManager")
    }

    override fun setFilterEnabled(enabled: Boolean, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Delegate setFilterEnabled to OpenGLManager")
        
        try {
            // DELEGATE: Direct to OpenGLManager
            OpenGLManager.setFilterEnabled(enabled)
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "OpenGLManager.setFilterEnabled error", e)
            callback(Result.failure(e))
        }
    }

    override fun adjustFilterIntensity(intensity: Double, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Delegate adjustFilterIntensity to OpenGLManager")
        
        try {
            // DELEGATE: Direct to OpenGLManager
            OpenGLManager.setFilterIntensity(intensity.toFloat())
            callback(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "OpenGLManager.setFilterIntensity error", e)
            callback(Result.failure(e))
        }
    }
}