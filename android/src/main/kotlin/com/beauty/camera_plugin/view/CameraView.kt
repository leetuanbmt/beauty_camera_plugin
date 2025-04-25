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
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.util.Rotation
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
    
    private var glSurfaceView: GLSurfaceView

    enum class ScaleType {
        CENTER_CROP, // Fill the view while maintaining aspect ratio, cropping if necessary
        CENTER_INSIDE // Fit entire preview inside view, letterboxing if necessary
    }

    init {
        // Create GLSurfaceView
        glSurfaceView = GLSurfaceView(context)
        
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
     * Configure the transform matrix for view to maintain aspect ratio
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (viewWidth == 0 || viewHeight == 0) return
        
        val previewSize = repository.getPreviewResolution()
        val rotation = repository.getDisplayRotation()
        val isFrontCamera = repository.isFrontCamera()

        // Set GPUImage rotation and scale type
        cameraFilterManager.setRotation(when (rotation) {
            Surface.ROTATION_0 -> Rotation.NORMAL
            Surface.ROTATION_90 -> Rotation.ROTATION_90
            Surface.ROTATION_180 -> Rotation.ROTATION_180
            Surface.ROTATION_270 -> Rotation.ROTATION_270
            else -> Rotation.NORMAL
        })
        
        cameraFilterManager.setScaleType(if (scaleType == ScaleType.CENTER_CROP) {
            GPUImage.ScaleType.CENTER_CROP
        } else {
            GPUImage.ScaleType.CENTER_INSIDE
        })

        // Request render after transform update
        glSurfaceView.requestRender()

        Log.d(tag, """
            Transform configured:
            - View size: $viewWidth x $viewHeight
            - Preview size: ${previewSize.first} x ${previewSize.second}
            - Rotation: $rotation
            - Scale type: $scaleType
            - Front camera: $isFrontCamera
        """.trimIndent())
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