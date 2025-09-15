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
class FlutterSurfaceProducer(private val textureRegistry: TextureRegistry) : SurfaceProducer {

    private var surfaceTextureEntry: TextureRegistry.SurfaceTextureEntry? = null
    private var surface: Surface? = null

    init {
        surfaceTextureEntry = textureRegistry.createSurfaceTexture()
        surface = Surface(surfaceTextureEntry!!.surfaceTexture())
    }

    /**
     * Sets the default buffer size for the underlying SurfaceTexture.
     * This is crucial for matching the consumer's size with the producer's size.
     */
    fun setSize(width: Int, height: Int) {
        surfaceTextureEntry?.surfaceTexture()?.setDefaultBufferSize(width, height)
    }

    override fun getSurface(): Surface {
        return surface ?: throw IllegalStateException("Surface not initialized")
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