package com.beauty.camera_plugin.renderer

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface
import com.beauty.camera_plugin.FilterParameters

/**
 * Manages surface for filter pipeline
 * Creates intermediate surface for camera input and filter output
 */
class FilterSurfaceManager {
    private val TAG = "FilterSurfaceManager"
    
    // EGL components
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    private var eglConfig: EGLConfig? = null
    
    // Surface components
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    
    // Camera input texture
    private var cameraTextureId = 0
    private var cameraTextureWidth = 0
    private var cameraTextureHeight = 0
    
    // Filter pipeline
    private var renderPipeline: RenderPipelineManager? = null
    
    // Threading
    private var isInitialized = false
    
    /**
     * Initialize filter surface manager
     * @param width Surface width
     * @param height Surface height
     * @param renderPipeline Render pipeline to use
     */
    fun initialize(width: Int, height: Int, renderPipeline: RenderPipelineManager): Boolean {
        try {
            Log.d(TAG, "Starting filter surface manager initialization: ${width}x${height}")
            this.renderPipeline = renderPipeline
            
            // Initialize EGL
            Log.d(TAG, "Initializing EGL...")
            if (!initializeEGL()) {
                Log.e(TAG, "Failed to initialize EGL")
                return false
            }
            Log.d(TAG, "EGL initialized successfully")
            
            // Don't create SurfaceTexture here - we'll use Flutter texture directly
            Log.d(TAG, "Skipping SurfaceTexture creation - will use Flutter texture directly")
            
            // Create EGL surface (off-screen rendering)
            Log.d(TAG, "Creating EGL surface...")
            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreatePbufferSurface(
                eglDisplay,
                eglConfig,
                surfaceAttribs,
                0
            )
            
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                Log.e(TAG, "Failed to create EGL surface")
                return false
            }
            Log.d(TAG, "EGL surface created successfully")
            
            // Make EGL context current now that surface is created
            Log.d(TAG, "Making EGL context current...")
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                Log.e(TAG, "Failed to make EGL context current")
                return false
            }
            Log.d(TAG, "EGL context made current successfully")
            
            // Generate camera texture
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            cameraTextureId = textures[0]
            
            // Configure camera texture
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cameraTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            
            cameraTextureWidth = width
            cameraTextureHeight = height
            
            isInitialized = true
            Log.d(TAG, "Filter surface manager initialized successfully: ${width}x${height}")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing filter surface manager", e)
            return false
        }
    }
    
    /**
     * Get surface for camera input
     */
    fun getCameraSurface(): Surface? = null // We don't provide our own surface anymore
    
    /**
     * Update camera texture when new frame is available
     */
    fun updateCameraTexture() {
        if (!isInitialized) return
        
        try {
            // No need to update SurfaceTexture since we're using Flutter texture directly
            // Just set camera input to render pipeline
            renderPipeline?.setCameraInput(cameraTextureId, cameraTextureWidth, cameraTextureHeight)
            
            Log.d(TAG, "Updated camera texture: ${cameraTextureId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating camera texture", e)
        }
    }
    
    /**
     * Render filtered frame
     */
    fun renderFilteredFrame(parameters: FilterParameters) {
        if (!isInitialized) return
        
        try {
            // Render frame through pipeline
            renderPipeline?.renderFrame(parameters)
            
            // Swap buffers
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            
            Log.d(TAG, "Rendered filtered frame")
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering filtered frame", e)
        }
    }
    
    /**
     * Initialize EGL context
     */
    private fun initializeEGL(): Boolean {
        try {
            // Get display
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                Log.e(TAG, "Failed to get EGL display")
                return false
            }
            
            // Initialize
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                Log.e(TAG, "Failed to initialize EGL")
                return false
            }
            
            // Choose config
            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
                Log.e(TAG, "Failed to choose EGL config")
                return false
            }
            
            eglConfig = configs[0]
            
            // Create context
            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                Log.e(TAG, "Failed to create EGL context")
                return false
            }
            
            Log.d(TAG, "EGL initialized successfully")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing EGL", e)
            return false
        }
    }
    
    /**
     * Dispose resources
     */
    fun dispose() {
        try {
            // Release surface
            surface?.release()
            surface = null
            
            // Release surface texture
            surfaceTexture?.release()
            surfaceTexture = null
            
            // Delete texture
            if (cameraTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(cameraTextureId), 0)
                cameraTextureId = 0
            }
            
            // Release EGL
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                
                EGL14.eglTerminate(eglDisplay)
            }
            
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
            
            isInitialized = false
            Log.d(TAG, "Filter surface manager disposed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing filter surface manager", e)
        }
    }
}
