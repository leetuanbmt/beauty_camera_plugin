package com.beauty.camera_plugin.view

import android.graphics.SurfaceTexture
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import io.flutter.view.TextureRegistry
import com.beauty.camera_plugin.repository.CameraRepository

/**
 * Handles the Flutter texture registry integration for camera preview
 */
class FlutterTextureHandler(
    private val textureRegistry: TextureRegistry,
    private val repository: CameraRepository,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "FlutterTextureHandler"
    }
    
    private var textureEntry: TextureRegistry.SurfaceTextureEntry? = null
    private var surfaceTexture: SurfaceTexture? = null
    
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


        // Start the camera with the surface
        textureEntry?.id()?.let { textureId: Long ->
            try {
                // Initialize the camera with the surface
                surfaceTexture?.let { texture: SurfaceTexture ->
                    repository.startCamera(lifecycleOwner, android.view.Surface(texture))
                   return textureId
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing camera texture", e)
            }
        }
        
        return -1
    }
    
    /**
     * Update the size of the texture
     */
    fun updateTexture(width: Int, height: Int) {
        try {
            Log.e(TAG,"Debug size camera $width $height")
            surfaceTexture?.setDefaultBufferSize(width, height)
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
            surfaceTexture = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
} 