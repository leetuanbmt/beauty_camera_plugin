# CameraX + OpenGL Implementation Plan

## 🎯 **Mục tiêu**

Tạo beauty camera plugin với:

- **CameraX** cho camera management
- **OpenGL** cho real-time filter processing
- **Clean architecture** đơn giản và hiệu quả

## 📐 **Architecture Design**

### **Simple & Clean Architecture:**

```
Dart (Pigeon API)
        ↓
BeautyCameraPlugin (Entry Point)
        ↓
┌─────────────────┬─────────────────┐
│   CameraManager │  FilterManager  │
│   (CameraX)     │   (OpenGL)      │
└─────────────────┴─────────────────┘
        ↓                 ↓
   Android CameraX    OpenGL ES
```

## 🏗️ **Component Design**

### **1. CameraManager (Singleton)**

```kotlin
object CameraManager {
    // SINGLE RESPONSIBILITY: Quản lý CameraX operations

    fun initialize(context: Context, lifecycleOwner: LifecycleOwner)
    fun startPreview(surfaceProvider: Preview.SurfaceProvider)
    fun switchCamera(): CameraFacing
    fun takePhoto(): String
    fun startVideoRecording(outputPath: String)
    fun stopVideoRecording(): String
    fun dispose()
}
```

**Responsibilities:**

- ✅ CameraX lifecycle management
- ✅ Camera facing switch (front/back)
- ✅ Photo capture
- ✅ Video recording
- ✅ Preview management

### **2. FilterManager (Singleton)**

```kotlin
object FilterManager {
    // SINGLE RESPONSIBILITY: Quản lý OpenGL filter processing

    fun initialize(context: Context)
    fun createFilterSurface(): Surface
    fun setOutputSurface(surface: Surface)
    fun applyFilter(filterType: FilterType, intensity: Float)
    fun setFilterEnabled(enabled: Boolean)
    fun dispose()
}
```

**Responsibilities:**

- ✅ OpenGL context management
- ✅ Shader compilation & management
- ✅ Filter processing pipeline
- ✅ Surface texture handling
- ✅ Real-time rendering

### **3. BeautyCameraPlugin (Entry Point)**

```kotlin
class BeautyCameraPlugin : FlutterPlugin, ActivityAware, BeautyCameraHostApi {
    // SINGLE RESPONSIBILITY: Pigeon API bridge

    // Chỉ delegate calls đến managers:
    override fun initialize() -> CameraManager.initialize()
    override fun switchCamera() -> CameraManager.switchCamera()
    override fun applyFilter() -> FilterManager.applyFilter()
}
```

**Responsibilities:**

- ✅ Pigeon API implementation
- ✅ Flutter plugin lifecycle
- ✅ Delegation to managers
- ❌ NO business logic
- ❌ NO direct Android API calls

## 🔄 **Data Flow**

### **Camera Preview Flow:**

```
CameraX → FilterManager (OpenGL) → Flutter Texture → Dart UI
```

1. **CameraX** capture frames
2. **FilterManager** process với OpenGL filters
3. **Flutter Texture** display processed frames
4. **Dart UI** hiển thị camera preview

### **Filter Processing Flow:**

```
Dart Filter Request → FilterManager → OpenGL Shaders → Processed Frame
```

1. **Dart** gửi filter request
2. **FilterManager** load appropriate shader
3. **OpenGL** process frame với filter
4. **Result** hiển thị real-time

## 📝 **Implementation Plan**

### **Phase 1: Core Camera (Priority 1)**

```kotlin
// File: CameraManager.kt
object CameraManager {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var currentFacing = CameraSelector.LENS_FACING_BACK

    fun initialize(context: Context, lifecycleOwner: LifecycleOwner): Boolean
    fun startPreview(surfaceProvider: Preview.SurfaceProvider): Boolean
    fun switchCamera(): Int // Return new facing
    fun dispose()
}
```

### **Phase 2: OpenGL Integration (Priority 2)**

```kotlin
// File: FilterManager.kt
object FilterManager {
    private var eglContext: EGLContext? = null
    private var inputSurface: Surface? = null
    private var outputSurface: Surface? = null
    private var shaderProgram: Int = 0

    fun initialize(context: Context): Boolean
    fun createInputSurface(): Surface
    fun setOutputSurface(surface: Surface)
    fun processFrame() // Real-time processing
    fun dispose()
}
```

