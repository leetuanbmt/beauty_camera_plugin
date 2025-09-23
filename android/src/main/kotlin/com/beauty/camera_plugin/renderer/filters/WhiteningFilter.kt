package com.beauty.camera_plugin.renderer.filters

import android.opengl.GLES20
import android.util.Log
import com.beauty.camera_plugin.FilterParameters

/**
 * Whitening Filter - Skin brightening effect
 * Implements selective brightening for skin areas
 */
class WhiteningFilter : BaseFilter() {
    override val name: String = "Whitening"
    override val category: FilterCategory = FilterCategory.BEAUTY
    
    private val TAG = "WhiteningFilter"
    
    // Shader program
    private var program = 0
    private var positionHandle = 0
    private var textureHandle = 0
    private var mvpMatrixHandle = 0
    private var brighteningStrengthHandle = 0
    private var intensityHandle = 0
    
    // Texture handles
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
    
    // Fragment shader code for skin brightening
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 texCoord;
        uniform sampler2D uTexture;
        uniform float uBrighteningStrength;
        uniform float uIntensity;
        
        // Skin tone detection
        bool isSkinTone(vec3 color) {
            // Convert to HSV for better skin detection
            float r = color.r;
            float g = color.g;
            float b = color.b;
            
            float maxVal = max(max(r, g), b);
            float minVal = min(min(r, g), b);
            float delta = maxVal - minVal;
            
            // Hue calculation
            float hue = 0.0;
            if (delta != 0.0) {
                if (maxVal == r) {
                    hue = 60.0 * mod(((g - b) / delta), 6.0);
                } else if (maxVal == g) {
                    hue = 60.0 * ((b - r) / delta + 2.0);
                } else {
                    hue = 60.0 * ((r - g) / delta + 4.0);
                }
            }
            
            // Skin tone range: 0-50 degrees (red to yellow)
            return hue >= 0.0 && hue <= 50.0;
        }
        
        // Selective brightening for skin tones
        vec3 brightenSkin(vec3 color) {
            if (isSkinTone(color)) {
                // Apply brightening to skin areas
                float brightness = uBrighteningStrength * uIntensity;
                
                // Increase luminance while preserving color ratios
                float luminance = dot(color, vec3(0.299, 0.587, 0.114));
                float newLuminance = luminance + brightness * (1.0 - luminance);
                
                // Scale color to new luminance
                if (luminance > 0.0) {
                    return color * (newLuminance / luminance);
                }
            }
            return color;
        }
        
        void main() {
            vec3 originalColor = texture2D(uTexture, texCoord).rgb;
            
            // Apply selective brightening
            vec3 brightenedColor = brightenSkin(originalColor);
            
            // Mix original and brightened color based on intensity
            vec3 finalColor = mix(originalColor, brightenedColor, uIntensity);
            
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
            brighteningStrengthHandle = GLES20.glGetUniformLocation(program, "uBrighteningStrength")
            intensityHandle = GLES20.glGetUniformLocation(program, "uIntensity")
            
            // Create framebuffer for processing
            val framebuffers = IntArray(1)
            GLES20.glGenFramebuffers(1, framebuffers, 0)
            framebuffer = framebuffers[0]
            
            initialized = true
            Log.d(TAG, "WhiteningFilter initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WhiteningFilter", e)
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
            
            // Set brightening strength from parameters
            val brighteningStrength = (parameters.skinBrightening * getIntensity()).toFloat()
            GLES20.glUniform1f(brighteningStrengthHandle, brighteningStrength)
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
            Log.e(TAG, "Error applying WhiteningFilter", e)
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
            Log.d(TAG, "WhiteningFilter disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing WhiteningFilter", e)
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
