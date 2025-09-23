import Foundation
import Metal
import QuartzCore

/**
 * PerformanceManager - iOS Performance Optimization
 * 
 * SINGLE RESPONSIBILITY:
 * - Monitor FPS và performance metrics
 * - Adaptive quality control
 * - Memory management
 * - Frame rate limiting
 * 
 * Design Pattern: Singleton
 * Thread Safety: Thread-safe với DispatchQueue
 */
class PerformanceManager {
    static let shared = PerformanceManager()
    
    // Performance tracking
    private var frameCount = 0
    private var lastFrameTime: CFTimeInterval = 0
    private var currentFPS: Double = 0
    private var targetFPS: Double = 30.0
    
    // Adaptive quality
    private var currentQuality: Float = 1.0
    private var minQuality: Float = 0.5
    private var maxQuality: Float = 1.0
    
    // Memory management
    private var memoryWarningCount = 0
    private var lastMemoryCheck: CFTimeInterval = 0
    
    // Frame skipping
    private var frameSkipCount = 0
    private var maxFrameSkip = 2
    
    private let performanceQueue = DispatchQueue(label: "performance.queue", qos: .userInteractive)
    
    private init() {
        setupMemoryWarningObserver()
    }
    
    // MARK: - Public Methods
    
    func shouldRenderFrame() -> Bool {
        let currentTime = CACurrentMediaTime()
        
        // Check if we should skip this frame
        if frameSkipCount < maxFrameSkip {
            frameSkipCount += 1
            return false
        }
        
        frameSkipCount = 0
        
        // Update FPS
        updateFPS(currentTime: currentTime)
        
        // Adaptive quality based on performance
        adjustQualityBasedOnPerformance()
        
        return true
    }
    
    func getCurrentFPS() -> Double {
        return currentFPS
    }
    
    func getCurrentQuality() -> Float {
        return currentQuality
    }
    
    func setTargetFPS(_ fps: Double) {
        targetFPS = fps
    }
    
    func reset() {
        frameCount = 0
        lastFrameTime = 0
        currentFPS = 0
        currentQuality = 1.0
        memoryWarningCount = 0
        frameSkipCount = 0
    }
    
    // MARK: - Private Methods
    
    private func updateFPS(currentTime: CFTimeInterval) {
        frameCount += 1
        
        if lastFrameTime == 0 {
            lastFrameTime = currentTime
            return
        }
        
        let timeDelta = currentTime - lastFrameTime
        if timeDelta >= 1.0 {
            currentFPS = Double(frameCount) / timeDelta
            frameCount = 0
            lastFrameTime = currentTime
            
            print("Performance: FPS = \(String(format: "%.1f", currentFPS))")
        }
    }
    
    private func adjustQualityBasedOnPerformance() {
        let fpsRatio = currentFPS / targetFPS
        
        if fpsRatio < 0.8 {
            // Performance is poor, reduce quality
            currentQuality = max(minQuality, currentQuality - 0.1)
            maxFrameSkip = min(5, maxFrameSkip + 1)
            print("Performance: Reducing quality to \(currentQuality)")
        } else if fpsRatio > 1.2 {
            // Performance is good, increase quality
            currentQuality = min(maxQuality, currentQuality + 0.05)
            maxFrameSkip = max(0, maxFrameSkip - 1)
            print("Performance: Increasing quality to \(currentQuality)")
        }
    }
    
    private func setupMemoryWarningObserver() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(memoryWarningReceived),
            name: UIApplication.didReceiveMemoryWarningNotification,
            object: nil
        )
    }
    
    @objc private func memoryWarningReceived() {
        memoryWarningCount += 1
        currentQuality = max(minQuality, currentQuality - 0.2)
        maxFrameSkip = min(10, maxFrameSkip + 2)
        
        print("Performance: Memory warning received, reducing quality to \(currentQuality)")
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
    }
}