### **Phase 3: Filter Pipeline (Priority 3)**

```kotlin
// Filters implementation
enum class FilterType {
    NONE, BEAUTY, VINTAGE, VIBRANT, PORTRAIT
}

// Shader management
class ShaderManager {
    fun loadShader(filterType: FilterType): Int
    fun applyFilter(program: Int, intensity: Float)
}
```

### **Phase 4: Integration (Priority 4)**

```kotlin
// BeautyCameraPlugin integration
class BeautyCameraPlugin {
    override fun initialize() {
        // 1. Initialize CameraManager
        // 2. Initialize FilterManager
        // 3. Connect camera → filter → flutter
    }
}
```

## 🔧 **Technical Specifications**

### **CameraX Configuration:**

```kotlin
// Preview setup
Preview.Builder()
    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
    .setTargetRotation(Surface.ROTATION_0)
    .build()

// Camera selector
CameraSelector.Builder()
    .requireLensFacing(facing)
    .build()
```

### **OpenGL Setup:**

```kotlin
// EGL context
EGLContext.create()
EGLSurface.createWindowSurface()

// Shader pipeline
Vertex Shader → Fragment Shader → Program Link → Render
```

### **Surface Connection:**

```
CameraX Output → OpenGL Input Surface → Filter Processing → OpenGL Output Surface → Flutter Texture
```

## 📊 **Performance Considerations**

### **Memory Management:**

- ✅ Singleton managers để tránh multiple instances
- ✅ Proper disposal trong lifecycle events
- ✅ Surface texture recycling

### **Threading:**

- ✅ CameraX trên main thread
- ✅ OpenGL trên dedicated GL thread
- ✅ Synchronization giữa threads

### **Resource Optimization:**

- ✅ Lazy initialization của heavy resources
- ✅ Efficient shader compilation
- ✅ Frame rate optimization

## 🧪 **Testing Strategy**

### **Unit Testing:**

- ✅ CameraManager operations
- ✅ FilterManager shader loading
- ✅ Plugin API responses

### **Integration Testing:**

- ✅ Camera → Filter → Flutter flow
- ✅ Lifecycle events handling
- ✅ Error scenarios

### **Performance Testing:**

- ✅ Frame rate measurement
- ✅ Memory usage monitoring
- ✅ Battery impact assessment

## 🚀 **Implementation Steps**

### **Step 1: CameraManager Foundation**

1. Create `CameraManager.kt` singleton
2. Implement basic CameraX setup
3. Test camera preview display
4. Add switch camera functionality

### **Step 2: FilterManager Foundation**

1. Create `FilterManager.kt` singleton
2. Setup basic OpenGL context
3. Create pass-through shader
4. Test surface connection

### **Step 3: Integration**

1. Connect CameraManager → FilterManager
2. Test camera preview through filter
3. Verify performance

### **Step 4: Filter Implementation**

1. Add beauty filter shaders
2. Implement filter switching
3. Add intensity control
4. Performance optimization

## 📋 **Success Criteria**

### **Functional Requirements:**

- ✅ Camera preview hiển thị đúng
- ✅ Switch camera hoạt động smooth
- ✅ Filters apply real-time
- ✅ Good performance (>24fps)

### **Non-Functional Requirements:**

- ✅ Clean, maintainable code
- ✅ Proper error handling
- ✅ Memory efficient
- ✅ Battery optimized

## 🔄 **Rollback Plan**

Nếu có vấn đề:

1. **Backup available**: `BeautyCameraPlugin_BACKUP.kt`
2. **Git restore**: `git restore .`
3. **Incremental approach**: Implement từng feature một

## 📚 **References**

- [Android CameraX Documentation](https://developer.android.com/training/camerax)
- [OpenGL ES for Android](https://developer.android.com/guide/topics/graphics/opengl)
- [Flutter Plugin Development](https://docs.flutter.dev/development/packages-and-plugins/developing-packages)
- [Pigeon Code Generation](https://pub.dev/packages/pigeon)

---

**🎯 READY TO START: Implement từng phase một cách cẩn thận và có thể test**
