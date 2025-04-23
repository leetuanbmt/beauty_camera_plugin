package com.beauty.camera_plugin

import android.graphics.SurfaceTexture
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import io.flutter.view.TextureRegistry
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

/**
 * A handler class for connecting CameraX preview to Flutter textures
 */
class CameraPreviewTextureHandler(
    private val textureRegistry: TextureRegistry,
    private val mainExecutor: Executor,
) : Preview.SurfaceProvider {
    
    private val TAG = "CameraPreviewTextureHandler"
    private var flutterTexture: TextureRegistry.SurfaceTextureEntry? = null
    private var resolution: Size? = null
    private var surfaceRequest: SurfaceRequest? = null
    private var surface: Surface? = null
    
    /**
     * Initialize the texture handler
     * @return The texture ID for the Flutter view
     */
    fun init(): Long {
        Log.d(TAG, "Initializing texture handler")
        // Cleanup any previous resources
        cleanup()
        
        // Create a new texture entry
        flutterTexture = textureRegistry.createSurfaceTexture()
        Log.d(TAG, "Created texture with ID: ${flutterTexture?.id()}")
        
        return flutterTexture?.id() ?: -1
    }
    
    /**
     * Get the texture ID
     * @return The texture ID
     */
    fun getTextureId(): Long {
        return flutterTexture?.id() ?: -1
    }
    
    /**
     * Get the current resolution
     * @return The resolution if set, null otherwise
     */
    fun getResolution(): Size? {
        return resolution
    }
    
    /**
     * Set the resolution
     * @param size The resolution to set
     */
    fun setResolution(size: Size) {
        Log.d(TAG, "Setting resolution to: ${size.width}x${size.height}")
        resolution = size
        
        val surfaceTexture = flutterTexture?.surfaceTexture()
        if (surfaceTexture != null) {
            Log.d(TAG, "Applying buffer size: ${size.width}x${size.height}")
            surfaceTexture.setDefaultBufferSize(size.width, size.height)
        }
    }
    
    /**
     * Called when a surface is requested by CameraX
     * @param request The surface request
     */
    override fun onSurfaceRequested(request: SurfaceRequest) {
        Log.d(TAG, "Surface requested with resolution: ${request.resolution}")
        
        surfaceRequest = request
        resolution = request.resolution
        
        try {
            val texture = flutterTexture
            if (texture == null) {
                Log.e(TAG, "Flutter texture is null when surface requested")
                request.willNotProvideSurface()
                return
            }
            
            val surfaceTexture = texture.surfaceTexture()
            if (surfaceTexture == null) {
                Log.e(TAG, "SurfaceTexture is null when surface requested")
                request.willNotProvideSurface()
                return
            }
            
            Log.d(TAG, "Setting buffer size to: ${resolution?.width}x${resolution?.height}")
            
            // Set the buffer size to match the requested resolution
            surfaceTexture.setDefaultBufferSize(resolution!!.width, resolution!!.height)
            
            // Create a surface from the texture
            if (surface != null) {
                surface!!.release()
            }
            
            surface = Surface(surfaceTexture)
            
            // Validate the surface
            if (surface == null || !surface!!.isValid) {
                Log.e(TAG, "Created surface is null or invalid")
                request.willNotProvideSurface()
                return
            }
            
            Log.d(TAG, "Providing surface to CameraX")
            
            // Register a listener for when the surface is no longer needed
            val surfaceReleaseFuture = request.provideSurface(
                surface!!,
                mainExecutor,
                { result ->
                    when (result.resultCode) {
                        SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY -> 
                            Log.d(TAG, "Surface was used successfully")
                        SurfaceRequest.Result.RESULT_REQUEST_CANCELLED -> 
                            Log.d(TAG, "Surface request was cancelled")
                        SurfaceRequest.Result.RESULT_SURFACE_ALREADY_PROVIDED -> 
                            Log.w(TAG, "Surface was already provided")
                        SurfaceRequest.Result.RESULT_INVALID_SURFACE -> 
                            Log.e(TAG, "The provided surface was invalid")
                        else -> 
                            Log.w(TAG, "Unknown surface result code: ${result.resultCode}")
                    }
                }
            )
            
            // Register a listener for when the surface request is cancelled
            request.addRequestCancellationListener(mainExecutor) {
                Log.d(TAG, "Surface request was cancelled")
                // We don't need to explicitly cancel the future as the cancellation is handled by CameraX
                // Note: Don't release the surface here as it's being used by the Flutter texture
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during surface provision", e)
            request.willNotProvideSurface()
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up texture handler")
        
        try {
            surfaceRequest?.willNotProvideSurface()
            surfaceRequest = null
            
            surface?.release()
            surface = null
            
            flutterTexture?.release()
            flutterTexture = null
            
            resolution = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}