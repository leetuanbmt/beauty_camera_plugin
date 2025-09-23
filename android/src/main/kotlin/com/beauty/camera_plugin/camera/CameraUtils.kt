package com.beauty.camera_plugin.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import com.beauty.camera_plugin.CameraInfo
import com.beauty.camera_plugin.CameraFacing
import com.beauty.camera_plugin.ResolutionInfo

/**
 * Camera utilities for device-specific operations
 * Provides helper methods for camera information and capabilities
 */
object CameraUtils {
    private const val TAG = "CameraUtils"
    
    /**
     * Get all available cameras on the device
     * @param context Application context
     * @return List of camera information
     */
    fun getAvailableCameras(context: Context): List<CameraInfo> {
        val cameras = mutableListOf<CameraInfo>()
        
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraIds = cameraManager.cameraIdList
            
            for (cameraId in cameraIds) {
                try {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    val supportedResolutions = getSupportedResolutions(characteristics)
                    
                    val cameraFacing = when (facing) {
                        CameraCharacteristics.LENS_FACING_FRONT -> CameraFacing.FRONT
                        CameraCharacteristics.LENS_FACING_BACK -> CameraFacing.BACK
                        else -> CameraFacing.BACK
                    }
                    
                    val cameraInfo = CameraInfo(
                        id = cameraId,
                        facing = cameraFacing,
                        hasFlash = flashAvailable,
                        supportedResolutions = supportedResolutions
                    )
                    
                    cameras.add(cameraInfo)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting characteristics for camera $cameraId", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available cameras", e)
        }
        
        return cameras
    }
    
    /**
     * Get supported resolutions for a camera
     * @param characteristics Camera characteristics
     * @return List of supported resolutions
     */
    private fun getSupportedResolutions(characteristics: CameraCharacteristics): List<ResolutionInfo> {
        val resolutions = mutableListOf<ResolutionInfo>()
        
        try {
            val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            streamConfigMap?.let { configMap ->
                val outputSizes = configMap.getOutputSizes(android.graphics.ImageFormat.JPEG)
                
                outputSizes?.forEach { size ->
                    resolutions.add(
                        ResolutionInfo(
                            width = size.width.toLong(),
                            height = size.height.toLong()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting supported resolutions", e)
        }
        
        // Add common resolutions if none found
        if (resolutions.isEmpty()) {
            resolutions.addAll(getCommonResolutions())
        }
        
        return resolutions.sortedWith(compareByDescending { it.width * it.height })
    }
    
    /**
     * Get common camera resolutions
     * @return List of common resolutions
     */
    private fun getCommonResolutions(): List<ResolutionInfo> {
        return listOf(
            ResolutionInfo(width = 3840, height = 2160), // 4K
            ResolutionInfo(width = 1920, height = 1080), // 1080p
            ResolutionInfo(width = 1280, height = 720),  // 720p
            ResolutionInfo(width = 854, height = 480),   // 480p
            ResolutionInfo(width = 640, height = 480),   // VGA
            ResolutionInfo(width = 320, height = 240)    // QVGA
        )
    }
    
    /**
     * Check if device has multiple cameras
     * @param context Application context
     * @return True if device has multiple cameras
     */
    fun hasMultipleCameras(context: Context): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.cameraIdList.size > 1
        } catch (e: Exception) {
            Log.e(TAG, "Error checking multiple cameras", e)
            false
        }
    }
    
    /**
     * Check if device has front camera
     * @param context Application context
     * @return True if device has front camera
     */
    fun hasFrontCamera(context: Context): Boolean {
        return getAvailableCameras(context).any { it.facing == CameraFacing.FRONT }
    }
    
    /**
     * Check if device has back camera
     * @param context Application context
     * @return True if device has back camera
     */
    fun hasBackCamera(context: Context): Boolean {
        return getAvailableCameras(context).any { it.facing == CameraFacing.BACK }
    }
    
    /**
     * Get camera with specific facing
     * @param context Application context
     * @param facing Camera facing
     * @return Camera info or null if not found
     */
    fun getCameraByFacing(context: Context, facing: CameraFacing): CameraInfo? {
        return getAvailableCameras(context).find { it.facing == facing }
    }
    
    /**
     * Calculate optimal preview size based on available space
     * @param availableWidth Available width
     * @param availableHeight Available height
     * @param aspectRatio Desired aspect ratio
     * @return Optimal preview size
     */
    fun calculateOptimalPreviewSize(
        availableWidth: Int,
        availableHeight: Int,
        aspectRatio: Float = 16f / 9f
    ): Pair<Int, Int> {
        val availableAspectRatio = availableWidth.toFloat() / availableHeight.toFloat()
        
        return if (availableAspectRatio > aspectRatio) {
            // Available space is wider, fit by height
            val width = (availableHeight * aspectRatio).toInt()
            Pair(width, availableHeight)
        } else {
            // Available space is taller, fit by width
            val height = (availableWidth / aspectRatio).toInt()
            Pair(availableWidth, height)
        }
    }
    
    /**
     * Check if resolution is supported
     * @param context Application context
     * @param cameraId Camera ID
     * @param width Desired width
     * @param height Desired height
     * @return True if resolution is supported
     */
    fun isResolutionSupported(
        context: Context,
        cameraId: String,
        width: Int,
        height: Int
    ): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            
            streamConfigMap?.getOutputSizes(android.graphics.ImageFormat.JPEG)?.any { size ->
                size.width == width && size.height == height
            } ?: false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking resolution support", e)
            false
        }
    }
}
