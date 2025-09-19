package com.beauty.camera_plugin

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import com.beauty.camera_plugin.gl.*
import com.beauty.camera_plugin.performance.*

/**
 * OpenGLManager - Singleton for OpenGL Rendering & Filter Management
 * 
 * SINGLE RESPONSIBILITY:
 * - Quản lý OpenGL rendering pipeline
 * - Handle filter application và shader management
 * - Manage SurfaceTexture và camera input
 * - Provide output surface cho display
 * 
 * Design Pattern: Singleton + Renderer
 * Thread Safety: OpenGL thread operations
 * 
 * Architecture: CameraManager → OpenGLManager → FilterRenderer
 */
object OpenGLManager {
    private const val TAG = "OpenGLManager"
    
    // OpenGL components
    private var renderer: FilterRenderer? = null
    private var glSurfaceView: GLSurfaceView? = null
    private var eglManager: EGLManager? = null
    private var isInitialized = false
    
    // Camera input
    private var cameraSurfaceTexture: SurfaceTexture? = null
    private var cameraTextureId = -1
    
    // Output surface
    private var outputSurface: Surface? = null
    private var outputSurfaceTexture: SurfaceTexture? = null
    private var outputTextureId = -1
    
    
    // Filter state
    private var currentFilter: FilterType = FilterType.NONE
    private var filterIntensity = 1.0f
    private var isFilterEnabled = false
    
    // Rendering state
    private var viewWidth = 0
    private var viewHeight = 0
    private var cameraWidth = 0
    private var cameraHeight = 0
    
    /**
     * Initialize OpenGL Manager
     * @param outputSurface Output surface for rendering
     * @return Success status
     */
    fun initialize(
        outputSurface: Surface
    ): Boolean {
        return try {
            this.outputSurface = outputSurface
            
            // Initialize EGL Manager
            eglManager = EGLManager()
            val eglInitialized = eglManager?.initialize(outputSurface) ?: false
            
            if (!eglInitialized) {
                Log.e(TAG, "Failed to initialize EGL Manager")
                return false
            }
            
            // Initialize OpenGL components
            initializeOpenGL()
            
            // Create renderer
            renderer = FilterRenderer()
            
            isInitialized = true
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OpenGL Manager", e)
            false
        }
    }
    
    /**
     * Set camera input texture
     * @param textureId Camera texture ID
     * @param width Camera width
     * @param height Camera height
     */
    fun setCameraInput(textureId: Int, width: Int, height: Int) {
        
        try {
        cameraTextureId = textureId
        cameraWidth = width
        cameraHeight = height
            
            // Make sure EGL context is current before configuring texture
            val contextMadeCurrent = eglManager?.makeCurrent() ?: false
            if (!contextMadeCurrent) {
                Log.e(TAG, "Failed to make EGL context current for camera input setup")
                return
            }
        
        // Configure camera texture parameters
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            val bindError = GLES20.glGetError()
            if (bindError != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "Error binding camera texture: $bindError")
                return
            }
            
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            
            val paramError = GLES20.glGetError()
            if (paramError != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "Error setting texture parameters: $paramError")
            }
        
        // Update renderer with camera info
        renderer?.setCameraInput(textureId, width, height)
        
