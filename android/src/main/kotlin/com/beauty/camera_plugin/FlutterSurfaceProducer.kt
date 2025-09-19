package com.beauty.camera_plugin

import android.view.Surface
import io.flutter.view.TextureRegistry

/**
 * Interface for a component that produces a Surface for native rendering
 * and provides a texture ID for Flutter to display.
 */
interface SurfaceProducer {
    /**
     * Returns the Surface that native components (e.g., CameraX, OpenGLRenderer)
     * should render to.
     */
    fun getSurface(): Surface

    /**
     * Returns the texture ID that Flutter's Texture widget should use
     * to display the content rendered on this Surface.
     */
    fun getTextureId(): Long

    /**
     * Releases all resources associated with this SurfaceProducer.
     */
    fun release()
}

/**
 * A concrete implementation of SurfaceProducer that uses Flutter's
 * TextureRegistry.SurfaceTextureEntry.
 */
class FlutterSurfaceProducer(textureRegistry: TextureRegistry) : SurfaceProducer {

    private var surfaceTextureEntry: TextureRegistry.SurfaceTextureEntry? = null
    private var surface: Surface? = null

    init {
        try {
            surfaceTextureEntry = textureRegistry.createSurfaceTexture()
            surface = Surface(surfaceTextureEntry!!.surfaceTexture())
            
            // Debug: Log surface and texture information
            val surfaceTexture = surfaceTextureEntry!!.surfaceTexture()

            // Set default buffer size for better compatibility
            surfaceTexture.setDefaultBufferSize(1920, 1080)
            
        } catch (e: Exception) {
            android.util.Log.e("FlutterSurfaceProducer", "Failed to create SurfaceTextureEntry", e)
            throw e
        }
    }

    /**
     * Sets the default buffer size for the underlying SurfaceTexture.
     * This is crucial for matching the consumer's size with the producer's size.
     */
    fun setSize(width: Int, height: Int) {
        try {
            surfaceTextureEntry?.surfaceTexture()?.setDefaultBufferSize(width, height)
        } catch (e: Exception) {
            android.util.Log.e("FlutterSurfaceProducer", "Failed to set buffer size: ${width}x${height}", e)
            throw e
        }
    }

    override fun getSurface(): Surface {
        if (surface == null) {
            android.util.Log.e("FlutterSurfaceProducer", "Surface is null, surfaceTextureEntry: $surfaceTextureEntry")
            throw IllegalStateException("Surface not initialized")
        }
        return surface!!
    }

    override fun getTextureId(): Long {
        return surfaceTextureEntry?.id() ?: throw IllegalStateException("SurfaceTextureEntry not initialized")
    }

    override fun release() {
        surface?.release()
        surfaceTextureEntry?.release()
        surface = null
        surfaceTextureEntry = null
    }
}