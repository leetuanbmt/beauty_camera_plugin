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
import kotlin.math.min

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
    private val textureMatrix = FloatArray(16)

    // Handles cho shader program
    private var posAttribHandle: Int = 0
    private var texCoordAttribHandle: Int = 0
    private var textureMatrixHandle: Int = 0
    private var sTextureHandle: Int = 0 // Handle for the sampler
    private var uLandmarksHandle: Int = -1
    private var uLandmarkCountHandle: Int = -1

    // Đồng bộ hóa thread
    private val startLock = Object()
    private var isReady = false

    // Biến lưu trữ dữ liệu landmark từ BeautyCameraPlugin
    private var faceLandmarks: List<FaceLandmark>? = null

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

        // Dữ liệu texture coordinates (Y swapped to fix camera bị che)
        val texCoordData = floatArrayOf(
            0.0f, 1.0f, // bottom left
            1.0f, 1.0f, // bottom right
            0.0f, 0.0f, // top left
            1.0f, 0.0f  // top right
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
        
        handler.post {
            Log.d(TAG, "OpenGL thread started. Initializing EGL.")
            eglCore = EglCore()
            eglCore.init(null)

            val pbufferSurface = eglCore.createPbufferSurface(1, 1)
            eglCore.makeCurrent(pbufferSurface)

            // Tạo texture cho camera input
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            GlUtil.checkGlError("glGenTextures")
            textureId = textures[0]
            Log.d(TAG, "Generated texture ID: $textureId")

            eglCore.releaseSurface(pbufferSurface)
            eglCore.makeNothingCurrent()

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

            cameraInputSurfaceTexture = SurfaceTexture(textureId)
            cameraResolution?.let {
                Log.d(TAG, "Applying initial camera resolution to SurfaceTexture: ${it.width}x${it.height}")
                cameraInputSurfaceTexture.setDefaultBufferSize(it.width, it.height)
            }
            cameraInputSurfaceTexture.setOnFrameAvailableListener(this)
            cameraInputSurface = Surface(cameraInputSurfaceTexture)
            Log.d(TAG, "Created input surface and texture.")

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
            outputSurface = surface
            outputEglSurface = eglCore.createWindowSurface(surface)
            Log.d(TAG, "Created EGL window surface.")
        }
    }

    fun setInputSurfaceBufferSize(width: Int, height: Int) {
        cameraInputSurfaceTexture.setDefaultBufferSize(width, height)
        Log.d(TAG, "Set camera input surface buffer size: ${width}x${height}")
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        // Log.v(TAG, "New frame available") // Verbose log, can be spammy
        handler.post {
            drawFrame()
        }
    }

    private fun checkGlError(msg: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$msg: GL error = 0x${Integer.toHexString(error)}")
        }
    }

    private fun drawFrame() {
        val output = outputEglSurface
        if (output == null) {
            Log.w(TAG, "drawFrame called but outputEglSurface is null")
            return
        }
        eglCore.makeCurrent(output)
        checkGlError("After makeCurrent")
        try {
            cameraInputSurfaceTexture.updateTexImage()
            checkGlError("After updateTexImage")
            cameraInputSurfaceTexture.getTransformMatrix(textureMatrix)
            checkGlError("After getTransformMatrix")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating texture image", e)
            return
        }
        Log.d(TAG, "Drawing frame with textureId: $textureId, program: $program")
        if (program == 0) {
            Log.d(TAG, "Creating GL program")
            val vertexShader = context.assets.open("vertex_shader.glsl").bufferedReader().use { it.readText() }
            val fragmentShader = context.assets.open("fragment_shader.glsl").bufferedReader().use { it.readText() }
            program = GlUtil.createProgram(vertexShader, fragmentShader)
            Log.d(TAG, "GL program created, ID: $program")
            posAttribHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texCoordAttribHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
            textureMatrixHandle = GLES20.glGetUniformLocation(program, "uTextureMatrix")
            sTextureHandle = GLES20.glGetUniformLocation(program, "sTexture")
            uLandmarksHandle = GLES20.glGetUniformLocation(program, "uLandmarks")
            uLandmarkCountHandle = GLES20.glGetUniformLocation(program, "uLandmarkCount")
            checkGlError("After shader program creation")
        }

        // Lấy kích thước surface động để set viewport
        val widthArray = IntArray(1)
        val heightArray = IntArray(1)
        eglCore.querySurface(output, EGL14.EGL_WIDTH, widthArray, 0)
        eglCore.querySurface(output, EGL14.EGL_HEIGHT, heightArray, 0)
        val width = widthArray[0]
        val height = heightArray[0]

        // Bỏ qua frame nếu surface chưa có kích thước
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "Skipping drawFrame, invalid surface dimensions: ${width}x${height}")
            return
        }

        GLES20.glUseProgram(program)
        checkGlError("After glUseProgram")
        GLES20.glViewport(0, 0, width, height)
        checkGlError("After glViewport")
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        checkGlError("After glClear")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        checkGlError("After glActiveTexture")
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        checkGlError("After glBindTexture")
        GLES20.glUniform1i(sTextureHandle, 0)
        checkGlError("After glUniform1i sTextureHandle")
        GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, textureMatrix, 0)
        checkGlError("After glUniformMatrix4fv textureMatrixHandle")
        GLES20.glEnableVertexAttribArray(posAttribHandle)
        checkGlError("After glEnableVertexAttribArray posAttribHandle")
        GLES20.glVertexAttribPointer(posAttribHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)
        checkGlError("After glVertexAttribPointer posAttribHandle")
        GLES20.glEnableVertexAttribArray(texCoordAttribHandle)
        checkGlError("After glEnableVertexAttribArray texCoordAttribHandle")
        GLES20.glVertexAttribPointer(texCoordAttribHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)
        checkGlError("After glVertexAttribPointer texCoordAttribHandle")

        // Truyền landmark vào shader nếu có
        faceLandmarks?.let { landmarks ->
            val count = min(landmarks.size, 468)
            val landmarkArray = FloatArray(count * 2)
            for (i in 0 until count) {
                // Chuẩn hóa về hệ tọa độ texture (0..1)
                landmarkArray[i * 2] = landmarks[i].x.toFloat()
                landmarkArray[i * 2 + 1] = landmarks[i].y.toFloat()
            }
            GLES20.glUniform2fv(uLandmarksHandle, count, landmarkArray, 0)
            checkGlError("After glUniform2fv uLandmarksHandle")
            GLES20.glUniform1i(uLandmarkCountHandle, count)
            checkGlError("After glUniform1i uLandmarkCountHandle")
        } ?: run {
            GLES20.glUniform1i(uLandmarkCountHandle, 0)
            checkGlError("After glUniform1i uLandmarkCountHandle (no landmarks)")
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("After glDrawArrays")
        GLES20.glDisableVertexAttribArray(posAttribHandle)
        GLES20.glDisableVertexAttribArray(texCoordAttribHandle)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glUseProgram(0)
        Log.d(TAG, "Viewport = ${width}x${height}, textureMatrix=${textureMatrix.contentToString()}")
        eglCore.swapBuffers(output)
        checkGlError("After swapBuffers")
        Log.d(TAG, "Frame drawn and buffers swapped.")
    }

    fun setFaceLandmarks(landmarks: List<FaceLandmark>?) {
        this.faceLandmarks = landmarks
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
}
