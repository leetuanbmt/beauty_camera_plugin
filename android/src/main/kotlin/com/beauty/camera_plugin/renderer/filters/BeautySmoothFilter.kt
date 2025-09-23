package com.beauty.camera_plugin.renderer.filters

import android.opengl.GLES20
import android.util.Log
import com.beauty.camera_plugin.FilterParameters

/**
 * Beauty Smooth Filter - Skin smoothing effect
 * Implements bilateral filtering for skin smoothing
 */
class BeautySmoothFilter : BaseFilter() {
    override val name: String = "BeautySmooth"
    override val category: FilterCategory = FilterCategory.BEAUTY
    
    private val TAG = "BeautySmoothFilter"
    
    // Shader program
    private var program = 0
    private var positionHandle = 0
    private var textureHandle = 0
    private var mvpMatrixHandle = 0
    private var smoothingStrengthHandle = 0
    private var intensityHandle = 0
    
    // Texture handles
    private var inputTexture = -1
    private var outputTexture = -1
    private var framebuffer = 0
    
    // Vertex shader code
    private val vertexShaderCode = """
        attribute vec4 vPosition;
        attribute vec2 vTexCoord;
        varying vec2 texCoord;
        uniform mat4 uMVPMatrix;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            texCoord = vTexCoord;
        }
    """.trimIndent()
    
    // Fragment shader code for skin smoothing
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 texCoord;
        uniform sampler2D uTexture;
        uniform float uSmoothingStrength;
        uniform float uIntensity;
        
        // Bilateral filter for skin smoothing
        vec3 bilateralFilter(vec2 uv, float sigmaSpace, float sigmaColor) {
            vec2 texelSize = 1.0 / vec2(textureSize(uTexture, 0));
            vec3 centerColor = texture2D(uTexture, uv).rgb;
            vec3 result = vec3(0.0);
            float weightSum = 0.0;
            
            // 5x5 kernel
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    vec2 offset = vec2(float(x), float(y)) * texelSize;
                    vec2 sampleUV = uv + offset;
                    
                    // Spatial weight
                    float spatialWeight = exp(-(x*x + y*y) / (2.0 * sigmaSpace * sigmaSpace));
                    
                    // Color weight
                    vec3 sampleColor = texture2D(uTexture, sampleUV).rgb;
                    float colorDiff = length(sampleColor - centerColor);
                    float colorWeight = exp(-(colorDiff * colorDiff) / (2.0 * sigmaColor * sigmaColor));
                    
                    float weight = spatialWeight * colorWeight;
                    result += sampleColor * weight;
                    weightSum += weight;
                }
            }
            
            return result / weightSum;
        }
        
        void main() {
            vec3 originalColor = texture2D(uTexture, texCoord).rgb;
            
            // Apply bilateral filter for skin smoothing
            float sigmaSpace = 1.0 + uSmoothingStrength * 3.0;
            float sigmaColor = 0.1 + uSmoothingStrength * 0.3;
            vec3 smoothedColor = bilateralFilter(texCoord, sigmaSpace, sigmaColor);
            
            // Mix original and smoothed color based on intensity
            vec3 finalColor = mix(originalColor, smoothedColor, uIntensity);
            
            gl_FragColor = vec4(finalColor, 1.0);
        }
    """.trimIndent()
    
    override fun initialize(): Boolean {
        return try {
            // Create shader program
            program = createShaderProgram(vertexShaderCode, fragmentShaderCode)
            if (program == 0) {
                Log.e(TAG, "Failed to create shader program")
                return false
            }
            
            // Get attribute and uniform locations
            positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
            textureHandle = GLES20.glGetAttribLocation(program, "vTexCoord")
            mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            smoothingStrengthHandle = GLES20.glGetUniformLocation(program, "uSmoothingStrength")
            intensityHandle = GLES20.glGetUniformLocation(program, "uIntensity")
            
            // Create framebuffer for processing
            val framebuffers = IntArray(1)
            GLES20.glGenFramebuffers(1, framebuffers, 0)
            framebuffer = framebuffers[0]
            
            initialized = true
            Log.d(TAG, "BeautySmoothFilter initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BeautySmoothFilter", e)
            false
        }
    }
    
    override fun apply(
        inputTexture: Int,
        outputTexture: Int,
        parameters: FilterParameters,
        width: Int,
        height: Int
    ): Int {
        if (!initialized) {
            return inputTexture
        }
        
        try {
            // Bind framebuffer for processing
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
            
            // Attach output texture to framebuffer
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                outputTexture,
                0
            )
            
            // Check framebuffer status
            val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "Framebuffer not complete: $status")
                return inputTexture
            }
            
            // Set viewport
            GLES20.glViewport(0, 0, width, height)
            
            // Use shader program
            GLES20.glUseProgram(program)
            
            // Set uniforms
            val mvpMatrix = getIdentityMatrix()
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            
            // Set smoothing strength from parameters
            val smoothingStrength = (parameters.skinSmoothing * getIntensity()).toFloat()
            GLES20.glUniform1f(smoothingStrengthHandle, smoothingStrength)
            GLES20.glUniform1f(intensityHandle, getIntensity())
            
            // Bind input texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
            
            // Draw fullscreen quad
            drawQuad()
            
            // Unbind framebuffer
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            
            return outputTexture
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying BeautySmoothFilter", e)
            return inputTexture
        }
    }
    
    override fun dispose() {
        try {
            if (framebuffer != 0) {
                GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
                framebuffer = 0
            }
            
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
            
            super.dispose()
            Log.d(TAG, "BeautySmoothFilter disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing BeautySmoothFilter", e)
        }
    }
    
    // --- Private Helper Methods ---
    
    private fun createShaderProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        if (vertexShader == 0 || fragmentShader == 0) {
            return 0
        }
        
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        
        if (linkStatus[0] == 0) {
            val info = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Failed to link program: $info")
            GLES20.glDeleteProgram(program)
            return 0
        }
        
        return program
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        
        if (compileStatus[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Failed to compile shader: $info")
            GLES20.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }
    
    private fun getIdentityMatrix(): FloatArray {
        val matrix = FloatArray(16)
        android.opengl.Matrix.setIdentityM(matrix, 0)
        return matrix
    }
    
    private fun drawQuad() {
        // Fullscreen quad vertices
        val vertices = floatArrayOf(
            -1.0f, -1.0f, 0.0f,
             1.0f, -1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f, 0.0f
        )
        
        val texCoords = floatArrayOf(
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
        )
        
        // Set vertex positions
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, java.nio.FloatBuffer.wrap(vertices))
        
        // Set texture coordinates
        GLES20.glEnableVertexAttribArray(textureHandle)
        GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 0, java.nio.FloatBuffer.wrap(texCoords))
        
        // Draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        
        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureHandle)
    }
}
