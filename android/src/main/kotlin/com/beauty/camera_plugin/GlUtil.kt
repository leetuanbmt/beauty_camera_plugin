package com.beauty.camera_plugin

import android.opengl.GLES20
import android.util.Log

object GlUtil {
    private const val TAG = "GlUtil"

    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        Log.d(TAG, "Creating shader program...")
        
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) {
            Log.e(TAG, "Failed to load vertex shader")
            return 0
        }
        
        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) {
            Log.e(TAG, "Failed to load fragment shader")
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        var program = GLES20.glCreateProgram()
        checkGlError("glCreateProgram")
        if (program == 0) {
            Log.e(TAG, "Could not create program")
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(pixelShader)
            return 0
        }
        
        Log.d(TAG, "Created program with ID: $program")
        
        GLES20.glAttachShader(program, vertexShader)
        checkGlError("glAttachShader vertex")
        GLES20.glAttachShader(program, pixelShader)
        checkGlError("glAttachShader fragment")
        
        GLES20.glLinkProgram(program)
        checkGlError("glLinkProgram")
        
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        checkGlError("glGetProgramiv")
        
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val infoLog = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Could not link program:")
            Log.e(TAG, if (infoLog.isNullOrEmpty()) "No link error message available" else infoLog)
            GLES20.glDeleteProgram(program)
            program = 0
        } else {
            Log.d(TAG, "Program linked successfully")
        }
        
        // Clean up shaders
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(pixelShader)
        
        return program
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        checkGlError("glCreateShader type=$shaderType")
        
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader of type $shaderType")
            return 0
        }
        
        GLES20.glShaderSource(shader, source)
        checkGlError("glShaderSource")
        
        GLES20.glCompileShader(shader)
        checkGlError("glCompileShader")
        
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        checkGlError("glGetShaderiv")
        
        if (compiled[0] == 0) {
            val errorLog = GLES20.glGetShaderInfoLog(shader)
            val shaderTypeName = when(shaderType) {
                GLES20.GL_VERTEX_SHADER -> "VERTEX"
                GLES20.GL_FRAGMENT_SHADER -> "FRAGMENT"
                else -> "UNKNOWN($shaderType)"
            }
            Log.e(TAG, "Could not compile $shaderTypeName shader:")
            Log.e(TAG, "Shader source:")
            Log.e(TAG, source)
            Log.e(TAG, "Compilation error:")
            Log.e(TAG, if (errorLog.isNullOrEmpty()) "No error message available" else errorLog)
            GLES20.glDeleteShader(shader)
            shader = 0
        } else {
            Log.d(TAG, "Shader compiled successfully (type: $shaderType)")
        }
        return shader
    }

    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            val msg = "$op: glError 0x" + Integer.toHexString(error)
            Log.e(TAG, msg)
            throw RuntimeException(msg)
        }
    }
}
