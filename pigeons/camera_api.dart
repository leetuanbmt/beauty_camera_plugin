import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    swiftOptions: SwiftOptions(),
    swiftOut: 'ios/Classes/BeautyCameraPluginPigeon.swift',
    kotlinOptions: KotlinOptions(package: 'com.beauty.camera_plugin'),
    kotlinOut:
        'android/src/main/kotlin/com/beauty/camera_plugin/BeautyCameraPluginPigeon.kt',
    copyrightHeader: 'pigeons/copyright.txt',
    dartPackageName: 'com.beauty.camera_plugin',
    dartOut: 'lib/src/camera_api.g.dart',
  ),
)

/// Định nghĩa các loại bộ lọc camera có thể áp dụng.
/// Sử dụng theo mẫu Strategy Pattern, cho phép dễ dàng thêm filter mới.
enum CameraFilterMode {
  /// Không áp dụng bộ lọc
  none,

  /// Làm mịn và tăng cường vẻ đẹp cho khuôn mặt
  beauty,

  /// Chuyển đổi sang chế độ đen trắng
  mono,

  /// Đảo ngược màu sắc
  negative,

  /// Tông màu nâu đỏ hoài cổ
  sepia,

  /// Hiệu ứng sáng cực đại tại một số vùng
  solarize,

  /// Giảm số lượng màu sắc, tạo hiệu ứng poster
  posterize,

  /// Hiệu ứng bảng trắng, tăng cường viền và độ tương phản
  whiteboard,

  /// Hiệu ứng bảng đen, tăng cường viền trên nền tối
  blackboard,

  /// Tông màu xanh nước biển
  aqua,

  /// Hiệu ứng chạm nổi
  emboss,

  /// Hiệu ứng phác họa
  sketch,

  /// Hiệu ứng màu sắc rực rỡ, phong cách neon
  neon,

  /// Hiệu ứng hoài cổ, tạo cảm giác hình ảnh cũ
  vintage,

  /// Điều chỉnh độ sáng
  brightness,

  /// Điều chỉnh độ tương phản
  contrast,

  /// Điều chỉnh độ bão hòa màu sắc
  saturation,

  /// Tăng cường chi tiết, làm sắc nét hình ảnh
  sharpen,

  /// Làm mờ hình ảnh theo thuật toán Gaussian
  gaussianBlur,

  /// Tạo hiệu ứng viền tối ở góc hình ảnh
  vignette,

  /// Điều chỉnh tông màu
  hue,

  /// Điều chỉnh độ phơi sáng
  exposure,

  /// Điều chỉnh vùng tối và vùng sáng
  highlightShadow,

  /// Điều chỉnh các mức độ màu sắc
  levels,

  /// Cân bằng màu RGB
  colorBalance,

  /// Áp dụng bảng màu tra cứu (Lookup Table - LUT)
  lookup,
}

/// Thông tin chi tiết về bộ lọc được hỗ trợ.
/// Cung cấp metadata về filter từ native lên Flutter.
class FilterInfo {
  /// Định danh độc nhất của filter
  final String id;

  /// Loại bộ lọc
  final CameraFilterMode mode;

  /// Tên hiển thị cho người dùng
  final String displayName;

  /// Đường dẫn đến hình thu nhỏ nếu có
  final String? thumbnailPath;

  /// Mô tả ngắn về bộ lọc
  final String? description;

  /// Danh sách các tham số có thể điều chỉnh
  final List<String>? adjustableParameters;

  FilterInfo({
    required this.id,
    required this.mode,
    required this.displayName,
    this.thumbnailPath,
    this.description,
    this.adjustableParameters,
  });
}

/// Cài đặt nâng cao cho camera.
/// Sử dụng để cấu hình chi tiết cho CameraX.
class AdvancedCameraSettings {
  /// Chất lượng video khi quay
  final VideoQuality? videoQuality;

  /// Tốc độ khung hình tối đa
  final int? maxFrameRate;

  /// Bật/tắt ổn định video
  final bool? videoStabilization;

  /// Bật/tắt tự động phơi sáng
  final bool? autoExposure;

  /// Bật/tắt nhận diện khuôn mặt
  final bool? enableFaceDetection;

  /// Chiều rộng của preview mong muốn
  final int? previewWidth;

  /// Chiều cao của preview mong muốn
  final int? previewHeight;

  AdvancedCameraSettings({
    this.videoQuality,
    this.maxFrameRate,
    this.videoStabilization,
    this.autoExposure,
    this.enableFaceDetection,
    this.previewWidth,
    this.previewHeight,
  });
}

/// Cài đặt tham số cho filter camera.
/// Sử dụng Builder Pattern để dễ dàng xây dựng và tùy chỉnh.
class FilterParameters {
  /// Cường độ áp dụng bộ lọc (0.0 - 1.0)
  final double intensity;

  /// Độ sáng (-1.0 đến 1.0, 0.0 là nguyên bản)
  final double brightness;

  /// Độ tương phản (0.0 - 2.0, 1.0 là nguyên bản)
  final double contrast;

