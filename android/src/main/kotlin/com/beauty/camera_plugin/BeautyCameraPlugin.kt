package com.beauty.camera_plugin

import android.app.Activity
import android.content.Context
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.PluginRegistry
import io.flutter.view.TextureRegistry

/** BeautyCameraPlugin */
class BeautyCameraPlugin: FlutterPlugin, ActivityAware, BeautyCameraHostApi, PluginRegistry.RequestPermissionsResultListener {
    private lateinit var context: Context
    private var activity: Activity? = null
    private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null
    private lateinit var cameraManager: CameraXManager
    private lateinit var flutterApi: BeautyCameraFlutterApi
    private var permissionRequestCallback: ((Boolean) -> Unit)? = null
    
    companion object {
        private const val TAG = "BeautyCameraPlugin"
        private const val CAMERA_REQUEST_ID = 512
        private const val PERMISSIONS_REQUEST_CODE = 10
        
        // Required permissions for camera and audio
        private val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
    }
    
    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        this.flutterPluginBinding = flutterPluginBinding
        context = flutterPluginBinding.applicationContext
        
        // Setup Pigeon APIs
        flutterApi = BeautyCameraFlutterApi(flutterPluginBinding.binaryMessenger)
        BeautyCameraHostApi.setUp(flutterPluginBinding.binaryMessenger, this)
        
        // Register the platform view factory for camera preview
        flutterPluginBinding.platformViewRegistry
            .registerViewFactory(
                "com.beauty.camera_plugin/cameraview", 
                BeautyCameraPlatformViewFactory(flutterPluginBinding.binaryMessenger)
            )
    }
    
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        
        // Register for permission results
        binding.addRequestPermissionsResultListener(this)
        
        val lifecycleOwner = activity as? LifecycleOwner
            ?: throw IllegalStateException("Activity is not a LifecycleOwner")
        
        // Initialize the camera manager with the activity context and lifecycle
        cameraManager = CameraXManager(
            context = activity!!,
            lifecycleOwner = lifecycleOwner,
            flutterApi = flutterApi,
            textureRegistry = flutterPluginBinding!!.textureRegistry
        )
    }
    
    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }
    
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }
    
    override fun onDetachedFromActivity() {
        activity = null
    }
    
    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        flutterPluginBinding = null
    }
    
    // BeautyCameraHostApi implementation
    override fun initializeCamera(settings: CameraSettings, callback: (Result<Unit>) -> Unit) {
        // Check and request permissions first, then initialize camera
        requestPermissionsIfNeeded { granted ->
            if (granted) {
                cameraManager.initializeCamera(settings, callback)
            } else {
                // Permission denied, return an error
                callback(Result.failure(FlutterError(
                    "permission_denied",
                    "Camera or audio permission not granted",
                    null
                )))
                
                // Also notify Flutter about the error
                flutterApi.onCameraError(
                    CameraError(
                        CameraErrorType.INITIALIZATION_FAILED,
                        "Camera or audio permission not granted"
                    )
                ) {}
            }
        }
    }
    
    override fun createPreviewTexture(callback: (Result<Long?>) -> Unit) {
        cameraManager.createPreviewTexture(callback)
    }
    
    override fun startPreview(textureId: Long, callback: (Result<Unit>) -> Unit) {
        cameraManager.startPreview(textureId, callback)
    }
    
    override fun stopPreview(callback: (Result<Unit>) -> Unit) {
        cameraManager.stopPreview(callback)
    }
    
    override fun disposeCamera(callback: (Result<Unit>) -> Unit) {
        cameraManager.disposeCamera(callback)
    }
    
    override fun takePicture(path: String, callback: (Result<Unit>) -> Unit) {
        // Check if we have camera permission before taking a picture
        if (!allPermissionsGranted()) {
            requestPermissionsIfNeeded { granted ->
                if (granted) {
                    cameraManager.takePicture(path, callback)
                } else {
                    callback(Result.failure(FlutterError(
                        "permission_denied",
                        "Camera permission not granted",
                        null
                    )))
                }
            }
        } else {
            cameraManager.takePicture(path, callback)
        }
    }
    
    override fun startRecording(path: String, callback: (Result<Unit>) -> Unit) {
        // Check if we have camera and audio permissions before recording
        if (!allPermissionsGranted()) {
            requestPermissionsIfNeeded { granted ->
                if (granted) {
                    cameraManager.startRecording(path, callback)
                } else {
                    callback(Result.failure(FlutterError(
                        "permission_denied",
                        "Camera or audio permission not granted",
                        null
                    )))
                }
            }
        } else {
            cameraManager.startRecording(path, callback)
        }
    }
    
    override fun stopRecording(callback: (Result<Unit>) -> Unit) {
        cameraManager.stopRecording(callback)
    }
    
    override fun applyFilter(textureId: Long, filterConfig: FilterConfig, callback: (Result<Unit>) -> Unit) {
        cameraManager.applyFilter(textureId, filterConfig, callback)
    }
    
    // Permission handling methods
    
    /**
     * Check if all required permissions are granted
     */
    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Request permissions if they are not already granted
     */
    private fun requestPermissionsIfNeeded(callback: (Boolean) -> Unit) {
        if (allPermissionsGranted()) {
            callback(true)
            return
        }
        
        val act = activity
        if (act == null) {
            android.util.Log.e(TAG, "Activity is null, cannot request permissions")
            callback(false)
            return
        }
        
        // Store the callback to be called when permission request completes
        permissionRequestCallback = callback
        
        // Request permissions
        ActivityCompat.requestPermissions(
            act,
            REQUIRED_PERMISSIONS,
            PERMISSIONS_REQUEST_CODE
        )
    }
    
    /**
     * Handle permission request results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val allGranted = grantResults.isNotEmpty() && 
                grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
            
            // Invoke the stored callback with the result
            permissionRequestCallback?.invoke(allGranted)
            permissionRequestCallback = null
            
            return true
        }
        return false
    }
} 