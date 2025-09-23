import Metal
import MetalKit
import CoreVideo
import AVFoundation

/**
 * MetalManager - Metal Rendering Pipeline cho iOS
 * 
 * SINGLE RESPONSIBILITY:
 * - Quản lý Metal device, command queue, render pipeline
 * - Handle Metal textures và buffers
 * - Apply beauty filters với Metal shaders
 * - Performance optimization với Metal
 * 
 * Design Pattern: Singleton + Delegate
 * Thread Safety: Thread-safe với DispatchQueue
 */
class MetalManager: NSObject {
    static let shared = MetalManager()
    
    // Metal objects
    private var device: MTLDevice?
    private var commandQueue: MTLCommandQueue?
    private var renderPipelineState: MTLRenderPipelineState?
    private var computePipelineState: MTLComputePipelineState?
    
    // Textures
    private var cameraTexture: MTLTexture?
    private var outputTexture: MTLTexture?
    
    // Buffers
    private var vertexBuffer: MTLBuffer?
    private var uniformBuffer: MTLBuffer?
    
    // Performance
    private let renderQueue = DispatchQueue(label: "metal.render.queue", qos: .userInteractive)
    private var isInitialized = false
    
    // Filter state
    private var filterEnabled = false
    private var filterIntensity: Float = 1.0
    private var currentFilter: FilterType = .none
    
    override init() {
        super.init()
        initializeMetal()
    }
    
    // MARK: - Initialization
    
    private func initializeMetal() {
        guard let device = MTLCreateSystemDefaultDevice() else {
            print("Metal is not supported on this device")
            return
        }
        
        self.device = device
        self.commandQueue = device.makeCommandQueue()
        
        setupRenderPipeline()
        setupComputePipeline()
        setupBuffers()
        
        isInitialized = true
        print("MetalManager initialized successfully")
    }
    
    private func setupRenderPipeline() {
        guard let device = device else { return }
        
        let library = device.makeDefaultLibrary()
        let vertexFunction = library?.makeFunction(name: "vertex_main")
        let fragmentFunction = library?.makeFunction(name: "fragment_main")
        
        let pipelineDescriptor = MTLRenderPipelineDescriptor()
        pipelineDescriptor.vertexFunction = vertexFunction
        pipelineDescriptor.fragmentFunction = fragmentFunction
        pipelineDescriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
        
        do {
            renderPipelineState = try device.makeRenderPipelineState(descriptor: pipelineDescriptor)
        } catch {
            print("Failed to create render pipeline state: \(error)")
        }
    }
    
    private func setupComputePipeline() {
        guard let device = device else { return }
        
        let library = device.makeDefaultLibrary()
        let computeFunction = library?.makeFunction(name: "beauty_filter_compute")
        
        do {
            computePipelineState = try device.makeComputePipelineState(function: computeFunction!)
        } catch {
            print("Failed to create compute pipeline state: \(error)")
        }
    }
    
    private func setupBuffers() {
        guard let device = device else { return }
        
        // Vertex buffer for full screen quad
        let vertices: [Float] = [
            -1.0, -1.0, 0.0, 1.0,  // Bottom-left
             1.0, -1.0, 1.0, 1.0,  // Bottom-right
            -1.0,  1.0, 0.0, 0.0,  // Top-left
             1.0,  1.0, 1.0, 0.0   // Top-right
        ]
        
        vertexBuffer = device.makeBuffer(bytes: vertices, length: vertices.count * MemoryLayout<Float>.size, options: [])
        
        // Uniform buffer for filter parameters
        let uniformSize = MemoryLayout<FilterUniforms>.size
        uniformBuffer = device.makeBuffer(length: uniformSize, options: [])
    }
    
    // MARK: - Public Methods
    
    func isReady() -> Bool {
        return isInitialized && device != nil && commandQueue != nil
    }
    
    func setFilterEnabled(_ enabled: Bool) {
        filterEnabled = enabled
    }
    
