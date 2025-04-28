package com.beauty.camera_plugin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.Recorder
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.view.TextureRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Quản lý camera sử dụng CameraX API
 * Xử lý vòng đời của camera và các chức năng camera chính
 */
class BeautyCameraManager(
    private val textureRegistry: TextureRegistry,
    private val flutterApi: BeautyCameraFlutterApi,
    private val filterProcessor: FilterProcessor
) {
    companion object {
        private const val TAG = "BeautyCameraManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Các biến liên quan đến camera
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var imageAnalysis: ImageAnalysis? = null
    
    // Flutter texture entry
    private var flutterTexture: TextureRegistry.SurfaceTextureEntry? = null
    
    // Activity related
    private var activityContext: Context? = null
    private var lifecycleOwner: LifecycleOwner? = null
    
    // Cài đặt camera
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = FlashMode.OFF
    private var currentZoom = 1.0
    private var previewSize: Size? = null
    private var scaleType = ScaleType.CENTER_CROP
    private var displayOrientation = 0L
    private var isRecording = false
    
    private val mainExecutor by lazy {
        activityContext?.let { ContextCompat.getMainExecutor(it) }
    }
    
    // Output directory cho ảnh và video
    private val outputDirectory: File by lazy {
        val mediaDir = activityContext?.externalMediaDirs?.firstOrNull()?.let {
            File(it, "BeautyCamera").apply { mkdirs() }
        }
        if (mediaDir != null && mediaDir.exists()) mediaDir else activityContext?.filesDir!!
    }
    
    fun setActivityBinding(binding: ActivityPluginBinding?) {
        if (binding != null) {
            activityContext = binding.activity
            lifecycleOwner = binding.lifecycle as? LifecycleOwner
        } else {
            activityContext = null
            lifecycleOwner = null
        }
    }

    /**
     * Lấy Flutter texture ID để hiển thị preview camera
     */
    fun getFlutterTextureId(): Long {
        return flutterTexture?.id() ?: throw IllegalStateException("Texture entry not created")
    }

    /**
     * Khởi tạo camera với các cài đặt cụ thể
     * Trả về textureId để Flutter sử dụng
     */
    suspend fun initialize(
        settings: AdvancedCameraSettings,
        lifecycleOwner: LifecycleOwner,
        context: Context
    ): Long {
        Log.d(TAG, "Initializing camera with settings: $settings")
        
        // Kiểm tra nếu camera đã được khởi tạo trước đó
        if (camera != null) {
            Log.d(TAG, "Camera already initialized, disposing first")
            dispose()
        }
        
        try {
            // Lưu lại context và LifecycleOwner
            this.activityContext = context
            this.lifecycleOwner = lifecycleOwner
            
            // Tạo texture trước
            flutterTexture = textureRegistry.createSurfaceTexture()
            val textureId = flutterTexture?.id() ?: throw IllegalStateException("Failed to create texture")
            Log.d(TAG, "Created Flutter texture with ID: $textureId")
            
            // Khởi tạo FilterProcessor với context
            // (Đã được inject thông qua constructor, chỉ cần initialize)
            filterProcessor.initialize(context)
            
            // Khởi tạo CameraX
            val processCameraProvider = withContext(Dispatchers.IO) {
                ProcessCameraProvider.getInstance(context).get()
            }
            cameraProvider = processCameraProvider
            
            // Setup các trường hợp sử dụng camera
            setupUseCases(settings)
            
            // Áp dụng các cài đặt mặc định
            withContext(Dispatchers.Main) {
                flashMode = settings.videoQuality?.let {
                    when (it) {
                        VideoQuality.LOW, VideoQuality.MEDIUM -> FlashMode.OFF
                        else -> FlashMode.AUTO
                    }
                } ?: FlashMode.AUTO
                
                // Khởi tạo TextureView cho preview
                setupFlutterTexture()
                
                // Thiết lập camera với LifecycleOwner
                bindToLifecycle(lifecycleOwner)
            }
            
            // Đánh dấu là đã khởi tạo thành công
            Log.d(TAG, "Camera initialized successfully")
            
            return textureId
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing camera: ${e.message}", e)
            throw e
        }
    }
    
    private fun setupFaceDetection() {
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            
        val executor = mainExecutor
        if (executor != null) {
            imageAnalysis?.setAnalyzer(executor) { imageProxy ->
                // TODO: Implement face detection with ML Kit or CameraX FaceDetection extension
                // Sau khi phát hiện khuôn mặt:
                // val faces = ... // Danh sách các khuôn mặt phát hiện được
                // flutterApi.onFaceDetected(faces) { /* ignore result */ }
                
                imageProxy.close()
            }
        } else {
            Log.e(TAG, "Cannot setup face detection: mainExecutor is null")
        }
    }

    fun dispose() {
        coroutineScope.launch {
            try {
                // Giải phóng camera
                cameraProvider?.unbindAll()
                camera = null
                
                // Giải phóng FilterProcessor
                filterProcessor.release()
                
                // Giải phóng texture
                flutterTexture?.release()
                flutterTexture = null
                
                // Reset các biến
                preview = null
                imageCapture = null
                videoCapture = null
                imageAnalysis = null
                cameraProvider = null
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing camera", e)
            }
        }
    }
    
    suspend fun switchCamera(): Unit = withContext(Dispatchers.Main) {
        try {
            // Đổi lens facing (front/back)
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            
            // Tái cấu hình camera với lens facing mới
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
                
            // Unbind tất cả và bind lại
            cameraProvider?.unbindAll()
            
            // Bind các use case vào camera
            val lifecycleOwner = lifecycleOwner ?: throw IllegalStateException("LifecycleOwner is null")
            val provider = cameraProvider ?: throw IllegalStateException("CameraProvider is null")
            
            // Chuẩn bị danh sách use cases
            val useCases = mutableListOf<androidx.camera.core.UseCase>()
            
            // Thêm preview
            if (preview != null) {
                useCases.add(preview!!)
            }
            
            // Thêm image capture
            if (imageCapture != null) {
                useCases.add(imageCapture!!)
            }
            
            // Thêm video capture
            if (videoCapture != null) {
                useCases.add(videoCapture!!)
            }
            
            // Thêm image analysis
            if (imageAnalysis != null) {
                useCases.add(imageAnalysis!!)
            }
            
            if (useCases.isEmpty()) {
                throw IllegalStateException("No use cases to bind")
            }
            
            // Bind tất cả use cases
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )
            
            // Thiết lập lại flash mode
            setFlashModeInternal(flashMode)
            
            // Thông báo cho Flutter về camera đã switch
            val cameraId = if (lensFacing == CameraSelector.LENS_FACING_BACK) "back" else "front"
            flutterApi.onCameraSwitched(cameraId) { /* ignore result */ }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch camera", e)
            throw e
        }
    }
    
    suspend fun setZoom(zoomLevel: Double): Unit = withContext(Dispatchers.Main) {
        try {
            // Kiểm tra camera
            val cam = camera ?: throw IllegalStateException("Camera not initialized")

            // Lấy range của camera
            val zoomState = cam.cameraInfo.zoomState.value
            val minZoom = zoomState?.minZoomRatio ?: 1.0f
            val maxZoom = zoomState?.maxZoomRatio ?: 5.0f
            
            // Giới hạn zoom trong khoảng hợp lệ
            val normalizedZoom = zoomLevel.coerceIn(minZoom.toDouble(), maxZoom.toDouble())
            
            // Áp dụng zoom
            cam.cameraControl.setZoomRatio(normalizedZoom.toFloat())
            
            // Lưu giá trị zoom hiện tại
            currentZoom = normalizedZoom
            
            // Thông báo cho Flutter
            flutterApi.onZoomChanged(normalizedZoom) { /* ignore result */ }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set zoom", e)
            throw e
        }
    }
    
    suspend fun focusOnPoint(x: Long, y: Long): Unit = withContext(Dispatchers.Main) {
        try {
            // Kiểm tra camera
            val cam = camera ?: throw IllegalStateException("Camera not initialized")
            val executor = mainExecutor ?: throw IllegalStateException("Main executor is null")
            
            // Sử dụng auto-focus thay vì focus cụ thể vì khó khăn với MeteringPoint
            val future = cam.cameraControl.cancelFocusAndMetering()
            
            // Đợi kết quả
            suspendCoroutine { continuation ->
                future.addListener({
                    try {
                        // Giả sử focus thành công
                        continuation.resume(Unit)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }, executor)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to focus on point", e)
            throw e
        }
    }
    
    suspend fun setFlashMode(mode: FlashMode): Unit = withContext(Dispatchers.Main) {
        try {
            // Lưu flash mode
            flashMode = mode
            
            // Áp dụng cho camera
            setFlashModeInternal(mode)
            
            // Thông báo cho Flutter
            flutterApi.onFlashModeChanged(mode) { /* ignore result */ }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set flash mode", e)
            throw e
        }
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
    
    suspend fun setDisplayOrientation(degrees: Long): Unit = withContext(Dispatchers.Main) {
        try {
            displayOrientation = degrees
            
            // Update rotation của camera
            val rotation = when (degrees) {
                0L -> Surface.ROTATION_0
                90L -> Surface.ROTATION_90
                180L -> Surface.ROTATION_180
                270L -> Surface.ROTATION_270
                else -> Surface.ROTATION_0
            }
            
            // Set rotation cho các use case
            imageCapture?.targetRotation = rotation
            imageAnalysis?.targetRotation = rotation
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set display orientation", e)
            throw e
        }
    }
    
    suspend fun getPreviewSize(): PreviewSize = withContext(Dispatchers.Main) {
        previewSize?.let {
            PreviewSize(it.width.toLong(), it.height.toLong())
        } ?: throw IllegalStateException("Preview size not available")
    }
    
    suspend fun getSensorAspectRatio(): Double = withContext(Dispatchers.Main) {
        previewSize?.let {
            it.width.toDouble() / it.height.toDouble()
        } ?: throw IllegalStateException("Preview size not available")
    }
    
    suspend fun takePhoto(): String = withContext(Dispatchers.IO) {
        val capture = imageCapture ?: throw IllegalStateException("Image capture not initialized")
        val executor = mainExecutor ?: throw IllegalStateException("Main executor is null")
        
        suspendCoroutine { continuation ->
            try {
                // Tạo file đầu ra
                val photoFile = File(
                    outputDirectory,
                    SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                        .format(System.currentTimeMillis()) + ".jpg"
                )
                
                // Cấu hình output
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                
                // Chụp ảnh
                capture.takePicture(
                    outputOptions,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            try {
                                val savedUri = outputFileResults.savedUri
                                val path = if (savedUri != null) {
                                    if (savedUri.toString().startsWith("file://")) {
                                        savedUri.toString().substring(7) 
                                    } else {
                                        photoFile.absolutePath
                                    }
                                } else {
                                    photoFile.absolutePath
                                }
                                
                                Log.d(TAG, "Photo saved to: $path")
                                continuation.resume(path)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing saved image: ${e.message}", e)
                                continuation.resumeWithException(e)
                            }
                        }
                        
                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                            continuation.resumeWithException(exception)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error taking photo: ${e.message}", e)
                continuation.resumeWithException(e)
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    suspend fun startVideoRecording(): Unit = withContext(Dispatchers.IO) {
        if (activityContext == null) throw IllegalStateException("Context is null")
        if (isRecording) throw IllegalStateException("Video recording already in progress")
        
        try {
            val vc = videoCapture ?: throw IllegalStateException("Video capture not initialized")
            val context = activityContext ?: throw IllegalStateException("Context is null")
            val executor = mainExecutor ?: throw IllegalStateException("Main executor is null")
            
            // Tạo file đầu ra
            val videoFile = File(
                outputDirectory,
                SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                    .format(System.currentTimeMillis()) + ".mp4"
            )
            
            // Cấu hình output
            val outputOptions = FileOutputOptions.Builder(videoFile).build()
            
            // Tham chiếu tới recording hiện tại
            recording = vc.output
                .prepareRecording(context, outputOptions)
                .start(executor) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            // Thông báo cho Flutter về việc bắt đầu ghi video
                            coroutineScope.launch(Dispatchers.Main) {
                                flutterApi.onVideoRecordingStarted { /* ignore result */ }
                            }
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            // Thông báo cho Flutter về việc kết thúc ghi video nếu thành công
                            if (recordEvent.hasError()) {
                                Log.e(TAG, "Video recording failed: ${recordEvent.error}")
                            } else {
                                // Sử dụng đường dẫn file trực tiếp thay vì đi qua URI
                                val videoPath = videoFile.absolutePath
                                Log.d(TAG, "Video saved to: $videoPath")
                                
                                coroutineScope.launch(Dispatchers.Main) {
                                    flutterApi.onVideoRecordingStopped(videoPath) { /* ignore result */ }
                                }
                            }
                        }
                    }
                }
            
            isRecording = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start video recording", e)
            throw e
        }
    }
    
    suspend fun stopVideoRecording(): String = withContext(Dispatchers.IO) {
        if (!isRecording) throw IllegalStateException("No video recording in progress")
        
        try {
            val currentRecording = recording
                ?: throw IllegalStateException("Cannot stop recording, recording is null")

            // Dừng recording hiện tại
            currentRecording.stop()
            recording = null
            
            // Chờ kết quả từ callback VideoRecordEvent.Finalize
            suspendCoroutine { continuation ->
                // Sau khi dừng ghi video, kết quả sẽ được xử lý trong 
                // callback VideoRecordEvent.Finalize ở phương thức startVideoRecording
                // Ở đây chúng ta sẽ tìm file video mới nhất để trả về
                val videoFiles = outputDirectory.listFiles { file ->
                    file.isFile && file.name.endsWith(".mp4")
                }
                
                if (videoFiles != null && videoFiles.isNotEmpty()) {
                    // Lấy file mới nhất
                    val latestVideo = videoFiles.maxByOrNull { it.lastModified() }
                    if (latestVideo != null) {
                        continuation.resume(latestVideo.absolutePath)
                    } else {
                        continuation.resumeWithException(IllegalStateException("No video file found"))
                    }
                } else {
                    continuation.resumeWithException(IllegalStateException("No video file found"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop video recording", e)
            throw e
        }
    }
    
    suspend fun setScaleType(scaleType: ScaleType): Unit = withContext(Dispatchers.Main) {
        try {


            // TODO: Cần implement thêm logic điều chỉnh scaling
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set scale type", e)
            throw e
        }
    }
    
    suspend fun getAvailableCameras(): List<CameraInfo> = withContext(Dispatchers.IO) {
        if (activityContext == null) throw IllegalStateException("Context is null")
        
        try {
            val provider = ProcessCameraProvider.getInstance(activityContext!!).get()
            val cameraList = mutableListOf<CameraInfo>()
            
            // Kiểm tra camera sau
            if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                val backCamera = CameraInfo(
                    id = "back",
                    facing = CameraFacing.BACK,
                    hasFlash = true, // Thường các camera sau đều có flash
                    supportedResolutions = getDefaultResolutions()
                )
                cameraList.add(backCamera)
            }
            
            // Kiểm tra camera trước
            if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                val frontCamera = CameraInfo(
                    id = "front",
                    facing = CameraFacing.FRONT,
                    hasFlash = false, // Camera trước thường không có flash
                    supportedResolutions = getDefaultResolutions()
                )
                cameraList.add(frontCamera)
            }
            
            return@withContext cameraList
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available cameras", e)
            throw e
        }
    }
    
    private fun getDefaultResolutions(): List<ResolutionInfo> {
        // Danh sách cố định độ phân giải phổ biến
        return listOf(
            ResolutionInfo(width = 640, height = 480),
            ResolutionInfo(width = 1280, height = 720),
            ResolutionInfo(width = 1920, height = 1080),
            ResolutionInfo(width = 2560, height = 1440)
        )
    }

    /**
     * Setup các trường hợp sử dụng camera (preview, image capture, video recording)
     */
    private fun setupUseCases(settings: AdvancedCameraSettings) {
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
        
        // Cấu hình VideoCapture
        try {
            val recorder = Recorder.Builder()
                .apply {
                    settings.videoQuality?.let { quality ->
                        // Chuyển đổi từ enum VideoQuality sang QualitySelector của CameraX
                        val qualitySelector = when (quality) {
                            VideoQuality.LOW -> QualitySelector.from(Quality.SD)
                            VideoQuality.MEDIUM -> QualitySelector.from(Quality.HD)
                            VideoQuality.HIGH -> QualitySelector.from(Quality.FHD)
                            VideoQuality.VERY_HIGH -> QualitySelector.from(Quality.UHD)
                            VideoQuality.ULTRA -> QualitySelector.from(Quality.HIGHEST)
                        }
                        setQualitySelector(qualitySelector)
                    }
                }
                .build()
            
            videoCapture = VideoCapture.withOutput(recorder)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize video capture: ${e.message}")
            // Không throw để tiếp tục với chụp ảnh nếu video không khởi tạo được
        }
        
        // Cấu hình ImageAnalysis cho face detection (nếu cần)
        if (settings.enableFaceDetection == true) {
            setupFaceDetection()
        }
    }

    /**
     * Khởi tạo Flutter texture để hiển thị preview
     */
    private fun setupFlutterTexture() {
        // Tạo SurfaceTexture mới cho Flutter
        flutterTexture = textureRegistry.createSurfaceTexture()
        val flutterSurfaceTexture = flutterTexture?.surfaceTexture()
            ?: throw IllegalStateException("Failed to create surface texture")
        
        // Chỉ định kích thước mặc định cho surfaceTexture - sẽ được điều chỉnh khi camera connect
        flutterSurfaceTexture.setDefaultBufferSize(1920, 1080)
        
        // Tạo một SurfaceTexture trung gian để nhận frames từ camera
        // Điều này sẽ ngăn không cho CameraX và FilterProcessor cùng sử dụng một surface
        val cameraSurfaceTexture = SurfaceTexture(0)
        cameraSurfaceTexture.setDefaultBufferSize(1920, 1080)
        
        // Kết nối preview với camera texture
        preview?.setSurfaceProvider { request ->
            // Log thông tin request
            Log.d(TAG, "Preview request received: resolution=${request.resolution}")
            
            // Cập nhật kích thước buffer tương ứng với độ phân giải của camera
            cameraSurfaceTexture.setDefaultBufferSize(
                request.resolution.width,
                request.resolution.height
            )
            flutterSurfaceTexture.setDefaultBufferSize(
                request.resolution.width,
                request.resolution.height
            )
            previewSize = request.resolution
            
            // Tạo surface từ camera surface texture
            val cameraSurface = Surface(cameraSurfaceTexture as SurfaceTexture)
            
            // Kết nối với Flutter surface
            val flutterSurface = Surface(flutterSurfaceTexture as SurfaceTexture)
            
            // Thiết lập frame listener cho camera surface
            cameraSurfaceTexture.setOnFrameAvailableListener { _: SurfaceTexture ->
                try {
                    // Cập nhật texture image
                    cameraSurfaceTexture.updateTexImage()
                    
                    // Lấy ma trận chuyển đổi
                    val transformMatrix = FloatArray(16)
                    cameraSurfaceTexture.getTransformMatrix(transformMatrix)
                    
                    // Chuyển frame từ camera đến flutter surface thông qua FilterProcessor
                    filterProcessor.onFrameAvailable(cameraSurfaceTexture)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating frame", e)
                }
            }
            
            try {
                // Thử setup renderer với flutter surface
                val setupSuccess = filterProcessor.setupRenderer(flutterSurface)
                if (!setupSuccess) {
                    Log.e(TAG, "Failed to setup renderer with surface! Camera preview may not apply filters correctly")
                } else {
                    Log.d(TAG, "Renderer setup successfully")
                }
                
                // Cung cấp surface cho CameraX
                request.provideSurface(cameraSurface, ContextCompat.getMainExecutor(activityContext!!)) { _ ->
                    // Surface sẽ được giải phóng khi nó không còn được sử dụng nữa
                    Log.d(TAG, "CameraX has released the surface")
                    cameraSurface.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error providing surface to CameraX", e)
                
                // Nếu có lỗi, vẫn phải giải phóng surface để tránh rò rỉ bộ nhớ
                cameraSurface.release()
                flutterSurface.release()
                throw e
            }
        }
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
        
        // Thêm image capture
        if (imageCapture != null) {
            useCases.add(imageCapture!!)
        }
        
        // Thêm video capture
        if (videoCapture != null) {
            useCases.add(videoCapture!!)
        }
        
        // Thêm image analysis
        if (imageAnalysis != null) {
            useCases.add(imageAnalysis!!)
        }
        
        if (useCases.isEmpty()) {
            throw IllegalStateException("No use cases to bind")
        }
        
        // Bind tất cả use cases
        camera = cameraProvider?.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            *useCases.toTypedArray()
        )
        
        // Thiết lập flash mode
        setFlashModeInternal(flashMode)
    }

    /**
     * Áp dụng bộ lọc mới cho camera preview
     */
    suspend fun setFilterMode(filterMode: CameraFilterMode, parameters: FilterParameters): Unit = withContext(Dispatchers.Main) {
        try {
            // Cập nhật filter trong FilterProcessor
            filterProcessor.setFilter(filterMode, parameters)
            
            // Log để debugging
            Log.d(TAG, "Set filter mode: $filterMode, parameters: $parameters")
            
            // Thông báo cho Flutter
            flutterApi.onFilterModeChanged(filterMode) { /* ignore result */ }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set filter mode", e)
            throw e
        }
    }
} 