package com.beauty.camera_plugin.filters

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.*
import jp.co.cyberagent.android.gpuimage.util.Rotation
import com.beauty.camera_plugin.models.CameraFilterMode
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraFilterManager(context: Context) {
    companion object {
        private const val TAG = "CameraFilterManager"
    }

    private var gpuImage: GPUImage? = null
    private var currentFilter: GPUImageFilter? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var currentFilterMode = CameraFilterMode.NONE
    private var filterLevel = 0.0
    private var outputSurface: Surface? = null
    private var previewWidth: Int = 0
    private var previewHeight: Int = 0
    private var glSurfaceView: GLSurfaceView? = null
    private var textureId: Int = -1

    init {
        // Initialize GPUImage
        gpuImage = GPUImage(context)
        currentFilter = FilterFactory.createFilter(CameraFilterMode.NONE)
        gpuImage?.setFilter(currentFilter)
        
        // Create and configure GLSurfaceView
        glSurfaceView = GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(object : GLSurfaceView.Renderer {
                override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                    // Generate texture for SurfaceTexture
                    val textures = IntArray(1)
                    gl?.glGenTextures(1, textures, 0)
                    textureId = textures[0]
                    
                    // Create new SurfaceTexture
                    surfaceTexture?.release()
                    surfaceTexture = SurfaceTexture(textureId).apply {
                        setDefaultBufferSize(previewWidth, previewHeight)
                    }
                    
                    // Create new Surface
                    outputSurface?.release()
                    outputSurface = Surface(surfaceTexture)
                }

                override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                    gpuImage?.setRotation(Rotation.ROTATION_90)
                    gpuImage?.setScaleType(GPUImage.ScaleType.CENTER_CROP)
                }

                override fun onDrawFrame(gl: GL10?) {
                    surfaceTexture?.updateTexImage()
                }
            })
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }

    fun setupSurfaceTexture(width: Int, height: Int): SurfaceTexture? {
        try {
            previewWidth = width
            previewHeight = height

            // Update GLSurfaceView
            glSurfaceView?.let { view ->
                gpuImage?.setGLSurfaceView(view)
                view.requestRender()
            }

            return surfaceTexture
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up surface texture", e)
            return null
        }
    }

    fun updatePreviewSize(width: Int, height: Int) {
        try {
            if (width != previewWidth || height != previewHeight) {
                previewWidth = width
                previewHeight = height
                surfaceTexture?.setDefaultBufferSize(width, height)
                glSurfaceView?.requestRender()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating preview size", e)
        }
    }

    fun setFilter(mode: CameraFilterMode, level: Double) {
        try {
            currentFilterMode = mode
            filterLevel = level.coerceIn(0.0, 10.0)
            
            currentFilter?.let { oldFilter ->
                gpuImage?.deleteImage()
                oldFilter.destroy()
            }
            
            currentFilter = FilterFactory.createFilter(mode)
            gpuImage?.setFilter(currentFilter)
            
            // Apply filter level
            applyFilterLevel()

            // Request render
            glSurfaceView?.requestRender()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting filter", e)
        }
    }

    private fun applyFilterLevel() {
        try {
            if(currentFilter == null) return
            FilterFactory.applyFilterLevel(currentFilter, filterLevel)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying filter level", e)
        }
    }

    fun getOutputSurface(): Surface? = outputSurface

    fun release() {
        try {
            currentFilter?.destroy()
            currentFilter = null
            
            gpuImage?.deleteImage()
            gpuImage = null
            
            outputSurface?.release()
            outputSurface = null
            
            surfaceTexture?.release()
            surfaceTexture = null

            glSurfaceView?.let { view ->
                view.queueEvent {
                    // Delete texture
                    if (textureId != -1) {
                        val textures = intArrayOf(textureId)
                        android.opengl.GLES20.glDeleteTextures(1, textures, 0)
                        textureId = -1
                    }
                }
            }
            glSurfaceView = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }
} 