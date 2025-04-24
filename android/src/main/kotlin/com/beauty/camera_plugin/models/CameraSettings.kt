package com.beauty.camera_plugin.models

import android.util.Size
import com.beauty.camera_plugin.AdvancedCameraSettings
import com.beauty.camera_plugin.FlashMode
import com.beauty.camera_plugin.VideoQuality

data class CameraSettings(
    // Camera settings from AdvancedCameraSettings
    val videoQuality: VideoQuality = VideoQuality.HIGH,
    val maxFrameRate: Int = 30,
    val videoStabilization: Boolean = false,
    val autoExposure: Boolean = true,
    val enableFaceDetection: Boolean = false,
    
    // Camera controls
    val zoom: Double = 1.0,
    val displayOrientation: Int = 0,
    val flashMode: FlashMode = FlashMode.OFF,
    
    // Camera facing
    val cameraLensFacing: Int = CAMERA_FACING_BACK,
    
    // Filter settings
    val filterMode: CameraFilterMode = CameraFilterMode.NONE,
    val filterLevel: Double = 5.0,
    
    // Internal settings
    val resolution: Size = Size(1920, 1080),
    val enableAudio: Boolean = true
) {
    companion object {
        const val CAMERA_FACING_BACK = 0
        const val CAMERA_FACING_FRONT = 1

        fun fromAdvancedSettings(settings: AdvancedCameraSettings): CameraSettings {
            return CameraSettings(
                videoQuality = settings.videoQuality ?: VideoQuality.HIGH,
                maxFrameRate = settings.maxFrameRate?.toInt() ?: 30,
                videoStabilization = settings.videoStabilization ?: false,
                autoExposure = settings.autoExposure ?: true,
                enableFaceDetection = settings.enableFaceDetection ?: false,
                resolution = videoQualityToResolution(settings.videoQuality ?: VideoQuality.HIGH)
            )
        }

        fun default(): CameraSettings = CameraSettings()

        private fun videoQualityToResolution(quality: VideoQuality): Size {
            return when (quality) {
                VideoQuality.LOW -> Size(640, 480)      // 480p
                VideoQuality.MEDIUM -> Size(1280, 720)  // 720p
                VideoQuality.HIGH -> Size(1920, 1080)   // 1080p
                VideoQuality.VERY_HIGH -> Size(2560, 1440) // 2K
                VideoQuality.ULTRA -> Size(3840, 2160)  // 4K
            }
        }


    }

    // Validation methods
    private fun validateZoom(): Double = zoom.coerceIn(1.0, 10.0)

    private fun validateDisplayOrientation(): Int = displayOrientation.coerceIn(0, 359)

    // Custom copy methods for specific updates
    fun copyWithZoom() = copy(zoom = validateZoom())

    fun copyWithCameraFacing(facing: Int) = copy(
        cameraLensFacing = when (facing) {
            CAMERA_FACING_FRONT, CAMERA_FACING_BACK -> facing
            else -> CAMERA_FACING_BACK
        }
    )
    
    fun copyWithFlashMode(mode: FlashMode) = copy(flashMode = mode)
    
    fun copyWithDisplayOrientation() = copy(
        displayOrientation = validateDisplayOrientation()
    )

}