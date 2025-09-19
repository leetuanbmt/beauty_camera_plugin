package com.beauty.camera_plugin.gl

import android.opengl.GLES20
import android.opengl.GLES11Ext
import android.util.Log
import com.beauty.camera_plugin.performance.MemoryPool

/**
 * ShaderProgram - OpenGL Shader Program Manager
 * 
 * SINGLE RESPONSIBILITY:
 * - Load and compile vertex/fragment shaders
 * - Link shader programs
 * - Handle uniform variables
 * - Render with different filter types
 * 
 * Based on OpenGLESPro patterns
 */
class ShaderProgram {
    companion object {
        private const val TAG = "ShaderProgram"
    }
    
    private var programId = -1

    // Attribute locations
    private var positionLocation = -1
    private var texCoordLocation = -1
    
    // Uniform locations
    private var mvpMatrixLocation = -1
    private var texMatrixLocation = -1
    private var textureLocation = -1
    private var filterTypeLocation = -1
    private var filterIntensityLocation = -1
    
    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "Initializing shader program with proper MVP matrix")
            
            // Load shaders
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, getVertexShaderSource())
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, getFragmentShaderSource())
            
            if (vertexShader == -1 || fragmentShader == -1) {
                Log.e(TAG, "Failed to load shaders")
                return false
            }
            
            // Create program
            programId = GLES20.glCreateProgram()
            if (programId == 0) {
                Log.e(TAG, "Failed to create shader program")
                return false
            }
            
            GLES20.glAttachShader(programId, vertexShader)
            GLES20.glAttachShader(programId, fragmentShader)
            GLES20.glLinkProgram(programId)
            
            // Check linking status
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
            
            val linkLog = GLES20.glGetProgramInfoLog(programId)
            if (linkLog.isNotEmpty()) {
                Log.d(TAG, "Program linking log: $linkLog")
            }
            
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Shader program linking failed: $linkLog")
                // Clean up
                GLES20.glDeleteProgram(programId)
                programId = -1
                return false
            }
            
            // Get attribute locations
            positionLocation = GLES20.glGetAttribLocation(programId, "aPosition")
            texCoordLocation = GLES20.glGetAttribLocation(programId, "aTextureCoord")
            
            Log.d(TAG, "Attribute locations: position=$positionLocation, texCoord=$texCoordLocation")
            
            // Get uniform locations
            mvpMatrixLocation = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
            texMatrixLocation = GLES20.glGetUniformLocation(programId, "uTextureMatrix")
            textureLocation = GLES20.glGetUniformLocation(programId, "sTexture")
            filterTypeLocation = GLES20.glGetUniformLocation(programId, "uFilterType")
            filterIntensityLocation = GLES20.glGetUniformLocation(programId, "uFilterIntensity")
            
            Log.d(TAG, "Uniform locations: mvp=$mvpMatrixLocation, tex=$texMatrixLocation, texture=$textureLocation, filter=$filterTypeLocation, intensity=$filterIntensityLocation")
            
            // Validate critical locations
            if (positionLocation == -1 || texCoordLocation == -1) {
                Log.e(TAG, "Critical attribute locations not found")
                return false
            }
            
            if (textureLocation == -1) {
                Log.e(TAG, "Texture uniform location not found")
                return false
            }
            
            Log.d(TAG, "Shader program initialized successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize shader program", e)
            // Clean up on error
            if (programId != -1) {
                GLES20.glDeleteProgram(programId)
                programId = -1
            }
            false
        }
    }

    fun render(
        textureId: Int,
        mvpMatrix: FloatArray,
        filterEnabled: Boolean,
        filterType: GlFilterType,
        filterIntensity: Float
    ) {
        if (programId == -1) {
            Log.w(TAG, "Shader program not initialized, skipping render")
            return
        }
        
        if (textureId == -1) {
            Log.w(TAG, "Invalid texture ID, skipping render")
            return
        }
        
        Log.d(TAG, "Rendering: textureId=$textureId, filterEnabled=$filterEnabled, filterType=$filterType, intensity=$filterIntensity")
        
        try {
            // Use shader program
            GLES20.glUseProgram(programId)
            checkGLError("glUseProgram")
            
            // Set uniforms with validation
            if (mvpMatrixLocation != -1) {
                GLES20.glUniformMatrix4fv(mvpMatrixLocation, 1, false, mvpMatrix, 0)
                checkGLError("glUniformMatrix4fv mvpMatrix")
            }
            
            if (texMatrixLocation != -1) {
                // Use identity matrix for proper texture mapping
                val identityMatrix = FloatArray(16)
                android.opengl.Matrix.setIdentityM(identityMatrix, 0)
                
                GLES20.glUniformMatrix4fv(texMatrixLocation, 1, false, identityMatrix, 0)
                checkGLError("glUniformMatrix4fv texMatrix")
                
                Log.d(TAG, "Using identity texture matrix for proper orientation")
            }
            
            if (textureLocation != -1) {
                GLES20.glUniform1i(textureLocation, 0)
                checkGLError("glUniform1i texture")
            }
            
            // Set filter uniforms
            if (filterTypeLocation != -1) {
                GLES20.glUniform1i(filterTypeLocation, if (filterEnabled) 1 else 0)
                checkGLError("glUniform1i filterEnabled")
            }
            
            if (filterIntensityLocation != -1) {
                GLES20.glUniform1f(filterIntensityLocation, filterIntensity)
                checkGLError("glUniform1f filterIntensity")
            }
            
         
            // Bind texture with proper error checking
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            checkGLError("glActiveTexture")
            
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            checkGLError("glBindTexture")
            
            Log.d(TAG, "Texture bound: textureId=$textureId, target=GL_TEXTURE_EXTERNAL_OES")
            
            // Enable vertex attributes
            if (positionLocation != -1) {
                GLES20.glEnableVertexAttribArray(positionLocation)
                checkGLError("glEnableVertexAttribArray position")
            }
            
            if (texCoordLocation != -1) {
                GLES20.glEnableVertexAttribArray(texCoordLocation)
                checkGLError("glEnableVertexAttribArray texCoord")
            }
            
            // Set vertex data using memory pool
            val vertices = MemoryPool.getQuadVertexBuffer()
            
            // Use normal texture coordinates (camera preview should not be flipped)
            val texCoords = MemoryPool.getQuadTextureBuffer()
            
            // Debug: Log texture coordinates
            val texCoordsArray = FloatArray(texCoords.remaining())
            texCoords.duplicate().get(texCoordsArray)
            Log.d(TAG, "Texture coordinates: ${texCoordsArray.joinToString(", ")}")
            
            // Use buffers from memory pool (already positioned at 0)
            if (positionLocation != -1) {
                GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, vertices)
                checkGLError("glVertexAttribPointer position")
            }
            
            if (texCoordLocation != -1) {
                GLES20.glVertexAttribPointer(texCoordLocation, 2, GLES20.GL_FLOAT, false, 0, texCoords)
                checkGLError("glVertexAttribPointer texCoord")
            }
            
            // Draw
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            checkGLError("glDrawArrays")
            
            // Disable vertex attributes
            if (positionLocation != -1) {
                GLES20.glDisableVertexAttribArray(positionLocation)
            }
            if (texCoordLocation != -1) {
                GLES20.glDisableVertexAttribArray(texCoordLocation)
            }
            
            Log.d(TAG, "Render completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during render", e)
        }
    }
    
    private fun checkGLError(operation: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "OpenGL error in $operation: $error")
        }
    }

    fun dispose() {
        if (programId != -1) {
            GLES20.glDeleteProgram(programId)
            programId = -1
        }
    }
    
    private fun loadShader(type: Int, source: String): Int {
        val shaderTypeName = if (type == GLES20.GL_VERTEX_SHADER) "VERTEX" else "FRAGMENT"
        Log.d(TAG, "Compiling $shaderTypeName shader...")
        Log.d(TAG, "Shader source:\n$source")
        
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        
        val infoLog = GLES20.glGetShaderInfoLog(shader)
        if (infoLog.isNotEmpty()) {
            Log.d(TAG, "$shaderTypeName shader info log: $infoLog")
        }
        
        if (compileStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "$shaderTypeName shader compilation failed: $infoLog")
            GLES20.glDeleteShader(shader)
            return -1
        }
        
        Log.d(TAG, "$shaderTypeName shader compiled successfully")
        return shader
    }
    
    private fun getVertexShaderSource(): String {
        return """
            #version 100

            attribute vec2 aPosition;
            attribute vec2 aTextureCoord;
            uniform mat4 uTextureMatrix;
            varying vec2 vTextureCoord;

            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTextureCoord = (uTextureMatrix * vec4(aTextureCoord, 0.0, 1.0)).xy;
            }
        """.trimIndent()
    }
    
    private fun getFragmentShaderSource(): String {
        return """
            #version 100
            #extension GL_OES_EGL_image_external : require

            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            uniform int uFilterType;
            uniform float uFilterIntensity;

            void main() {
                vec4 textureColor = texture2D(sTexture, vTextureCoord);
                
                if (uFilterType > 0) {
                    // Apply beauty filter with intensity
                    float intensity = uFilterIntensity;
                    
                    // Skin smoothing (blur effect)
                    vec4 smoothed = textureColor;
                    smoothed.rgb = mix(textureColor.rgb, smoothed.rgb, intensity * 0.3);
                    
                    // Brightness enhancement
                    smoothed.rgb *= (1.0 + intensity * 0.2);
                    
                    // Contrast enhancement
                    smoothed.rgb = (smoothed.rgb - 0.5) * (1.0 + intensity * 0.3) + 0.5;
                    
                    // Saturation boost
                    float gray = dot(smoothed.rgb, vec3(0.299, 0.587, 0.114));
                    smoothed.rgb = mix(vec3(gray), smoothed.rgb, 1.0 + intensity * 0.2);
                    
                    textureColor = smoothed;
                }
                // No enhancement when filter is disabled - pure camera preview
                
                // Clamp to valid range
                textureColor.rgb = clamp(textureColor.rgb, 0.0, 1.0);
                
                gl_FragColor = textureColor;
            }
        """.trimIndent()
    }
}
