package com.beauty.camera_plugin

import android.util.Log
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Triển khai API cơ bản cho camera.
 * Wrapper đơn giản cho BeautyCameraManager.
 */
class CameraApiImpl(
    private val cameraManager: BeautyCameraManager
) : CameraApi {
    companion object {
        private const val TAG = "CameraApiImpl"
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activityBinding: ActivityPluginBinding? = null
    
    fun setActivityBinding(binding: ActivityPluginBinding?) {
        activityBinding = binding
    }
    
    fun dispose() {
        // Không cần giải phóng cameraManager ở đây, vì nó đã được quản lý bởi BeautyCameraHostApiImpl
    }

    override fun initialize(settings: CameraSettings, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Initialize with settings: $settings")
        
        // Check if we have a valid activity context
        val activity = activityBinding?.activity
        if (activity == null) {
            Log.e(TAG, "Activity context is null")
            callback(Result.failure(IllegalStateException("Activity context is null")))
            return
        }
        
        // Chuyển đổi từ CameraSettings sang AdvancedCameraSettings
        val advancedSettings = AdvancedCameraSettings(
            previewWidth = settings.previewWidth,
            previewHeight = settings.previewHeight,
            enableFaceDetection = settings.enableFaceDetection
        )
        
        coroutineScope.launch {
            try {
                // Sử dụng BeautyCameraManager để khởi tạo
                cameraManager.initialize(
                    settings = advancedSettings,
                    lifecycleOwner = activity as androidx.lifecycle.LifecycleOwner,
                    context = activity
                )
                
                // Thiết lập các tham số khác nếu có
                settings.flashMode?.let { cameraManager.setFlashMode(it) }
                settings.zoom?.let { cameraManager.setZoom(it) }
                settings.displayOrientation?.let { cameraManager.setDisplayOrientation(it) }
                
                callback(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize camera", e)
                callback(Result.failure(e))
            }
        }
    }

    override fun startPreview(textureId: Long, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Start preview with texture ID: $textureId")
        
        // Texture ID đã được thiết lập trong cameraManager.initialize()
        // Ở đây chúng ta chỉ cần trả về success
        callback(Result.success(Unit))
    }

    override fun stopPreview(callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Stop preview")
        
        // Vì cameraManager không có phương thức stopPreview riêng biệt,
        // chúng ta sẽ đơn giản hóa và gọi thành công
        callback(Result.success(Unit))
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

    override fun setZoom(zoom: Double, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Set zoom: $zoom")
        
        coroutineScope.launch {
            try {
                cameraManager.setZoom(zoom)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set zoom", e)
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

    override fun startVideoRecording( callback: (Result<Unit>) -> Unit) {

//        Log.d(TAG, "Start video recording to path: $filePath")
        
        // Trong triển khai hiện tại, cameraManager.startVideoRecording() không 
        // nhận filePath mà tự tạo file. Trong trường hợp thực tế, cần sửa lại 
        // để hỗ trợ filePath
        
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

    override fun dispose(callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Dispose CameraApi")
        
        // Không thực sự giải phóng cameraManager ở đây, vì nó được quản lý 
        // bởi BeautyCameraHostApiImpl. Chỉ đơn giản trả về thành công.
        callback(Result.success(Unit))
    }
} 