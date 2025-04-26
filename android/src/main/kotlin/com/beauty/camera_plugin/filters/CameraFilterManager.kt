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
    private var renderer: GPUImageRenderer? = null
    private val transformMatrix = FloatArray(16)
    private var isRendererSet = false

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
    }

    private fun setupGLSurfaceView() {
        if (isRendererSet) {
            Log.w(tag, "Renderer already set, skipping setup")
            return
        }

        Log.d(tag, "Setting up GLSurfaceView")

        glSurfaceView?.let { view ->
            try {
                view.setEGLContextClientVersion(2)
                renderer = GPUImageRenderer(currentFilter).apply {
                    setRotation(Rotation.NORMAL)
                    setScaleType(GPUImage.ScaleType.CENTER_CROP)
                }

                view.setRenderer(object : GLSurfaceView.Renderer {
                    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                        // Initialize GPUImage renderer
                        renderer?.onSurfaceCreated(gl, config)
                        
                        // Generate texture for camera preview
                        val textures = IntArray(1)
                        GLES20.glGenTextures(1, textures, 0)
                        textureId = textures[0]
                        
                        // Create new SurfaceTexture
                        surfaceTexture?.release()
                        surfaceTexture = SurfaceTexture(textureId)
                        surfaceTexture?.setOnFrameAvailableListener { 
                            view.requestRender() 
                        }
                    }

                    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                        GLES20.glViewport(0, 0, width, height)
                        renderer?.onSurfaceChanged(gl, width, height)
                    }

                    override fun onDrawFrame(gl: GL10?) {
                        // Update texture with new frame
                        surfaceTexture?.let { texture ->
                            texture.updateTexImage()
                            texture.getTransformMatrix(transformMatrix)
                        }
                        
                        // Draw the frame
                        renderer?.onDrawFrame(gl)
                    }
                })
                view.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                isRendererSet = true
                Log.d(tag, "GLSurfaceView setup completed successfully")
            } catch (e: Exception) {
                Log.e(tag, "Error setting up GLSurfaceView", e)
                isRendererSet = false
            }
        }
    }

    /**
     * Set GLSurfaceView for rendering
     */
    fun setGLSurfaceView(view: GLSurfaceView) {
        if (glSurfaceView == view) {
            Log.d(tag, "Same GLSurfaceView instance, skipping setup")
            return
        }

        // Clean up previous GLSurfaceView if exists
        releaseGLSurfaceView()
        
        glSurfaceView = view
        isRendererSet = false
        setupGLSurfaceView()
    }

    private fun releaseGLSurfaceView() {
        try {
            glSurfaceView?.queueEvent {
                try {
                    // Destroy current filter
                    currentFilter.destroy()
                    
                    // Clean up GPUImage
                    gpuImage.deleteImage()
                    
                    // Release surface texture
                    surfaceTexture?.release()
                    surfaceTexture = null
                    
                    // Delete texture if it exists
                    if (textureId != -1) {
                        val textures = intArrayOf(textureId)
                        GLES20.glDeleteTextures(1, textures, 0)
                        textureId = -1
                    }
                    
                    // Clean up renderer
                    renderer?.deleteImage()
                    renderer = null
                    
                    Log.d(tag, "GLSurfaceView resources released successfully")
                } catch (e: Exception) {
                    Log.e(tag, "Error during GLSurfaceView cleanup", e)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error in releaseGLSurfaceView()", e)
        }
    }

    fun release() {
        try {
            releaseGLSurfaceView()
            glSurfaceView = null
            instance = null
            isRendererSet = false
        } catch (e: Exception) {
            Log.e(tag, "Error in release()", e)
        }
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

            // Create new filter
            val newFilter = FilterFactory.createFilter(mode)
            
            // Apply filter level
            FilterFactory.applyFilterLevel(newFilter, level)
            
            // Clean up old filter
            currentFilter.destroy()
            currentFilter = newFilter

            // Apply filter on GL thread
            glSurfaceView?.queueEvent {
                try {
                    gpuImage.setFilter(currentFilter)
                    glSurfaceView?.requestRender()
                    Log.d(tag, "Filter set successfully: mode=$mode, level=$level")
                } catch (e: Exception) {
                    Log.e(tag, "Error applying filter on GL thread", e)
                    // Revert to previous filter if possible
                    try {
                        currentFilter = FilterFactory.createFilter(CameraFilterMode.NONE)
                        gpuImage.setFilter(currentFilter)
                        glSurfaceView?.requestRender()
                    } catch (e: Exception) {
                        Log.e(tag, "Error reverting to default filter", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting filter", e)
            // Notify error through callback if available
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
} 