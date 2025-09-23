package com.beauty.camera_plugin.renderer

import android.opengl.GLES20
import android.opengl.Matrix
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import com.beauty.camera_plugin.FilterParameters
import com.beauty.camera_plugin.renderer.filters.FilterChain
import com.beauty.camera_plugin.renderer.filters.IFilter

/**
 * Interface for OpenGL renderer
 * Handles the rendering pipeline and filter application
 */
interface IRenderer {
    /**
     * Initialize renderer
     * @param width Viewport width
     * @param height Viewport height
     * @return Success status
     */
    fun initialize(width: Int, height: Int): Boolean
    
    /**
     * Set camera input texture
     * @param textureId Camera texture ID
     * @param width Texture width
     * @param height Texture height
     */
    fun setCameraInput(textureId: Int, width: Int, height: Int)
    
    /**
     * Render frame with filters
     * @param parameters Filter parameters
     */
    fun renderFrame(parameters: FilterParameters)
    
    /**
     * Set viewport size
     * @param width Viewport width
     * @param height Viewport height
     */
    fun setViewport(width: Int, height: Int)
    
    /**
     * Add filter to renderer
     * @param filter Filter to add
     */
    fun addFilter(filter: IFilter)
    
    /**
     * Remove filter from renderer
     * @param filter Filter to remove
     */
    fun removeFilter(filter: IFilter)
    
    /**
     * Get filter chain
     */
    fun getFilterChain(): FilterChain
    
    /**
     * Enable/disable rendering
     * @param enabled Rendering enabled state
     */
    fun setEnabled(enabled: Boolean)
    
    /**
     * Check if renderer is initialized
     */
    fun isInitialized(): Boolean
    
    /**
     * Dispose renderer resources
     */
    fun dispose()
}

/**
 * OpenGL renderer implementation
 * Manages the rendering pipeline and filter chain
 */
class GlRenderer : IRenderer {
    private val TAG = "GlRenderer"
    
    private var initialized = false
    private var enabled = true
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var cameraTextureId = -1
    private var cameraWidth = 0
    private var cameraHeight = 0
    
    // Filter chain for processing
    private val filterChain = FilterChain()
    
    // OpenGL components
    private var shaderProgram: ShaderProgram? = null
    
    // EGL context management
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    
    override fun initialize(width: Int, height: Int): Boolean {
        return try {
            viewportWidth = width
            viewportHeight = height
            
            // Initialize EGL context
            if (!initializeEGL()) {
                android.util.Log.e(TAG, "Failed to initialize EGL context")
                return false
            }
            
            // Initialize shader program
            shaderProgram = ShaderProgram()
            val shaderInitialized = shaderProgram?.initialize() ?: false
            
            if (!shaderInitialized) {
                android.util.Log.e(TAG, "Failed to initialize shader program")
                return false
            }
            
            initialized = true
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to initialize GlRenderer", e)
            false
        }
    }
    
    override fun setCameraInput(textureId: Int, width: Int, height: Int) {
        cameraTextureId = textureId
        cameraWidth = width
        cameraHeight = height
    }
    
    override fun renderFrame(parameters: FilterParameters) {
        if (!initialized || !enabled || cameraTextureId == -1) {
            return
        }
        
        try {
            // Set viewport
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
            
            // Clear screen
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            
            // Apply filter chain
            val outputTexture = filterChain.applyFilters(
                inputTexture = cameraTextureId,
                parameters = parameters,
                width = cameraWidth,
                height = cameraHeight
            )
            
            // Render final result to screen
            renderToScreen(outputTexture)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error rendering frame", e)
        }
    }
    
    override fun setViewport(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
    }
    
    override fun addFilter(filter: IFilter) {
        filterChain.addFilter(filter)
    }
    
    override fun removeFilter(filter: IFilter) {
        filterChain.removeFilter(filter)
    }
    
    override fun getFilterChain(): FilterChain {
        return filterChain
    }
    
    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
    
    override fun isInitialized(): Boolean {
        return initialized
    }
    
    override fun dispose() {
        try {
            // Dispose EGL context
            disposeEGL()
            
            filterChain.disposeAll()
            shaderProgram?.dispose()
            shaderProgram = null
            initialized = false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error disposing GlRenderer", e)
        }
    }
    
    /**
     * Dispose EGL context
     */
    private fun disposeEGL() {
        try {
            if (eglDisplay != null && eglContext != null) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                
                if (eglSurface != null) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    eglSurface = null
                }
                
                if (eglContext != null) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                    eglContext = null
                }
                
