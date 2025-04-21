package com.beauty.camera_plugin.camera

import android.app.Activity
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.beauty.camera_plugin.camera.preview.PreviewHandler
import com.beauty.camera_plugin.camera.capture.ImageCaptureHandler
import com.beauty.camera_plugin.camera.recording.VideoRecordHandler
import com.beauty.camera_plugin.camera.utils.*
import io.flutter.view.TextureRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Main controller for camera operations.
 * Manages the lifecycle of camera components and coordinates between different handlers.
 */
class CameraController(
    private val activity: Activity,
    private val textureRegistry: TextureRegistry
) {
    companion object {
        private const val TAG = "CameraController"
    }

    private var cameraDevice: CameraDevice? = null
    private var cameraManager: CameraManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundHandler = Handler(Looper.getMainLooper())
    private val cameraOpenCloseLock = CountDownLatch(1)
    private val threadUtils = ThreadUtils
    private val errorUtils = ErrorUtils()
    
    private var previewHandler: PreviewHandler? = null
    private var imageCaptureHandler: ImageCaptureHandler? = null
    private var videoRecordHandler: VideoRecordHandler? = null

    init {
        try {
            cameraManager = activity.getSystemService(Activity.CAMERA_SERVICE) as CameraManager
            if (cameraManager == null) {
                throw CameraNotAvailableException("Camera service is not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing camera manager", e)
            throw CameraNotAvailableException("Failed to initialize camera manager: ${e.message}")
        }
        threadUtils.release()
    }

    /**
     * Opens the camera with the specified ID.
     * 
     * @param cameraId The ID of the camera to open
     * @param callback Callback to be invoked with the result
     */
    fun openCamera(cameraId: String, callback: (Result<CameraDevice>) -> Unit) {
        try {
            if (cameraDevice != null) {
                callback(Result.success(cameraDevice!!))
                return
            }

            val manager = cameraManager ?: run {
                callback(Result.failure(CameraNotAvailableException("Camera manager is not initialized")))
                return
            }

            backgroundHandler.post {
                try {
                    manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            cameraDevice = camera
                            cameraOpenCloseLock.countDown()
                            mainHandler.post {
                                callback(Result.success(camera))
                            }
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            camera.close()
                            cameraDevice = null
                            cameraOpenCloseLock.countDown()
                            mainHandler.post {
                                callback(Result.failure(CameraDisconnectedException()))
                            }
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            camera.close()
                            cameraDevice = null
                            cameraOpenCloseLock.countDown()
                            mainHandler.post {
                                callback(Result.failure(CameraException("Failed to open camera: $error")))
                            }
                        }
                    }, backgroundHandler)
                } catch (e: Exception) {
                    cameraOpenCloseLock.countDown()
                    mainHandler.post {
                        callback(Result.failure(CameraException("Failed to open camera: ${e.message}")))
                    }
                }
            }

            if (!cameraOpenCloseLock.await(CameraConstants.CAMERA_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw CameraTimeoutException("Time out waiting to lock camera opening.")
            }
        } catch (e: Exception) {
            callback(Result.failure(CameraException("Failed to open camera: ${e.message}")))
        }
    }

    /**
     * Closes the camera and releases resources.
     */
    fun closeCamera() {
        try {
            if (!cameraOpenCloseLock.await(CameraConstants.CAMERA_CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw CameraTimeoutException()
            }
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        } finally {
            cameraOpenCloseLock.countDown()
        }
    }

    /**
     * Starts the camera preview.
     * 
     * @param cameraId The ID of the camera to use
     * @param width The desired width of the preview
     * @param height The desired height of the preview
     * @param callback Callback to be invoked with the result
     */
    fun startPreview(
        cameraId: String,
        width: Int,
        height: Int,
        callback: (Result<Unit>) -> Unit
    ) {
        try {
            previewHandler = PreviewHandler(
                activity,
                textureRegistry,
                cameraManager!!,
                backgroundHandler,
                errorUtils
            )
            previewHandler?.startPreview(cameraId, width, height) { result ->
                result.fold(
                    onSuccess = { textureId ->
                        callback(Result.success(Unit))
                    },
                    onFailure = { error ->
                        callback(Result.failure(error))
                    }
                )
            }
        } catch (e: Exception) {
            errorUtils.handleError(e, callback)
        }
    }

    /**
     * Takes a picture and saves it to the specified path.
     * 
     * @param filePath The path where the image will be saved
     * @param callback Callback to be invoked with the result
     */
    fun takePicture(
        filePath: String,
        callback: (Result<Unit>) -> Unit
    ) {
        try {
            val device = previewHandler?.getCameraDevice() ?: run {
                callback(Result.failure(CameraNotAvailableException()))
                return
            }
            
            imageCaptureHandler = ImageCaptureHandler(
                activity,
                device,
                backgroundHandler,
                errorUtils
            )
            imageCaptureHandler?.capture(filePath, callback)
        } catch (e: Exception) {
            errorUtils.handleError(e, callback)
        }
    }

    /**
     * Starts recording video to the specified path.
     * 
     * @param filePath The path where the video will be saved
     * @param width The width of the video
     * @param height The height of the video
     * @param bitRate The bit rate of the video
     * @param callback Callback to be invoked with the result
     */
    fun startRecording(
        filePath: String,
        width: Int = CameraConstants.DEFAULT_PREVIEW_WIDTH,
        height: Int = CameraConstants.DEFAULT_PREVIEW_HEIGHT,
        bitRate: Int = CameraConstants.DEFAULT_VIDEO_BITRATE,
        callback: (Result<Unit>) -> Unit
    ) {
        try {
            val device = previewHandler?.getCameraDevice() ?: run {
                callback(Result.failure(CameraNotAvailableException()))
                return
            }
            
            videoRecordHandler = VideoRecordHandler(
                activity,
                device,
                backgroundHandler,
                errorUtils
            )
            videoRecordHandler?.startRecording(filePath, width, height, bitRate, callback)
        } catch (e: Exception) {
            errorUtils.handleError(e, callback)
        }
    }

    /**
     * Stops the current video recording.
     * 
     * @param callback Callback to be invoked with the result
     */
    fun stopRecording(callback: (Result<Unit>) -> Unit) {
        videoRecordHandler?.stopRecording(callback)
    }

    /**
     * Stops the camera preview.
     * 
     * @param callback Callback to be invoked with the result
     */
    fun stopPreview(callback: (Result<Unit>) -> Unit) {
        try {
            previewHandler?.stopPreview()
            previewHandler = null
            callback(Result.success(Unit))
        } catch (e: Exception) {
            errorUtils.handleError(e, callback)
        }
    }

    /**
     * Releases all resources used by the camera controller.
     */
    fun release() {
        closeCamera()
        mainHandler.removeCallbacksAndMessages(null)
        backgroundHandler.removeCallbacksAndMessages(null)
        try {
            previewHandler?.release()
            imageCaptureHandler?.release()
            videoRecordHandler?.release()
            threadUtils.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera controller", e)
        }
    }
}