        // Create SurfaceTexture for camera input with correct texture ID
        cameraSurfaceTexture = SurfaceTexture(textureId)
        cameraSurfaceTexture?.setOnFrameAvailableListener {
            // Direct rendering call instead of glSurfaceView
            updateCameraFrame()
        }
        
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting camera input", e)
        }
    }
    
    /**
     * Set output surface size
     * @param width Output width
     * @param height Output height
     */
    fun setOutputSize(width: Int, height: Int) {
        
        viewWidth = width
        viewHeight = height
        
        // Update renderer viewport
        renderer?.setViewport(width, height)
    }
    
    
    /**
     * Enable/disable filter
     * @param enabled Filter enabled state
     */
    fun setFilterEnabled(enabled: Boolean) {
        isFilterEnabled = enabled
        renderer?.setFilterEnabled(enabled)
    }
    
    /**
     * Apply specific filter (internal GL version)
     * @param filterType GL Filter type to apply
     */
     fun applyGLFilter(filterType:FilterType) {
        currentFilter = filterType
        renderer?.applyFilter(filterType)
    }
    
    /**
     * Set filter intensity
     * @param intensity Filter intensity (0.0 - 1.0)
     */
    fun setFilterIntensity(intensity: Float) {
        filterIntensity = intensity.coerceIn(0.0f, 1.0f)
        renderer?.setFilterIntensity(filterIntensity)
    }
    
    /**
     * Update camera frame
     * Call this when new camera frame is available
     */
    private fun updateCameraFrame() {
        try {
            // Performance check - should we render this frame?
            if (!PerformanceManager.shouldRenderFrame()) {
                return
            }
            
            // Update camera texture
            cameraSurfaceTexture?.updateTexImage()
            
            // Make EGL context current with error checking
            val contextMadeCurrent = eglManager?.makeCurrent() ?: false
            if (!contextMadeCurrent) {
                Log.e(TAG, "Failed to make EGL context current for frame update")
                return
            }
            
            // Ensure renderer exists
            if (renderer == null) {
                renderer = FilterRenderer()
                renderer?.onSurfaceCreated()
            }
        
            // Get texture transform matrix with caching
            val textureMatrix = MatrixCache.getFloatArray()
            cameraSurfaceTexture?.getTransformMatrix(textureMatrix)
        
        // Render directly to output surface
        renderer?.onDrawFrame()
        
        // Swap buffers to display the rendered frame
            val swapSuccess = eglManager?.swapBuffers() ?: false
            if (!swapSuccess) {
                Log.e(TAG, "Failed to swap EGL buffers")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateCameraFrame", e)
        }
    }
    
    /**
     * Initialize renderer when OpenGL context is ready
     */
    fun initializeRenderer() {
        
        try {
            // Make sure renderer exists
            if (renderer == null) {
                renderer = FilterRenderer()
            }
            
            // Make EGL context current before initializing shaders
            val contextMadeCurrent = eglManager?.makeCurrent() ?: false
            if (!contextMadeCurrent) {
                Log.e(TAG, "Failed to make EGL context current for renderer initialization")
                return
            }
            
            renderer?.onSurfaceCreated()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing renderer", e)
            // Don't throw the exception, just log it
        }
    }

    /**
     * Get camera SurfaceTexture for CameraX
     */
    fun getCameraSurfaceTexture(): SurfaceTexture? {
        return cameraSurfaceTexture
    }
    
    
    
    /**
     * Dispose OpenGL resources
     */
    fun dispose() {
        
        try {
            // Stop rendering
            glSurfaceView?.onPause()
            glSurfaceView = null
            
            // Dispose EGL Manager
            eglManager?.dispose()
            eglManager = null
            
            // Release textures
            if (cameraTextureId != -1) {
                GLES20.glDeleteTextures(1, intArrayOf(cameraTextureId), 0)
                cameraTextureId = -1
            }
            
            if (outputTextureId != -1) {
                GLES20.glDeleteTextures(1, intArrayOf(outputTextureId), 0)
                outputTextureId = -1
            }
            
            
            // Release surfaces
            cameraSurfaceTexture?.release()
            outputSurfaceTexture?.release()
            
            // Performance cleanup
            PerformanceManager.reset()
            MatrixCache.clearCache()
            MemoryPool.clearPools()
            RenderOptimizer.reset()
            
            cameraSurfaceTexture = null
            outputSurfaceTexture = null
            outputSurface = null
            
            // Dispose filter renderer
            renderer?.dispose()
            
            // Clear renderer
            renderer = null
            
            isInitialized = false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing OpenGL Manager", e)
        }
    }
    
    // --- Private Methods ---
    
    private fun initializeOpenGL() {
        
        try {
            // Note: cameraTextureId will be set when camera provides the texture
            // We don't generate texture here, we use the one provided by camera
            
            // Framebuffer will be created in FilterRenderer
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in initializeOpenGL", e)
            throw e
        }
    }
    
    
    
}

/**
 * Filter Renderer - Handles actual OpenGL rendering
 * Based on OpenGLESPro architecture patterns
 */
private class FilterRenderer {
    private val TAG = "FilterRenderer"
    
    // Shader programs
    private var shaderProgram: ShaderProgram? = null
    private var currentFilter = GlFilterType.NONE
    private var filterEnabled = false
    private var filterIntensity = 1.0f
    
    // Rendering state
    private var viewWidth = 0
    private var viewHeight = 0
    private var cameraTextureId = -1
    private var cameraWidth = 0
    private var cameraHeight = 0
    
