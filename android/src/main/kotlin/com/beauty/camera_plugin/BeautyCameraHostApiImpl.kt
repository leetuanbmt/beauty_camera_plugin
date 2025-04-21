package com.beauty.camera_plugin

import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import com.beauty.camera_plugin.camera.CameraController
import com.beauty.camera_plugin.camera.filters.FilterManager
import com.beauty.camera_plugin.camera.utils.*
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.TextureRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Implementation of BeautyCameraHostApi
 */
class BeautyCameraHostApiImpl(
    private val activity: Activity,
    private val messenger: BinaryMessenger,
    private val textureRegistry: TextureRegistry
) : BeautyCameraHostApi  {
    private var cameraController: CameraController? = null
    private var filterManager: FilterManager? = null
    private var previewTexture: TextureRegistry.SurfaceTextureEntry? = null
    private var imageReader: ImageReader? = null
    private var isRecording = false
    private var isReleased = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val errorUtils = ErrorUtils()

    override fun initializeCamera(settings: CameraSettings, callback: (Result<Unit>) -> Unit) {
        if (isReleased) {
            throw CameraNotAvailableException("Camera is released")
        }

        try {
            // Check permissions
            if (!PermissionUtils.hasCameraPermission(activity)) {
                throw CameraPermissionException()
            }

            // Initialize camera controller
            cameraController = CameraController(activity, textureRegistry)

            // Initialize filter manager
            filterManager = FilterManager()

            // Create preview texture
            createPreviewTexture()

            callback(Result.success(Unit))

        } catch (e: Exception) {
            errorUtils.handleError(e) { result ->
                result.onFailure { error ->
                    errorUtils.logError("BeautyCameraHostApiImpl", "Failed to initialize camera", error as Throwable)
                    callback(Result.failure(error))
                }
            }
        }
    }

    override fun startPreview(textureId: Long, callback: (Result<Unit>) -> Unit) {
        if (isReleased) {
            throw CameraNotAvailableException("Camera is released")
        }

        try {
            // Initialize filter manager if needed
            if (filterManager == null) {
                filterManager = FilterManager()
            }

            // Configure camera preview
            previewTexture?.let { texture ->
                val surface = Surface(texture.surfaceTexture())
                cameraController?.startPreview(
                    cameraId = "0", // Use default camera
                    width = CameraConstants.DEFAULT_PREVIEW_WIDTH,
                    height = CameraConstants.DEFAULT_PREVIEW_HEIGHT
                ) { result ->
                    result.fold(
                        onSuccess = {
                            callback(Result.success(Unit))
                        },
                        onFailure = { error ->
                            errorUtils.logError("BeautyCameraHostApiImpl", "Failed to start preview", error as Throwable)
                            callback(Result.failure(error))
                        }
                    )
                }
            } ?: throw CameraPreviewException("Preview texture not available")

        } catch (e: Exception) {
            errorUtils.handleError(e) { result ->
                result.onFailure { error ->
                    errorUtils.logError("BeautyCameraHostApiImpl", "Failed to start preview", error as Throwable)
                    callback(Result.failure(error))
                }
            }
        }
    }

    override fun stopPreview(callback: (Result<Unit>) -> Unit) {
        try {
            cameraController?.stopPreview { result: Result<Unit> ->
                result.fold(
                    onSuccess = {
                        filterManager?.release()
                        filterManager = null
                        callback(Result.success(Unit))
                    },
                    onFailure = { error ->
                        errorUtils.logError("BeautyCameraHostApiImpl", "Failed to stop preview", error as Throwable)
                        callback(Result.failure(error))
                    }
                )
            } ?: callback(Result.success(Unit))
        } catch (e: Exception) {
            errorUtils.handleError(e) { result ->
                result.onFailure { error ->
                    errorUtils.logError("BeautyCameraHostApiImpl", "Failed to stop preview", error as Throwable)
                    callback(Result.failure(error))
                }
            }
        }
    }

    override fun takePicture(path: String, callback: (Result<Unit>) -> Unit) {
        if (isReleased) {
            throw CameraNotAvailableException("Camera is released")
        }

        try {
            // Create image reader for capture
            imageReader = ImageReader.newInstance(
                CameraConstants.DEFAULT_PREVIEW_WIDTH,
                CameraConstants.DEFAULT_PREVIEW_HEIGHT,
                android.graphics.ImageFormat.JPEG,
                CameraConstants.DEFAULT_JPEG_QUALITY
            ).apply {
                setOnImageAvailableListener({ reader ->
                    // Handle captured image
                    val image = reader.acquireLatestImage()
                    try {
                        // Process image with current filter if any
                        filterManager?.let { manager ->
                            // Apply filter to image
                        }
                        // Save image to file
                        // TODO: Implement image saving
                    } finally {
                        image?.close()
                    }
                }, mainHandler)
            }

            // Take picture
            cameraController?.takePicture(path) { result ->
                result.fold(
                    onSuccess = {
                        callback(Result.success(Unit))
                    },
                    onFailure = { error ->
                        errorUtils.logError("BeautyCameraHostApiImpl", "Failed to take picture", error as Throwable)
                        callback(Result.failure(error))
                    }
                )
            }

        } catch (e: Exception) {
            errorUtils.handleError(e) { result ->
                result.onFailure { error ->
                    errorUtils.logError("BeautyCameraHostApiImpl", "Failed to take picture", error as Throwable)
                    callback(Result.failure(error))
                }
            }
        }
    }

    override fun startRecording(path: String, callback: (Result<Unit>) -> Unit) {
        if (isReleased) {
            throw CameraNotAvailableException("Camera is released")
        }

        try {
            if (isRecording) {
                throw CameraRecordingException("Recording already in progress")
            }

            // Start recording
            cameraController?.startRecording(path) { result ->
                result.fold(
                    onSuccess = {
                        isRecording = true
                        callback(Result.success(Unit))
                    },
                    onFailure = { error ->
                        errorUtils.logError("BeautyCameraHostApiImpl", "Failed to start recording", error as Throwable)
                        callback(Result.failure(error))
                    }
                )
            }
        } catch (e: Exception) {
            errorUtils.handleError(e) { result ->
                result.onFailure { error ->
                    errorUtils.logError("BeautyCameraHostApiImpl", "Failed to start recording", error as Throwable)
                    callback(Result.failure(error))
                }
            }
        }
    }

    override fun stopRecording(callback: (Result<Unit>) -> Unit) {
        try {
            if (!isRecording) {
                throw CameraRecordingException("No recording in progress")
            }

            // Stop recording
            cameraController?.stopRecording { result ->
                result.fold(
                    onSuccess = {
                        isRecording = false
                        callback(Result.success(Unit))
                    },
                    onFailure = { error ->
                        errorUtils.logError("BeautyCameraHostApiImpl", "Failed to stop recording", error as Throwable)
                        callback(Result.failure(error))
                    }
                )
            }
        } catch (e: Exception) {
            errorUtils.handleError(e) { result ->
                result.onFailure { error ->
                    errorUtils.logError("BeautyCameraHostApiImpl", "Failed to stop recording", error as Throwable)
                    callback(Result.failure(error))
                }
            }
        }
    }

    override fun applyFilter(textureId: Long, filterConfig: FilterConfig, callback: (Result<Unit>) -> Unit) {
        if (isReleased) {
            throw CameraNotAvailableException("Camera is released")
        }

        try {
            filterManager?.let { manager ->
                // Apply filter based on type
                when (filterConfig.filterType) {
                    "beauty" -> {
                        val blurSize = (filterConfig.parameters?.get("blurSize") as? Number)?.toFloat() ?: CameraConstants.DEFAULT_BLUR_SIZE
                        val brightness = (filterConfig.parameters?.get("brightness") as? Number)?.toFloat() ?: CameraConstants.DEFAULT_BRIGHTNESS
                        val smoothness = (filterConfig.parameters?.get("smoothness") as? Number)?.toFloat() ?: CameraConstants.DEFAULT_SHARPNESS
                        // TODO: Apply beauty filter
                    }
                    "vintage" -> {
                        val sepia = (filterConfig.parameters?.get("sepia") as? Number)?.toFloat() ?: 0.5f
                        val vignette = (filterConfig.parameters?.get("vignette") as? Number)?.toFloat() ?: 0.3f
                        // TODO: Apply vintage filter
                    }
                    "mono" -> {
                        val contrast = (filterConfig.parameters?.get("contrast") as? Number)?.toFloat() ?: CameraConstants.DEFAULT_CONTRAST
                        // TODO: Apply mono filter
                    }
                    "saturation" -> {
                        val saturation = (filterConfig.parameters?.get("saturation") as? Number)?.toFloat() ?: CameraConstants.DEFAULT_SATURATION
                        // TODO: Apply saturation filter
                    }
                    else -> throw CameraFilterException("Unknown filter type: ${filterConfig.filterType}")
                }
                callback(Result.success(Unit))
            } ?: throw CameraFilterException("Filter manager not initialized")

        } catch (e: Exception) {
            errorUtils.handleError(e) { result ->
                result.onFailure { error ->
                    errorUtils.logError("BeautyCameraHostApiImpl", "Failed to apply filter", error as Throwable)
                    callback(Result.failure(error))
                }
            }
        }
    }

    override fun disposeCamera(callback: (Result<Unit>) -> Unit) {
        try {
            isReleased = true
            stopPreview { _ -> }
            stopRecording { _ -> }
            cameraController?.release()
            cameraController = null
            filterManager?.release()
            filterManager = null
            previewTexture?.release()
            previewTexture = null
            imageReader?.close()
            imageReader = null
            callback(Result.success(Unit))
        } catch (e: Exception) {
            errorUtils.handleError(e) { result ->
                result.onFailure { error ->
                    errorUtils.logError("BeautyCameraHostApiImpl", "Failed to dispose camera", error as Throwable)
                    callback(Result.failure(error))
                }
            }
        }
    }

    override fun createPreviewTexture(callback: (Result<Long?>) -> Unit) {
        try {
            previewTexture = textureRegistry.createSurfaceTexture().apply {
                surfaceTexture().setOnFrameAvailableListener { texture ->
                    // Update filter transform matrix
                    val matrix = FloatArray(16)
                    texture.getTransformMatrix(matrix)
                    filterManager?.updateTransformMatrix(matrix)
                }
            }
            callback(Result.success(previewTexture?.id()))
        } catch (e: Exception) {
            errorUtils.handleError(e) { result ->
                result.onFailure { error ->
                    errorUtils.logError("BeautyCameraHostApiImpl", "Failed to create preview texture", error as Throwable)
                    callback(Result.failure(error))
                }
            }
        }
    }

    private fun createPreviewTexture() {
        previewTexture = textureRegistry.createSurfaceTexture().apply {
            surfaceTexture().setOnFrameAvailableListener { texture ->
                // Update filter transform matrix
                val matrix = FloatArray(16)
                texture.getTransformMatrix(matrix)
                filterManager?.updateTransformMatrix(matrix)
            }
        }
    }

    private fun safeApiCall(block: () -> Unit) {
        if (isReleased) {
            throw CameraNotAvailableException("Camera is released")
        }
        block()
    }
} 