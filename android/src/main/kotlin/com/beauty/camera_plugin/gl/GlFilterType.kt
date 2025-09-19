package com.beauty.camera_plugin.gl

/**
 * GlFilterType - Enum for different GL filter types
 * 
 * Based on OpenGLESPro filter patterns
 */
enum class GlFilterType(val displayName: String, val shaderName: String) {
    NONE("None", "none"),
    BEAUTY("Beauty", "beauty"),
    PORTRAIT("Portrait", "portrait"),
    VINTAGE("Vintage", "vintage"),
    FOOD("Food", "food"),
    BLACK_WHITE("Black & White", "black_white"),
    SEPIA("Sepia", "sepia"),
    COOL("Cool", "cool"),
    WARM("Warm", "warm");
    
    companion object {
        fun fromString(name: String): GlFilterType {
            return values().find { it.shaderName == name.lowercase() } ?: NONE
        }
        
        fun getAllFilters(): List<GlFilterType> {
            return values().filter { it != NONE }
        }
    }
}