    // Transform matrices
    private val mvpMatrix = FloatArray(16)
    private val texMatrix = FloatArray(16)
    
    
    fun onSurfaceCreated() {
        
        try {
            // Initialize shader program
            shaderProgram = ShaderProgram()
            
            val shaderInitialized = shaderProgram?.initialize() ?: false
            
            if (!shaderInitialized) {
                Log.e(TAG, "Failed to initialize shader program")
                return
            }
            
            // Initialize matrices
            Matrix.setIdentityM(mvpMatrix, 0)
            Matrix.setIdentityM(texMatrix, 0)
            
            
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onSurfaceCreated", e)
            throw e
        }
    }

    
    fun onDrawFrame() {
        if (cameraTextureId == -1) {
            return
        }
        
        if (shaderProgram == null) {
            // Try to initialize shader program if it's null
            try {
                shaderProgram = ShaderProgram()
                val shaderInitialized = shaderProgram?.initialize() ?: false
                if (!shaderInitialized) {
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing shader program in onDrawFrame", e)
            return
            }
        }
        
        
        try {
            // Performance optimization - check if we should render
            val qualityLevel = PerformanceManager.getQualityLevel()
            if (!RenderOptimizer.shouldRenderFrame(
                    cameraTextureId, 
                    currentFilter.name, 
                    filterIntensity, 
                    qualityLevel
                )) {
                return
            }
            
            // Set viewport with caching
            RenderOptimizer.setViewport(viewWidth, viewHeight)
            
            // Render directly to Flutter surface (default framebuffer)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            
            // Clear screen
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            
            val clearError = GLES20.glGetError()
            if (clearError != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "OpenGL error after clear: $clearError")
            }
            
            // Get cached MVP matrix
            val mvpMatrix = MatrixCache.getMVPMatrix(viewWidth, viewHeight)
            
            // Render with current filter directly to Flutter surface
            shaderProgram?.render(
                cameraTextureId,
                mvpMatrix,
                filterEnabled,
                currentFilter,
                filterIntensity
            )
            
            // Check for OpenGL errors
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "OpenGL error after render: $error")
            }
            
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in FilterRenderer.onDrawFrame", e)
            // Don't throw the exception, just log it to prevent crashes
        }
    }
    
    
    
    
    fun setCameraInput(textureId: Int, width: Int, height: Int) {
        cameraTextureId = textureId
        cameraWidth = width
        cameraHeight = height
        updateProjectionMatrix()
    }
    
    fun setViewport(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES20.glViewport(0, 0, width, height)
        updateProjectionMatrix()
    }
    
    fun setFilterEnabled(enabled: Boolean) {
        filterEnabled = enabled
    }
    
    fun applyFilter(filterType: FilterType) {
        // Convert from Pigeon FilterType to GL FilterType
        val glFilterType = when (filterType) {
            com.beauty.camera_plugin.FilterType.NONE -> GlFilterType.NONE
            
            // Beauty filters -> Beauty GL filter
            com.beauty.camera_plugin.FilterType.BEAUTY_NATURAL,
            com.beauty.camera_plugin.FilterType.BEAUTY_GLOW,
            com.beauty.camera_plugin.FilterType.BEAUTY_DOLL,
            com.beauty.camera_plugin.FilterType.BEAUTY_FRESH,
            com.beauty.camera_plugin.FilterType.BEAUTY_SMOOTH,
            com.beauty.camera_plugin.FilterType.BEAUTY_BRIGHT -> GlFilterType.BEAUTY
            
            // Portrait filters -> Portrait GL filter
            com.beauty.camera_plugin.FilterType.PORTRAIT_CLASSIC,
            com.beauty.camera_plugin.FilterType.PORTRAIT_DRAMATIC,
            com.beauty.camera_plugin.FilterType.PORTRAIT_SOFT,
            com.beauty.camera_plugin.FilterType.PORTRAIT_BW -> GlFilterType.PORTRAIT
            
            // Food filters -> Food GL filter
            com.beauty.camera_plugin.FilterType.FOOD_WARM,
            com.beauty.camera_plugin.FilterType.FOOD_VIBRANT,
            com.beauty.camera_plugin.FilterType.FOOD_FRESH,
            com.beauty.camera_plugin.FilterType.FOOD_INSTAGRAM -> GlFilterType.FOOD
            
            // Vintage filters -> Vintage GL filter (except sepia)
            com.beauty.camera_plugin.FilterType.VINTAGE_FILM,
            com.beauty.camera_plugin.FilterType.VINTAGE_FADED,
            com.beauty.camera_plugin.FilterType.VINTAGE_RETRO -> GlFilterType.VINTAGE
            
            // Sepia specific
            com.beauty.camera_plugin.FilterType.VINTAGE_SEPIA -> GlFilterType.SEPIA
            
            // Warm mapping (exclude FOOD_WARM already mapped above)
            com.beauty.camera_plugin.FilterType.VIBRANT_SUMMER,
            com.beauty.camera_plugin.FilterType.VIBRANT_TROPICAL -> GlFilterType.WARM
            
            com.beauty.camera_plugin.FilterType.MOODY_BLUE -> GlFilterType.COOL
            
            // Default mapping for others
            else -> GlFilterType.NONE
        }
        
        currentFilter = glFilterType
    }
    
    fun setFilterIntensity(intensity: Float) {
        filterIntensity = intensity
    }
    
    private fun updateProjectionMatrix() {
        if (viewWidth == 0 || viewHeight == 0 || cameraWidth == 0 || cameraHeight == 0) return
        
        
        // Use simple orthographic projection for full screen quad
        Matrix.orthoM(mvpMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)
        
    }
    
    
    fun dispose() {
        try {
            shaderProgram?.dispose()
            shaderProgram = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing FilterRenderer", e)
        }
    }
    
}
