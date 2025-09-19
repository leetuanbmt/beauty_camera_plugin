package com.beauty.camera_plugin.performance

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * PerformanceManager - Quản lý Performance Metrics & Frame Rate Control
 * 
 * SINGLE RESPONSIBILITY:
 * - Quản lý frame rate limiting
 * - Đo lường performance metrics (FPS, frame time)
 * - Adaptive quality control
 * - Memory usage monitoring
 * 
 * Design Pattern: Singleton + Observer
 * Thread Safety: Thread-safe với AtomicLong
 */
object PerformanceManager {
    private const val TAG = "PerformanceManager"
    
    // Frame rate control
    private const val TARGET_FPS = 60
    private const val MIN_FPS = 30
    private const val FRAME_TIME_NS = 1_000_000_000L / TARGET_FPS
    private const val MIN_FRAME_TIME_NS = 1_000_000_000L / MIN_FPS
    
    // Performance metrics
    private val frameCount = AtomicLong(0)
    private val lastFrameTime = AtomicLong(0)
    private val frameTimeSum = AtomicLong(0)
    private val lastFpsUpdate = AtomicLong(0)
    private var currentFps = 0.0
    private var averageFrameTime = 0.0
    
    // Adaptive quality
    private var qualityLevel = 1.0f // 0.5f - 1.0f
    private var isPerformanceMode = false
    
    // Memory monitoring
    private var lastMemoryCheck = 0L
    private const val MEMORY_CHECK_INTERVAL = 5000L // 5 seconds
    
    /**
     * Kiểm tra xem có nên render frame này không (frame rate limiting)
     */
    fun shouldRenderFrame(): Boolean {
        val currentTime = System.nanoTime()
        val lastTime = lastFrameTime.get()
        
        // Frame rate limiting
        if (currentTime - lastTime < FRAME_TIME_NS) {
            return false
        }
        
        // Update frame time
        if (lastTime > 0) {
            val frameTime = currentTime - lastTime
            frameTimeSum.addAndGet(frameTime)
            frameCount.incrementAndGet()
            
            // Update FPS every second
            if (currentTime - lastFpsUpdate.get() > 1_000_000_000L) {
                updateFpsMetrics(currentTime)
            }
        }
        
        lastFrameTime.set(currentTime)
        return true
    }
    
    /**
     * Cập nhật FPS metrics
     */
    private fun updateFpsMetrics(currentTime: Long) {
        val frames = frameCount.get()
        val totalTime = frameTimeSum.get()
        
        if (frames > 0 && totalTime > 0) {
            currentFps = frames * 1_000_000_000.0 / totalTime
            averageFrameTime = totalTime.toDouble() / frames / 1_000_000.0 // ms
            
            // Adaptive quality control
            updateQualityLevel()
            
            // Reset counters
            frameCount.set(0)
            frameTimeSum.set(0)
            lastFpsUpdate.set(currentTime)
            
            Log.d(TAG, "FPS: ${String.format("%.1f", currentFps)}, " +
                    "Avg Frame Time: ${String.format("%.2f", averageFrameTime)}ms, " +
                    "Quality: ${String.format("%.2f", qualityLevel)}")
        }
    }
    
    /**
     * Adaptive quality control dựa trên performance
     */
    private fun updateQualityLevel() {
        when {
            currentFps < 25 -> {
                // Performance quá thấp, giảm quality
                qualityLevel = (qualityLevel * 0.9f).coerceAtLeast(0.5f)
                isPerformanceMode = true
                Log.w(TAG, "Low FPS detected, reducing quality to $qualityLevel")
            }
            currentFps > 55 -> {
                // Performance tốt, có thể tăng quality
                qualityLevel = (qualityLevel * 1.05f).coerceAtMost(1.0f)
                isPerformanceMode = false
            }
            currentFps < 45 -> {
                // Performance trung bình, giữ nguyên hoặc giảm nhẹ
                if (!isPerformanceMode) {
                    qualityLevel = (qualityLevel * 0.95f).coerceAtLeast(0.7f)
                    isPerformanceMode = true
                }
            }
        }
    }
    
    /**
     * Lấy quality level hiện tại
     */
    fun getQualityLevel(): Float = qualityLevel
    
    /**
     * Kiểm tra xem có đang ở performance mode không
     */
    fun isPerformanceMode(): Boolean = isPerformanceMode
    
    /**
     * Lấy FPS hiện tại
     */
    fun getCurrentFps(): Double = currentFps
    
    /**
     * Lấy average frame time (ms)
     */
    fun getAverageFrameTime(): Double = averageFrameTime
    
    /**
     * Reset performance metrics
     */
    fun reset() {
        frameCount.set(0)
        frameTimeSum.set(0)
        lastFrameTime.set(0)
        lastFpsUpdate.set(0)
        currentFps = 0.0
        averageFrameTime = 0.0
        qualityLevel = 1.0f
        isPerformanceMode = false
    }
    
    /**
     * Kiểm tra memory usage (simplified)
     */
    fun checkMemoryUsage(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastMemoryCheck > MEMORY_CHECK_INTERVAL) {
            lastMemoryCheck = currentTime
            
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsage = usedMemory.toDouble() / maxMemory.toDouble()
            
            if (memoryUsage > 0.8) {
                Log.w(TAG, "High memory usage: ${String.format("%.1f", memoryUsage * 100)}%")
                return false
            }
        }
        return true
    }
    
    /**
     * Force garbage collection nếu cần
     */
    fun optimizeMemory() {
        if (!checkMemoryUsage()) {
            System.gc()
            Log.d(TAG, "Memory optimization triggered")
        }
    }
}
