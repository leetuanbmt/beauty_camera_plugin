import AVFoundation
import Metal

// Delegate để gửi frame buffer về cho plugin
protocol CameraFrameDelegate: AnyObject {
    func didOutput(sampleBuffer: CMSampleBuffer)
    func didOutput(pixelBuffer: CVPixelBuffer)
}

class CameraHandler: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    private var captureSession: AVCaptureSession?
    private let sessionQueue = DispatchQueue(label: "session queue")
    private var metalManager: MetalManager?
    weak var delegate: CameraFrameDelegate?
    
    // Filter settings
    private var filterEnabled = false
    private var filterIntensity: Float = 1.0
    private var currentFilter: FilterType = .none

    func initialize(completion: @escaping (Error?) -> Void) {
        sessionQueue.async {
            do {
                try self.setupCaptureSession()
                self.metalManager = MetalManager.shared
                completion(nil)
            } catch {
                completion(error)
            }
        }
    }

    private func setupCaptureSession() throws {
        let session = AVCaptureSession()
        session.beginConfiguration()

        // Tìm camera sau
        guard let videoDevice = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            throw NSError(domain: "CameraHandler", code: -1, userInfo: [NSLocalizedDescriptionKey: "No back camera found"])
        }
        let videoInput = try AVCaptureDeviceInput(device: videoDevice)
        if session.canAddInput(videoInput) {
            session.addInput(videoInput)
        } else {
             throw NSError(domain: "CameraHandler", code: -1, userInfo: [NSLocalizedDescriptionKey: "Cannot add video input"])
        }

        // Cấu hình output để nhận frame
        let videoOutput = AVCaptureVideoDataOutput()
        videoOutput.setSampleBufferDelegate(self, queue: DispatchQueue(label: "video data queue"))
        if session.canAddOutput(videoOutput) {
            session.addOutput(videoOutput)
        } else {
            throw NSError(domain: "CameraHandler", code: -1, userInfo: [NSLocalizedDescriptionKey: "Cannot add video output"])
        }

        session.commitConfiguration()
        self.captureSession = session
    }

    func start() {
        sessionQueue.async {
            self.captureSession?.startRunning()
        }
    }

    func stop() {
        sessionQueue.async {
            self.captureSession?.stopRunning()
        }
    }

    // MARK: - AVCaptureVideoDataOutputSampleBufferDelegate
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        // Gửi sample buffer về cho delegate (là BeautyCameraPlugin)
        delegate?.didOutput(sampleBuffer: sampleBuffer)
        
        // Process with Metal if filter is enabled
        if filterEnabled, let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) {
            processWithMetal(pixelBuffer)
        } else if let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) {
            // Send original pixel buffer if no filter
            delegate?.didOutput(pixelBuffer: pixelBuffer)
        }
    }
    
    // MARK: - Metal Processing
    
    private func processWithMetal(_ pixelBuffer: CVPixelBuffer) {
        metalManager?.setFilterEnabled(filterEnabled)
        metalManager?.setFilterIntensity(filterIntensity)
        metalManager?.setFilterType(currentFilter)
        
        metalManager?.processCameraFrame(pixelBuffer) { [weak self] processedPixelBuffer in
            if let processedPixelBuffer = processedPixelBuffer {
                self?.delegate?.didOutput(pixelBuffer: processedPixelBuffer)
            } else {
                // Fallback to original if Metal processing fails
                self?.delegate?.didOutput(pixelBuffer: pixelBuffer)
            }
        }
    }
    
    // MARK: - Filter Control
    
    func setFilterEnabled(_ enabled: Bool) {
        filterEnabled = enabled
    }
    
    func setFilterIntensity(_ intensity: Float) {
        filterIntensity = intensity
    }
    
    func setFilterType(_ type: FilterType) {
        currentFilter = type
    }
}
