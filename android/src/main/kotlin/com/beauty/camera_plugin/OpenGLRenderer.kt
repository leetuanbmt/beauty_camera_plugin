// cSpell:disable
package com.beauty.camera_plugin

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


class OpenGLRenderer(private val context: Context) : SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "OpenGLRenderer"

    }

    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private lateinit var eglCore: EglCore
    private lateinit var cameraInputSurfaceTexture: SurfaceTexture
    lateinit var cameraInputSurface: Surface
        private set

    private var program = 0
    private var textureId = 0

    private var outputSurface: Surface? = null
    private var outputEglSurface: EGLSurface? = null

    private var cameraResolution: Size? = null

    // Buffer cho vertices và texture coordinates
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    // Handles cho shader program
    private var posAttribHandle: Int = 0
    private var texCoordAttribHandle: Int = 0
    private var textureMatrixHandle: Int = 0
    private var sTextureHandle: Int = 0 // Handle for the sampler

    // Simplified - no complex filter handles for now

    // Đồng bộ hóa thread
    private val startLock = Object()
    private var isReady = false

    // Simplified - basic rendering only
    private var frameCount: Int = 0

    init {
        Log.d(TAG, "Initializing OpenGLRenderer")
        // Dữ liệu hình chữ nhật phủ kín màn hình
        val vertexData = floatArrayOf(
            -1.0f, -1.0f, // bottom left
            1.0f, -1.0f,  // bottom right
            -1.0f, 1.0f,   // top left
            1.0f, 1.0f    // top right
        )
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertexData).position(0)

        // Dữ liệu texture coordinates (điều chỉnh để fix camera xoay ngược)
        val texCoordData = floatArrayOf(
            0.0f, 0.0f, // bottom left
            1.0f, 0.0f, // bottom right
            0.0f, 1.0f, // top left
            1.0f, 1.0f  // top right
        )
        texCoordBuffer = ByteBuffer.allocateDirect(texCoordData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        texCoordBuffer.put(texCoordData).position(0)
    }

    fun setCameraResolution(size: Size) {
        Log.d(TAG, "Setting camera resolution hint to ${size.width}x${size.height}")
        this.cameraResolution = size
        // The surface texture might already be created, so we try to set it.
        if (::cameraInputSurfaceTexture.isInitialized) {
            cameraInputSurfaceTexture.setDefaultBufferSize(size.width, size.height)
        }
    }

    fun waitUntilReady() {
        synchronized(startLock) {
            while (!isReady) {
                try {
                    Log.d(TAG, "Waiting for renderer to be ready...")
                    startLock.wait()
                } catch (e: InterruptedException) {
                    Log.w(TAG, "waitUntilReady was interrupted", e)
                }
            }
        }
        Log.d(TAG, "Renderer is ready.")
    }

    fun start() {
        Log.d(TAG, "Starting OpenGLRenderer thread")
        handlerThread = HandlerThread("OpenGLRenderer")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        Log.d(TAG, "OpenGLRenderer thread started successfully")
        
        handler.post {
            Log.d(TAG, "OpenGL thread started. Initializing EGL.")
            eglCore = EglCore()
            eglCore.init(null)
            Log.d(TAG, "EGL core initialized successfully")

            val pbufferSurface = eglCore.createPbufferSurface(1, 1)
            eglCore.makeCurrent(pbufferSurface)
            Log.d(TAG, "EGL context made current")

            // Tạo texture cho camera input
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            GlUtil.checkGlError("glGenTextures")
            textureId = textures[0]
            Log.d(TAG, "Generated texture ID: $textureId")

            Log.d(TAG, "Setting up texture parameters...")
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            Log.d(TAG, "Texture parameters set successfully")

            // Initialize shader program while EGL context is still active
            Log.d(TAG, "About to initialize shader program...")
            initializeShaderProgram()
            Log.d(TAG, "Shader program initialization completed")

            eglCore.releaseSurface(pbufferSurface)
            eglCore.makeNothingCurrent()

            cameraInputSurfaceTexture = SurfaceTexture(textureId)
            Log.d(TAG, "Created SurfaceTexture with textureId: $textureId")
            cameraResolution?.let {
                Log.d(TAG, "Applying initial camera resolution to SurfaceTexture: ${it.width}x${it.height}")
                cameraInputSurfaceTexture.setDefaultBufferSize(it.width, it.height)
            }
            cameraInputSurfaceTexture.setOnFrameAvailableListener(this)
            cameraInputSurface = Surface(cameraInputSurfaceTexture)
            Log.d(TAG, "Created input surface and texture. Surface: $cameraInputSurface")
            Log.d(TAG, "Surface is valid: ${cameraInputSurface.isValid}")

            synchronized(startLock) {
                isReady = true
                startLock.notify()
                Log.d(TAG, "Renderer is now ready and notified.")
            }
        }
    }

    fun setOutputSurface(surface: Surface) {
        handler.post {
            Log.d(TAG, "Setting output surface: $surface")
            Log.d(TAG, "Output surface is valid: ${surface.isValid}")
            outputSurface = surface
            try {
                outputEglSurface = eglCore.createWindowSurface(surface)
                Log.d(TAG, "Created EGL window surface: $outputEglSurface")
                Log.d(TAG, "EGL window surface is valid: ${outputEglSurface != null}")
                
                // Check surface size after creation
                val currentSurface = outputEglSurface
                if (currentSurface != null) {
                    val widthArray = IntArray(1)
                    val heightArray = IntArray(1)
                    eglCore.querySurface(currentSurface, EGL14.EGL_WIDTH, widthArray, 0)
                    eglCore.querySurface(currentSurface, EGL14.EGL_HEIGHT, heightArray, 0)
                    val width = widthArray[0]
                    val height = heightArray[0]
                    Log.d(TAG, "Surface size after creation: ${width}x${height}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating EGL window surface", e)
            }
        }
    }


    private fun drawFrame(textureMatrix: FloatArray? = null) {
        val output = outputEglSurface ?: return
        
        try {
            // EGL context should already be current from renderFrame
            
            // Get surface dimensions
            val widthArray = IntArray(1)
            val heightArray = IntArray(1)
            eglCore.querySurface(output, EGL14.EGL_WIDTH, widthArray, 0)
            eglCore.querySurface(output, EGL14.EGL_HEIGHT, heightArray, 0)
            val width = widthArray[0]
            val height = heightArray[0]

            // Skip if invalid dimensions
            if (width <= 0 || height <= 0) return

            // Use provided texture matrix (should be passed from onFrameAvailable)
            val matrixToUse = textureMatrix ?: FloatArray(16).apply { 
                // Identity matrix as fallback
                this[0] = 1f; this[5] = 1f; this[10] = 1f; this[15] = 1f
            }

            // Setup OpenGL state
            GLES20.glUseProgram(program)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            // Bind texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(sTextureHandle, 0)

            // Set texture matrix if available
            if (textureMatrixHandle >= 0) {
                GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, matrixToUse, 0)
            }

            // Setup vertex attributes
            GLES20.glEnableVertexAttribArray(posAttribHandle)
            GLES20.glVertexAttribPointer(posAttribHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)
            GLES20.glEnableVertexAttribArray(texCoordAttribHandle)
            GLES20.glVertexAttribPointer(texCoordAttribHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)

            // Draw camera frame
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // Cleanup
            GLES20.glDisableVertexAttribArray(posAttribHandle)
            GLES20.glDisableVertexAttribArray(texCoordAttribHandle)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            GLES20.glUseProgram(0)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in drawFrame", e)
        }
    }

    // Simplified methods - no filter logic for now
    fun setFilterEnabled(enabled: Boolean) {
        Log.d(TAG, "Filter functionality disabled - using simple camera rendering $enabled")
    }


    fun setFaceLandmarks(landmarks: List<FaceLandmark>?) {
        // Face landmarks disabled for simple rendering
        Log.d(TAG, "Face landmarks functionality disabled - using simple camera rendering")
    }

    private fun initializeShaderProgram() {
        try {
            Log.d(TAG, "=== INITIALIZING SHADER PROGRAM ===")
            
            // Try multiple fallback approaches
            program = createShaderProgramWithFallbacks()
            
            if (program == 0) {
                throw RuntimeException("Failed to create any shader program")
            }
            
            Log.d(TAG, "Shader program created successfully (ID: $program)")
            
            // Get attribute handles
            posAttribHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texCoordAttribHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
            
            // Get uniform handles (some may not exist in ultra basic shader)
            textureMatrixHandle = GLES20.glGetUniformLocation(program, "uTextureMatrix")
            sTextureHandle = GLES20.glGetUniformLocation(program, "sTexture")
            
            Log.d(TAG, "Shader program initialized successfully")
            Log.d(TAG, "Program: $program, posAttrib: $posAttribHandle, texCoord: $texCoordAttribHandle")
            Log.d(TAG, "Texture uniforms - matrix: $textureMatrixHandle, sampler: $sTextureHandle")
            
            // Validate critical handles (attributes must exist)
            if (posAttribHandle < 0 || texCoordAttribHandle < 0) {
                throw RuntimeException("Failed to get critical attribute handles")
            }
            
            // Texture sampler must exist
            if (sTextureHandle < 0) {
                throw RuntimeException("Failed to get texture sampler handle")
            }
            
            // Matrix handle is optional for ultra basic shader
            if (textureMatrixHandle < 0) {
                Log.w(TAG, "Texture matrix handle not found - using ultra basic shader mode")
            }
            
            Log.d(TAG, "=== SIMPLE SHADER PROGRAM INITIALIZED SUCCESSFULLY ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing shader program", e)
            throw e
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        // Trigger rendering when new camera frame is available
        handler.post {
            if (outputEglSurface != null && program != 0) {
                try {
                    Log.v(TAG, "New frame available, starting rendering...")
                    Log.v(TAG, "Program ID: $program, Output surface: $outputEglSurface")
                    
                    // Make EGL context current before updating texture
                    eglCore.makeCurrent(outputEglSurface!!)
                    Log.v(TAG, "EGL context made current")
                    
                    // Update texture with latest camera frame
                    cameraInputSurfaceTexture.updateTexImage()
                    Log.v(TAG, "Texture updated successfully")
                    
                    // Get texture transformation matrix
                    val textureMatrix = FloatArray(16)
                    cameraInputSurfaceTexture.getTransformMatrix(textureMatrix)
                    
                    // Render frame
                    renderFrame(textureMatrix)
                    Log.v(TAG, "Frame rendered successfully")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onFrameAvailable", e)
                    Log.e(TAG, "Program: $program, OutputSurface: $outputEglSurface")
                    Log.e(TAG, "TextureId: $textureId")
                }
            } else {
                if (outputEglSurface == null) {
                    Log.w(TAG, "Frame available but no output surface set")
                }
                if (program == 0) {
                    Log.w(TAG, "Frame available but shader program not initialized")
                }
            }
        }
    }

    private fun renderFrame(textureMatrix: FloatArray) {
        val outputSurface = outputEglSurface ?: return
        
        try {
            // EGL context should already be current from onFrameAvailable
            // Call existing drawFrame method
            drawFrame(textureMatrix)
            
            // Swap buffers to display
            eglCore.swapBuffers(outputSurface)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering frame", e)
        }
    }

    fun release() {
        Log.d(TAG, "Releasing OpenGLRenderer")
        if (::handler.isInitialized) {
            handler.post {
                Log.d(TAG, "Releasing EGL core")
                eglCore.release()
            }
        }
        if (::handlerThread.isInitialized) {
            handlerThread.quitSafely()
            Log.d(TAG, "OpenGL thread quit")
        }
    }

    private fun createShaderProgramWithFallbacks(): Int {
        // Try 1: Load from assets
        try {
            Log.d(TAG, "Trying to create shaders from assets...")
            val vertexSource = context.assets.open("vertex_shader.glsl").bufferedReader().use { it.readText() }
            val fragmentSource = context.assets.open("fragment_shader.glsl").bufferedReader().use { it.readText() }
            
            Log.d(TAG, "Vertex shader source loaded:\n$vertexSource")
            Log.d(TAG, "Fragment shader source loaded:\n$fragmentSource")
            
            val program = GlUtil.createProgram(vertexSource, fragmentSource)
            if (program != 0) {
                Log.d(TAG, "Shader program created successfully from assets")
                return program
            } else {
                Log.e(TAG, "Failed to create shader program from assets")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while creating shaders from assets", e)
        }
        return 0;
    }

}
