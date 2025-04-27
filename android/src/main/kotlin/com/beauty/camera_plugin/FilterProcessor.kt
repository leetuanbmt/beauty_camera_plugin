package com.beauty.camera_plugin

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Bộ xử lý bộ lọc cho camera sử dụng OpenGL ES.
 * Hỗ trợ áp dụng các bộ lọc khác nhau lên preview camera.
 */
class FilterProcessor {
    companion object {
        private const val TAG = "FilterProcessor"
        
        // Vertex shader mặc định
        private const val DEFAULT_VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """
        
        // Fragment shader không có bộ lọc
        private const val NO_FILTER_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
        
        // Fragment shader cho bộ lọc làm đẹp
        private const val BEAUTY_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform float uSmoothness;
            
            void main() {
                vec2 texelSize = vec2(1.0 / 1080.0, 1.0 / 1920.0);
                vec4 center = texture2D(sTexture, vTexCoord);
                
                // Simple blur for smoothing
                vec4 sum = vec4(0.0);
                sum += texture2D(sTexture, vTexCoord + vec2(-1.0, -1.0) * texelSize) * 0.0625;
                sum += texture2D(sTexture, vTexCoord + vec2(-1.0, 0.0) * texelSize) * 0.125;
                sum += texture2D(sTexture, vTexCoord + vec2(-1.0, 1.0) * texelSize) * 0.0625;
                sum += texture2D(sTexture, vTexCoord + vec2(0.0, -1.0) * texelSize) * 0.125;
                sum += center * 0.25;
                sum += texture2D(sTexture, vTexCoord + vec2(0.0, 1.0) * texelSize) * 0.125;
                sum += texture2D(sTexture, vTexCoord + vec2(1.0, -1.0) * texelSize) * 0.0625;
                sum += texture2D(sTexture, vTexCoord + vec2(1.0, 0.0) * texelSize) * 0.125;
                sum += texture2D(sTexture, vTexCoord + vec2(1.0, 1.0) * texelSize) * 0.0625;
                
                // Mix original with blurred based on smoothness parameter
                gl_FragColor = mix(center, sum, uSmoothness);
            }
        """
        
        // Fragment shader cho bộ lọc đen trắng
        private const val MONO_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform float uIntensity;
            
            void main() {
                vec4 color = texture2D(sTexture, vTexCoord);
                float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                vec4 grayColor = vec4(gray, gray, gray, color.a);
                gl_FragColor = mix(color, grayColor, uIntensity);
            }
        """
        
        // Fragment shader cho bộ lọc âm bản
        private const val NEGATIVE_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform float uIntensity;
            
            void main() {
                vec4 color = texture2D(sTexture, vTexCoord);
                vec4 negative = vec4(1.0 - color.r, 1.0 - color.g, 1.0 - color.b, color.a);
                gl_FragColor = mix(color, negative, uIntensity);
            }
        """
        
        // Fragment shader cho điều chỉnh độ sáng
        private const val BRIGHTNESS_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform float uBrightness;
            
            void main() {
                vec4 color = texture2D(sTexture, vTexCoord);
                gl_FragColor = vec4(color.rgb + uBrightness, color.a);
            }
        """
        
        // Fragment shader cho điều chỉnh độ tương phản
        private const val CONTRAST_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform float uContrast;
            
            void main() {
                vec4 color = texture2D(sTexture, vTexCoord);
                gl_FragColor = vec4((color.rgb - 0.5) * uContrast + 0.5, color.a);
            }
        """
    }
    
    // Trạng thái hiện tại
    private var currentFilter = CameraFilterMode.NONE
    private var currentParameters = FilterParameters(
        intensity = 0.5,
        brightness = 0.0,
        contrast = 1.0,
        saturation = 1.0,
        hue = 0.0,
        sharpen = 0.0,
        blurRadius = 0.0,
        redChannel = 1.0,
        greenChannel = 1.0,
        blueChannel = 1.0,
        skinSmoothness = 0.5,
        lookupTablePath = null
    )
    
    // OpenGL context
    private var renderer: FilterRenderer? = null
    private var context: Context? = null
    private var glSurfaceView: GLSurfaceView? = null
    
    // Filter registry - mapping CameraFilterMode to shader program
    private val filterShaders = mutableMapOf<CameraFilterMode, String>()
    
