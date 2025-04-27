package com.beauty.camera_plugin

import android.content.Context
import android.util.Log
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Triển khai các phương thức API Pigeon BeautyCameraHostApi.
 * Làm cầu nối giữa Flutter và native Android.
 */
class BeautyCameraHostApiImpl(
    private val cameraManager: BeautyCameraManager,
    private val filterProcessor: FilterProcessor
) : BeautyCameraHostApi {
    companion object {
        private const val TAG = "BeautyCameraHostApiImpl"
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activityBinding: ActivityPluginBinding? = null
    
    fun setActivityBinding(binding: ActivityPluginBinding?) {
        activityBinding = binding
    }
    
    fun dispose() {
        coroutineScope.launch {
            try {
                // Đảm bảo giải phóng tài nguyên khi plugin bị dispose
                cameraManager.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing BeautyCameraHostApiImpl", e)
            }
        }
    }

    override fun initialize(settings: AdvancedCameraSettings, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Initialize with settings: $settings")
        
        coroutineScope.launch {
            try {
                // Check if we have a valid activity context
                val activity = activityBinding?.activity
                if (activity == null) {
                    Log.e(TAG, "Activity context is null")
                    callback(Result.failure(IllegalStateException("Activity context is null")))
                    return@launch
                }
                
                // Pass the lifecycleOwner and context to the cameraManager
                val textureId = cameraManager.initialize(
                    settings = settings,
                    lifecycleOwner = activity as androidx.lifecycle.LifecycleOwner,
                    context = activity
                )
                
                // Initialize filter processor with context
                filterProcessor.initialize(activity)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize camera", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun dispose(callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Dispose")
        
        coroutineScope.launch {
            try {
                cameraManager.dispose()
                filterProcessor.release()
                callback(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing camera", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun switchCamera(callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Switch camera")
        
        coroutineScope.launch {
            try {
                cameraManager.switchCamera()
                callback(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch camera", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun setZoom(zoomLevel: Double, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Set zoom: $zoomLevel")
        
        coroutineScope.launch {
            try {
                cameraManager.setZoom(zoomLevel)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set zoom", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun focusOnPoint(x: Long, y: Long, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Focus on point: ($x, $y)")
        
        coroutineScope.launch {
            try {
                cameraManager.focusOnPoint(x, y)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to focus on point", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun setFlashMode(mode: FlashMode, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Set flash mode: $mode")
        
        coroutineScope.launch {
            try {
                cameraManager.setFlashMode(mode)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set flash mode", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun setDisplayOrientation(degrees: Long, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Set display orientation: $degrees")
        
        coroutineScope.launch {
            try {
                cameraManager.setDisplayOrientation(degrees)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set display orientation", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun getPreviewTexture(callback: (Result<Long>) -> Unit) {
        Log.d(TAG, "Get preview texture")
        
        // Texture ID được tạo lúc khởi tạo camera, và được cameraManager lưu trữ
        // Ở đây chúng ta chỉ gọi lại để lấy ID
        // Không cần bất đồng bộ
        try {
            val flutterTexture = cameraManager.getFlutterTextureId()
            callback(Result.success(flutterTexture))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get preview texture", e)
            callback(Result.failure(e))
        }
    }

    override fun getPreviewSize(callback: (Result<PreviewSize>) -> Unit) {
        Log.d(TAG, "Get preview size")
        
        coroutineScope.launch {
            try {
                val previewSize = cameraManager.getPreviewSize()
                callback(Result.success(previewSize))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get preview size", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun takePhoto(callback: (Result<String>) -> Unit) {
        Log.d(TAG, "Take photo")
        
        coroutineScope.launch {
            try {
                val photoPath = cameraManager.takePhoto()
                callback(Result.success(photoPath))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take photo", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun startVideoRecording(callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Start video recording")
        
        coroutineScope.launch {
            try {
                cameraManager.startVideoRecording()
                callback(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start video recording", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun stopVideoRecording(callback: (Result<String>) -> Unit) {
        Log.d(TAG, "Stop video recording")
        
        coroutineScope.launch {
            try {
                val videoPath = cameraManager.stopVideoRecording()
                callback(Result.success(videoPath))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop video recording", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun getCameraSensorAspectRatio(callback: (Result<Double>) -> Unit) {
        Log.d(TAG, "Get camera sensor aspect ratio")
        
        coroutineScope.launch {
            try {
                val ratio = cameraManager.getSensorAspectRatio()
                callback(Result.success(ratio))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera sensor aspect ratio", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun setFilterMode(mode: CameraFilterMode, parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Set filter mode: $mode, parameters: $parameters")
        
        coroutineScope.launch {
            try {
                // Áp dụng filter mới
                filterProcessor.setFilter(mode, parameters)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set filter mode", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun setScaleType(scaleType: ScaleType, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Set scale type: $scaleType")
        
        coroutineScope.launch {
            try {
                cameraManager.setScaleType(scaleType)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set scale type", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun getAvailableFilters(callback: (Result<List<FilterInfo>>) -> Unit) {
        Log.d(TAG, "Get available filters")
        
        coroutineScope.launch {
            try {
                // Lấy danh sách các filter có sẵn từ FilterProcessor
                val filters = filterProcessor.getAvailableFilters()
                callback(Result.success(filters))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get available filters", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun adjustFilterParameters(parameters: FilterParameters, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Adjust filter parameters: $parameters")
        
        coroutineScope.launch {
            try {
                // Cập nhật tham số cho filter hiện tại
                // Ở đây chúng ta giữ nguyên filter mode, chỉ thay đổi tham số
                val currentMode = filterProcessor.getCurrentFilterMode()
                filterProcessor.setFilter(currentMode, parameters)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to adjust filter parameters", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun getAvailableCameras(callback: (Result<List<CameraInfo>>) -> Unit) {
        Log.d(TAG, "Get available cameras")
        
        coroutineScope.launch {
            try {
                val cameras = cameraManager.getAvailableCameras()
                callback(Result.success(cameras))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get available cameras", e)
                callback(Result.failure(e))
            }
        }
    }
    
    // Thêm phương thức helper
    private suspend fun Context.createTempFile(prefix: String, suffix: String): java.io.File = withContext(Dispatchers.IO) {
        java.io.File.createTempFile(prefix, suffix, cacheDir).apply {
            deleteOnExit()
        }
    }
} 