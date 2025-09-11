package com.beauty.camera_plugin

import android.annotation.SuppressLint
import android.content.Context
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.TorchState
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.util.Log
import android.util.Size
import com.beauty.camera_plugin.models.CameraSettings



@SuppressLint("RestrictedApi")
class CameraHandler(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val settings: CameraSettings
) {
    companion object {
        private const val TAG = "CameraHandler"
    }
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var previewSize: Size? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    @SuppressLint("SuspiciousIndentation")
    fun initialize(callback: () -> Unit) {
      val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()
            
            // Bind use cases to camera
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
        val builder = Preview.Builder()

        // Cấu hình CameraSelector
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        builder.setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()
        )

        preview = builder.build()

         // Cấu hình ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()
            )
            .build()
        
    }


    private fun selectOptimalPreviewSize(
        supportedResolutions: Array<Size>?,
        targetResolution: Size,
        targetAspectRatio: Double
    ): Size? {
        if (supportedResolutions.isNullOrEmpty()) {
            return null
        }

        var optimalSize: Size? = null
        var minDiff = Double.MAX_VALUE

        for (size in supportedResolutions) {
            val aspectRatio = size.width.toDouble() / size.height.toDouble()
            if (Math.abs(aspectRatio - targetAspectRatio) > 0.01) { // Allow small tolerance for aspect ratio
                continue
            }

            val diff = Math.abs(size.width - targetResolution.width).toDouble()
            if (diff < minDiff) {
                minDiff = diff
                optimalSize = size
            } else if (diff == minDiff && size.width > (optimalSize?.width ?: 0)) {
                // If difference is same, prefer larger resolution
                optimalSize = size
            }
        }

        if (optimalSize == null) {
            // If no size with matching aspect ratio found, find the closest resolution
            minDiff = Double.MAX_VALUE
            for (size in supportedResolutions) {
                val diff = Math.abs(size.width - targetResolution.width).toDouble()
                if (diff < minDiff) {
                    minDiff = diff
                    optimalSize = size
                }
            }
        }
        return optimalSize
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