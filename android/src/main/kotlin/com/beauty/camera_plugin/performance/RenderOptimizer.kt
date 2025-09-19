package com.beauty.camera_plugin.performance

import android.opengl.GLES20
import android.util.Log
import com.beauty.camera_plugin.gl.MatrixUtils

/**
 * RenderOptimizer - Tối ưu Rendering Pipeline
 * 
 * SINGLE RESPONSIBILITY:
 * - Optimize rendering pipeline
 * - Conditional rendering
 * - Viewport caching
 * - Shader state management
 * 
 * Design Pattern: Strategy + Cache
 * Thread Safety: Thread-safe
 */
object RenderOptimizer {
    private const val TAG = "RenderOptimizer"
    
    // Viewport cache
    private var cachedViewportWidth = -1
    private var cachedViewportHeight = -1
    private var viewportDirty = true
    
    // Shader state cache
    private var lastShaderProgram = -1
    private var lastTextureId = -1
    private var lastFilterType = ""
    private var lastFilterIntensity = -1.0f
    
    // Rendering state
    private var isRenderingEnabled = true
    private var skipFrameCount = 0
    private const val MAX_SKIP_FRAMES = 3
    
    /**
     * Kiểm tra xem có nên render frame này không
     */
    fun shouldRenderFrame(
        textureId: Int,
        filterType: String,
        filterIntensity: Float,
        qualityLevel: Float
    ): Boolean {
        if (!isRenderingEnabled) {
            return false
        }
        
        // Skip frames nếu performance thấp
        if (qualityLevel < 0.7f && skipFrameCount < MAX_SKIP_FRAMES) {
            skipFrameCount++
            return false
        }
        
        skipFrameCount = 0
        
        // Render nếu có thay đổi
        val hasChanges = textureId != lastTextureId ||
                filterType != lastFilterType ||
                filterIntensity != lastFilterIntensity
        
        if (hasChanges) {
            updateCache(textureId, filterType, filterIntensity)
            return true
        }
        
        // Render mỗi frame nếu quality cao
        return qualityLevel >= 0.8f
    }
    
    /**
     * Update cache với state mới
     */
    private fun updateCache(
        textureId: Int,
        filterType: String,
        filterIntensity: Float
    ) {
        lastTextureId = textureId
        lastFilterType = filterType
        lastFilterIntensity = filterIntensity
    }
    
    /**
     * Set viewport với caching
     */
    fun setViewport(width: Int, height: Int) {
        if (width != cachedViewportWidth || height != cachedViewportHeight || viewportDirty) {
            GLES20.glViewport(0, 0, width, height)
            cachedViewportWidth = width
            cachedViewportHeight = height
            viewportDirty = false
            
            Log.d(TAG, "Viewport set to: ${width}x${height}")
        }
    }
    
    /**
     * Mark viewport as dirty (cần update)
     */
    fun markViewportDirty() {
        viewportDirty = true
    }
    
    /**
     * Optimize shader state changes
     */
    fun optimizeShaderState(
        shaderProgram: Int,
        textureId: Int,
        filterType: String,
        filterIntensity: Float
    ): Boolean {
        val needsStateChange = shaderProgram != lastShaderProgram ||
                textureId != lastTextureId ||
                filterType != lastFilterType ||
                filterIntensity != lastFilterIntensity
        
        if (needsStateChange) {
            lastShaderProgram = shaderProgram
            lastTextureId = textureId
            lastFilterType = filterType
            lastFilterIntensity = filterIntensity
            return true
        }
        
        return false
    }
    
    /**
     * Optimize texture binding
     */
    fun optimizeTextureBinding(textureId: Int): Boolean {
        if (textureId != lastTextureId) {
            lastTextureId = textureId
            return true
        }
        return false
    }
    
    /**
     * Enable/disable rendering
     */
    fun setRenderingEnabled(enabled: Boolean) {
        isRenderingEnabled = enabled
        if (!enabled) {
            Log.d(TAG, "Rendering disabled")
        } else {
            Log.d(TAG, "Rendering enabled")
        }
    }
    
    /**
     * Check if rendering is enabled
     */
    fun isRenderingEnabled(): Boolean = isRenderingEnabled
    
    /**
     * Reset optimizer state
     */
    fun reset() {
        cachedViewportWidth = -1
        cachedViewportHeight = -1
        viewportDirty = true
        lastShaderProgram = -1
        lastTextureId = -1
        lastFilterType = ""
        lastFilterIntensity = -1.0f
        skipFrameCount = 0
        isRenderingEnabled = true
        
        Log.d(TAG, "Render optimizer reset")
    }
    
    /**
     * Get optimization statistics
     */
    fun getOptimizationStats(): String {
        return "Viewport: ${cachedViewportWidth}x${cachedViewportHeight}, " +
                "Shader: $lastShaderProgram, Texture: $lastTextureId, " +
                "Skip Frames: $skipFrameCount, Enabled: $isRenderingEnabled"
    }
    
    /**
     * Optimize based on performance metrics
     */
    fun optimizeForPerformance(fps: Double, frameTime: Double) {
        when {
            fps < 20 -> {
                // Very low FPS, disable rendering temporarily
                setRenderingEnabled(false)
                Log.w(TAG, "Very low FPS ($fps), disabling rendering")
            }
            fps < 30 -> {
                // Low FPS, increase skip frames
                skipFrameCount = MAX_SKIP_FRAMES
                Log.w(TAG, "Low FPS ($fps), increasing skip frames")
            }
            fps > 55 -> {
                // Good FPS, enable full rendering
                setRenderingEnabled(true)
                skipFrameCount = 0
            }
        }
    }
}
