package com.beauty.camera_plugin.view

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.beauty.camera_plugin.filters.CameraFilterManager
import com.beauty.camera_plugin.models.CameraFilterMode
import com.beauty.camera_plugin.repository.CameraRepository
/**
 * Custom camera view with GPUImage filtering
 */
@SuppressLint("ViewConstructor")
class CameraView(
    context: Context,
    private val repository: CameraRepository,
    private val lifecycleOwner: LifecycleOwner
) : FrameLayout(context) {
    
    private var surface: Surface? = null
    private var bufferSize: Size? = null
    private val tag = "CameraView"
    private var scaleType = ScaleType.CENTER_CROP
    private val cameraFilterManager: CameraFilterManager = CameraFilterManager.getInstance(context)

    // Create GLSurfaceView
    private var glSurfaceView: GLSurfaceView = GLSurfaceView(context)

    enum class ScaleType {
        CENTER_CROP, // Fill the view while maintaining aspect ratio, cropping if necessary
        CENTER_INSIDE // Fit entire preview inside view, letterboxing if necessary
    }

    init {

        // Add GLSurfaceView to layout
        addView(glSurfaceView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ))
        
        // Pass GLSurfaceView to filter manager
        cameraFilterManager.setGLSurfaceView(glSurfaceView)
    }

    /**
     * Set up camera with the provided Surface from Flutter
     */
    fun setExternalTexture(surface: Surface, width: Int, height: Int) {
        try {
            // Release existing surface if any
            release()
            
            // Store the provided surface
            this.surface = surface
            
            // Set up filter manager
            cameraFilterManager.setupSurfaceTexture(width, height)
            
            // Start camera preview
            startCamera()
            
            Log.d(tag, "External surface setup complete: ${width}x${height}")
        } catch (e: Exception) {
            Log.e(tag, "Error setting up external surface", e)
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bufferSize = Size(w, h)
        updatePreviewSize(w, h)
    }

    /**
     * Set filter for camera preview
     */
    fun setFilter(mode: CameraFilterMode, level: Double) {
        try {
            cameraFilterManager.setFilter(mode, level)
            Log.d(tag, "Filter set: mode=$mode, level=$level")
        } catch (e: Exception) {
            Log.e(tag, "Error setting filter", e)
        }
    }

    /**
     * Set scale type for preview
     */
    fun setScaleType(type: ScaleType) {
        if (scaleType != type) {
            scaleType = type
            bufferSize?.let { size ->
                updatePreviewSize(size.width, size.height)
            }
        }
    }

    private fun updatePreviewSize(width: Int, height: Int) {
        try {
            // Update filter manager
            cameraFilterManager.updatePreviewSize(width, height)
            
            Log.d(tag, "Preview size updated: ${width}x${height}")
        } catch (e: Exception) {
            Log.e(tag, "Error updating preview size", e)
        }
    }

    /**
     * Start the camera preview
     */
    private fun startCamera() {
        surface?.let { surface ->
            repository.startCamera(lifecycleOwner, surface)
            Log.d(tag, "Camera started")
        }
    }

    fun release() {
        try {
            surface?.release()
            surface = null
            
            Log.d(tag, "Resources released")
        } catch (e: Exception) {
            Log.e(tag, "Error releasing resources", e)
        }
    }
}