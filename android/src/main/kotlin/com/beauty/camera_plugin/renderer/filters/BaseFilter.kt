package com.beauty.camera_plugin.renderer.filters

import com.beauty.camera_plugin.FilterParameters

/**
 * Base interface for all filters
 * Implements the Chain of Responsibility pattern
 */
interface IFilter {
    /**
     * Filter name for identification
     */
    val name: String
    
    /**
     * Filter category
     */
    val category: FilterCategory
    
    /**
     * Apply filter to texture
     * @param inputTexture Input texture ID
     * @param outputTexture Output texture ID (can be same as input for in-place)
     * @param parameters Filter parameters
     * @param width Texture width
     * @param height Texture height
     * @return Output texture ID
     */
    fun apply(
        inputTexture: Int,
        outputTexture: Int,
        parameters: FilterParameters,
        width: Int,
        height: Int
    ): Int
    
    /**
     * Initialize filter resources
     * @return Success status
     */
    fun initialize(): Boolean
    
    /**
     * Release filter resources
     */
    fun dispose()
    
    /**
     * Check if filter is initialized
     */
    fun isInitialized(): Boolean
    
    /**
     * Get filter intensity
     */
    fun getIntensity(): Float
    
    /**
     * Set filter intensity
     * @param intensity Intensity (0.0 - 1.0)
     */
    fun setIntensity(intensity: Float)
}

/**
 * Filter categories
 */
enum class FilterCategory {
    NONE,
    BEAUTY,      // Face-based filters (skin smoothing, brightening)
    PORTRAIT,    // Portrait enhancement
    FOOD,        // Food photography
    LANDSCAPE,   // Landscape photography
    VINTAGE,     // Vintage effects
    VIBRANT,     // Vibrant colors
    MOODY,       // Moody tones
    FILM,        // Film simulation
    ART,         // Artistic effects
    AR           // AR effects (stickers, 3D objects)
}

/**
 * Abstract base class for filters
 * Provides common functionality
 */
abstract class BaseFilter : IFilter {
    protected var initialized = false
    private var _intensity = 1.0f
    
    override fun getIntensity(): Float = _intensity
    
    override fun setIntensity(intensity: Float) {
        this._intensity = intensity.coerceIn(0.0f, 1.0f)
    }
    
    override fun isInitialized(): Boolean = initialized
    
    override fun dispose() {
        initialized = false
    }
}

/**
 * Filter chain for applying multiple filters in sequence
 */
class FilterChain {
    private val filters = mutableListOf<IFilter>()
    
    /**
     * Add filter to chain
     * @param filter Filter to add
     */
    fun addFilter(filter: IFilter) {
        filters.add(filter)
    }
    
    /**
     * Remove filter from chain
     * @param filter Filter to remove
     */
    fun removeFilter(filter: IFilter) {
        filters.remove(filter)
    }
    
    /**
     * Apply all filters in chain
     * @param inputTexture Input texture
     * @param parameters Filter parameters
     * @param width Texture width
     * @param height Texture height
     * @return Final output texture
     */
    fun applyFilters(
        inputTexture: Int,
        parameters: FilterParameters,
        width: Int,
        height: Int
    ): Int {
        var currentTexture = inputTexture
        
        for (filter in filters) {
            if (filter.isInitialized() && filter.getIntensity() > 0.0f) {
                currentTexture = filter.apply(
                    inputTexture = currentTexture,
                    outputTexture = currentTexture, // In-place processing
                    parameters = parameters,
                    width = width,
                    height = height
                )
            }
        }
        
        return currentTexture
    }
    
    /**
     * Initialize all filters in chain
     */
    fun initializeAll(): Boolean {
        var allInitialized = true
        
        for (filter in filters) {
            if (!filter.initialize()) {
                allInitialized = false
            }
        }
        
        return allInitialized
    }
    
    /**
     * Dispose all filters in chain
     */
    fun disposeAll() {
        for (filter in filters) {
            filter.dispose()
        }
        filters.clear()
    }
    
    /**
     * Get filter by name
     * @param name Filter name
     * @return Filter or null if not found
     */
    fun getFilter(name: String): IFilter? {
        return filters.find { it.name == name }
    }
    
    /**
     * Get filters by category
     * @param category Filter category
     * @return List of filters in category
     */
    fun getFiltersByCategory(category: FilterCategory): List<IFilter> {
        return filters.filter { it.category == category }
    }
    
    /**
     * Clear all filters
     */
    fun clear() {
        disposeAll()
    }
}
