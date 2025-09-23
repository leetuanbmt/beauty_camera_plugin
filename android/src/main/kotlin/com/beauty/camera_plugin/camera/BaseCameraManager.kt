package com.beauty.camera_plugin.camera

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import android.view.Surface
import com.beauty.camera_plugin.AdvancedCameraSettings
import com.beauty.camera_plugin.PreviewSize

/**
 * Base interface for camera management
 * Defines the contract for camera operations
 */
interface ICameraManager {
    /**
     * Initialize camera manager
     * @param context Application context
     * @param lifecycleOwner Activity lifecycle owner
     * @return Success status
     */
    fun initialize(context: Context, lifecycleOwner: LifecycleOwner): Boolean
    
    /**
     * Initialize camera with settings
     * @param settings Camera settings
     * @param surface Camera surface for preview
     * @param onSuccess Success callback
     * @param onError Error callback
     */
    fun initializeCamera(
        settings: AdvancedCameraSettings,
        surface: Surface,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )
    
    /**
     * Switch between front and back camera
     * @return New camera facing
     */
    fun switchCamera(): Int
    
    /**
     * Get preview size
     * @param callback Result callback
     */
    fun getPreviewSize(callback: (Result<PreviewSize>) -> Unit)
    
    /**
     * Set zoom level
     * @param zoomLevel Zoom level (1.0 - 10.0)
     */
    fun setZoom(zoomLevel: Float)
    
    /**
     * Focus on specific point
     * @param x X coordinate
     * @param y Y coordinate
     */
    fun focusOnPoint(x: Int, y: Int)
    
    /**
     * Set flash mode
     * @param mode Flash mode
     */
    fun setFlashMode(mode: com.beauty.camera_plugin.FlashMode)
    
    /**
     * Take photo
     * @param callback Result callback with file path
     */
    fun takePhoto(callback: (Result<String>) -> Unit)
    
    /**
     * Start video recording
     * @param callback Result callback
     */
    fun startVideoRecording(callback: (Result<Unit>) -> Unit)
    
    /**
     * Stop video recording
     * @param callback Result callback with file path
     */
    fun stopVideoRecording(callback: (Result<String>) -> Unit)
    
    /**
     * Check if camera is initialized
     */
    fun isInitialized(): Boolean
    
    /**
     * Stop camera
     */
    fun stopCamera()
    
    /**
     * Dispose camera resources
     */
    fun dispose()
}
