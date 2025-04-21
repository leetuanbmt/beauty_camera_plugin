package com.beauty.camera_plugin.camera.utils

import android.util.Log

/**
 * Utility class for handling camera errors.
 * Provides methods for logging errors and converting exceptions to CameraException.
 */
class ErrorUtils {
    companion object {
        private const val TAG = "ErrorUtils"
    }

    /**
     * Logs an error with the specified tag, message, and optional throwable.
     * 
     * @param tag The tag to use for logging
     * @param message The error message
     * @param throwable Optional throwable to log
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    /**
     * Handles an error by logging it and invoking the callback with a failure result.
     * 
     * @param error The error to handle
     * @param callback The callback to invoke with the result
     */
    fun handleError(error: Throwable, callback: (Result<Unit>) -> Unit) {
        logError(TAG, "Camera error", error)
        val cameraError = createCameraError(error)
        callback(Result.failure(cameraError))
    }

    /**
     * Creates a CameraException from a Throwable.
     * 
     * @param error The throwable to convert
     * @return A CameraException representing the throwable
     */
    fun createCameraError(error: Throwable): CameraException {
        return when (error) {
            is CameraException -> error
            is SecurityException -> CameraPermissionException(error.message ?: "Permission denied")
            is IllegalStateException -> CameraConfigurationException(error.message ?: "Invalid camera state")
            is java.util.concurrent.TimeoutException -> CameraTimeoutException(error.message ?: "Operation timed out")
            else -> CameraException(
                error.message ?: "An unknown error occurred",
                "unknown_error"
            )
        }
    }
} 