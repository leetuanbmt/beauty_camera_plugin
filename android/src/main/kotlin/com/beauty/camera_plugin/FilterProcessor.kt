package com.beauty.camera_plugin

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLES11Ext
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bộ xử lý bộ lọc cho camera sử dụng OpenGL ES.
 * Hỗ trợ áp dụng các bộ lọc khác nhau lên preview camera.
 */
class FilterProcessor : SurfaceTexture.OnFrameAvailableListener {
    companion object {
        private const val TAG = "FilterProcessor"
        private const val NO_TEXTURE = -1
        
        // Vertex shader mặc định
        private const val DEFAULT_VERTEX_SHADER = """
            precision highp vec4;
            precision mediump vec2;
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
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            
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
        
        // Fragment shader cho bộ lọc solarize (đảo ngược ánh sáng)
        private const val SOLARIZE_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform float uIntensity;
            
            void main() {
                vec4 color = texture2D(sTexture, vTexCoord);
                vec3 solarized = step(0.5, color.rgb) * (1.0 - color.rgb) + step(color.rgb, 0.5) * color.rgb * 2.0;
                gl_FragColor = vec4(mix(color.rgb, solarized, uIntensity), color.a);
            }
        """
        
        // Fragment shader cho bộ lọc sepia (màu nâu cổ điển)
        private const val SEPIA_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform float uIntensity;
            
            void main() {
                vec4 color = texture2D(sTexture, vTexCoord);
                vec3 sepia = vec3(
                    dot(color.rgb, vec3(0.393, 0.769, 0.189)),
                    dot(color.rgb, vec3(0.349, 0.686, 0.168)),
                    dot(color.rgb, vec3(0.272, 0.534, 0.131))
                );
                gl_FragColor = vec4(mix(color.rgb, sepia, uIntensity), color.a);
            }
        """
        
        // Fragment shader cho bộ lọc vignette (viền tối)
        private const val VIGNETTE_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform float uIntensity;
            
            void main() {
                vec4 color = texture2D(sTexture, vTexCoord);
                
                // Tính toán vị trí cho vignette
                vec2 position = vTexCoord - 0.5;
                float vignette = 1.0 - length(position) * uIntensity * 1.5;
                vignette = clamp(vignette, 0.0, 1.0);
                
                gl_FragColor = vec4(color.rgb * vignette, color.a);
            }
        """
        
        // Fragment shader cho bộ lọc sharpen (làm sắc nét)
        private const val SHARPEN_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform float uSharpen;
            
            void main() {
                vec2 texelSize = vec2(1.0 / 1080.0, 1.0 / 1920.0);
                vec4 center = texture2D(sTexture, vTexCoord);
                
                // Sharpen kernel
                float kernel[9];
                kernel[0] = -1.0; kernel[1] = -1.0; kernel[2] = -1.0;
                kernel[3] = -1.0; kernel[4] = 9.0 + uSharpen * 4.0; kernel[5] = -1.0;
                kernel[6] = -1.0; kernel[7] = -1.0; kernel[8] = -1.0;
                
                vec4 sum = vec4(0.0);
                
                // Apply kernel
                sum += texture2D(sTexture, vTexCoord + vec2(-1.0, -1.0) * texelSize) * kernel[0];
                sum += texture2D(sTexture, vTexCoord + vec2(0.0, -1.0) * texelSize) * kernel[1];
                sum += texture2D(sTexture, vTexCoord + vec2(1.0, -1.0) * texelSize) * kernel[2];
                sum += texture2D(sTexture, vTexCoord + vec2(-1.0, 0.0) * texelSize) * kernel[3];
                sum += center * kernel[4];
                sum += texture2D(sTexture, vTexCoord + vec2(1.0, 0.0) * texelSize) * kernel[5];
                sum += texture2D(sTexture, vTexCoord + vec2(-1.0, 1.0) * texelSize) * kernel[6];
                sum += texture2D(sTexture, vTexCoord + vec2(0.0, 1.0) * texelSize) * kernel[7];
                sum += texture2D(sTexture, vTexCoord + vec2(1.0, 1.0) * texelSize) * kernel[8];
                
                gl_FragColor = sum;
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
    
    // SurfaceTexture và Surface
    private var surfaceTexture: SurfaceTexture? = null
    private var outputSurface: Surface? = null
    
    // Texture ID khi sử dụng external texture
    private var externalTextureId = NO_TEXTURE
    private var transformMatrix = FloatArray(16)
    private val transformMatrixLock = Object()
    
    // Filter registry - mapping CameraFilterMode to shader program
    private val filterShaders = mutableMapOf<CameraFilterMode, String>()
    
    // Thread và synchronization objects
    private var frameUpdaterRunning = AtomicBoolean(false)
    private var frameUpdaterThread: Thread? = null
    private val frameAvailable = AtomicBoolean(false)
    private val lock = Object()
    
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
    
    /**
     * Đăng ký các API cần thiết để ưu tiên EGL
     */
    private fun registerEGLAPIPriority() {
        try {
            val surfaceClass = Class.forName("android.view.Surface")
            val setEGLAPIPriorityMethod = surfaceClass.getDeclaredMethod("setEGLAPIPriority", Int::class.java)
            
            // Ưu tiên EGL (giá trị 1)
            setEGLAPIPriorityMethod.isAccessible = true
            
            Log.d(TAG, "Successfully registered EGL API priority")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set EGL API priority: ${e.message}", e)
        }
    }
    
    private fun registerDefaultShaders() {
        filterShaders[CameraFilterMode.NONE] = NO_FILTER_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.BEAUTY] = BEAUTY_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.MONO] = MONO_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.NEGATIVE] = NEGATIVE_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.BRIGHTNESS] = BRIGHTNESS_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.CONTRAST] = CONTRAST_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.SOLARIZE] = SOLARIZE_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.SEPIA] = SEPIA_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.VIGNETTE] = VIGNETTE_FRAGMENT_SHADER
        filterShaders[CameraFilterMode.SHARPEN] = SHARPEN_FRAGMENT_SHADER
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
        
        filters.add(FilterInfo(
            id = "solarize",
            mode = CameraFilterMode.SOLARIZE,
            displayName = "Solarize",
            adjustableParameters = listOf("intensity")
        ))
        
        filters.add(FilterInfo(
            id = "sepia",
            mode = CameraFilterMode.SEPIA,
            displayName = "Sepia",
            adjustableParameters = listOf("intensity")
        ))
        
        filters.add(FilterInfo(
            id = "vignette",
            mode = CameraFilterMode.VIGNETTE,
            displayName = "Vignette",
            adjustableParameters = listOf("intensity")
        ))
        
        filters.add(FilterInfo(
            id = "sharpen",
            mode = CameraFilterMode.SHARPEN,
            displayName = "Sharpen",
            adjustableParameters = listOf("sharpen")
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
        Log.d(TAG, "Releasing FilterProcessor resources")
        
        try {
            // Stop the frame updater thread
            frameUpdaterRunning.set(false)
            frameUpdaterThread?.join(1000)  // Wait up to 1 second for thread to finish
            frameUpdaterThread = null
            
            // Release the renderer
            renderer?.let {
                it.release()
                renderer = null
            }
            
            // Release SurfaceTexture and Surface
            surfaceTexture?.let {
                it.release()
                surfaceTexture = null
            }
            
            outputSurface?.let {
                it.release()
                outputSurface = null
            }
            
            // Clean up any other resources
            externalTextureId = NO_TEXTURE
            transformMatrix = FloatArray(16)
            
            Log.d(TAG, "FilterProcessor resources released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing FilterProcessor resources", e)
        }
    }
    
    /**
     * Thiết lập renderer với surface từ camera
     */
    fun setupRenderer(surface: Surface): Boolean {
        try {
            // Đảm bảo giải phóng renderer cũ nếu có
            release()
            
            // ===== Thử ngắt kết nối surface khỏi các API khác trước khi sử dụng =====
            var disconnected = false
            
            try {
                // Phương pháp 1: Sử dụng nativeDisconnectFromSurfaceTexture
                try {
                    val surfaceClass = Class.forName("android.view.Surface")
                    val disconnectMethod = surfaceClass.getDeclaredMethod("nativeDisconnectFromSurfaceTexture", Int::class.java)
                    disconnectMethod.isAccessible = true
                    
                    // Lấy native handle của surface
                    val getNativeHandleMethod = surfaceClass.getDeclaredMethod("getNativeHandle")
                    getNativeHandleMethod.isAccessible = true
                    val nativeHandle = getNativeHandleMethod.invoke(surface) as Int
                    
                    // Gọi phương thức disconnect với native handle
                    disconnectMethod.invoke(null, nativeHandle)
                    Log.d(TAG, "Successfully disconnected surface using nativeDisconnectFromSurfaceTexture")
                    disconnected = true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to disconnect using nativeDisconnectFromSurfaceTexture: ${e.message}")
                }
                
                // Phương pháp 2: Thử setEGLAPIPriority - đặt EGL làm ưu tiên cao nhất
                if (!disconnected) {
                    try {
                        val surfaceClass = Class.forName("android.view.Surface")
                        val setEGLAPIPriorityMethod = surfaceClass.getDeclaredMethod("setEGLAPIPriority", Int::class.java)
                        setEGLAPIPriorityMethod.isAccessible = true
                        setEGLAPIPriorityMethod.invoke(surface, 1) // 1 = ưu tiên EGL
                        Log.d(TAG, "Successfully set EGL API priority")
                        disconnected = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not set EGL API priority: ${e.message}")
                    }
                }
                
                // Phương pháp 3: Thử detach surface từ owner hiện tại
                if (!disconnected) {
                    try {
                        val surfaceClass = Class.forName("android.view.Surface") 
                        val detachFromOwnerMethod = surfaceClass.getDeclaredMethod("detachFromOwner")
                        detachFromOwnerMethod.isAccessible = true
                        detachFromOwnerMethod.invoke(surface)
                        Log.d(TAG, "Successfully detached surface from owner")
                        disconnected = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not detach surface from owner: ${e.message}")
                    }
                }
                
                // Phương pháp 4: Thử hạ cờ kết nối bằng cách lockCanvas và unlockCanvas
                if (!disconnected) {
                    try {
                        val canvas = surface.lockCanvas(null)
                        if (canvas != null) {
                            surface.unlockCanvasAndPost(canvas)
                            Log.d(TAG, "Successfully reset surface connection using lockCanvas/unlockCanvas")
                            disconnected = true
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not reset surface connection using canvas locking: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not disconnect surface from previous API, may cause EGL errors", e)
            }

            Log.d(TAG, "Surface disconnection attempt result: ${if (disconnected) "SUCCESS" else "FAILED"}")
            
            // Thêm delay để đảm bảo các thay đổi surface được áp dụng
            if (disconnected) {
                Thread.sleep(20)
            }

            // Thử tạo renderer với surface
            try {
                // Lưu surface hiện tại
                outputSurface = surface
                
                // Khởi tạo renderer với surface từ camera
                renderer = FilterRenderer(surface)
                Log.d(TAG, "Renderer created successfully with provided surface")
                
                // Khởi tạo các tham số và filter
                setFilter(currentFilter, currentParameters)
                
                return true
            } catch (e: Exception) {
                // Thử tạo surface mới nếu không thể sử dụng surface hiện tại
                Log.e(TAG, "Error creating renderer with provided surface: ${e.message}", e)
                
                try {
                    // Tạo một SurfaceTexture mới
                    surfaceTexture = SurfaceTexture(0)
                    surfaceTexture?.setDefaultBufferSize(1080, 1920) // Mặc định, sẽ được điều chỉnh sau
                    val newSurface = Surface(surfaceTexture)
                    outputSurface = newSurface
                    
                    // Khởi tạo renderer với surface mới
                    renderer = FilterRenderer(newSurface)
                    Log.d(TAG, "Created renderer with new surface as alternative")
                    
                    // Khởi tạo frame updater thread
                    startFrameUpdaterThread(null)
                    
                    // Khởi tạo các tham số và filter
                    setFilter(currentFilter, currentParameters)
                    
                    return true
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to create renderer even with alternative approach: ${e2.message}", e2)
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup renderer: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Được gọi khi có frame mới từ camera
     */
    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        Log.v(TAG, "Frame available")
        
        synchronized(lock) {
            // Không lưu trữ surfaceTexture mới vào instance variable
            // vì nó thuộc về CameraX và chúng ta không muốn mất tham chiếu gốc
            // this.surfaceTexture = surfaceTexture
            
            // Nếu chưa khởi tạo thread cập nhật frame, khởi tạo nó
            if (frameUpdaterThread == null || !frameUpdaterThread!!.isAlive) {
                startFrameUpdaterThread(surfaceTexture)
            }
            
            // Signal để cập nhật frame
            frameAvailable.set(true)
            lock.notifyAll()
        }
    }

    /**
     * Khởi tạo thread cập nhật frame từ camera
     */
    private fun startFrameUpdaterThread(cameraTexture: SurfaceTexture?) {
        Log.d(TAG, "Starting frame updater thread")
        
        // Nếu thread đã tồn tại và đang chạy, không cần khởi tạo lại
        if (frameUpdaterThread?.isAlive == true && frameUpdaterRunning.get()) {
            Log.d(TAG, "Frame updater thread is already running")
            return
        }
        
        // Đảm bảo chỉ chạy khi có texture hợp lệ
        if (cameraTexture == null) {
            Log.w(TAG, "Cannot start frame updater thread - no valid texture available")
            return
        }
        
        // Set flag đang chạy
        frameUpdaterRunning.set(true)
        
        // Tạo và khởi chạy thread
        frameUpdaterThread = Thread {
            Log.d(TAG, "Frame updater thread started")
            
            try {
                while (frameUpdaterRunning.get() && !Thread.interrupted()) {
                    var updated = false
                    
                    synchronized(lock) {
                        if (frameAvailable.get()) {
                            try {
                                if (renderer != null) {
                                    // Yêu cầu vẽ frame - không cần updateTexImage() ở đây
                                    // vì đã được thực hiện trong BeautyCameraManager
                                    renderer!!.drawFrame(cameraTexture)
                                }
                                
                                // Đánh dấu đã cập nhật
                                frameAvailable.set(false)
                                updated = true
                            } catch (e: Exception) {
                                // Nếu có lỗi khi cập nhật texture, log lỗi và tiếp tục
                                Log.e(TAG, "Error processing frame", e)
                                frameAvailable.set(false)
                            }
                        }
                    }
                    
                    if (!updated) {
                        // Nếu không có frame mới, đợi một lúc
                        try {
                            // Đợi khoảng 16ms để giữ tốc độ khoảng 60fps
                            Thread.sleep(16)
                        } catch (e: InterruptedException) {
                            Log.d(TAG, "Frame updater thread was interrupted", e)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in frame updater thread", e)
            } finally {
                Log.d(TAG, "Frame updater thread stopping")
                frameUpdaterRunning.set(false)
            }
        }
        
        // Set thread priorities và start
        frameUpdaterThread!!.name = "FilterProcessor-FrameUpdater"
        frameUpdaterThread!!.priority = Thread.MAX_PRIORITY
        frameUpdaterThread!!.start()
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
            try {
                // Lấy EGL display mặc định
                eglDisplay = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
                if (eglDisplay == android.opengl.EGL14.EGL_NO_DISPLAY) {
                    throw RuntimeException("Unable to get EGL14 display")
                }
                
                // Khởi tạo EGL
                val version = IntArray(2)
                if (!android.opengl.EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                    throw RuntimeException("Unable to initialize EGL14")
                }
                Log.d(TAG, "EGL initialized successfully. Version: ${version[0]}.${version[1]}")
                
                // Cấu hình EGL
                val configAttribs = intArrayOf(
                    android.opengl.EGL14.EGL_RENDERABLE_TYPE, android.opengl.EGL14.EGL_OPENGL_ES2_BIT,
                    android.opengl.EGL14.EGL_RED_SIZE, 8,
                    android.opengl.EGL14.EGL_GREEN_SIZE, 8,
                    android.opengl.EGL14.EGL_BLUE_SIZE, 8,
                    android.opengl.EGL14.EGL_ALPHA_SIZE, 8,
                    android.opengl.EGL14.EGL_DEPTH_SIZE, 16,
                    android.opengl.EGL14.EGL_STENCIL_SIZE, 0,
                    android.opengl.EGL14.EGL_NONE
                )
                
                val configs = Array<android.opengl.EGLConfig?>(1) { null }
                val numConfigs = IntArray(1)
                if (!android.opengl.EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, configs.size, numConfigs, 0)) {
                    throw RuntimeException("Unable to find a suitable EGLConfig")
                }
                
                // Tạo EGL context
                val contextAttribs = intArrayOf(
                    android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    android.opengl.EGL14.EGL_NONE
                )
                eglContext = android.opengl.EGL14.eglCreateContext(eglDisplay, configs[0], android.opengl.EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
                if (eglContext == android.opengl.EGL14.EGL_NO_CONTEXT) {
                    throw RuntimeException("Unable to create EGL14 context")
                }
                
                // Tạo EGL surface
                val surfaceAttribs = intArrayOf(
                    android.opengl.EGL14.EGL_NONE
                )
                
                // Chuẩn bị surface - thử các phương pháp khác nhau
                var success = false
                var errorDetails = ""
                
                // Thử phương pháp 1: Sử dụng trực tiếp surface
                try {
                    // Thử disconnectFromSurfaceTexture trước
                    val surfaceClass = surface.javaClass
                    try {
                        // Thử gọi phương thức native để ngắt kết nối surface từ các API khác
                        val method = surfaceClass.getDeclaredMethod("nativeDisconnectFromSurfaceTexture")
                        method.isAccessible = true
                        method.invoke(surface)
                        Log.d(TAG, "Successfully disconnected surface from previous API before creating EGL surface")
                    } catch (e: Exception) {
                        Log.e(TAG, "Unable to disconnect surface from previous API", e)
                        // Tiếp tục mặc dù không thể ngắt kết nối
                    }
                    
                    // Thêm một chút delay để đảm bảo trạng thái surface ổn định
                    Thread.sleep(10)
                    
                    // Thực hiện tạo EGL surface
                    eglSurface = android.opengl.EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
                    
                    if (eglSurface != android.opengl.EGL14.EGL_NO_SURFACE) {
                        success = true
                        Log.d(TAG, "Created EGL surface successfully with direct surface approach")
                    }
                } catch (e: Exception) {
                    errorDetails += "Direct surface approach failed: ${e.message}\n"
                    Log.e(TAG, "Failed to create EGL surface with direct approach", e)
                }
                
                // Phương pháp 2: Thử tạo một surface mới từ SurfaceTexture nếu phương pháp 1 thất bại
                if (!success) {
                    try {
                        Log.d(TAG, "Trying fallback approach with a new SurfaceTexture")
                        val surfaceTexture = SurfaceTexture(0)
                        surfaceTexture.setDefaultBufferSize(1080, 1920) // Sử dụng kích thước mặc định
                        val newSurface = Surface(surfaceTexture)
                        
                        eglSurface = android.opengl.EGL14.eglCreateWindowSurface(eglDisplay, configs[0], newSurface, surfaceAttribs, 0)
                        
                        if (eglSurface != android.opengl.EGL14.EGL_NO_SURFACE) {
                            success = true
                            Log.d(TAG, "Created EGL surface successfully with new SurfaceTexture approach")
                        } else {
                            val error = android.opengl.EGL14.eglGetError()
                            errorDetails += "New SurfaceTexture approach failed with error: $error\n"
                        }
                    } catch (e: Exception) {
                        errorDetails += "New SurfaceTexture approach failed: ${e.message}\n"
                        Log.e(TAG, "Failed to create EGL surface with new SurfaceTexture", e)
                    }
                }
                
                // Nếu cả hai phương pháp đều thất bại, throw exception
                if (!success) {
                    throw RuntimeException("Unable to create EGL surface after trying multiple approaches: $errorDetails")
                }
                
                // Make the context current
                if (!android.opengl.EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    val error = android.opengl.EGL14.eglGetError()
                    throw RuntimeException("Unable to make EGL14 context current with error: $error")
                }
                
                Log.d(TAG, "EGL setup completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up EGL", e)
                releaseEGL()
                throw e
            }
        }
        
        private fun releaseEGL() {
            if (eglDisplay != null) {
                if (eglSurface != null) {
                    android.opengl.EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    eglSurface = null
                }
                if (eglContext != null) {
                    android.opengl.EGL14.eglDestroyContext(eglDisplay, eglContext)
                    eglContext = null
                }
                android.opengl.EGL14.eglTerminate(eglDisplay)
                eglDisplay = null
            }
        }
        
        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            
            // Tạo texture cho camera input
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            externalTextureId = textures[0]
            
            // Kết nối texture với SurfaceTexture
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            
            // Kiểm tra lỗi OpenGL
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "GL error after texture setup: $error")
            }
            
            // Điều chỉnh shader mặc định cho external texture
            val modifiedNoFilterShader = NO_FILTER_FRAGMENT_SHADER
            
            // Khởi tạo shader program ban đầu với filter hiện tại
            programHandle = createProgram(DEFAULT_VERTEX_SHADER, modifiedNoFilterShader)
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
            
            // Kiểm tra lỗi OpenGL sau khi setup
            val finalError = GLES20.glGetError()
            if (finalError != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "GL error after shader setup: $finalError")
            } else {
                Log.d(TAG, "OpenGL initialization successful")
            }
        }
        
        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            
            // Cập nhật projection matrix nếu cần
            // Ví dụ: nếu cần apply các transform khác
            Matrix.setIdentityM(mvpMatrix, 0)
        }
        
        override fun onDrawFrame(gl: GL10) {
            // This method is required by the GLSurfaceView.Renderer interface
            // If we have a surfaceTexture, process it
            surfaceTexture?.let { 
                drawFrame(it)
            }
        }
        
        fun drawFrame(surfaceTexture: SurfaceTexture) {
            try {
                if (programHandle <= 0) {
                    Log.e(TAG, "Program not initialized")
                    return
                }

                // Đảm bảo đang trên đúng thread OpenGL
                if (eglDisplay == null || eglContext == null || eglSurface == null) {
                    Log.e(TAG, "EGL context not ready")
                    return
                }
                
                // Make current context if needed
                if (!android.opengl.EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    Log.e(TAG, "Failed to make EGL context current")
                    return
                }

                // Update texture image from camera
                surfaceTexture.updateTexImage()

                // Get the transformation matrix from SurfaceTexture
                val transformMatrix = FloatArray(16)
                surfaceTexture.getTransformMatrix(transformMatrix)

                // Clear the rendering surface
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
                
                // Enable blending for transparency
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

                // Use our shader program
                GLES20.glUseProgram(programHandle)

                // Set the MVP matrix
                GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

                // Set the texture transformation matrix
                GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, transformMatrix, 0)

                // Active texture unit
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                
                // Bind correct texture based on filter type
                if (currentFilter == CameraFilterMode.NONE) {
                    // For no filter, use external texture directly
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
                } else {
                    // For filters, use the processed 2D texture
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, externalTextureId)
                }
                
                // Verify texture binding
                val error = GLES20.glGetError()
                if (error != GLES20.GL_NO_ERROR) {
                    Log.e(TAG, "GL error after binding texture: $error")
                }

                // Set the vertex attributes
                GLES20.glEnableVertexAttribArray(positionHandle)
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

                // Set texture coordinates
                GLES20.glEnableVertexAttribArray(texCoordHandle)
                GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

                // Apply any uniform parameters for the current filter
                applyFilterParameters(currentParameters)

                // Draw the rectangle
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                // Disable vertex arrays
                GLES20.glDisableVertexAttribArray(positionHandle)
                GLES20.glDisableVertexAttribArray(texCoordHandle)
                
                // Disable blending
                GLES20.glDisable(GLES20.GL_BLEND)
                
                // Swap buffers to display rendering
                android.opengl.EGL14.eglSwapBuffers(eglDisplay, eglSurface)

            } catch (e: Exception) {
                Log.e(TAG, "Error in drawFrame", e)
            }
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
            releaseEGL()
        }
        
        fun updateShaderProgram(filterMode: CameraFilterMode, fragmentShader: String, parameters: FilterParameters) {
            currentShader = fragmentShader
            
            // Log để debug
            Log.d(TAG, "Updating shader program for filter: $filterMode")
            
            // Phải đảm bảo chúng ta đang chạy trên GL thread
            // Có thể cần queueEvent nếu không đang ở GL thread
            
            // Make sure we're on the correct GL thread
            if (eglDisplay != null && eglContext != null && eglSurface != null) {
                if (!android.opengl.EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    Log.e(TAG, "Failed to make EGL context current during shader update")
                    return
                }
            }
            
            // Modify shader if needed for external texture
            var modifiedShader = fragmentShader
            if (filterMode == CameraFilterMode.NONE) {
                // Make sure we're using external texture in the shader
                if (!modifiedShader.contains("GL_OES_EGL_image_external")) {
                    modifiedShader = "#extension GL_OES_EGL_image_external : require\n" + modifiedShader
                }
                if (modifiedShader.contains("sampler2D sTexture")) {
                    modifiedShader = modifiedShader.replace("sampler2D sTexture", "samplerExternalOES sTexture")
                }
            }
            
            // Create the shader program
            programHandle = createProgram(DEFAULT_VERTEX_SHADER, modifiedShader)
            
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
                CameraFilterMode.MONO, CameraFilterMode.NEGATIVE, CameraFilterMode.SOLARIZE, 
                CameraFilterMode.SEPIA, CameraFilterMode.VIGNETTE -> {
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
                CameraFilterMode.SHARPEN -> {
                    uniformHandles["uSharpen"] = GLES20.glGetUniformLocation(programHandle, "uSharpen")
                }
                else -> { /* Các filter khác */ }
            }
            
            // Áp dụng các tham số filter
            applyFilterParameters(parameters)
            
            // Kiểm tra lỗi OpenGL
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "GL error after shader update: $error")
            } else {
                Log.d(TAG, "Shader program updated successfully with programHandle: $programHandle")
            }
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
                Log.e(TAG, "Shader compilation failed! Type: ${if (type == GLES20.GL_VERTEX_SHADER) "VERTEX" else "FRAGMENT"}")
                Log.e(TAG, "Shader Code: $shaderCode")
                Log.e(TAG, "Compilation error: $info")
                GLES20.glDeleteShader(shaderHandle)
                throw RuntimeException("Could not compile shader (${if (type == GLES20.GL_VERTEX_SHADER) "vertex" else "fragment"}): $info")
            }
            
            return shaderHandle
        }
    }
} 