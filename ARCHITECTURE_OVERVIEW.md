# Beauty Camera Plugin - Architecture Overview

## 🏗️ **Kiến Trúc Tổng Quan**

Plugin đã được cải thiện theo **Clean Architecture** với **Single Responsibility Principle** và **FilterChain Pattern**.

### **Cấu Trúc Thư Mục Cuối Cùng**

```
android/src/main/kotlin/com/beauty/camera_plugin/
├── BeautyCameraPlugin.kt                    # Main plugin orchestrator
├── BeautyCameraPluginPigeon.g.kt            # Generated Pigeon code
├── camera/                                  # Camera layer
│   ├── BaseCameraManager.kt                 # Camera interface
│   ├── CameraController.kt                  # Camera implementation
│   └── CameraUtils.kt                       # Camera utilities
├── flutter_bridge/                          # Flutter integration
│   └── FlutterTextureBridge.kt              # Texture management
└── renderer/                                # Rendering layer
    ├── GlRenderer.kt                        # OpenGL renderer + utilities
    ├── RenderPipelineManager.kt             # Pipeline management
    └── filters/                             # Filter system
        ├── BaseFilter.kt                    # Filter interface & chain
        ├── BeautySmoothFilter.kt            # Skin smoothing
        ├── WhiteningFilter.kt               # Skin brightening
        └── LutFilter.kt                     # Color grading
```

### **Assets Structure**

```
android/src/main/assets/
└── shaders/                                 # GLSL shader files
    ├── base_vertex.glsl                     # Base vertex shader
    ├── passthrough_fragment.glsl            # Passthrough filter
    ├── beauty_smooth_fragment.glsl          # Skin smoothing
    ├── whitening_fragment.glsl              # Skin brightening
    ├── lut_fragment.glsl                    # Color grading
    └── color_correction_fragment.glsl       # Color adjustments
```

## 🎯 **Nguyên Tắc Thiết Kế**

### **1. Single Responsibility Principle**

- **BeautyCameraPlugin**: Orchestrator, lifecycle management
- **CameraController**: Camera operations only
- **RenderPipelineManager**: Rendering pipeline only
- **FlutterTextureBridge**: Texture management only
- **Filters**: Individual filter implementations

### **2. FilterChain Pattern**

```kotlin
interface IFilter {
    fun initialize(): Boolean
    fun applyFilter(inputTexture: Int, outputTexture: Int): Int
    fun setIntensity(intensity: Float)
    fun dispose()
}

class FilterChain {
    private val filters = mutableListOf<IFilter>()

    fun addFilter(filter: IFilter) { filters.add(filter) }
    fun removeFilter(filter: IFilter) { filters.remove(filter) }
    fun processChain(inputTexture: Int): Int { /* Chain processing */ }
}
```

### **3. Threading Model**

- **Camera Thread**: Managed by CameraX internally
- **Render Thread**: Dedicated ExecutorService for OpenGL operations
- **Main Thread**: UI updates and plugin lifecycle

### **4. Lifecycle Management**

```kotlin
// Plugin Lifecycle
onAttachedToActivity() -> initializeComponents()
onDetachedFromActivity() -> disposeComponents()
onDetachedFromEngine() -> cleanup()
```

## 🔄 **Data Flow**

```
Flutter UI
    ↓ (Pigeon API calls)
BeautyCameraPlugin (Orchestrator)
    ↓ (Camera operations)
CameraController (CameraX)
    ↓ (Surface texture)
FlutterTextureBridge
    ↓ (Render requests)
RenderPipelineManager
    ↓ (Filter chain)
GlRenderer + Filters
    ↓ (OpenGL rendering)
Flutter Texture (Display)
```

## 📱 **Flutter Integration**

### **Plugin Usage**

```dart
// Initialize camera
await beautyCameraController.initialize(
  settings: AdvancedCameraSettings(
    resolution: ResolutionInfo(width: 1280, height: 720),
    fps: 30,
    facing: CameraFacing.front,
  ),
);

// Apply beauty filter
await beautyCameraController.applyFilter(
  FilterParameters(
    category: FilterCategory.beauty,
    type: BeautyFilterType.smooth,
    intensity: 0.8,
  ),
);

// Take photo
final photo = await beautyCameraController.takePhoto();
```

### **Widget Integration**

```dart
BeautyCameraView(
  controller: beautyCameraController,
  onCameraReady: () => print('Camera ready'),
  onError: (error) => print('Error: $error'),
)
```

## 🚀 **Performance Optimizations**

### **1. Memory Management**

- Texture pooling for filters
- Proper OpenGL resource disposal
- Camera buffer management

### **2. Rendering Pipeline**

- Single-pass rendering when possible
- Filter chain optimization
- Frame rate monitoring

### **3. Threading**

- Non-blocking camera operations
- Dedicated render thread
- Async filter processing

## 🔧 **Development Guidelines**

### **Adding New Filters**

1. Implement `IFilter` interface
2. Add to `FilterChain` in `RenderPipelineManager`
3. Create GLSL shader files
4. Update filter presets

### **Camera Features**

1. Extend `ICameraManager` interface
2. Implement in `CameraController`
3. Add Pigeon API methods
4. Update Flutter controller

### **Testing**

- Unit tests for individual components
- Integration tests for camera operations
- Performance tests for rendering pipeline

## 📋 **TODO & Future Enhancements**

### **Phase 1: Core Stability**

- [ ] Complete filter implementations
- [ ] Performance optimization
- [ ] Error handling improvements

### **Phase 2: Advanced Features**

- [ ] AR sticker filters
- [ ] Face detection integration
- [ ] Video recording with filters

### **Phase 3: Platform Expansion**

- [ ] iOS implementation
- [ ] Desktop support
- [ ] Web support

## 🎉 **Kết Quả**

Plugin giờ đây có:

- ✅ **Clean Architecture** với separation of concerns rõ ràng
- ✅ **FilterChain Pattern** cho extensibility
- ✅ **Proper Threading** với CameraX và dedicated render thread
- ✅ **Lifecycle Management** đúng cách
- ✅ **Type-safe Communication** với Pigeon
- ✅ **Performance Optimized** rendering pipeline

Cấu trúc này cho phép dễ dàng mở rộng và maintain trong tương lai! 🚀
