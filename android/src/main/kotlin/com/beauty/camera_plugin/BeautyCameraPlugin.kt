package com.beauty.camera_plugin

import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.view.TextureRegistry
import com.beauty.camera_plugin.repository.CameraRepository
import com.beauty.camera_plugin.view.FlutterTextureHandler
import com.beauty.camera_plugin.viewmodel.CameraViewModel
import com.beauty.camera_plugin.filters.CameraFilterManager
import com.beauty.camera_plugin.models.CameraSettings
import com.beauty.camera_plugin.models.CameraFilterMode
import kotlinx.coroutines.*

class BeautyCameraPlugin : FlutterPlugin, ActivityAware, BeautyCameraHostApi {
    private lateinit var cameraViewModel: CameraViewModel
    private var flutterApi: BeautyCameraFlutterApi? = null
    
    // Coroutine scope
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    
    // Texture handling
    private var textureRegistry: TextureRegistry? = null
    private var textureHandler: FlutterTextureHandler? = null
    private var repository: CameraRepository? = null
    private var filterManager: CameraFilterManager? = null
    
    // Activity reference
    private var lifecycleOwner: LifecycleOwner? = null
    
    // Track texture ID
    private var textureId: Long = -1
    
    // Preview size
    private var previewWidth: Int = 1280
    private var previewHeight: Int = 720

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        // Set up Pigeon communication
        BeautyCameraHostApi.setUp(flutterPluginBinding.binaryMessenger, this)
        flutterApi = BeautyCameraFlutterApi(flutterPluginBinding.binaryMessenger)
        
        // Store texture registry for later
        textureRegistry = flutterPluginBinding.textureRegistry
        
        // Create the camera repository
        repository = CameraRepository(flutterPluginBinding.applicationContext)
        
        // Create filter manager
        filterManager = CameraFilterManager(flutterPluginBinding.applicationContext)
        
        // Create the view model
        cameraViewModel = CameraViewModel(flutterPluginBinding.applicationContext)
        cameraViewModel.setFlutterApi(flutterApi)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        BeautyCameraHostApi.setUp(binding.binaryMessenger, null)
        flutterApi = null
        
        // Clean up resources
        textureHandler?.cleanup()
        textureHandler = null
        textureId = -1L
        
        filterManager?.release()
        filterManager = null
        
        repository?.cleanup()
        repository = null
        
        textureRegistry = null
        cameraViewModel.cleanup()
        
