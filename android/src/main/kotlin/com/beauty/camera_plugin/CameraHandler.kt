package com.beauty.camera_plugin

import android.annotation.SuppressLint
import android.content.Context
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.util.Log
import android.util.Size
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.video.*
import com.beauty.camera_plugin.models.CameraSettings
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executor

/**
 * CameraHandler: Quản lý toàn bộ hoạt động của CameraX.
 *
 * Class này được tái cấu trúc để rõ ràng và dễ bảo trì hơn:
 * - Đơn giản hóa luồng khởi tạo.
 * - Tách biệt logic cấu hình use case.
 * - Kích hoạt và hoàn thiện chức năng quay video.
 *
 * @param context Context của ứng dụng.
 * @param lifecycleOwner Vòng đời để CameraX tự động quản lý tài nguyên.
 * @param settings Cấu hình camera được truyền từ Flutter.
 */
@SuppressLint("RestrictedApi")
class CameraHandler(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val settings: CameraSettings
) {
    companion object {
        private const val TAG = "CameraHandler"
    }

    // CameraX core components
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    // Use Cases
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    /**
     * Khởi tạo và bắt đầu hiển thị camera preview lên một Surface.
     *
     * @param surface Surface dùng để hiển thị preview (thường từ Texture widget của Flutter).
     * @param onInitialized Callback được gọi khi camera đã sẵn sàng.
     */
    fun startCameraPreview(surface: Surface, onInitialized: () -> Unit) {
        Log.d(TAG, "Starting camera preview...")
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            setupAndBindUseCases(surface)
            onInitialized()
            Log.d(TAG, "Camera preview started successfully.")
        }, mainExecutor)
    }

    /**
     * Cấu hình các use case (Preview, ImageCapture, VideoCapture) và gắn chúng vào lifecycle.
     */
    private fun setupAndBindUseCases(surface: Surface) {
        val cameraProvider = cameraProvider ?: run {
            Log.e(TAG, "CameraProvider is not available.")
            return
        }

        // 1. Unbind mọi use case cũ trước khi cấu hình lại
        cameraProvider.unbindAll()

        // 2. Cấu hình các use case mới
        preview = createPreviewUseCase(surface)
        imageCapture = createImageCaptureUseCase()
        videoCapture = createVideoCaptureUseCase()

        // 3. Chọn camera trước/sau
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(settings.cameraLensFacing)
            .build()

        // 4. Gắn các use case vào lifecycle
        try {
            val useCases = listOfNotNull(preview, imageCapture, videoCapture)
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )
            // Áp dụng cài đặt zoom
            camera?.cameraControl?.setZoomRatio(settings.zoom.toFloat())
            Log.d(TAG, "Use cases bound to lifecycle.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind use cases", e)
        }
    }

    /**
     * Bắt đầu quay video.
     *
     * @param outputFile Đường dẫn file để lưu video.
     * @param onResult Callback trả về true nếu thành công, false nếu có lỗi.
     */
    @SuppressLint("MissingPermission")
    fun startVideoRecording(outputFile: String, onResult: (Boolean) -> Unit) {
        val currentVideoCapture = videoCapture ?: run {
            Log.e(TAG, "VideoCapture is not initialized.")
            onResult(false)
            return
        }

        // Dừng ghi hình cũ nếu có
        stopVideoRecording()

        val file = File(outputFile)
        val outputOptions = FileOutputOptions.Builder(file).build()

        activeRecording = currentVideoCapture.output
            .prepareRecording(context, outputOptions)
            .start(mainExecutor) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        Log.d(TAG, "Video recording started.")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (recordEvent.hasError()) {
                            Log.e(TAG, "Video recording failed: ${recordEvent.error} - ${recordEvent.cause?.message}")
                            activeRecording = null
                            onResult(false)
                        } else {
                            Log.d(TAG, "Video recording completed successfully: ${recordEvent.outputResults.outputUri}")
                            onResult(true)
                        }
                    }
                }
            }
        Log.d(TAG, "Recording prepared and started for: $outputFile")
    }

    /**
     * Dừng quay video hiện tại.
     */
    fun stopVideoRecording() {
        activeRecording?.let {
            it.stop()
            activeRecording = null
            Log.d(TAG, "Video recording stopped.")
        }
    }

    /**
     * Lấy kích thước của preview sau khi đã điều chỉnh theo xoay màn hình.
     * @return PreviewSize chứa width và height, hoặc null nếu chưa sẵn sàng.
     */
    fun getPreviewSize(): PreviewSize? {
        val info = preview?.resolutionInfo ?: return null
        val resolution = info.resolution
        val rotation = info.rotationDegrees

        // Nếu xoay 90 hoặc 270 độ, thì width và height sẽ bị đảo ngược
        val isSideways = rotation == 90 || rotation == 270
        val adjustedWidth = if (isSideways) resolution.height.toLong() else resolution.width.toLong()
        val adjustedHeight = if (isSideways) resolution.width.toLong() else resolution.height.toLong()

        Log.d(TAG, "getPreviewSize: original=${resolution.width}x${resolution.height}, rotation=$rotation°, adjusted=${adjustedWidth}x${adjustedHeight}")
        return PreviewSize(width = adjustedWidth, height = adjustedHeight)
    }

    /**
     * Giải phóng tài nguyên camera.
     */
    fun dispose() {
        Log.d(TAG, "Disposing camera handler.")
        stopVideoRecording()
        cameraProvider?.unbindAll()
        camera = null
        preview = null
        imageCapture = null
        videoCapture = null
    }

    // --- Use Case Factory Methods ---

    private fun createPreviewUseCase(surface: Surface): Preview {
        return Preview.Builder()
            .setTargetRotation(settings.displayOrientation)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(settings.resolution, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            .build()
            .also { it.setSurfaceProvider { request -> request.provideSurface(surface, mainExecutor, null) } }
    }

    private fun createImageCaptureUseCase(): ImageCapture {
        return ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(settings.displayOrientation)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(settings.resolution, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            .build()
    }

    private fun createVideoCaptureUseCase(): VideoCapture<Recorder> {
        val quality = when (settings.videoQuality) {
            VideoQuality.LOW -> Quality.SD
            VideoQuality.MEDIUM -> Quality.HD
            VideoQuality.HIGH -> Quality.FHD
            else -> Quality.HIGHEST
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(quality))
            .build()

        return VideoCapture.withOutput(recorder).apply {
            targetRotation = settings.displayOrientation
        }
    }
}