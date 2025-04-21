package com.beauty.camera_plugin.camera.filters

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import com.beauty.camera_plugin.camera.utils.CameraConstants
import com.beauty.camera_plugin.camera.utils.CameraFilterException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Manages the application of filters to camera preview and captured images.
 */
class FilterManager {
    private var currentFilter: Filter? = null
    private var filterProgram: Int = 0
    private var textureId: Int = -1
    private var surfaceTexture: SurfaceTexture? = null
    private var transformMatrix = FloatArray(16)
    private var vertexBuffer: FloatBuffer? = null
    private var texCoordBuffer: FloatBuffer? = null

    init {
        Matrix.setIdentityM(transformMatrix, 0)
        initializeBuffers()
    }

    private fun initializeBuffers() {
        // Vertex coordinates for a full screen quad
        val vertices = floatArrayOf(
            -1.0f, -1.0f, 0.0f,
            1.0f, -1.0f, 0.0f,
            -1.0f, 1.0f, 0.0f,
            1.0f, 1.0f, 0.0f
        )

        // Texture coordinates
        val texCoords = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(texCoords)
                position(0)
            }
    }

    fun setFilter(filter: Filter) {
        currentFilter = filter
        filterProgram = createProgram(filter.vertexShader, filter.fragmentShader)
        if (filterProgram == 0) {
            throw CameraFilterException("Failed to create filter program")
        }
    }

    fun setSurfaceTexture(texture: SurfaceTexture) {
        surfaceTexture = texture
        textureId = createTexture()
        if (textureId == -1) {
            throw CameraFilterException("Failed to create texture")
        }
    }

    fun updateTransformMatrix(matrix: FloatArray) {
        System.arraycopy(matrix, 0, transformMatrix, 0, matrix.size)
    }

    fun draw() {
        if (filterProgram == 0 || textureId == -1) return

        GLES20.glUseProgram(filterProgram)
        checkGlError("glUseProgram")

        // Set vertex attributes
        val positionHandle = GLES20.glGetAttribLocation(filterProgram, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(filterProgram, "aTextureCoord")
        val transformHandle = GLES20.glGetUniformLocation(filterProgram, "uTransform")

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        checkGlError("glVertexAttribPointer position")

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        checkGlError("glVertexAttribPointer texCoord")

        GLES20.glUniformMatrix4fv(transformHandle, 1, false, transformMatrix, 0)
        checkGlError("glUniformMatrix4fv")

        // Set texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        checkGlError("glBindTexture")

        val samplerHandle = GLES20.glGetUniformLocation(filterProgram, "sTexture")
        GLES20.glUniform1i(samplerHandle, 0)
        checkGlError("glUniform1i")

        // Set filter parameters
        currentFilter?.setParameters(filterProgram)

        // Draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")

        // Cleanup
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        checkGlError("glGenTextures")

        val textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        checkGlError("glBindTexture")

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkGlError("glTexParameteri")

        return textureId
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0

        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        var program = GLES20.glCreateProgram()
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                val error = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader != 0) {
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)

            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val error = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                return 0
            }
        }
        return shader
    }

    private fun checkGlError(op: String) {
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
            throw CameraFilterException("$op: glError $error")
        }
    }

    fun release() {
        if (textureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = -1
        }
        if (filterProgram != 0) {
            GLES20.glDeleteProgram(filterProgram)
            filterProgram = 0
        }
        currentFilter = null
        surfaceTexture = null
    }
} 