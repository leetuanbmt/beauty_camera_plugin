package com.beauty.camera_plugin.performance

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * MemoryPool - Object Pooling cho Memory Optimization
 * 
 * SINGLE RESPONSIBILITY:
 * - Object pooling cho FloatBuffer, ByteBuffer
 * - Reuse objects thay vì tạo mới
 * - Memory allocation optimization
 * - Garbage collection reduction
 * 
 * Design Pattern: Object Pool
 * Thread Safety: Thread-safe với ConcurrentLinkedQueue
 */
object MemoryPool {
    private const val TAG = "MemoryPool"
    
    // Object pools
    private val floatBufferPool = ConcurrentLinkedQueue<FloatBuffer>()
    private val byteBufferPool = ConcurrentLinkedQueue<ByteBuffer>()
    private val floatArrayPool = ConcurrentLinkedQueue<FloatArray>()
    
    // Pool sizes
    private const val FLOAT_BUFFER_POOL_SIZE = 20
    private const val BYTE_BUFFER_POOL_SIZE = 20
    private const val FLOAT_ARRAY_POOL_SIZE = 30
    
    // Pre-allocated sizes
    private const val DEFAULT_FLOAT_BUFFER_SIZE = 8 * 4 // 8 vertices * 4 bytes
    private const val DEFAULT_BYTE_BUFFER_SIZE = 16 * 4 // 16 floats * 4 bytes
    private const val DEFAULT_FLOAT_ARRAY_SIZE = 16 // 4x4 matrix
    
    init {
        initializePools()
    }
    
    /**
     * Initialize object pools với pre-allocated objects
     */
    private fun initializePools() {
        // Initialize FloatBuffer pool
        for (i in 0 until FLOAT_BUFFER_POOL_SIZE) {
            val byteBuffer = ByteBuffer.allocateDirect(DEFAULT_FLOAT_BUFFER_SIZE)
                .order(ByteOrder.nativeOrder())
            floatBufferPool.offer(byteBuffer.asFloatBuffer())
        }
        
        // Initialize ByteBuffer pool
        for (i in 0 until BYTE_BUFFER_POOL_SIZE) {
            val byteBuffer = ByteBuffer.allocateDirect(DEFAULT_BYTE_BUFFER_SIZE)
                .order(ByteOrder.nativeOrder())
            byteBufferPool.offer(byteBuffer)
        }
        
        // Initialize FloatArray pool
        for (i in 0 until FLOAT_ARRAY_POOL_SIZE) {
            floatArrayPool.offer(FloatArray(DEFAULT_FLOAT_ARRAY_SIZE))
        }
        
        Log.d(TAG, "Memory pools initialized: FloatBuffer($FLOAT_BUFFER_POOL_SIZE), " +
                "ByteBuffer($BYTE_BUFFER_POOL_SIZE), FloatArray($FLOAT_ARRAY_POOL_SIZE)")
    }
    
    /**
     * Lấy FloatBuffer từ pool
     */
    fun getFloatBuffer(size: Int = DEFAULT_FLOAT_BUFFER_SIZE): FloatBuffer {
        val buffer = floatBufferPool.poll()
        return if (buffer != null && buffer.capacity() >= size) {
            buffer.clear()
            buffer
        } else {
            // Tạo mới nếu pool empty hoặc size không đủ
            val byteBuffer = ByteBuffer.allocateDirect(size * 4)
                .order(ByteOrder.nativeOrder())
            byteBuffer.asFloatBuffer()
        }
    }
    
    /**
     * Trả FloatBuffer về pool
     */
    fun returnFloatBuffer(buffer: FloatBuffer) {
        if (floatBufferPool.size < FLOAT_BUFFER_POOL_SIZE) {
            buffer.clear()
            floatBufferPool.offer(buffer)
        }
    }
    
    /**
     * Lấy ByteBuffer từ pool
     */
    fun getByteBuffer(size: Int = DEFAULT_BYTE_BUFFER_SIZE): ByteBuffer {
        val buffer = byteBufferPool.poll()
        return if (buffer != null && buffer.capacity() >= size) {
            buffer.clear()
            buffer
        } else {
            // Tạo mới nếu pool empty hoặc size không đủ
            ByteBuffer.allocateDirect(size)
                .order(ByteOrder.nativeOrder())
        }
    }
    
