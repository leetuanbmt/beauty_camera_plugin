package com.beauty.camera_plugin.renderer

import com.beauty.camera_plugin.FilterParameters
import com.beauty.camera_plugin.renderer.filters.FilterChain
import com.beauty.camera_plugin.renderer.filters.IFilter
import com.beauty.camera_plugin.renderer.filters.FilterCategory
import com.beauty.camera_plugin.renderer.filters.BeautySmoothFilter
import com.beauty.camera_plugin.renderer.filters.WhiteningFilter
import com.beauty.camera_plugin.renderer.filters.LutFilter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import android.util.Log

/**
 * Render Pipeline Manager
 * Manages the complete rendering pipeline including threading and performance optimization
 */
class RenderPipelineManager {
    private val TAG = "RenderPipelineManager"
    
    // Threading
    private val renderExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "RenderThread").apply {
            priority = Thread.MAX_PRIORITY
        }
    }
    
    // Components
    private var renderer: IRenderer? = null
    private var isInitialized = false
    private var isEnabled = true
    
    // Viewport dimensions
    private var viewportWidth = 1920
    private var viewportHeight = 1080
    
    // Performance tracking
    private var lastRenderTime = 0L
    private var frameCount = 0
    private var fpsStartTime = 0L
    
    // Current render task
    private var currentRenderTask: Future<*>? = null
    
    /**
     * Initialize render pipeline
     * @param width Viewport width
     * @param height Viewport height
     * @return Success status
     */
    fun initialize(width: Int, height: Int): Boolean {
        return try {
            // Create renderer
            renderer = GlRenderer()
            
            val rendererInitialized = renderer?.initialize(width, height) ?: false
            if (!rendererInitialized) {
                Log.e(TAG, "Failed to initialize renderer")
                return false
            }
            
            // Add default filters
            addDefaultFilters()
            
            // Initialize performance tracking
            fpsStartTime = System.currentTimeMillis()
            
            isInitialized = true
            Log.d(TAG, "Render pipeline initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize render pipeline", e)
            false
        }
    }
    
    /**
     * Set camera input texture
     * @param textureId Camera texture ID
     * @param width Texture width
     * @param height Texture height
     */
    fun setCameraInput(textureId: Int, width: Int, height: Int) {
        renderer?.setCameraInput(textureId, width, height)
    }
    
    /**
     * Render frame asynchronously
     * @param parameters Filter parameters
     */
    fun renderFrame(parameters: FilterParameters) {
        if (!isInitialized || !isEnabled) {
            return
        }
        
        // Cancel previous render task if still running
        currentRenderTask?.cancel(true)
        
        // Submit new render task
        currentRenderTask = renderExecutor.submit {
            try {
                val startTime = System.nanoTime()
                
                // Render frame
                renderer?.renderFrame(parameters)
                
                // Update performance metrics
                updatePerformanceMetrics(startTime)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in render task", e)
            }
        }
    }
    
    /**
     * Set viewport size
     * @param width Viewport width
     * @param height Viewport height
     */
    fun setViewport(width: Int, height: Int) {
        renderer?.setViewport(width, height)
    }
    
    /**
     * Update viewport size (can be called even if not initialized)
     * @param width Viewport width
     * @param height Viewport height
     */
    fun updateViewport(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        
        if (isInitialized) {
            renderer?.setViewport(width, height)
            Log.d(TAG, "Viewport updated to: ${width}x${height}")
        } else {
            Log.d(TAG, "Viewport size updated to: ${width}x${height} (will apply when pipeline initializes)")
        }
    }
    
    /**
     * Add filter to pipeline
     * @param filter Filter to add
     */
    fun addFilter(filter: IFilter) {
        renderer?.addFilter(filter)
    }
    
    /**
     * Remove filter from pipeline
     * @param filter Filter to remove
     */
    fun removeFilter(filter: IFilter) {
        renderer?.removeFilter(filter)
    }
    
    /**
     * Get filter chain
     */
    fun getFilterChain(): FilterChain? {
        return renderer?.getFilterChain()
    }
    
    /**
     * Get filter by name
     * @param name Filter name
     */
    fun getFilter(name: String): IFilter? {
        return renderer?.getFilterChain()?.getFilter(name)
    }
    
    /**
     * Get filters by category
     * @param category Filter category
     */
    fun getFiltersByCategory(category: FilterCategory): List<IFilter> {
        return renderer?.getFilterChain()?.getFiltersByCategory(category) ?: emptyList()
    }
    
    /**
     * Enable/disable rendering
     * @param enabled Rendering enabled state
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        renderer?.setEnabled(enabled)
    }
    
    /**
     * Get current FPS
     */
    fun getCurrentFPS(): Float {
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - fpsStartTime
        
        return if (elapsed > 0) {
            (frameCount * 1000.0f / elapsed)
        } else {
            0.0f
        }
    }
    
    /**
     * Get average render time
     */
    fun getAverageRenderTime(): Long {
        return lastRenderTime
    }
    
    /**
     * Check if renderer is initialized
     */
    fun isInitialized(): Boolean {
        return isInitialized
    }
    
    /**
     * Add default filters to the pipeline
     */
    private fun addDefaultFilters() {
        try {
            // Add beauty filters
            renderer?.addFilter(BeautySmoothFilter())
            renderer?.addFilter(WhiteningFilter())
            renderer?.addFilter(LutFilter())
            
            Log.d(TAG, "Added default filters to pipeline")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding default filters", e)
        }
    }
    
    /**
     * Dispose render pipeline
     */
    fun dispose() {
        try {
            // Cancel current render task
            currentRenderTask?.cancel(true)
            
            // Shutdown executor
            renderExecutor.shutdown()
            
            // Dispose renderer
            renderer?.dispose()
            renderer = null
            
            isInitialized = false
            
            Log.d(TAG, "Render pipeline disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing render pipeline", e)
        }
    }
    
    /**
     * Update performance metrics
     * @param startTime Render start time in nanoseconds
     */
    private fun updatePerformanceMetrics(startTime: Long) {
        val endTime = System.nanoTime()
        lastRenderTime = (endTime - startTime) / 1_000_000 // Convert to milliseconds
        
        frameCount++
        
        // Reset FPS counter every second
        val currentTime = System.currentTimeMillis()
        if (currentTime - fpsStartTime >= 1000) {
            frameCount = 0
            fpsStartTime = currentTime
        }
    }
}