                if (eglDisplay != null) {
                    EGL14.eglTerminate(eglDisplay)
                    eglDisplay = null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error disposing EGL", e)
        }
    }
    
    /**
     * Render texture to screen
     * @param textureId Texture to render
     */
    private fun renderToScreen(textureId: Int) {
        // Implementation depends on your shader program
        // This is a simplified version
        shaderProgram?.render(
            textureId = textureId,
            mvpMatrix = getIdentityMatrix(),
            filterEnabled = false, // Already processed by filter chain
            filterType = FilterType.NONE,
            filterIntensity = 1.0f
        )
    }
    
    /**
     * Initialize EGL context
     */
    private fun initializeEGL(): Boolean {
        return try {
            // Get default display
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                android.util.Log.e(TAG, "Failed to get EGL display")
                return false
            }
            
            // Initialize EGL
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                android.util.Log.e(TAG, "Failed to initialize EGL")
                return false
            }
            
            // Configure EGL
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_NONE
            )
            
            if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, configs.size, numConfigs, 0)) {
                android.util.Log.e(TAG, "Failed to choose EGL config")
                return false
            }
            
            // Create context
            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                android.util.Log.e(TAG, "Failed to create EGL context")
                return false
            }
            
            // Create surface (pbuffer for offscreen rendering)
            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, viewportWidth,
                EGL14.EGL_HEIGHT, viewportHeight,
                EGL14.EGL_NONE
            )
            
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                android.util.Log.e(TAG, "Failed to create EGL surface")
                return false
            }
            
            // Make current
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                android.util.Log.e(TAG, "Failed to make EGL context current")
                return false
            }
            
            android.util.Log.d(TAG, "EGL context initialized successfully")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error initializing EGL", e)
            false
        }
    }
    
    /**
     * Get identity matrix
     */
    private fun getIdentityMatrix(): FloatArray {
        val matrix = FloatArray(16)
        Matrix.setIdentityM(matrix, 0)
        return matrix
    }
}

/**
 * Filter type enum for OpenGL
 */
enum class FilterType {
    NONE, BEAUTY, PORTRAIT, FOOD, LANDSCAPE, VINTAGE, VIBRANT, MOODY, FILM, ART
}

/**
 * Simple shader program for basic rendering
 */
class ShaderProgram {
    private val TAG = "ShaderProgram"
    private var program = 0
    
    fun initialize(): Boolean {
        // Simple passthrough shader
        val vertexShader = """
            attribute vec4 vPosition;
            attribute vec2 vTexCoord;
            varying vec2 texCoord;
            uniform mat4 uMVPMatrix;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                texCoord = vTexCoord;
            }
        """.trimIndent()
        
        val fragmentShader = """
            precision mediump float;
            varying vec2 texCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, texCoord);
            }
        """.trimIndent()
        
        program = createProgram(vertexShader, fragmentShader)
        return program != 0
    }
    
    fun render(
        textureId: Int,
        mvpMatrix: FloatArray,
        filterEnabled: Boolean,
        filterType: FilterType,
        filterIntensity: Float
    ) {
        GLES20.glUseProgram(program)
        
        // Set uniforms
        val mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        
        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        val textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        GLES20.glUniform1i(textureHandle, 0)
        
        // Draw quad
        drawQuad()
    }
    
    private fun createProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        if (vertexShader == 0 || fragmentShader == 0) {
            android.util.Log.e(TAG, "Failed to load shaders")
            return 0
        }
        
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            android.util.Log.e(TAG, "Failed to create program")
            return 0
        }
        
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        
        if (linkStatus[0] == 0) {
            val infoLog = GLES20.glGetProgramInfoLog(program)
            android.util.Log.e(TAG, "Program linking failed: $infoLog")
            GLES20.glDeleteProgram(program)
            return 0
        }
        
        return program
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            android.util.Log.e(TAG, "Failed to create shader")
            return 0
        }
        
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        
        if (compileStatus[0] == 0) {
            val infoLog = GLES20.glGetShaderInfoLog(shader)
            android.util.Log.e(TAG, "Shader compilation failed: $infoLog")
            GLES20.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }
    
    private fun drawQuad() {
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
        
        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        val textureHandle = GLES20.glGetAttribLocation(program, "vTexCoord")
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, java.nio.FloatBuffer.wrap(vertices))
        
        GLES20.glEnableVertexAttribArray(textureHandle)
        GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 0, java.nio.FloatBuffer.wrap(texCoords))
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureHandle)
    }
    
    fun dispose() {
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }
}