  /// Độ bão hòa màu (0.0 - 2.0, 1.0 là nguyên bản)
  final double saturation;

  /// Điều chỉnh tông màu (-1.0 đến 1.0)
  final double hue;

  /// Độ sắc nét (0.0 - 2.0)
  final double sharpen;

  /// Bán kính làm mờ (0.0 - 10.0)
  final double blurRadius;

  /// Hệ số kênh đỏ (0.0 - 2.0, 1.0 là nguyên bản)
  final double redChannel;

  /// Hệ số kênh xanh lá (0.0 - 2.0, 1.0 là nguyên bản)
  final double greenChannel;

  /// Hệ số kênh xanh dương (0.0 - 2.0, 1.0 là nguyên bản)
  final double blueChannel;

  /// Độ làm mịn da (0.0 - 1.0)
  final double skinSmoothness;

  /// Đường dẫn đến file LUT (Lookup Table)
  final String? lookupTablePath;

  FilterParameters({
    this.intensity = 0.5,
    this.brightness = 0.0,
    this.contrast = 1.0,
    this.saturation = 1.0,
    this.hue = 0.0,
    this.sharpen = 0.0,
    this.blurRadius = 0.0,
    this.redChannel = 1.0,
    this.greenChannel = 1.0,
    this.blueChannel = 1.0,
    this.skinSmoothness = 0.5,
    this.lookupTablePath,
  });
}

/// Chất lượng video khi quay.
/// Định nghĩa theo mức độ tăng dần.
enum VideoQuality {
  /// Chất lượng thấp (480p)
  low,

  /// Chất lượng trung bình (720p)
  medium,

  /// Chất lượng cao (1080p)
  high,

  /// Chất lượng rất cao (1440p)
  veryHigh,

  /// Chất lượng cực cao (2160p/4K)
  ultra,
}

/// Thông tin về khuôn mặt được phát hiện
class FaceData {
  /// Tọa độ X của trung tâm khuôn mặt (đã chuẩn hóa)
  final double x;

  /// Tọa độ Y của trung tâm khuôn mặt (đã chuẩn hóa)
  final double y;

  /// Kích thước tương đối của khuôn mặt
  final double size;

  /// ID để theo dõi khuôn mặt này qua các frame
  final int id;

  /// Các điểm mốc trên khuôn mặt
  final List<FaceLandmark>? landmarks;

  /// Điểm số nụ cười (0.0 - 1.0)
  final double? smileScore;

  /// Điểm số mắt mở (0.0 - 1.0)
  final double? eyeOpenScore;

  FaceData({
    required this.x,
    required this.y,
    required this.size,
    required this.id,
    this.landmarks,
    this.smileScore,
    this.eyeOpenScore,
  });
}

/// Điểm mốc trên khuôn mặt
class FaceLandmark {
  /// Loại điểm mốc (mắt, mũi, miệng, v.v.)
  final int type;

  /// Tọa độ X (đã chuẩn hóa)
  final double x;

  /// Tọa độ Y (đã chuẩn hóa)
  final double y;

  FaceLandmark({
    required this.type,
    required this.x,
    required this.y,
  });
}

/// API chính để giao tiếp từ Flutter đến native.
/// Tuân theo các nguyên tắc Clean Architecture.
@HostApi()
abstract class BeautyCameraHostApi {
  /// Khởi tạo camera với các cài đặt cụ thể
  @async
  void initialize(AdvancedCameraSettings settings);

  /// Khởi tạo camera cho mục đích test (không có OpenGL)
  @async
  void initializeForTest(AdvancedCameraSettings settings);

  /// Giải phóng tài nguyên
  @async
  void dispose();

  /// Chuyển đổi giữa camera trước và sau
  @async
  void switchCamera();

  /// Đặt mức zoom
  @async
  void setZoom(double zoomLevel);

  /// Tập trung vào một điểm cụ thể
  @async
  void focusOnPoint(int x, int y);

  /// Đặt chế độ đèn flash
  @async
  void setFlashMode(FlashMode mode);

  /// Thiết lập hướng hiển thị
  @async
  void setDisplayOrientation(int degrees);

  /// Lấy ID texture để hiển thị preview
  @async
  int getPreviewTexture();

  /// Lấy kích thước preview hiện tại
  @async
  PreviewSize getPreviewSize();

  /// Chụp ảnh và trả về đường dẫn
  @async
  String takePhoto();

  /// Bắt đầu quay video
  @async
  void startVideoRecording();

  /// Dừng quay video và trả về đường dẫn
  @async
  String stopVideoRecording();

  /// Lấy tỷ lệ khung hình cảm biến
  @async
  double getCameraSensorAspectRatio();

  /// Đặt chế độ bộ lọc với các tham số
  @async
  void setFilterMode(CameraFilterMode mode, FilterParameters parameters);

  /// Đặt kiểu scale cho preview
  @async
  void setScaleType(ScaleType scaleType);

  /// Lấy danh sách thông tin chi tiết về các bộ lọc có sẵn từ native
  @async
  List<FilterInfo> getAvailableFilters();

