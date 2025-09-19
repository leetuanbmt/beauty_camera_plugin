package com.beauty.camera_plugin.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface

/**
 * EGLManager - Manages EGL context for Flutter surface rendering
 * 
 * SINGLE RESPONSIBILITY:
 * - Create and manage EGL context
 * - Handle surface creation and binding
 * - Provide OpenGL context for rendering to Flutter surface
 * 
 * Based on OpenGLESPro EGL patterns
 */
class EGLManager {
    companion object {
        private const val TAG = "EGLManager"
    }
    
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    private var eglConfig: EGLConfig? = null
    
    private var surface: Surface? = null
    private var isInitialized = false
    
    fun initialize(surface: Surface): Boolean {
        return try {
            Log.d(TAG, "Initializing EGL Manager")
            
            this.surface = surface
            
            // Get default display
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                Log.e(TAG, "Failed to get EGL display")
                return false
            }
            
            // Initialize EGL
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                Log.e(TAG, "Failed to initialize EGL")
                return false
            }
            
            Log.d(TAG, "EGL initialized: version ${version[0]}.${version[1]}")
            
            // Choose config
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            
            if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, configs.size, numConfigs, 0)) {
                Log.e(TAG, "Failed to choose EGL config")
                return false
            }
            
            eglConfig = configs[0]
            Log.d(TAG, "EGL config chosen")
            
            // Create context
            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                Log.e(TAG, "Failed to create EGL context")
                return false
            }
            
            Log.d(TAG, "EGL context created")
            
            // Create surface - CRITICAL: Must create window surface for Flutter
            Log.d(TAG, "Creating EGL window surface for Flutter surface")
            
            // Try different surface attributes for Flutter compatibility
            val surfaceAttribs = intArrayOf(
                EGL14.EGL_RENDER_BUFFER, EGL14.EGL_BACK_BUFFER,
                EGL14.EGL_NONE
            )
            
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                val error = EGL14.eglGetError()
                Log.w(TAG, "Failed to create EGL window surface with back buffer, error: $error")
                
                // Try with single buffer for Flutter compatibility
                val surfaceAttribs2 = intArrayOf(
                    EGL14.EGL_RENDER_BUFFER, EGL14.EGL_SINGLE_BUFFER,
                    EGL14.EGL_NONE
                )
                
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs2, 0)
                if (eglSurface == EGL14.EGL_NO_SURFACE) {
                    val error2 = EGL14.eglGetError()
                    Log.w(TAG, "Failed to create EGL window surface with single buffer, error: $error2")
                    
                    // Try with no attributes (default)
                    eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, null, 0)
                    if (eglSurface == EGL14.EGL_NO_SURFACE) {
                        val error3 = EGL14.eglGetError()
                        Log.e(TAG, "Failed to create EGL window surface with no attributes, error: $error3")
                        Log.e(TAG, "CRITICAL: Cannot create window surface for Flutter - this will cause 'Invalid external texture'")
                        return false
                    } else {
                        Log.d(TAG, "Created EGL window surface with no attributes successfully")
                    }
                } else {
                    Log.d(TAG, "Created EGL window surface with single buffer successfully")
                }
            } else {
                Log.d(TAG, "Created EGL window surface with back buffer successfully")
            }
            
            Log.d(TAG, "EGL window surface created successfully - OpenGL will be visible on Flutter")
            
            Log.d(TAG, "EGL surface created")
            
            // Make current
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                val makeCurrentError = EGL14.eglGetError()
                Log.e(TAG, "Failed to make EGL context current, error: $makeCurrentError")
                return false
            }
            
            Log.d(TAG, "EGL context made current successfully")
            
            isInitialized = true
            Log.d(TAG, "EGL Manager initialized successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EGL Manager", e)
            false
        }
    }
    
    fun makeCurrent(): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "EGL Manager not initialized, cannot make current")
            return false
        }
        
        // Check if context is already current to avoid unnecessary calls
        val currentDisplay = EGL14.eglGetCurrentDisplay()
        val currentContext = EGL14.eglGetCurrentContext()
        
        if (currentDisplay == eglDisplay && currentContext == eglContext) {
            Log.d(TAG, "EGL context already current, skipping makeCurrent")
            return true
        }
        
        val result = EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        if (result) {
            Log.d(TAG, "EGL context made current successfully")
        } else {
            val error = EGL14.eglGetError()
            Log.e(TAG, "Failed to make EGL context current, error: $error")
            
            // Try to recover by reinitializing if context was lost
            if (error == EGL14.EGL_CONTEXT_LOST) {
                Log.w(TAG, "EGL context lost, attempting recovery...")
                // Note: In a real app, you might want to reinitialize the entire EGL setup
                // For now, we'll just return false and let the caller handle it
            }
        }
        return result
    }
    
    fun swapBuffers(): Boolean {
        if (!isInitialized) return false
        
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun isInitialized(): Boolean = isInitialized
    
    fun dispose() {
        Log.d(TAG, "Disposing EGL Manager")
        
        try {
            if (isInitialized) {
                // Make no context current
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                
                // Destroy surface
                eglSurface?.let { EGL14.eglDestroySurface(eglDisplay, it) }
                
                // Destroy context
                eglContext?.let { EGL14.eglDestroyContext(eglDisplay, it) }
                
                // Terminate display
                EGL14.eglTerminate(eglDisplay)
                
                eglDisplay = null
                eglContext = null
                eglSurface = null
                eglConfig = null
                surface = null
                
                isInitialized = false
                Log.d(TAG, "EGL Manager disposed successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing EGL Manager", e)
        }
    }
}
