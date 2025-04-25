package com.beauty.camera_plugin.view

import android.annotation.SuppressLint
import android.util.Log
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.lifecycle.LifecycleOwner
import com.beauty.camera_plugin.repository.CameraRepository
import kotlin.math.max
import kotlin.math.min

/**
 * Custom TextureView for camera preview with GPUImage filtering
 */
@SuppressLint("ViewConstructor")
class CameraView(
    context: Context,
    private val repository: CameraRepository,
    private val lifecycleOwner: LifecycleOwner
) : TextureView(context), TextureView.SurfaceTextureListener {
    
    private var surface: Surface? = null
    private var bufferSize: Size? = null
    private val tag = "CameraView"
    private var scaleType = ScaleType.CENTER_CROP

    enum class ScaleType {
        CENTER_CROP, // Fill the view while maintaining aspect ratio, cropping if necessary
        CENTER_INSIDE // Fit entire preview inside view, letterboxing if necessary
    }

    // Add property for custom surface texture handling
    var customSurfaceTexture: SurfaceTexture? = null
        set(value) {
            field = value
            if (value != null) {
                onSurfaceTextureAvailable(value, width, height)
            }
        }
    
    init {
        surfaceTextureListener = this
    }
    
    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        bufferSize = Size(width, height)
        surface = Surface(surfaceTexture)
        configureTransform(width, height)
        startCamera()
    }
    
    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        bufferSize = Size(width, height)
        configureTransform(width, height)
        startCamera()
    }
    
    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        surface?.release()
        surface = null
        return true
    }
    
    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // This is called every frame, we don't need to do anything here
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
     * Configure the transform matrix for TextureView to maintain aspect ratio
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
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        // Correct for display rotation
        val rotationDegrees = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        // Rotate if needed
        if (rotationDegrees != 0) {
            bufferRect.offset(-centerX, -centerY)
            matrix.postRotate(rotationDegrees.toFloat(), 0f, 0f)
            matrix.postTranslate(centerX, centerY)
        }

        // Handle front camera mirroring
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f, centerX, centerY)
        }

        // Calculate scaling
        val scale = when (scaleType) {
            ScaleType.CENTER_CROP -> {
                val scaleX = viewWidth.toFloat() / bufferRect.width()
                val scaleY = viewHeight.toFloat() / bufferRect.height()
                max(scaleX, scaleY)
            }
            ScaleType.CENTER_INSIDE -> {
                val scaleX = viewWidth.toFloat() / bufferRect.width()
                val scaleY = viewHeight.toFloat() / bufferRect.height()
                min(scaleX, scaleY)
            }
        }

        // Apply scaling
        bufferRect.offset(-centerX, -centerY)
        matrix.postScale(scale, scale, 0f, 0f)
        matrix.postTranslate(centerX, centerY)

        // Apply final transform
        setTransform(matrix)

        // Log transformation details
        Log.d(tag, """Transform applied: - View size: $viewWidth x $viewHeight - Preview size: ${previewSize.first} x ${previewSize.second} - Rotation: $rotationDegrees° - Scale: $scale - Scale type: $scaleType - Front camera: $isFrontCamera""")
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