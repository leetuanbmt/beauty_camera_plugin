package com.beauty.camera_plugin

import android.annotation.SuppressLint
import android.content.Context
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.TorchState
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.extensions.ExtensionMode
import android.util.Log
import android.util.Size
import androidx.camera.core.AspectRatio



@SuppressLint("RestrictedApi")
class CameraHandler(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val settings: AdvancedCameraSettings
) {
    companion object {
        private const val TAG = "CameraHandler"
    }
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var previewSize: Size? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    fun initialize(callback: () -> Unit) {
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            setupUseCases()
            callback()
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("RestrictedApi")
    fun startCamera(surface: Surface) {

        preview?.setSurfaceProvider { request ->
            request.provideSurface(surface, ContextCompat.getMainExecutor(context), {})
        }

        bindToLifecycle(lifecycleOwner)
    }

    @SuppressLint("RestrictedApi")
    fun getPreviewSize(): PreviewSize? {
        val resolution = preview?.resolutionInfo?.resolution ?: return null
        return PreviewSize(width = resolution.width.toLong(), height = resolution.height.toLong())
    }

    fun dispose() {
        cameraProvider?.unbindAll()
        preview = null
        camera = null
        imageCapture = null
    }

     /**
     * Bind camera uses cases vào lifecycle
     */

      private fun bindToLifecycle(lifecycleOwner: LifecycleOwner) {
          // Unbind tất cả use case hiện tại
         cameraProvider?.unbindAll()
         // Cấu hình CameraSelector
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

         // Chuẩn bị danh sách use cases
        val useCases = mutableListOf<androidx.camera.core.UseCase>()

        // Thêm preview
        if (preview != null) {
            useCases.add(preview!!)
        }


        // Bind tất cả use cases
        camera = cameraProvider?.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            *useCases.toTypedArray()
        )
      }


     /**
     * Setup các trường hợp sử dụng camera (preview, image capture, video recording)
     */
    private fun setupUseCases() {
        // Cấu hình Preview
        preview = Preview.Builder().apply {
            settings.previewWidth?.let { width ->
                settings.previewHeight?.let { height ->
                    previewSize = Size(width.toInt(), height.toInt())
                    setTargetResolution(previewSize!!)
                }
            }
        }.build()


         // Cấu hình ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .apply {
                // Video stabilization không còn được hỗ trợ trong ImageCapture API mới
            }
            .build()
        
    }


    private fun setFlashModeInternal(mode: FlashMode) {
        try {
            // Kiểm tra ảnh chụp
            val capture = imageCapture
            if (capture != null) {
                // ImageCapture flash mode
                val imageCaptureMode = when (mode) {
                    FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    FlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF // Torch được xử lý riêng
                }
                capture.flashMode = imageCaptureMode
            }
            
            // Torch mode
            val cam = camera
            if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                if (mode == FlashMode.TORCH) {
                    cam.cameraControl.enableTorch(true)
                } else {
                    // Nếu trước đó đã bật torch, tắt đi
                    if (cam.cameraInfo.torchState.value == TorchState.ON) {
                        cam.cameraControl.enableTorch(false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting flash mode: ${e.message}")
        }
    }
}