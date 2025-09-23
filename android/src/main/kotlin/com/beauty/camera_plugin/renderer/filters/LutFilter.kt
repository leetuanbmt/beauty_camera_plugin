package com.beauty.camera_plugin.renderer.filters

import android.opengl.GLES20
import android.util.Log
import com.beauty.camera_plugin.FilterParameters

/**
 * LUT (Look-Up Table) Filter - Color grading filter
 * Applies color grading using 3D LUT textures
 */
class LutFilter : BaseFilter() {
    override val name: String = "LUT"
    override val category: FilterCategory = FilterCategory.ART
    
    private val TAG = "LutFilter"
    
    // Shader program
    private var program = 0
    private var positionHandle = 0
    private var textureHandle = 0
    private var mvpMatrixHandle = 0
    private var lutTextureHandle = 0
    private var intensityHandle = 0
    
    // Texture handles
    private var framebuffer = 0
    private var lutTexture = -1
    
    // LUT parameters
    private var lutSize = 32 // Default 32x32x32 LUT
    private var lutData: FloatArray? = null
    
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
    
    // Fragment shader code for LUT color grading
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 texCoord;
        uniform sampler2D uTexture;
        uniform sampler2D uLUT;
        uniform float uIntensity;
        uniform float uLUTSize;
        
        // 3D LUT lookup
        vec3 lut3D(vec3 color) {
            // Clamp color to [0, 1]
            color = clamp(color, 0.0, 1.0);
            
            // Scale to LUT coordinates
            color = color * (uLUTSize - 1.0) / uLUTSize + 0.5 / uLUTSize;
            
            // Calculate 3D texture coordinates
            float sliceSize = 1.0 / uLUTSize;
            float slice = floor(color.b * uLUTSize);
            float nextSlice = min(slice + 1.0, uLUTSize - 1.0);
            
            // Interpolate between slices
            float sliceOffset = slice * sliceSize;
            float nextSliceOffset = nextSlice * sliceSize;
            
            vec2 lutCoord1 = vec2(
                color.r * sliceSize + sliceOffset,
                color.g
            );
            vec2 lutCoord2 = vec2(
                color.r * sliceSize + nextSliceOffset,
                color.g
            );
            
            vec3 color1 = texture2D(uLUT, lutCoord1).rgb;
            vec3 color2 = texture2D(uLUT, lutCoord2).rgb;
            
            // Interpolate between the two slices
            float factor = fract(color.b * uLUTSize);
            return mix(color1, color2, factor);
        }
        
        void main() {
            vec3 originalColor = texture2D(uTexture, texCoord).rgb;
            
            // Apply LUT color grading
            vec3 lutColor = lut3D(originalColor);
            
            // Mix original and LUT color based on intensity
            vec3 finalColor = mix(originalColor, lutColor, uIntensity);
            
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
            lutTextureHandle = GLES20.glGetUniformLocation(program, "uLUT")
            intensityHandle = GLES20.glGetUniformLocation(program, "uIntensity")
            
            // Create framebuffer for processing
            val framebuffers = IntArray(1)
            GLES20.glGenFramebuffers(1, framebuffers, 0)
            framebuffer = framebuffers[0]
            
            // Load default LUT (identity)
            loadDefaultLUT()
            
            initialized = true
            Log.d(TAG, "LutFilter initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LutFilter", e)
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
        if (!initialized || lutTexture == -1) {
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
            GLES20.glUniform1f(intensityHandle, getIntensity())
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uLUTSize"), lutSize.toFloat())
            
            // Bind input texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
            
            // Bind LUT texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexture)
            GLES20.glUniform1i(lutTextureHandle, 1)
            
            // Draw fullscreen quad
            drawQuad()
            
            // Unbind framebuffer
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            
            return outputTexture
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying LutFilter", e)
            return inputTexture
        }
    }
    
    /**
     * Load LUT from data
     * @param lutData LUT data (RGB values)
     * @param size LUT size (e.g., 32 for 32x32x32 LUT)
     */
    fun loadLUT(lutData: FloatArray, size: Int) {
        try {
            this.lutSize = size
            this.lutData = lutData
            
            // Create LUT texture
            if (lutTexture != -1) {
                GLES20.glDeleteTextures(1, intArrayOf(lutTexture), 0)
            }
            
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            lutTexture = textures[0]
            
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexture)
            
            // Set texture parameters
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            
            // Upload LUT data
            val width = size * size
            val height = size
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB,
                width, height, 0, GLES20.GL_RGB, GLES20.GL_FLOAT,
                java.nio.FloatBuffer.wrap(lutData)
            )
            
            Log.d(TAG, "LUT loaded: ${size}x${size}x${size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading LUT", e)
        }
    }
    
    override fun dispose() {
        try {
            if (framebuffer != 0) {
                GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
                framebuffer = 0
            }
            
            if (lutTexture != -1) {
                GLES20.glDeleteTextures(1, intArrayOf(lutTexture), 0)
                lutTexture = -1
            }
            
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
            
            super.dispose()
            Log.d(TAG, "LutFilter disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing LutFilter", e)
        }
    }
    
    // --- Private Helper Methods ---
    
    private fun loadDefaultLUT() {
        // Create identity LUT (no color change)
        val lutData = FloatArray(lutSize * lutSize * lutSize * 3)
        
        for (b in 0 until lutSize) {
            for (g in 0 until lutSize) {
                for (r in 0 until lutSize) {
                    val index = (b * lutSize * lutSize + g * lutSize + r) * 3
                    lutData[index] = r.toFloat() / (lutSize - 1).toFloat()     // Red
                    lutData[index + 1] = g.toFloat() / (lutSize - 1).toFloat() // Green
                    lutData[index + 2] = b.toFloat() / (lutSize - 1).toFloat() // Blue
                }
            }
        }
        
        loadLUT(lutData, lutSize)
    }
    
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
