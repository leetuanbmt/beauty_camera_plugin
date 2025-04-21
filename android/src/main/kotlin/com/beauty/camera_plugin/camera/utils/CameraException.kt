package com.beauty.camera_plugin.camera.utils

/**
 * Base exception class for camera errors
 */
open class CameraException(
    override val message: String,
    val code: String = "camera_error"
) : Exception(message)

/**
 * Exception thrown when camera permission is denied
 */
class CameraPermissionException(
    message: String = "Camera permission denied",
    code: String = "camera_permission_denied"
) : CameraException(message, code)

/**
 * Exception thrown when camera operation times out
 */
class CameraTimeoutException(
    message: String = "Camera operation timed out",
    code: String = "camera_timeout"
) : CameraException(message, code)

/**
 * Exception thrown when camera is invalid
 */
class InvalidCameraException(
    message: String = "Invalid camera",
    code: String = "invalid_camera"
) : CameraException(message, code)

/**
 * Exception thrown when camera is not available
 */
class CameraNotAvailableException(
    message: String = "Camera not available",
    code: String = "camera_not_available"
) : CameraException(message, code)

/**
 * Exception thrown when camera is in use
 */
class CameraInUseException(
    message: String = "Camera is in use",
    code: String = "camera_in_use"
) : CameraException(message, code)

/**
 * Exception thrown when camera configuration fails
 */
class CameraConfigurationException(
    message: String = "Camera configuration failed",
    code: String = "camera_configuration_failed"
) : CameraException(message, code)

/**
 * Exception thrown when camera capture fails
 */
class CameraCaptureException(
    message: String = "Camera capture failed",
    code: String = "camera_capture_failed"
) : CameraException(message, code)

/**
 * Exception thrown when camera recording fails
 */
class CameraRecordingException(
    message: String = "Camera recording failed",
    code: String = "camera_recording_failed"
) : CameraException(message, code)

/**
 * Exception thrown when camera preview fails
 */
class CameraPreviewException(
    message: String = "Camera preview failed",
    code: String = "camera_preview_failed"
) : CameraException(message, code)

/**
 * Exception thrown when camera filter fails
 */
class CameraFilterException(
    message: String = "Camera filter failed",
    code: String = "camera_filter_failed"
) : CameraException(message, code)

/**
 * Exception thrown when camera is disconnected
 */
class CameraDisconnectedException(
    message: String = "Camera disconnected",
    code: String = "camera_disconnected"
) : CameraException(message, code) 