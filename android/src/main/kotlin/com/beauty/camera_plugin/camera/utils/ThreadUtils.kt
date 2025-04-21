package com.beauty.camera_plugin.camera.utils

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.beauty.camera_plugin.camera.utils.ErrorUtils
/**
 * Utility class for managing threads and handlers
 */
object ThreadUtils {
    private const val BACKGROUND_THREAD_NAME = "CameraBackgroundThread"
    private const val PREVIEW_THREAD_NAME = "CameraPreviewThread"
    private const val RECORDING_THREAD_NAME = "CameraRecordingThread"
    private const val FILTER_THREAD_NAME = "CameraFilterThread"

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var previewThread: HandlerThread? = null
    private var previewHandler: Handler? = null
    private var recordingThread: HandlerThread? = null
    private var recordingHandler: Handler? = null
    private var filterThread: HandlerThread? = null
    private var filterHandler: Handler? = null
    private var executorService: ExecutorService? = null
    private val errorUtils = ErrorUtils()

    /**
     * Initialize all threads and handlers
     */
    fun initialize() {
        try {
            // Initialize background thread
            backgroundThread = HandlerThread(BACKGROUND_THREAD_NAME).apply {
                start()
            }
            backgroundHandler = Handler(backgroundThread!!.looper)

            // Initialize preview thread
            previewThread = HandlerThread(PREVIEW_THREAD_NAME).apply {
                start()
            }
            previewHandler = Handler(previewThread!!.looper)

            // Initialize recording thread
            recordingThread = HandlerThread(RECORDING_THREAD_NAME).apply {
                start()
            }
            recordingHandler = Handler(recordingThread!!.looper)

            // Initialize filter thread
            filterThread = HandlerThread(FILTER_THREAD_NAME).apply {
                start()
            }
            filterHandler = Handler(filterThread!!.looper)

            // Initialize executor service for general tasks
            executorService = Executors.newFixedThreadPool(4)
        } catch (e: Exception) {
            errorUtils.logError("ThreadUtils", "Failed to initialize threads", e)
            release()
        }
    }

    /**
     * Get background handler
     */
    fun getBackgroundHandler(): Handler {
        return backgroundHandler ?: throw IllegalStateException("Background handler not initialized")
    }

    /**
     * Get preview handler
     */
    fun getPreviewHandler(): Handler {
        return previewHandler ?: throw IllegalStateException("Preview handler not initialized")
    }

    /**
     * Get recording handler
     */
    fun getRecordingHandler(): Handler {
        return recordingHandler ?: throw IllegalStateException("Recording handler not initialized")
    }

    /**
     * Get filter handler
     */
    fun getFilterHandler(): Handler {
        return filterHandler ?: throw IllegalStateException("Filter handler not initialized")
    }

    /**
     * Execute task on background thread
     */
    fun executeOnBackground(runnable: Runnable) {
        backgroundHandler?.post(runnable)
            ?: throw IllegalStateException("Background handler not initialized")
    }

    /**
     * Execute task on preview thread
     */
    fun executeOnPreview(runnable: Runnable) {
        previewHandler?.post(runnable)
            ?: throw IllegalStateException("Preview handler not initialized")
    }

    /**
     * Execute task on recording thread
     */
    fun executeOnRecording(runnable: Runnable) {
        recordingHandler?.post(runnable)
            ?: throw IllegalStateException("Recording handler not initialized")
    }

    /**
     * Execute task on filter thread
     */
    fun executeOnFilter(runnable: Runnable) {
        filterHandler?.post(runnable)
            ?: throw IllegalStateException("Filter handler not initialized")
    }

    /**
     * Execute task on executor service
     */
    fun execute(runnable: Runnable) {
        executorService?.execute(runnable)
            ?: throw IllegalStateException("Executor service not initialized")
    }

    /**
     * Check if current thread is main thread
     */
    fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    /**
     * Release all threads and handlers
     */
    fun release() {
        try {
            // Release background thread
            backgroundHandler?.removeCallbacksAndMessages(null)
            backgroundThread?.quitSafely()
            backgroundThread?.join(CameraConstants.THREAD_TIMEOUT_MS)
            backgroundHandler = null
            backgroundThread = null

            // Release preview thread
            previewHandler?.removeCallbacksAndMessages(null)
            previewThread?.quitSafely()
            previewThread?.join(CameraConstants.THREAD_TIMEOUT_MS)
            previewHandler = null
            previewThread = null

            // Release recording thread
            recordingHandler?.removeCallbacksAndMessages(null)
            recordingThread?.quitSafely()
            recordingThread?.join(CameraConstants.THREAD_TIMEOUT_MS)
            recordingHandler = null
            recordingThread = null

            // Release filter thread
            filterHandler?.removeCallbacksAndMessages(null)
            filterThread?.quitSafely()
            filterThread?.join(CameraConstants.THREAD_TIMEOUT_MS)
            filterHandler = null
            filterThread = null

            // Release executor service
            executorService?.shutdown()
            executorService?.awaitTermination(CameraConstants.THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            executorService = null
        } catch (e: Exception) {
            errorUtils.logError("ThreadUtils", "Failed to release threads", e)
        }
    }
} 