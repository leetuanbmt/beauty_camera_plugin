package com.beauty.camera_plugin.performance

import android.opengl.Matrix
import android.util.Log
import com.beauty.camera_plugin.gl.MatrixUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * MatrixCache - Cache Matrices & Object Pooling cho Performance
 * 
 * SINGLE RESPONSIBILITY:
 * - Cache MVP matrices để tránh tính toán lại
 * - Object pooling cho FloatArray
 * - Cache texture matrices
 * - Optimize matrix operations
 * 
 * Design Pattern: Object Pool + Cache
 * Thread Safety: Thread-safe với ConcurrentHashMap
 */
object MatrixCache {
    private const val TAG = "MatrixCache"
    
    // Cache cho các matrices thường dùng
    private val mvpMatrixCache = ConcurrentHashMap<String, FloatArray>()
    private val textureMatrixCache = ConcurrentHashMap<String, FloatArray>()
    
    // Object pool cho FloatArray
    private val floatArrayPool = mutableListOf<FloatArray>()
    private val poolSize = 10
    private var poolIndex = 0
    
    // Cache keys
    private const val DEFAULT_MVP_KEY = "default_mvp"
    private const val ORTHO_MVP_KEY = "ortho_mvp"
    
    init {
        // Pre-allocate object pool
        for (i in 0 until poolSize) {
            floatArrayPool.add(FloatArray(16))
        }
    }
    
    /**
     * Lấy FloatArray từ pool (thread-safe)
     */
    fun getFloatArray(): FloatArray {
        synchronized(floatArrayPool) {
            val array = floatArrayPool[poolIndex]
            poolIndex = (poolIndex + 1) % poolSize
            return array
        }
    }
    
    /**
     * Cache MVP matrix cho viewport cụ thể
     */
    fun getMVPMatrix(width: Int, height: Int): FloatArray {
        val key = "${width}x${height}"
        
        return mvpMatrixCache.getOrPut(key) {
            val matrix = getFloatArray()
            MatrixUtils.createOrthographicMatrix(
                left = -1.0f, right = 1.0f,
                bottom = -1.0f, top = 1.0f,
                near = -1.0f, far = 1.0f
            ).copyInto(matrix)
            matrix
        }
    }
    
    /**
     * Cache default MVP matrix
     */
    fun getDefaultMVPMatrix(): FloatArray {
        return mvpMatrixCache.getOrPut(DEFAULT_MVP_KEY) {
            val matrix = getFloatArray()
            Matrix.setIdentityM(matrix, 0)
            matrix
        }
    }
    
    /**
     * Cache orthographic MVP matrix
     */
    fun getOrthoMVPMatrix(): FloatArray {
        return mvpMatrixCache.getOrPut(ORTHO_MVP_KEY) {
            val matrix = getFloatArray()
            Matrix.orthoM(matrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)
            matrix
        }
    }
    
    /**
     * Cache texture matrix với key cụ thể
     */
    fun getTextureMatrix(key: String, matrix: FloatArray): FloatArray {
        return textureMatrixCache.getOrPut(key) {
            val cachedMatrix = getFloatArray()
            matrix.copyInto(cachedMatrix)
            cachedMatrix
        }
    }
    
    /**
     * Update texture matrix cache nếu có thay đổi
     */
    fun updateTextureMatrix(key: String, matrix: FloatArray): FloatArray {
        val cachedMatrix = textureMatrixCache[key]
        return if (cachedMatrix != null && !matrix.contentEquals(cachedMatrix)) {
            // Matrix đã thay đổi, update cache
            matrix.copyInto(cachedMatrix)
            cachedMatrix
        } else {
            // Matrix không thay đổi, return cached version
            cachedMatrix ?: getTextureMatrix(key, matrix)
        }
    }
    
    /**
     * Tạo MVP matrix với model, view, projection
     */
    fun createMVPMatrix(
        modelMatrix: FloatArray? = null,
        viewMatrix: FloatArray? = null,
        projectionMatrix: FloatArray? = null
    ): FloatArray {
        val mvpMatrix = getFloatArray()
        
        // Default matrices nếu không provided
        val model = modelMatrix ?: getDefaultMVPMatrix()
        val view = viewMatrix ?: getDefaultMVPMatrix()
        val projection = projectionMatrix ?: getOrthoMVPMatrix()
        
        // MVP = Projection * View * Model
        val viewModelMatrix = getFloatArray()
        Matrix.multiplyMM(viewModelMatrix, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projection, 0, viewModelMatrix, 0)
        
        return mvpMatrix
    }
    
    /**
     * Tạo texture matrix với transform
     */
    fun createTextureMatrix(
        scaleX: Float = 1.0f,
        scaleY: Float = 1.0f,
        translateX: Float = 0.0f,
        translateY: Float = 0.0f,
        rotation: Float = 0.0f
    ): FloatArray {
        val matrix = getFloatArray()
        Matrix.setIdentityM(matrix, 0)
        
        // Apply transformations
        if (scaleX != 1.0f || scaleY != 1.0f) {
            Matrix.scaleM(matrix, 0, scaleX, scaleY, 1.0f)
        }
        
        if (translateX != 0.0f || translateY != 0.0f) {
            Matrix.translateM(matrix, 0, translateX, translateY, 0.0f)
        }
        
        if (rotation != 0.0f) {
            Matrix.rotateM(matrix, 0, rotation, 0.0f, 0.0f, 1.0f)
        }
        
        return matrix
    }
    
    /**
     * Clear cache để giải phóng memory
     */
    fun clearCache() {
        mvpMatrixCache.clear()
        textureMatrixCache.clear()
        Log.d(TAG, "Matrix cache cleared")
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): String {
        return "MVP Cache: ${mvpMatrixCache.size}, Texture Cache: ${textureMatrixCache.size}, Pool: $poolIndex/$poolSize"
    }
    
    /**
     * Optimize cache dựa trên memory usage
     */
    fun optimizeCache() {
        // Clear old entries nếu cache quá lớn
        if (mvpMatrixCache.size > 20) {
            val keysToRemove = mvpMatrixCache.keys.take(10)
            keysToRemove.forEach { mvpMatrixCache.remove(it) }
            Log.d(TAG, "Cleared ${keysToRemove.size} MVP cache entries")
        }
        
        if (textureMatrixCache.size > 20) {
            val keysToRemove = textureMatrixCache.keys.take(10)
            keysToRemove.forEach { textureMatrixCache.remove(it) }
            Log.d(TAG, "Cleared ${keysToRemove.size} texture cache entries")
        }
    }
}
