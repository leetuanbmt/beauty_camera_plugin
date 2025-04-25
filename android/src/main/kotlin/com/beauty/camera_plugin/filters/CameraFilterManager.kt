package com.beauty.camera_plugin.filters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.*
import jp.co.cyberagent.android.gpuimage.util.Rotation
import com.beauty.camera_plugin.models.CameraFilterMode
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import jp.co.cyberagent.android.gpuimage.GPUImageRenderer


/**
 * Manages camera filters using GPUImage
 */
class CameraFilterManager private constructor(context: Context) {

    private val tag = "CameraFilterManager"
    private var gpuImage: GPUImage = GPUImage(context)
    private var currentFilter: GPUImageFilter = GPUImageFilter()
    private var currentFilterMode: CameraFilterMode = CameraFilterMode.NONE
    private var currentFilterLevel: Double = 0.0
    private var glSurfaceView: GLSurfaceView? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var textureId: Int = -1
    private lateinit var renderer: GPUImageRenderer
    private val transformMatrix = FloatArray(16)

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: CameraFilterManager? = null

        fun getInstance(context: Context): CameraFilterManager {
            return instance ?: synchronized(this) {
                instance ?: CameraFilterManager(context).also { instance = it }
            }
        }
    }

    init {
        gpuImage.setFilter(currentFilter)
        renderer = GPUImageRenderer(currentFilter)
        renderer.setRotation(Rotation.NORMAL)
        renderer.setScaleType(GPUImage.ScaleType.CENTER_CROP)
    }

    private fun setupGLSurfaceView() {
        gpuImage.setGLSurfaceView(glSurfaceView)
        glSurfaceView?.setEGLContextClientVersion(2)
        glSurfaceView?.setRenderer(object : GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                // Initialize GPUImage renderer
                renderer.onSurfaceCreated(gl, config)
                
                // Generate texture for camera preview
                val textures = IntArray(1)
                GLES20.glGenTextures(1, textures, 0)
                textureId = textures[0]
                
                // Create new SurfaceTexture
                surfaceTexture?.release()
                surfaceTexture = SurfaceTexture(textureId)
                surfaceTexture?.setOnFrameAvailableListener { 
                    glSurfaceView?.requestRender() 
                }
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                GLES20.glViewport(0, 0, width, height)
                renderer.onSurfaceChanged(gl, width, height)
            }

            override fun onDrawFrame(gl: GL10?) {
                // Update texture with new frame
                surfaceTexture?.let { texture ->
                    texture.updateTexImage()
                    texture.getTransformMatrix(transformMatrix)
                }
                
                // Draw the frame
                renderer.onDrawFrame(gl)
            }
        })
        glSurfaceView?.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    /**
     * Set GLSurfaceView for rendering
     */
    fun setGLSurfaceView(view: GLSurfaceView) {
        glSurfaceView = view
        setupGLSurfaceView()
    }

    fun getSurfaceTexture(): SurfaceTexture? {
        return surfaceTexture
    }

    /**
     * Set up surface texture for camera preview
     */
    fun setupSurfaceTexture(width: Int, height: Int) {
        try {
            gpuImage.setRotation(Rotation.NORMAL)
            gpuImage.setScaleType(GPUImage.ScaleType.CENTER_CROP)
            updatePreviewSize(width, height)
            
            // Re-apply current filter
            setFilter(currentFilterMode, currentFilterLevel)
            
            Log.d(tag, "Surface texture setup complete: ${width}x${height}")
        } catch (e: Exception) {
            Log.e(tag, "Error setting up surface texture", e)
        }
    }

    /**
     * Update preview size
     */
    fun updatePreviewSize(width: Int, height: Int) {
        try {
            gpuImage.setRotation(Rotation.NORMAL)
            gpuImage.setScaleType(GPUImage.ScaleType.CENTER_CROP)
            
            // Update surface texture buffer size
            surfaceTexture?.setDefaultBufferSize(width, height)
            
            // Update viewport
            GLES20.glViewport(0, 0, width, height)
            
            Log.d(tag, "Preview size updated: ${width}x${height}")
        } catch (e: Exception) {
            Log.e(tag, "Error updating preview size", e)
        }
    }

    /**
     * Set filter for camera preview
     */
    fun setFilter(mode: CameraFilterMode, level: Double) {
        try {
            currentFilterMode = mode
            currentFilterLevel = level

            currentFilter = FilterFactory.createFilter(mode)

            // Apply filter
            gpuImage.setFilter(currentFilter)
            
            // Request render
            glSurfaceView?.requestRender()
            
            Log.d(tag, "Filter set: mode=$mode, level=$level")
        } catch (e: Exception) {
            Log.e(tag, "Error setting filter", e)
        }
    }

    /**
     * Set rotation for preview
     */
    fun setRotation(rotation: Rotation) {
        gpuImage.setRotation(rotation)
        glSurfaceView?.requestRender()
    }

    /**
     * Set scale type for preview
     */
    fun setScaleType(scaleType: GPUImage.ScaleType) {
        gpuImage.setScaleType(scaleType)
        glSurfaceView?.requestRender()
    }

    fun onNewFrame() {
        try {
            // Request render to process new frame
            glSurfaceView?.requestRender()
        } catch (e: Exception) {
            Log.e(tag, "Error processing new frame", e)
        }
    }

    fun release() {
        try {
            currentFilter.destroy()
            gpuImage.deleteImage()
            
            // Delete texture if it exists
            if (textureId != -1) {
                val textures = intArrayOf(textureId)
                GLES20.glDeleteTextures(1, textures, 0)
                textureId = -1
            }
            
            surfaceTexture?.release()
            surfaceTexture = null
            
            Log.d(tag, "Filter manager resources released")
        } catch (e: Exception) {
            Log.e(tag, "Error releasing resources", e)
        }
    }
} 