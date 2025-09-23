package com.beauty.camera_plugin.flutter_bridge

import android.util.Log
import io.flutter.view.TextureRegistry
import android.graphics.SurfaceTexture
import android.view.Surface

/**
 * Flutter Texture Bridge
 * Manages Flutter texture lifecycle and surface creation
 * 
 * SINGLE RESPONSIBILITY: Flutter texture management
 */
class FlutterTextureBridge(private val textureRegistry: TextureRegistry) {
    private val TAG = "FlutterTextureBridge"
    
    private var textureEntry: TextureRegistry.SurfaceTextureEntry? = null
    private var surface: Surface? = null
    private var surfaceTexture: SurfaceTexture? = null
    
    /**
     * Create Flutter texture
     * @return Texture ID
     */
    fun createTexture(): Long {
        try {
            textureEntry = textureRegistry.createSurfaceTexture()
            surfaceTexture = textureEntry?.surfaceTexture()
            surface = Surface(surfaceTexture)
            
            val textureId = textureEntry?.id() ?: -1L
            Log.d(TAG, "Created Flutter texture with ID: $textureId")
            return textureId
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Flutter texture", e)
            return -1L
        }
    }
    
    /**
     * Get Flutter surface
     * @return Surface or null if not created
     */
    fun getSurface(): Surface? {
        return surface
    }
    
    /**
     * Get surface texture
     * @return SurfaceTexture or null if not created
     */
    fun getSurfaceTexture(): SurfaceTexture? {
        return surfaceTexture
    }
    
    /**
     * Get texture ID
     * @return Texture ID or -1 if not created
     */
    fun getTextureId(): Long {
        return textureEntry?.id() ?: -1L
    }
    
    /**
     * Set surface size
     * @param width Surface width
     * @param height Surface height
     */
    fun setSize(width: Int, height: Int) {
        try {
            surfaceTexture?.setDefaultBufferSize(width, height)
            Log.d(TAG, "Set surface size: ${width}x${height}")
            
            // Note: Don't call updateTexImage() here as it can cause issues
            // The camera will provide the frames with correct size
        } catch (e: Exception) {
            Log.e(TAG, "Error setting surface size", e)
        }
    }
    
    /**
     * Release texture and surface
     */
    fun release() {
        try {
            surface?.release()
            surfaceTexture?.release()
            textureEntry?.release()
            
            surface = null
            surfaceTexture = null
            textureEntry = null
            
            Log.d(TAG, "Flutter texture bridge released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Flutter texture bridge", e)
        }
    }
    
    /**
     * Check if texture is created
     * @return True if texture is created
     */
    fun isCreated(): Boolean {
        return textureEntry != null && surface != null
    }
}