    /**
     * Khởi tạo filter processor với context
     */
    fun initialize(context: Context) {
        this.context = context
        Log.d(TAG, "FilterProcessor initialized with context")
        
        // Load shaders và tài nguyên khác từ assets nếu cần
        try {
            // Đăng ký các shader cho từng loại filter
            registerDefaultShaders()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FilterProcessor: ${e.message}", e)
        }
    }
    
    private fun registerDefaultShaders() {
        filterShaders[CameraFilterMode.NONE] = NO_FILTER_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.BEAUTY] = BEAUTY_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.MONO] = MONO_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.NEGATIVE] = NEGATIVE_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.BRIGHTNESS] = BRIGHTNESS_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.CONTRAST] = CONTRAST_FRAGMENT_SHADER
        // TODO: Add more shader registrations for other filter types
    }
    
    fun setFilter(filterMode: CameraFilterMode, parameters: FilterParameters) {
        currentFilter = filterMode
        currentParameters = parameters
        
        renderer?.updateShaderProgram(
            filterMode,
            filterShaders[filterMode] ?: NO_FILTER_FRAGMENT_SHADER,
            parameters
        )
    }
    
    fun getAvailableFilters(): List<FilterInfo> {
        val filters = mutableListOf<FilterInfo>()
        
        // Tạo thông tin chi tiết cho từng bộ lọc
        filters.add(FilterInfo(
            id = "none",
            mode = CameraFilterMode.NONE,
            displayName = "Original",
            adjustableParameters = null
        ))
        
        filters.add(FilterInfo(
            id = "beauty",
            mode = CameraFilterMode.BEAUTY,
            displayName = "Beauty",
            adjustableParameters = listOf("skinSmoothness")
        ))
        
        filters.add(FilterInfo(
            id = "mono",
            mode = CameraFilterMode.MONO,
            displayName = "Mono",
            adjustableParameters = listOf("intensity")
        ))
        
        filters.add(FilterInfo(
            id = "negative",
            mode = CameraFilterMode.NEGATIVE,
            displayName = "Negative",
            adjustableParameters = listOf("intensity")
        ))
        
        filters.add(FilterInfo(
            id = "brightness",
            mode = CameraFilterMode.BRIGHTNESS,
            displayName = "Brightness",
            adjustableParameters = listOf("brightness")
        ))
        
        filters.add(FilterInfo(
            id = "contrast",
            mode = CameraFilterMode.CONTRAST,
            displayName = "Contrast",
            adjustableParameters = listOf("contrast")
        ))
        
        // TODO: Add more filter infos
        
        return filters
    }
    
    /**
     * Lấy chế độ filter hiện tại
     */
    fun getCurrentFilterMode(): CameraFilterMode {
        return currentFilter
    }
    
    fun release() {
        renderer?.release()
        renderer = null
        context = null
        glSurfaceView = null
    }
    
    /**
     * Thiết lập renderer với surface từ camera
     */
    fun setupRenderer(surface: Surface): Boolean {
        try {
            // Khởi tạo renderer với surface
            renderer = FilterRenderer(surface)
            
            // Thiết lập filter ban đầu
            setFilter(CameraFilterMode.NONE, currentParameters)
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup renderer", e)
            return false
        }
    }
    
    /**
     * Renderer cho OpenGL ES để áp dụng bộ lọc.
     */
    private inner class FilterRenderer(private val surface: Surface) : GLSurfaceView.Renderer {
        private val mvpMatrix = FloatArray(16)
        private val texMatrix = FloatArray(16)
        
        private var programHandle = 0
        private var textureHandle = 0
        private var positionHandle = 0
        private var texCoordHandle = 0
        private var mvpMatrixHandle = 0
        private var texMatrixHandle = 0
        
        private var currentShader = NO_FILTER_FRAGMENT_SHADER
        private var currentShaderProgram = 0
        
        // External texture cho camera input
        private var externalTextureId = 0
        private var surfaceTexture: SurfaceTexture? = null
        
        // Vertices và texture coordinates
        private val vertexData = floatArrayOf(
            -1.0f, -1.0f, 0.0f,  // bottom left
             1.0f, -1.0f, 0.0f,  // bottom right
            -1.0f,  1.0f, 0.0f,  // top left
             1.0f,  1.0f, 0.0f   // top right
        )
        
        private val texCoordData = floatArrayOf(
            0.0f, 0.0f,  // bottom left
            1.0f, 0.0f,  // bottom right
            0.0f, 1.0f,  // top left
            1.0f, 1.0f   // top right
        )
        
        private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexData)
                position(0)
            }
            
        private val texCoordBuffer: FloatBuffer = ByteBuffer.allocateDirect(texCoordData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(texCoordData)
                position(0)
            }
            
        // Các uniform handles cho current filter
        private val uniformHandles = mutableMapOf<String, Int>()
        
        // EGL
        private var eglDisplay: android.opengl.EGLDisplay? = null
        private var eglContext: android.opengl.EGLContext? = null
        private var eglSurface: android.opengl.EGLSurface? = null
            
        init {
            Matrix.setIdentityM(mvpMatrix, 0)
            Matrix.setIdentityM(texMatrix, 0)
            
            // Khởi tạo EGL context và surface
            setupEGL()
        }
        
        private fun setupEGL() {
            // TODO: Khởi tạo EGL context và surface nếu cần
        }
        
        fun updateShaderProgram(filterMode: CameraFilterMode, fragmentShader: String, parameters: FilterParameters) {
            currentShader = fragmentShader
            
            // Phải đảm bảo chúng ta đang chạy trên GL thread
            // Có thể cần queueEvent nếu không đang ở GL thread
            programHandle = createProgram(DEFAULT_VERTEX_SHADER, fragmentShader)
            
            // Cập nhật currentShaderProgram
            currentShaderProgram = programHandle
            
            // Cache các uniform locations
            uniformHandles.clear()
            
            // Lấy các handles cơ bản
            GLES20.glUseProgram(programHandle)
            
            positionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(programHandle, "aTexCoord")
            mvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix")
            texMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uTexMatrix")
            textureHandle = GLES20.glGetUniformLocation(programHandle, "sTexture")
            
            // Lấy các uniform handles cho filter
            when (filterMode) {
                CameraFilterMode.BEAUTY -> {
                    uniformHandles["uSmoothness"] = GLES20.glGetUniformLocation(programHandle, "uSmoothness")
                }
                CameraFilterMode.MONO, CameraFilterMode.NEGATIVE -> {
                    uniformHandles["uIntensity"] = GLES20.glGetUniformLocation(programHandle, "uIntensity")
                }
                CameraFilterMode.BRIGHTNESS -> {
                    uniformHandles["uBrightness"] = GLES20.glGetUniformLocation(programHandle, "uBrightness")
                }
                CameraFilterMode.CONTRAST -> {
                    uniformHandles["uContrast"] = GLES20.glGetUniformLocation(programHandle, "uContrast")
                }
                CameraFilterMode.SATURATION -> {
                    uniformHandles["uSaturation"] = GLES20.glGetUniformLocation(programHandle, "uSaturation")
                }
                else -> { /* Các filter khác */ }
            }
            
            // Áp dụng các tham số filter
            applyFilterParameters(parameters)
        }
        
        private fun applyFilterParameters(parameters: FilterParameters) {
            GLES20.glUseProgram(programHandle)
            
            // Áp dụng các tham số phù hợp với filter hiện tại
            uniformHandles["uSmoothness"]?.let {
                GLES20.glUniform1f(it, parameters.skinSmoothness.toFloat())
            }
            
            uniformHandles["uIntensity"]?.let {
                GLES20.glUniform1f(it, parameters.intensity.toFloat())
            }
            
            uniformHandles["uBrightness"]?.let {
                GLES20.glUniform1f(it, parameters.brightness.toFloat())
            }
            
            uniformHandles["uContrast"]?.let {
                GLES20.glUniform1f(it, parameters.contrast.toFloat())
            }
            
            uniformHandles["uSaturation"]?.let {
                GLES20.glUniform1f(it, parameters.saturation.toFloat())
            }
            
            uniformHandles["uHue"]?.let {
                GLES20.glUniform1f(it, parameters.hue.toFloat())
            }
            
            uniformHandles["uSharpen"]?.let {
                GLES20.glUniform1f(it, parameters.sharpen.toFloat())
            }
            
            uniformHandles["uBlurRadius"]?.let {
                GLES20.glUniform1f(it, parameters.blurRadius.toFloat())
            }
            
            uniformHandles["uRedChannel"]?.let {
                GLES20.glUniform1f(it, parameters.redChannel.toFloat())
            }
            
            uniformHandles["uGreenChannel"]?.let {
                GLES20.glUniform1f(it, parameters.greenChannel.toFloat())
            }
            
            uniformHandles["uBlueChannel"]?.let {
                GLES20.glUniform1f(it, parameters.blueChannel.toFloat())
            }
        }
        
        private fun createProgram(vertexShader: String, fragmentShader: String): Int {
            val vertexShaderHandle = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
            val fragmentShaderHandle = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
            
            val programHandle = GLES20.glCreateProgram()
            GLES20.glAttachShader(programHandle, vertexShaderHandle)
            GLES20.glAttachShader(programHandle, fragmentShaderHandle)
            GLES20.glLinkProgram(programHandle)
            
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                val info = GLES20.glGetProgramInfoLog(programHandle)
                GLES20.glDeleteProgram(programHandle)
                throw RuntimeException("Could not link program: $info")
            }
            
            // Detach và delete shader để giải phóng bộ nhớ
            GLES20.glDetachShader(programHandle, vertexShaderHandle)
            GLES20.glDetachShader(programHandle, fragmentShaderHandle)
            GLES20.glDeleteShader(vertexShaderHandle)
            GLES20.glDeleteShader(fragmentShaderHandle)
            
            return programHandle
        }
        
        private fun compileShader(type: Int, shaderCode: String): Int {
            val shaderHandle = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shaderHandle, shaderCode)
            GLES20.glCompileShader(shaderHandle)
            
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] != GLES20.GL_TRUE) {
                val info = GLES20.glGetShaderInfoLog(shaderHandle)
                GLES20.glDeleteShader(shaderHandle)
                throw RuntimeException("Could not compile shader (${if (type == GLES20.GL_VERTEX_SHADER) "vertex" else "fragment"}): $info")
            }
            
            return shaderHandle
        }
        
        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            
            // Tạo texture cho camera input
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            externalTextureId = textures[0]
            
            // Kết nối texture với SurfaceTexture
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, externalTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            
            // Khởi tạo shader program ban đầu với filter hiện tại
            programHandle = createProgram(DEFAULT_VERTEX_SHADER, currentShader)
            currentShaderProgram = programHandle
            
            GLES20.glUseProgram(programHandle)
            
            // Lấy các attribute và uniform handles
            positionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(programHandle, "aTexCoord")
            mvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix")
            texMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uTexMatrix")
            textureHandle = GLES20.glGetUniformLocation(programHandle, "sTexture")
            
            // Enable attributes
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
        }
        
        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            
            // Cập nhật projection matrix nếu cần
            // Ví dụ: nếu cần apply các transform khác
            Matrix.setIdentityM(mvpMatrix, 0)
        }
        
        override fun onDrawFrame(gl: GL10) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            
            // Sử dụng shader program hiện tại
            GLES20.glUseProgram(currentShaderProgram)
            
            // Cập nhật texture
            if (surfaceTexture != null) {
                surfaceTexture!!.updateTexImage()
                surfaceTexture!!.getTransformMatrix(texMatrix)
            }
            
            // Bind vertices
            GLES20.glVertexAttribPointer(
                positionHandle, 3, GLES20.GL_FLOAT, false,
                0, vertexBuffer
            )
            
            // Bind texture coords
            GLES20.glVertexAttribPointer(
                texCoordHandle, 2, GLES20.GL_FLOAT, false,
                0, texCoordBuffer
            )
            
            // Đặt matrixes
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, texMatrix, 0)
            
            // Bind texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, externalTextureId)
            GLES20.glUniform1i(textureHandle, 0)
            
            // Draw
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        
        // Hàm này được gọi từ CameraManager để xử lý frame mới từ camera
        fun onFrameAvailable(newSurfaceTexture: SurfaceTexture) {
            this.surfaceTexture = newSurfaceTexture
            // Nếu đang sử dụng EGL thủ công, có thể cần render thủ công
            // renderer.requestRender()
        }
        
        fun release() {
            // Giải phóng OpenGL resources
            if (currentShaderProgram != 0) {
                GLES20.glDeleteProgram(currentShaderProgram)
                currentShaderProgram = 0
            }
            
            if (externalTextureId != 0) {
                val textures = intArrayOf(externalTextureId)
                GLES20.glDeleteTextures(1, textures, 0)
                externalTextureId = 0
            }
            
            surfaceTexture?.release()
            surfaceTexture = null
            
            // Giải phóng EGL resources nếu có
            if (eglDisplay != null) {
                android.opengl.EGL14.eglDestroySurface(eglDisplay, eglSurface)
                android.opengl.EGL14.eglDestroyContext(eglDisplay, eglContext)
                android.opengl.EGL14.eglTerminate(eglDisplay)
                
                eglSurface = null
                eglContext = null
                eglDisplay = null
            }
        }
    }
} 