package com.beauty.camera_plugin.camera.utils

/**
 * Constants used throughout the camera plugin.
 */
object CameraConstants {
    // Permission request codes
    const val PERMISSION_REQUEST_CODE_ALL = 100
    const val PERMISSION_REQUEST_CODE_AUDIO = 101

    // Camera error codes
    const val ERROR_CAMERA_DISCONNECTED = "camera_disconnected"
    const val ERROR_CAMERA_TIMEOUT = "camera_timeout"
    const val ERROR_CAMERA_IN_USE = "camera_in_use"
    const val ERROR_MAX_CAMERAS_IN_USE = "max_cameras_in_use"
    const val ERROR_CAMERA_DISABLED = "camera_disabled"
    const val ERROR_CAMERA_DEVICE = "camera_device"
    const val ERROR_CAMERA_SERVICE = "camera_service"
    const val ERROR_CAMERA_UNKNOWN = "camera_unknown"

    // Timeouts and delays
    const val CAMERA_TIMEOUT_MS = 2500L
    const val CAMERA_OPEN_TIMEOUT_MS = 2500L
    const val CAMERA_CLOSE_TIMEOUT_MS = 1000L
    const val CAMERA_PREVIEW_TIMEOUT_MS = 1000L
    const val CAMERA_CAPTURE_TIMEOUT_MS = 5000L
    const val CAMERA_RECORDING_TIMEOUT_MS = 5000L

    // Default values
    const val DEFAULT_PREVIEW_WIDTH = 1280
    const val DEFAULT_PREVIEW_HEIGHT = 720
    const val DEFAULT_JPEG_QUALITY = 90
    const val DEFAULT_VIDEO_BITRATE = 10_000_000 // 10 Mbps
    const val DEFAULT_VIDEO_FRAMERATE = 30
    const val DEFAULT_AUDIO_BITRATE = 128_000 // 128 Kbps
    const val DEFAULT_AUDIO_SAMPLERATE = 44100
    const val DEFAULT_AUDIO_CHANNELS = 2

    // Filter constants
    const val DEFAULT_BLUR_SIZE = 1.0f
    const val MAX_BLUR_SIZE = 25.0f
    const val DEFAULT_BRIGHTNESS = 0.0f
    const val DEFAULT_CONTRAST = 1.0f
    const val DEFAULT_SATURATION = 1.0f
    const val DEFAULT_SHARPNESS = 0.5f


    const val THREAD_TIMEOUT_MS = 5000L
    
} 