    func setFilterIntensity(_ intensity: Float) {
        filterIntensity = intensity
    }
    
    func setFilterType(_ type: FilterType) {
        currentFilter = type
    }
    
    // MARK: - Texture Processing
    
    func processCameraFrame(_ pixelBuffer: CVPixelBuffer, completion: @escaping (CVPixelBuffer?) -> Void) {
        renderQueue.async { [weak self] in
            guard let self = self, self.isReady() else {
                completion(nil)
                return
            }
            
            self.createTextureFromPixelBuffer(pixelBuffer)
            self.renderFrame()
            
            // Convert back to CVPixelBuffer
            let outputPixelBuffer = self.createPixelBufferFromTexture()
            completion(outputPixelBuffer)
        }
    }
    
    private func createTextureFromPixelBuffer(_ pixelBuffer: CVPixelBuffer) {
        guard let device = device else { return }
        
        let textureCache = CVMetalTextureCacheCreate(kCFAllocatorDefault, nil, device, nil, nil)
        var metalTexture: CVMetalTexture?
        
        let width = CVPixelBufferGetWidth(pixelBuffer)
        let height = CVPixelBufferGetHeight(pixelBuffer)
        
        CVMetalTextureCacheCreateTextureFromImage(
            kCFAllocatorDefault,
            textureCache!,
            pixelBuffer,
            nil,
            .bgra8Unorm,
            width,
            height,
            0,
            &metalTexture
        )
        
        cameraTexture = CVMetalTextureGetTexture(metalTexture!)
    }
    
    private func createPixelBufferFromTexture() -> CVPixelBuffer? {
        // Implementation to convert Metal texture back to CVPixelBuffer
        // This is a simplified version - in production you'd need proper conversion
        return nil
    }
    
    // MARK: - Rendering
    
    private func renderFrame() {
        guard let device = device,
              let commandQueue = commandQueue,
              let renderPipelineState = renderPipelineState,
              let cameraTexture = cameraTexture else { return }
        
        let commandBuffer = commandQueue.makeCommandBuffer()
        let renderPassDescriptor = MTLRenderPassDescriptor()
        
        // Setup render pass
        renderPassDescriptor.colorAttachments[0].texture = outputTexture
        renderPassDescriptor.colorAttachments[0].loadAction = .clear
        renderPassDescriptor.colorAttachments[0].clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)
        
        let renderEncoder = commandBuffer?.makeRenderCommandEncoder(descriptor: renderPassDescriptor)
        renderEncoder?.setRenderPipelineState(renderPipelineState)
        
        // Set vertex buffer
        renderEncoder?.setVertexBuffer(vertexBuffer, offset: 0, index: 0)
        
        // Set uniform buffer
        updateUniformBuffer()
        renderEncoder?.setFragmentBuffer(uniformBuffer, offset: 0, index: 0)
        
        // Set texture
        renderEncoder?.setFragmentTexture(cameraTexture, index: 0)
        
        // Draw
        renderEncoder?.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
        renderEncoder?.endEncoding()
        
        commandBuffer?.commit()
        commandBuffer?.waitUntilCompleted()
    }
    
    private func updateUniformBuffer() {
        guard let uniformBuffer = uniformBuffer else { return }
        
        let uniforms = FilterUniforms(
            filterEnabled: filterEnabled ? 1 : 0,
            filterIntensity: filterIntensity,
            filterType: Int32(currentFilter.rawValue),
            time: Float(Date().timeIntervalSince1970)
        )
        
        let pointer = uniformBuffer.contents().bindMemory(to: FilterUniforms.self, capacity: 1)
        pointer.pointee = uniforms
    }
}

// MARK: - Supporting Types

struct FilterUniforms {
    let filterEnabled: Int32
    let filterIntensity: Float
    let filterType: Int32
    let time: Float
}

enum FilterType: Int {
    case none = 0
    case beauty = 1
    case skinSmoothing = 2
    case brightness = 3
    case contrast = 4
}
