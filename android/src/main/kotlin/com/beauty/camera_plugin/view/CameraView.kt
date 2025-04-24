 package com.beauty.camera_plugin.view

 import android.annotation.SuppressLint
 import android.util.Log
 import android.content.Context
 import android.graphics.Matrix
 import android.graphics.SurfaceTexture
 import android.util.Size
 import android.view.Surface
 import android.view.TextureView
 import androidx.lifecycle.LifecycleOwner
 import com.beauty.camera_plugin.repository.CameraRepository
 import kotlin.math.max

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

     private  val tag = "CameraRepository"
    
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
      * Configure the transform matrix for TextureView to maintain aspect ratio
      */
     private fun configureTransform(viewWidth: Int, viewHeight: Int) {
         Log.d(tag, "configureTransform viewWidth: $viewWidth, viewHeight: $viewHeight")
         val previewSize = repository.getPreviewResolution()
         val rotation = repository.getDisplayRotation()

         val matrix = Matrix()

         // Compute the center of the view and preview
         val centerX = viewWidth / 2f
         val centerY = viewHeight / 2f

         // Correct preview output to account for display rotation
         val rotationDegrees = when (rotation) {
             Surface.ROTATION_0 -> 0
             Surface.ROTATION_90 -> 90
             Surface.ROTATION_180 -> 180
             Surface.ROTATION_270 -> 270
             else -> 0
         }
         matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

         // Get the preview size
         val (previewWidth, previewHeight) = previewSize
        
         // Calculate the scale factor to maintain aspect ratio while filling the view
         val scale = when (rotationDegrees) {
             90, 270 -> {
                 val scaleX = viewWidth.toFloat() / previewHeight
                 val scaleY = viewHeight.toFloat() / previewWidth
                 max(scaleX, scaleY)
             }
             else -> {
                 val scaleX = viewWidth.toFloat() / previewWidth
                 val scaleY = viewHeight.toFloat() / previewHeight
                 max(scaleX, scaleY)
             }
         }

         // Scale preview maintaining aspect ratio
         matrix.postScale(scale, scale, centerX, centerY)

         // Apply the transform
         setTransform(matrix)
     }
    
     /**
      * Start the camera preview
      */
     private fun startCamera() {
         surface?.let {
             bufferSize?.let { size ->
                 // Set the surface texture buffer size to match the preview size
                 surfaceTexture?.setDefaultBufferSize(size.width, size.height)
             }
             repository.startCamera(lifecycleOwner, it)
         }
     }
 }