    /**
     * Trả ByteBuffer về pool
     */
    fun returnByteBuffer(buffer: ByteBuffer) {
        if (byteBufferPool.size < BYTE_BUFFER_POOL_SIZE) {
            buffer.clear()
            byteBufferPool.offer(buffer)
        }
    }
    
    /**
     * Lấy FloatArray từ pool
     */
    fun getFloatArray(size: Int = DEFAULT_FLOAT_ARRAY_SIZE): FloatArray {
        val array = floatArrayPool.poll()
        return if (array != null && array.size >= size) {
            array
        } else {
            // Tạo mới nếu pool empty hoặc size không đủ
            FloatArray(size)
        }
    }
    
    /**
     * Trả FloatArray về pool
     */
    fun returnFloatArray(array: FloatArray) {
        if (floatArrayPool.size < FLOAT_ARRAY_POOL_SIZE) {
            floatArrayPool.offer(array)
        }
    }
    
    /**
     * Tạo FloatBuffer với data từ pool
     */
    fun createFloatBuffer(data: FloatArray): FloatBuffer {
        val buffer = getFloatBuffer(data.size)
        buffer.put(data)
        buffer.position(0)
        return buffer
    }
    
    /**
     * Tạo vertex buffer cho quad (4 vertices, 2 coordinates each)
     */
    fun getQuadVertexBuffer(): FloatBuffer {
        val vertices = floatArrayOf(
            -1.0f, -1.0f,  // Bottom-left
             1.0f, -1.0f,  // Bottom-right
            -1.0f,  1.0f,  // Top-left
             1.0f,  1.0f   // Top-right
        )
        return createFloatBuffer(vertices)
    }
    
    /**
     * Tạo texture coordinate buffer cho quad
     */
    fun getQuadTextureBuffer(): FloatBuffer {
        val texCoords = floatArrayOf(
            0.0f, 0.0f,  // Bottom-left
            1.0f, 0.0f,  // Bottom-right
            0.0f, 1.0f,  // Top-left
            1.0f, 1.0f   // Top-right
        )
        return createFloatBuffer(texCoords)
    }
    
    /**
     * Tạo texture coordinate buffer với vertical flip
     */
    fun getQuadTextureBufferFlipped(): FloatBuffer {
        val texCoords = floatArrayOf(
            0.0f, 1.0f,  // Bottom-left (flipped)
            1.0f, 1.0f,  // Bottom-right (flipped)
            0.0f, 0.0f,  // Top-left (flipped)
            1.0f, 0.0f   // Top-right (flipped)
        )
        return createFloatBuffer(texCoords)
    }
    
    /**
     * Get pool statistics
     */
    fun getPoolStats(): String {
        return "FloatBuffer: ${floatBufferPool.size}/$FLOAT_BUFFER_POOL_SIZE, " +
                "ByteBuffer: ${byteBufferPool.size}/$BYTE_BUFFER_POOL_SIZE, " +
                "FloatArray: ${floatArrayPool.size}/$FLOAT_ARRAY_POOL_SIZE"
    }
    
    /**
     * Clear all pools
     */
    fun clearPools() {
        floatBufferPool.clear()
        byteBufferPool.clear()
        floatArrayPool.clear()
        Log.d(TAG, "All memory pools cleared")
    }
    
    /**
     * Optimize pools - remove excess objects
     */
    fun optimizePools() {
        // Remove excess FloatBuffers
        while (floatBufferPool.size > FLOAT_BUFFER_POOL_SIZE) {
            floatBufferPool.poll()
        }
        
        // Remove excess ByteBuffers
        while (byteBufferPool.size > BYTE_BUFFER_POOL_SIZE) {
            byteBufferPool.poll()
        }
        
        // Remove excess FloatArrays
        while (floatArrayPool.size > FLOAT_ARRAY_POOL_SIZE) {
            floatArrayPool.poll()
        }
        
        Log.d(TAG, "Memory pools optimized")
    }
    
    /**
     * Force garbage collection và reinitialize pools
     */
    fun forceCleanup() {
        clearPools()
        System.gc()
        initializePools()
        Log.d(TAG, "Memory pools force cleaned and reinitialized")
    }
}
