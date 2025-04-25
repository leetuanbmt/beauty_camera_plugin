package com.beauty.camera_plugin.view

import android.annotation.SuppressLint
import android.util.Log
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.util.Size
import android.view.Surface
import android.opengl.GLSurfaceView
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.beauty.camera_plugin.repository.CameraRepository
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.util.Rotation
import com.beauty.camera_plugin.filters.FilterFactory
import com.beauty.camera_plugin.models.CameraFilterMode

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

    // GPUImage components
    private var gpuImage: GPUImage? = null
    private var currentFilter: GPUImageFilter? = null
    private var currentFilterMode = CameraFilterMode.NONE
    private var filterLevel = 0.0
    private var glTextureView: GLSurfaceView? = null

    enum class ScaleType {
        CENTER_CROP, // Fill the view while maintaining aspect ratio, cropping if necessary
        CENTER_INSIDE // Fit entire preview inside view, letterboxing if necessary
    }

    // Add property for custom surface texture handling
    var customSurfaceTexture: SurfaceTexture? = null
        set(value) {
            field = value
            if (value != null) {
                setupSurface(value, width, height)
            }
        }
    
    init {
        // Initialize GPUImage
        gpuImage = GPUImage(context)
        currentFilter = FilterFactory.createFilter(CameraFilterMode.NONE)
        gpuImage?.setFilter(currentFilter)
        
        // Create GLSurfaceView for rendering
        glTextureView = GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
        }
        
        // Add GLSurfaceView to layout
        addView(glTextureView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ))
        
        // Set up GPUImage with GLSurfaceView
        gpuImage?.setGLSurfaceView(glTextureView)
    }
    
     fun setupSurface(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        bufferSize = Size(width, height)
        
        // Create surface for camera preview
        surface?.release()
        surface = Surface(surfaceTexture)
        
        configureTransform(width, height)
        startCamera()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bufferSize = Size(w, h)
        configureTransform(w, h)
        startCamera()
    }

    /**
     * Set filter for camera preview
     */
    fun setFilter(mode: CameraFilterMode, level: Double) {
        try {
            Log.d(tag, "Setting filter: $mode, $level")
            currentFilterMode = mode
            filterLevel = level.coerceIn(0.0, 10.0)
            
            // Clean up old filter
            currentFilter?.let { oldFilter ->
                gpuImage?.deleteImage()
                oldFilter.destroy()
            }
            
            // Create and set new filter
            currentFilter = FilterFactory.createFilter(mode)
            gpuImage?.setFilter(currentFilter)
            
            // Apply filter level
            FilterFactory.applyFilterLevel(currentFilter, filterLevel)
            
            // Request render
            glTextureView?.requestRender()
            
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
                configureTransform(size.width, size.height)
            }
        }
    }

    /**
     * Configure the transform matrix for view to maintain aspect ratio
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        Log.d(tag, "configureTransform viewWidth: $viewWidth, viewHeight: $viewHeight")
        if (viewWidth == 0 || viewHeight == 0) return
        
        val previewSize = repository.getPreviewResolution()
        val rotation = repository.getDisplayRotation()
        val isFrontCamera = repository.isFrontCamera()

        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.first.toFloat(), previewSize.second.toFloat())

        // Calculate aspect ratios
        val viewAspectRatio = viewWidth.toFloat() / viewHeight
        val previewAspectRatio = previewSize.first.toFloat() / previewSize.second

        // Center the preview
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        // Handle rotation
        val rotationDegrees = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        // Calculate scale based on scale type and aspect ratios
        val scale = when (scaleType) {
            ScaleType.CENTER_CROP -> {
                if (viewAspectRatio > previewAspectRatio) {
                    viewWidth.toFloat() / bufferRect.width()
                } else {
                    viewHeight.toFloat() / bufferRect.height()
                }
            }
            ScaleType.CENTER_INSIDE -> {
                if (viewAspectRatio > previewAspectRatio) {
                    viewHeight.toFloat() / bufferRect.height()
                } else {
                    viewWidth.toFloat() / bufferRect.width()
                }
            }
        }

        // Apply transformations in the correct order
        bufferRect.offset(-centerX, -centerY)
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat(), 0f, 0f)
        }
        matrix.postScale(scale, scale)
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f)
        }
        matrix.postTranslate(centerX, centerY)

        // Set GPUImage rotation and scale type based on calculations
        gpuImage?.setRotation(when (rotationDegrees) {
            0 -> Rotation.NORMAL
            90 -> Rotation.ROTATION_90
            180 -> Rotation.ROTATION_180
            270 -> Rotation.ROTATION_270
            else -> Rotation.NORMAL
        })
        gpuImage?.setScaleType(if (scaleType == ScaleType.CENTER_CROP) {
            GPUImage.ScaleType.CENTER_CROP
        } else {
            GPUImage.ScaleType.CENTER_INSIDE
        })

        Log.d(tag, """
            Transform details:
            - View size: $viewWidth x $viewHeight (ratio: $viewAspectRatio)
            - Preview size: ${previewSize.first} x ${previewSize.second} (ratio: $previewAspectRatio)
            - Rotation: $rotationDegrees°
            - Scale: $scale
            - Scale type: $scaleType
            - Front camera: $isFrontCamera
            - Center point: ($centerX, $centerY)
            - Buffer rect: $bufferRect
        """.trimIndent())
    }
    
    /**
     * Start the camera preview
     */
    private fun startCamera() {
        surface?.let {
            bufferSize?.let { size ->
                // Set the surface texture buffer size to match the preview size
                customSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
            }
            repository.startCamera(lifecycleOwner, it)
        }
    }
}