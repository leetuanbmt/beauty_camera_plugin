package com.beauty.camera_plugin

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class OpenGLRenderer(private val context: Context) : SurfaceTexture.OnFrameAvailableListener {

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

    // Buffer cho vertices và texture coordinates
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer
    private val textureMatrix = FloatArray(16)

    // Handles cho shader program
    private var posAttribHandle: Int = 0
    private var texCoordAttribHandle: Int = 0
    private var textureMatrixHandle: Int = 0

    // Đồng bộ hóa thread
    private val startLock = Object()
    private var isReady = false

    init {
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

        // Dữ liệu texture coordinates
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

    fun waitUntilReady() {
        synchronized(startLock) {
            while (!isReady) {
                try {
                    startLock.wait()
                } catch (e: InterruptedException) { /* ignore */ }
            }
        }
    }

    fun start() {
        handlerThread = HandlerThread("OpenGLRenderer")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        
        handler.post {
            eglCore = EglCore()
            eglCore.init(null)

            // Tạo texture cho camera input
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            GlUtil.checkGlError("glGenTextures")
            textureId = textures[0]

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

            cameraInputSurfaceTexture = SurfaceTexture(textureId)
            cameraInputSurfaceTexture.setOnFrameAvailableListener(this)
            cameraInputSurface = Surface(cameraInputSurfaceTexture)

            synchronized(startLock) {
                isReady = true
                startLock.notify()
            }
        }
    }

    fun setOutputSurface(surface: Surface) {
        handler.post {
            outputSurface = surface
            outputEglSurface = eglCore.createWindowSurface(surface)
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        handler.post {
            drawFrame()
        }
    }

    private fun drawFrame() {
        val output = outputEglSurface ?: return
        eglCore.makeCurrent(output)

        cameraInputSurfaceTexture.updateTexImage()
        cameraInputSurfaceTexture.getTransformMatrix(textureMatrix)

        if (program == 0) {
            val vertexShader = context.assets.open("vertex_shader.glsl").bufferedReader().use { it.readText() }
            val fragmentShader = context.assets.open("fragment_shader.glsl").bufferedReader().use { it.readText() }
            program = GlUtil.createProgram(vertexShader, fragmentShader)

            posAttribHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texCoordAttribHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
            textureMatrixHandle = GLES20.glGetUniformLocation(program, "uTextureMatrix")
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
            return
        }

        GLES20.glUseProgram(program)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, textureMatrix, 0)

        GLES20.glEnableVertexAttribArray(posAttribHandle)
        GLES20.glVertexAttribPointer(posAttribHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texCoordAttribHandle)
        GLES20.glVertexAttribPointer(texCoordAttribHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posAttribHandle)
        GLES20.glDisableVertexAttribArray(texCoordAttribHandle)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glUseProgram(0)

        eglCore.swapBuffers(output)
    }

    fun release() {
        if (::handler.isInitialized) {
            handler.post {
                eglCore.release()
            }
        }
        if (::handlerThread.isInitialized) {
            handlerThread.quitSafely()
        }
    }
}