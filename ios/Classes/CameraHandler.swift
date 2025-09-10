import AVFoundation

// Delegate để gửi frame buffer về cho plugin
protocol CameraFrameDelegate: AnyObject {
    func didOutput(sampleBuffer: CMSampleBuffer)
}

class CameraHandler: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    private var captureSession: AVCaptureSession?
    private let sessionQueue = DispatchQueue(label: "session queue")
    weak var delegate: CameraFrameDelegate?

    func initialize(completion: @escaping (Error?) -> Void) {
        sessionQueue.async {
            do {
                try self.setupCaptureSession()
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
    }
}