        // Cancel all coroutines
        coroutineScope.cancel()
    }
    
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        lifecycleOwner = binding.activity as? LifecycleOwner
        cameraViewModel.setActivity(binding.activity)
        
        // Initialize texture handler with activity as lifecycle owner
        repository?.let { repo ->
            textureRegistry?.let { registry ->
                textureHandler = FlutterTextureHandler(
                    registry,
                    repo,
                    binding.activity as LifecycleOwner
                )
            }
        }
    }

    override fun onDetachedFromActivity() {
        cameraViewModel.releaseActivity()
        textureHandler?.cleanup()
        textureHandler = null
        textureId = -1L
        lifecycleOwner = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        cameraViewModel.releaseActivity()
        textureHandler?.cleanup()
        textureHandler = null
        textureId = -1L
        lifecycleOwner = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        lifecycleOwner = binding.activity as? LifecycleOwner
        cameraViewModel.setActivity(binding.activity)
        
        // Reset texture state for recreation
        textureId = -1L
        textureHandler = null
    }

    override fun initialize(settings: AdvancedCameraSettings, callback: (Result<Unit>) -> Unit) {
        coroutineScope.launch {
            try {
                // Reset texture state
                textureId = -1L
                textureHandler?.cleanup()
                textureHandler = null
                
                // Convert settings to CameraSettings
                val cameraSettings = CameraSettings.fromAdvancedSettings(settings)
                
                // Initialize repository with settings
                repository?.let { repo ->
                    lifecycleOwner?.let { owner ->
                        withContext(Dispatchers.IO) {
                            // Initialize with camera settings
                            repo.initialize(owner, cameraSettings)
                            
                            // Update preview dimensions based on settings
                            previewWidth = cameraSettings.resolution.width
                            previewHeight = cameraSettings.resolution.height
                        }
                        
                        // Create new texture handler
                        textureRegistry?.let { registry ->
                            textureHandler = FlutterTextureHandler(
                                textureRegistry = registry,
                                repository = repo,
                                lifecycleOwner = owner
                            )

                        }
                        
                        callback(Result.success(Unit))
                    } ?: callback(Result.failure(Exception("LifecycleOwner not available")))
                } ?: callback(Result.failure(Exception("Camera repository not initialized")))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun dispose(callback: (Result<Unit>) -> Unit) {
        try {
            repository?.cleanup()
            filterManager?.release()
            textureHandler?.cleanup()
            textureHandler = null
            textureId = -1L
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun switchCamera(callback: (Result<Unit>) -> Unit) {
        try {
            repository?.let { repo ->
                // Switch camera
                repo.switchCamera()
                
                // Update texture if needed
                textureHandler?.let { handler ->
                    // Get current preview size
                    val resolution = repo.getPreviewResolution()

                    handler.updateTexture(resolution.first, resolution.second)
                }
                
                callback(Result.success(Unit))
            } ?: callback(Result.failure(Exception("Camera repository not initialized")))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun setZoom(zoomLevel: Double, callback: (Result<Unit>) -> Unit) {
        try {
            repository?.setZoom()
            // Notify Flutter about zoom change
            flutterApi?.onZoomChanged(zoomLevel) {}
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun focusOnPoint(x: Long, y: Long, callback: (Result<Unit>) -> Unit) {
        try {
            repository?.focusOnPoint(x.toInt(), y.toInt())
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun setFlashMode(mode: FlashMode, callback: (Result<Unit>) -> Unit) {
        try {
            repository?.setFlashMode(mode)
            // Notify Flutter about flash mode change
            flutterApi?.onFlashModeChanged(mode) {}
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun setDisplayOrientation(degrees: Long, callback: (Result<Unit>) -> Unit) {
        try {
            repository?.setDisplayOrientation()
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun getPreviewTexture(callback: (Result<Long>) -> Unit) {
        try {
            // Ensure we have all required components
            if (repository == null || textureRegistry == null || lifecycleOwner == null) {
                throw Exception("Required components not initialized")
            }
            
            // Create new texture handler if needed
            if (textureHandler == null) {
                textureHandler = FlutterTextureHandler(
                    textureRegistry = textureRegistry!!,
                    repository = repository!!,
                    lifecycleOwner = lifecycleOwner!!
                )
            }
            
            // Always create new texture if none exists
            if (textureId == -1L) {
                textureId = textureHandler?.initialize() ?: -1L
                if (textureId == -1L) {
                    throw Exception("Failed to initialize texture")
                }
                
                // Update texture with preview size
                textureHandler?.updateTexture(previewWidth, previewHeight)
            }
            
            callback(Result.success(textureId))
        } catch (e: Exception) {
            textureId = -1L // Reset on error
            callback(Result.failure(e))
        }
    }
    
    override fun getPreviewSize(callback: (Result<PreviewSize>) -> Unit) {
        try {
            // Get preview size from repository
            val resolution = repository?.getPreviewResolution() ?: Pair(previewWidth, previewHeight)
            previewWidth = resolution.first
            previewHeight = resolution.second
            
            val previewSize = PreviewSize(width = previewWidth.toLong(), height = previewHeight.toLong())
            callback(Result.success(previewSize))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun takePhoto(callback: (Result<String>) -> Unit) {
        try {
            repository?.takePhoto(object : CameraRepository.PhotoCaptureCallback {
                override fun onSuccess(path: String) {
                    callback(Result.success(path))
                }
                
                override fun onFailure(exception: Exception) {
                    callback(Result.failure(exception))
                }
            })
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun startVideoRecording(callback: (Result<Unit>) -> Unit) {
        try {
            val outputPath = "${repository?.getCacheDirectory()}/video_${System.currentTimeMillis()}.mp4"
            repository?.startRecording(outputPath)
            flutterApi?.onVideoRecordingStarted {}
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun stopVideoRecording(callback: (Result<String>) -> Unit) {
        try {
            val videoPath = repository?.stopRecording() ?: throw Exception("No video recording in progress")
            flutterApi?.onVideoRecordingStopped(videoPath) {}
            callback(Result.success(videoPath))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun getCameraSensorAspectRatio(callback: (Result<Double>) -> Unit) {
        try {
            val ratio = repository?.getCameraSensorAspectRatio() ?: (4.0 / 3.0)
            callback(Result.success(ratio))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun setFilterMode(mode: com.beauty.camera_plugin.CameraFilterMode, level: Double, callback: (Result<Unit>) -> Unit) {
        try {
            val internalMode = CameraFilterMode.fromPigeon(mode)
            filterManager?.setFilter(internalMode, level)
            flutterApi?.onFilterModeChanged(mode) {}
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }
} 