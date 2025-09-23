import Flutter
import UIKit
import AVFoundation

public class BeautyCameraPlugin: NSObject, FlutterPlugin, BeautyCameraHostApi, FlutterTexture, CameraFrameDelegate {

    private let registry: FlutterTextureRegistry
    private var cameraHandler: CameraHandler?
    private var textureId: Int64 = -1
    private var latestPixelBuffer: CVPixelBuffer?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = BeautyCameraPlugin(registry: registrar.textures())
        // Chú ý: dùng `registrar.messenger()`
        BeautyCameraHostApi.setUp(registrar.messenger(), api: instance)
    }

    init(registry: FlutterTextureRegistry) {
        self.registry = registry
        super.init()
    }

    // MARK: - BeautyCameraHostApi Implementation

    public func initialize(settings: AdvancedCameraSettings, completion: @escaping (Result<Void, Error>) -> Void) {
        cameraHandler = CameraHandler()
        cameraHandler?.delegate = self
        cameraHandler?.initialize { error in
            if let error = error {
                completion(.failure(error))
            } else {
                completion(.success(Void()))
            }
        }
    }

    public func getPreviewTexture(completion: @escaping (Result<Int64, Error>) -> Void) {
        // Đăng ký một texture mới và giữ lại textureId
        textureId = registry.register(self)
        cameraHandler?.start()
        completion(.success(textureId))
    }

    public func dispose(completion: @escaping (Result<Void, Error>) -> Void) {
        cameraHandler?.stop()
        if textureId != -1 {
            registry.unregisterTexture(textureId)
        }
        textureId = -1
        latestPixelBuffer = nil
        completion(.success(Void()))
    }

    // MARK: - FlutterTexture (Nhà cung cấp PixelBuffer cho Flutter)

    public func copyPixelBuffer() -> Unmanaged<CVPixelBuffer>? {
        // Khi Flutter yêu cầu một frame mới, chúng ta cung cấp frame gần nhất đã nhận được
        if let pixelBuffer = latestPixelBuffer {
            return Unmanaged.passRetained(pixelBuffer)
        }
        return nil
    }

    // MARK: - CameraFrameDelegate

    public func didOutput(sampleBuffer: CMSampleBuffer) {
        // Lấy CVPixelBuffer từ CMSampleBuffer
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        // Lưu lại frame mới nhất
        latestPixelBuffer = pixelBuffer
        // Báo cho Flutter biết rằng có frame mới, hãy gọi `copyPixelBuffer`
        if textureId != -1 {
            registry.textureFrameAvailable(textureId)
        }
    }
    
    public func didOutput(pixelBuffer: CVPixelBuffer) {
        // Lưu lại frame mới nhất (có thể đã được xử lý bởi Metal)
        latestPixelBuffer = pixelBuffer
        // Báo cho Flutter biết rằng có frame mới
        if textureId != -1 {
            registry.textureFrameAvailable(textureId)
        }
    }
    
    // MARK: - Unimplemented Methods
    public func switchCamera(completion: @escaping (Result<Void, Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func setZoom(zoomLevel: Double, completion: @escaping (Result<Void, Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func focusOnPoint(x: Int64, y: Int64, completion: @escaping (Result<Void, Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func setFlashMode(mode: FlashMode, completion: @escaping (Result<Void, Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func setDisplayOrientation(degrees: Int64, completion: @escaping (Result<Void, Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func getPreviewSize(completion: @escaping (Result<PreviewSize, Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func takePhoto(completion: @escaping (Result<String, Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func startVideoRecording(completion: @escaping (Result<Void, Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func stopVideoRecording(completion: @escaping (Result<String, Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func getCameraSensorAspectRatio(completion: @escaping (Result<Double, Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func setFilterMode(mode: CameraFilterMode, parameters: FilterParameters, completion: @escaping (Result<Void, Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func setScaleType(scaleType: ScaleType, completion: @escaping (Result<Void, Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func getAvailableFilters(completion: @escaping (Result<[FilterInfo], Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func adjustFilterParameters(parameters: FilterParameters, completion: @escaping (Result<Void, Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
    public func getAvailableCameras(completion: @escaping (Result<[CameraInfo], Error>) -> Void) { completion(.failure(FlutterError(code: "UNIMPLEMENTED", message: nil, details: nil))) }
}