  /// Điều chỉnh tham số của bộ lọc hiện tại
  @async
  void adjustFilterParameters(FilterParameters parameters);

  /// Lấy thông tin về các camera có sẵn trên thiết bị
  @async
  List<CameraInfo> getAvailableCameras();
}

/// Chế độ đèn flash
enum FlashMode {
  /// Tắt đèn flash
  off,

  /// Bật đèn flash
  on,

  /// Tự động điều chỉnh flash
  auto,

  /// Bật đèn flash liên tục (đèn pin)
  torch,
}

/// Hướng camera (trước/sau)
enum CameraFacing {
  /// Camera trước (selfie)
  front,

  /// Camera sau
  back,
}

/// Kiểu scale cho preview
enum ScaleType {
  /// Cắt để lấp đầy, có thể cắt bớt hình ảnh
  centerCrop,

  /// Thu nhỏ để vừa khung, có thể có đường viền đen
  centerInside,
}

/// Thông tin về camera trên thiết bị
class CameraInfo {
  /// ID độc nhất của camera
  final String id;

  /// Hướng camera (trước/sau)
  final CameraFacing facing;

  /// Có hỗ trợ đèn flash không
  final bool hasFlash;

  /// Độ phân giải hỗ trợ
  final List<ResolutionInfo> supportedResolutions;

  CameraInfo({
    required this.id,
    required this.facing,
    required this.hasFlash,
    required this.supportedResolutions,
  });
}

/// Thông tin về độ phân giải
class ResolutionInfo {
  /// Chiều rộng (pixels)
  final int width;

  /// Chiều cao (pixels)
  final int height;

  ResolutionInfo({
    required this.width,
    required this.height,
  });
}

/// Cài đặt cơ bản cho camera
class CameraSettings {
  /// Hướng camera (trước/sau)
  final CameraFacing? cameraLensFacing;

  /// Chế độ đèn flash
  final FlashMode? flashMode;

  /// Mức zoom
  final double? zoom;

  /// Hướng hiển thị
  final int? displayOrientation;

  /// Bật/tắt nhận diện khuôn mặt
  final bool? enableFaceDetection;

  /// Chiều rộng preview mong muốn
  final int? previewWidth;

  /// Chiều cao preview mong muốn
  final int? previewHeight;

  CameraSettings({
    this.cameraLensFacing,
    this.flashMode,
    this.zoom,
    this.displayOrientation,
    this.enableFaceDetection,
    this.previewWidth,
    this.previewHeight,
  });
}

/// API camera cơ bản
@HostApi()
abstract class CameraApi {
  /// Khởi tạo camera với các cài đặt cơ bản
  @async
  void initialize(CameraSettings settings);

  /// Bắt đầu hiển thị preview với textureId đã cung cấp
  @async
  void startPreview(int textureId);

  /// Dừng hiển thị preview
  @async
  void stopPreview();

  /// Chuyển đổi giữa camera trước và sau
  @async
  void switchCamera();

  /// Đặt chế độ đèn flash
  @async
  void setFlashMode(FlashMode mode);

  /// Đặt mức zoom
  @async
  void setZoom(double zoom);

  /// Đặt kiểu scale cho preview
  @async
  void setScaleType(ScaleType scaleType);

  /// Chụp ảnh và trả về đường dẫn
  @async
  String takePhoto();

  /// Bắt đầu quay video với đường dẫn file đầu ra
  @async
  void startVideoRecording();

  /// Dừng quay video và trả về đường dẫn
  @async
  String stopVideoRecording();

  /// Giải phóng tài nguyên
  @async
  void dispose();
}

/// API cho phép native gọi ngược về Flutter.
/// Sử dụng Observer Pattern để thông báo các sự kiện camera.
@FlutterApi()
abstract class BeautyCameraFlutterApi {
  /// Thông báo khi zoom thay đổi
  @async
  void onZoomChanged(double zoomLevel);

  /// Thông báo khi chế độ flash thay đổi
  @async
  void onFlashModeChanged(FlashMode mode);

  /// Thông báo khi camera được chuyển đổi
  @async
  void onCameraSwitched(String cameraId);

  /// Thông báo khi phát hiện khuôn mặt
  @async
  void onFaceDetected(List<FaceData> faces);

  /// Thông báo khi bắt đầu quay video
  @async
  void onVideoRecordingStarted();

  /// Thông báo khi dừng quay video
  @async
  void onVideoRecordingStopped(String path);

  /// Thông báo khi chế độ bộ lọc thay đổi
  @async
  void onFilterModeChanged(CameraFilterMode mode);

  /// Thông báo khi tham số bộ lọc thay đổi
  @async
  void onFilterParametersChanged(FilterParameters parameters);

  /// Thông báo khi xảy ra lỗi camera
  @async
  void onCameraError(String errorCode, String errorMessage);
}

/// Kích thước preview
class PreviewSize {
  /// Chiều rộng (pixels)
  final int width;

  /// Chiều cao (pixels)
  final int height;

  PreviewSize({
    required this.width,
    required this.height,
  });
}
