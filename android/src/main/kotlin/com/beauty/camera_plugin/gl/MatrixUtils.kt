package com.beauty.camera_plugin.gl

/**
 * Utility class for OpenGL matrix operations
 * Based on Android OpenGL projection guidelines
 */
object MatrixUtils {
    

    /**
     * Create orthographic projection matrix for 2D rendering
     * @param left Left boundary
     * @param right Right boundary  
     * @param bottom Bottom boundary
     * @param top Top boundary
     * @param near Near plane
     * @param far Far plane
     * @return 4x4 projection matrix as FloatArray
     */
    fun createOrthographicMatrix(
        left: Float, right: Float,
        bottom: Float, top: Float,
        near: Float, far: Float
    ): FloatArray {
        val matrix = FloatArray(16)
        
        // Initialize as identity matrix
        for (i in 0..15) {
            matrix[i] = if (i % 5 == 0) 1.0f else 0.0f
        }
        
        // Set orthographic projection values
        matrix[0] = 2.0f / (right - left)
        matrix[5] = 2.0f / (top - bottom)
        matrix[10] = -2.0f / (far - near)
        matrix[12] = -(right + left) / (right - left)
        matrix[13] = -(top + bottom) / (top - bottom)
        matrix[14] = -(far + near) / (far - near)
        
        return matrix
    }

}
