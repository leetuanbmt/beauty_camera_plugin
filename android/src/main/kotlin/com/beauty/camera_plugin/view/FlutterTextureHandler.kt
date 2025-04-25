package com.beauty.camera_plugin.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import io.flutter.view.TextureRegistry
import com.beauty.camera_plugin.repository.CameraRepository

/**
 * Handles the Flutter texture registry integration for camera preview
 */
class FlutterTextureHandler(
    private val context: Context,
    private val textureRegistry: TextureRegistry,
    private val repository: CameraRepository,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "FlutterTextureHandler"
        
        // Scale type constants matching Flutter enum
        const val SCALE_TYPE_CENTER_CROP = "centerCrop"
        const val SCALE_TYPE_CENTER_INSIDE = "centerInside"
    }
    
    private var textureEntry: TextureRegistry.SurfaceTextureEntry? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var cameraView: CameraView? = null
    
    /**
     * Initialize and register a texture for the Flutter preview
     */
    fun initialize(): Long {
        // Clean up old texture if any
        cleanup()
        
        // Create a new texture entry
        textureEntry = textureRegistry.createSurfaceTexture()
        
        // Get the surface texture to render camera output to
        surfaceTexture = textureEntry?.surfaceTexture()

        // Create CameraView instance with default CENTER_CROP scale type
        cameraView = CameraView(context, repository, lifecycleOwner)
        
        // Start the camera with the surface
        textureEntry?.id()?.let { textureId: Long ->
            try {
                // Initialize the camera with the surface
                surfaceTexture?.let { texture: SurfaceTexture ->
                    // Set the surface texture to CameraView
                    cameraView?.customSurfaceTexture = texture
                    return textureId
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing camera texture", e)
            }
        }
        
        return -1
    }
    
    /**
     * Set the scale type for the camera preview
     * @param scaleType String representing the scale type ("centerCrop" or "centerInside")
     */
    fun setScaleType(scaleType: String) {
        try {
            val newScaleType = when (scaleType) {
                SCALE_TYPE_CENTER_CROP -> CameraView.ScaleType.CENTER_CROP
                SCALE_TYPE_CENTER_INSIDE -> CameraView.ScaleType.CENTER_INSIDE
                else -> {
                    Log.w(TAG, "Invalid scale type: $scaleType, defaulting to CENTER_CROP")
                    CameraView.ScaleType.CENTER_CROP
                }
            }
            
            Log.d(TAG, "Setting scale type to: $scaleType")
            cameraView?.setScaleType(newScaleType)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting scale type", e)
        }
    }
    
    /**
     * Update the size of the texture
     */
    fun updateTexture(width: Int, height: Int) {
        try {
            Log.d(TAG, "Updating texture size: $width x $height")
            surfaceTexture?.setDefaultBufferSize(width, height)
            surfaceTexture?.let { texture ->
                cameraView?.onSurfaceTextureSizeChanged(texture, width, height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating texture size", e)
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            textureEntry?.release()
            textureEntry = null
            surfaceTexture?.let { texture ->
                cameraView?.onSurfaceTextureDestroyed(texture)
            }
            surfaceTexture = null
            cameraView = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
} 