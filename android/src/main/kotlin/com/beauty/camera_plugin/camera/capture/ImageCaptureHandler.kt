package com.beauty.camera_plugin.camera.capture

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraDevice
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.view.Surface
import com.beauty.camera_plugin.camera.utils.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Handles image capture functionality
 */
class ImageCaptureHandler(
    private val context: Context,
    private val cameraDevice: CameraDevice,
    private val backgroundHandler: Handler,
    private val errorUtils: ErrorUtils
) {
    private var imageReader: ImageReader? = null
    private var captureSession: android.hardware.camera2.CameraCaptureSession? = null
    private var captureRequestBuilder: android.hardware.camera2.CaptureRequest.Builder? = null
    private var captureRequest: android.hardware.camera2.CaptureRequest? = null
    private val captureLock = CountDownLatch(1)

    /**
     * Capture image and save to file
     */
    fun capture(path: String, callback: (Result<Unit>) -> Unit) {
        try {
            // Create image reader
            imageReader = ImageReader.newInstance(
                CameraConstants.DEFAULT_PREVIEW_WIDTH,
                CameraConstants.DEFAULT_PREVIEW_HEIGHT,
                ImageFormat.JPEG,
                1
            ).apply {
                setOnImageAvailableListener({ reader ->
                    // Handle captured image
                    val image = reader.acquireLatestImage()
                    try {
                        // Save image to file
                        saveImageToFile(image, path)
                        callback(Result.success(Unit))
                    } catch (e: Exception) {
                        callback(Result.failure(CameraCaptureException("Failed to save image: ${e.message}")))
                    } finally {
                        image?.close()
                    }
                }, backgroundHandler)
            }

            // Create capture session
            val surfaces = listOf(imageReader?.surface!!)
            cameraDevice.createCaptureSession(
                surfaces,
                object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                        captureSession = session
                        takePicture(callback)
                    }

                    override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                        callback(Result.failure(CameraConfigurationException("Failed to configure capture session")))
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            callback(Result.failure(CameraCaptureException("Failed to capture image: ${e.message}")))
        }
    }

    /**
     * Release resources
     */
    fun release() {
        try {
            captureSession?.close()
            captureSession = null
            captureRequestBuilder = null
            captureRequest = null
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            errorUtils.logError("ImageCaptureHandler", "Failed to release resources", e)
        }
    }

    private fun takePicture(callback: (Result<Unit>) -> Unit) {
        try {
            if (!captureLock.await(CameraConstants.CAMERA_CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw CameraTimeoutException()
            }

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequestBuilder?.addTarget(imageReader?.surface!!)

            // Set auto focus mode
            captureRequestBuilder?.set(
                android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )

            // Set auto exposure mode
            captureRequestBuilder?.set(
                android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE,
                android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )

            // Build the request
            captureRequest = captureRequestBuilder?.build()

            // Take picture
            captureSession?.capture(
                captureRequest!!,
                object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: android.hardware.camera2.CameraCaptureSession,
                        request: android.hardware.camera2.CaptureRequest,
                        result: android.hardware.camera2.TotalCaptureResult
                    ) {
                        captureLock.countDown()
                    }

                    override fun onCaptureFailed(
                        session: android.hardware.camera2.CameraCaptureSession,
                        request: android.hardware.camera2.CaptureRequest,
                        failure: android.hardware.camera2.CaptureFailure
                    ) {
                        captureLock.countDown()
                        callback(Result.failure(CameraCaptureException("Capture failed: ${failure.reason}")))
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            captureLock.countDown()
            callback(Result.failure(CameraCaptureException("Failed to take picture: ${e.message}")))
        }
    }

    private fun saveImageToFile(image: Image, path: String) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        FileOutputStream(File(path)).use { output ->
            output.write(bytes)
        }
    }
} 