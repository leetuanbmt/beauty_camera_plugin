package com.beauty.camera_plugin.camera.preview

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.util.Size
import android.view.Surface
import com.beauty.camera_plugin.camera.utils.*
import io.flutter.view.TextureRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Handles camera preview functionality
 */
class PreviewHandler(
    private val context: Context,
    private val textureRegistry: TextureRegistry,
    private val cameraManager: CameraManager,
    private val backgroundHandler: Handler,
    private val errorUtils: ErrorUtils
) {
    private var cameraDevice: CameraDevice? = null
    private var previewSession: android.hardware.camera2.CameraCaptureSession? = null
    private var previewRequestBuilder: android.hardware.camera2.CaptureRequest.Builder? = null
    private var previewRequest: android.hardware.camera2.CaptureRequest? = null
    private var previewSize: Size? = null
    private var previewSurface: Surface? = null
    private var textureId: Long = -1L
    private val cameraOpenCloseLock = CountDownLatch(1)

    /**
     * Start camera preview
     */
    fun startPreview(
        cameraId: String,
        width: Int,
        height: Int,
        callback: (Result<Long>) -> Unit
    ) {
        try {
            // Create preview texture
            val textureEntry = textureRegistry.createSurfaceTexture()
            textureId = textureEntry.id()
            previewSurface = Surface(textureEntry.surfaceTexture())

            // Configure preview size
            previewSize = chooseOptimalSize(
                cameraManager.getCameraCharacteristics(cameraId)
                    .get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray(),
                width,
                height
            )

            // Open camera
            openCamera(cameraId) { result ->
                result.fold(
                    onSuccess = {
                        // Create capture session
                        createCaptureSession(cameraId, callback)
                    },
                    onFailure = { error ->
                        callback(Result.failure(error))
                    }
                )
            }
        } catch (e: Exception) {
            callback(Result.failure(CameraPreviewException("Failed to start preview: ${e.message}")))
        }
    }

    /**
     * Stop camera preview
     */
    fun stopPreview() {
        try {
            previewSession?.close()
            previewSession = null
            previewRequestBuilder = null
            previewRequest = null
            previewSurface?.release()
            previewSurface = null
            closeCamera()
        } catch (e: Exception) {
            errorUtils.logError("PreviewHandler", "Failed to stop preview", e)
        }
    }

    /**
     * Get camera device
     */
    fun getCameraDevice(): CameraDevice? {
        return cameraDevice
    }

    /**
     * Get capture request builder
     */
    fun getCaptureRequestBuilder(): android.hardware.camera2.CaptureRequest.Builder? {
        return previewRequestBuilder
    }

    /**
     * Update preview request
     */
    fun updatePreviewRequest(request: android.hardware.camera2.CaptureRequest) {
        previewRequest = request
        previewSession?.setRepeatingRequest(
            request,
            object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {},
            backgroundHandler
        )
    }

    /**
     * Release resources
     */
    fun release() {
        stopPreview()
    }

    private fun openCamera(cameraId: String, callback: (Result<Unit>) -> Unit) {
        try {
            if (!cameraOpenCloseLock.await(CameraConstants.CAMERA_OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw CameraTimeoutException()
            }

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    cameraOpenCloseLock.countDown()
                    callback(Result.success(Unit))
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    cameraOpenCloseLock.countDown()
                    callback(Result.failure(CameraDisconnectedException()))
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    cameraOpenCloseLock.countDown()
                    callback(Result.failure(CameraException("Camera error: $error")))
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            cameraOpenCloseLock.countDown()
            callback(Result.failure(e))
        }
    }

    private fun createCaptureSession(cameraId: String, callback: (Result<Long>) -> Unit) {
        try {
            val surfaces = listOf(previewSurface!!)
            cameraDevice?.createCaptureSession(
                surfaces,
                object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                        previewSession = session
                        updatePreview()
                        callback(Result.success(textureId))
                    }

                    override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                        callback(Result.failure(CameraConfigurationException("Failed to configure camera session")))
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            callback(Result.failure(CameraPreviewException("Failed to create capture session: ${e.message}")))
        }
    }

    private fun updatePreview() {
        try {
            previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder?.addTarget(previewSurface!!)

            // Set auto focus mode
            previewRequestBuilder?.set(
                android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )

            // Set auto exposure mode
            previewRequestBuilder?.set(
                android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE,
                android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )

            // Build the request
            previewRequest = previewRequestBuilder?.build()

            // Start repeating request
            previewSession?.setRepeatingRequest(
                previewRequest!!,
                object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {},
                backgroundHandler
            )
        } catch (e: Exception) {
            errorUtils.logError("PreviewHandler", "Failed to update preview", e)
        }
    }

    private fun closeCamera() {
        try {
            if (!cameraOpenCloseLock.await(CameraConstants.CAMERA_CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw CameraTimeoutException()
            }
            cameraDevice?.close()
            cameraDevice = null
            cameraOpenCloseLock.countDown()
        } catch (e: Exception) {
            errorUtils.logError("PreviewHandler", "Failed to close camera", e)
        }
    }

    private fun chooseOptimalSize(choices: Array<Size>, width: Int, height: Int): Size {
        return choices.firstOrNull { it.width >= width && it.height >= height }
            ?: choices.maxByOrNull { it.width * it.height }
            ?: Size(width, height)
    }
} 