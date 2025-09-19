import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    swiftOptions: SwiftOptions(),
    swiftOut: 'ios/Classes/BeautyCameraPluginPigeon.swift',
    kotlinOptions: KotlinOptions(package: 'com.beauty.camera_plugin'),
    kotlinOut:
        'android/src/main/kotlin/com/beauty/camera_plugin/BeautyCameraPluginPigeon.g.kt',
    copyrightHeader: 'pigeons/copyright.txt',
    dartPackageName: 'com.beauty.camera_plugin',
    dartOut: 'lib/src/camera_api.g.dart',
  ),
)

/// Filter Categories (TikTok/CapCut style)
enum FilterCategory {
  /// Không filter
  none,

  /// Beauty filters - làm đẹp (cần face detection)
  beauty,

  /// Portrait filters - chân dung
  portrait,

  /// Food filters - đồ ăn
  food,

  /// Landscape filters - phong cảnh
  landscape,

  /// Vintage filters - cổ điển
  vintage,

  /// Vibrant filters - sống động
  vibrant,

  /// Moody filters - tâm trạng
  moody,

  /// Film filters - phim ảnh
  film,

  /// Art filters - nghệ thuật
  art,
}

/// Filter Types (TikTok/CapCut style)
enum FilterType {
  // None
  none,

  // Beauty filters (cần face detection)
  beautyNatural, // Tự nhiên
  beautyGlow, // Rạng rỡ
  beautyDoll, // Búp bê
  beautyFresh, // Tươi tắn
  beautySmooth, // Mịn màng
  beautyBright, // Sáng da

  // Portrait filters
  portraitClassic, // Cổ điển
  portraitDramatic, // Kịch tính
  portraitSoft, // Mềm mại
  portraitBW, // Đen trắng

  // Food filters
  foodWarm, // Ấm áp
  foodVibrant, // Sống động
  foodFresh, // Tươi ngon
  foodInstagram, // Instagram style

  // Landscape filters
  landscapeGolden, // Giờ vàng
  landscapeDramatic, // Kịch tính
  landscapeVibrant, // Sống động
  landscapeMoody, // U ám

  // Vintage filters
  vintageFilm, // Phim cũ
  vintageSepia, // Nâu cổ điển
  vintageFaded, // Phai màu
  vintageRetro, // Retro

  // Vibrant filters
  vibrantPop, // Pop
  vibrantNeon, // Neon
  vibrantSummer, // Mùa hè
  vibrantTropical, // Nhiệt đới

  // Moody filters
  moodyDark, // Tối
  moodyBlue, // Xanh u ám
  moodyCinematic, // Điện ảnh
  moodyNoir, // Noir

  // Film filters
  filmKodak, // Kodak
  filmFuji, // Fujifilm
  filmPolaroid, // Polaroid
  filmVHS, // VHS

  // Art filters
  artCartoon, // Hoạt hình
  artOilPainting, // Sơn dầu
  artWatercolor, // Màu nước
  artSketch, // Phác họa
}

/// Định nghĩa các loại beauty filter cụ thể (TikTok/CapCut style)
enum BeautyFilterType {
  /// Không áp dụng beauty filter
  none,

  /// Natural - làm đẹp tự nhiên
  natural,

  /// Glow - hiệu ứng rạng rỡ
  glow,

  /// Doll - hiệu ứng búp bê
  doll,

  /// Fresh - tươi tắn
  fresh,

  /// Smooth - làm mịn
  smooth,
}

/// Parameters cho beauty filter
class BeautyFilterParameters {
  /// Loại beauty filter
  final BeautyFilterType type;

  /// Độ mạnh làm mịn da (0.0 - 1.0)
  final double smoothingStrength;

  /// Độ mạnh làm sáng da (0.0 - 1.0)
  final double brighteningStrength;

  /// Độ mạnh tổng thể của filter (0.0 - 1.0)
  final double intensity;

  /// Có áp dụng filter lên toàn bộ frame hay chỉ vùng face
  final bool faceOnly;

  const BeautyFilterParameters({
    this.type = BeautyFilterType.none,
    this.smoothingStrength = 0.3,
    this.brighteningStrength = 0.2,
    this.intensity = 1.0,
    this.faceOnly = true,
  });
}

/// Cài đặt nâng cao cho camera.
/// Sử dụng để cấu hình chi tiết cho CameraX.
class AdvancedCameraSettings {
  /// Hướng camera (trước/sau)
  final CameraFacing? cameraLensFacing;

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

  /// Bật/tắt xử lý OpenGL cho filter
  final bool? isFilterEnabled;

  AdvancedCameraSettings({
    this.cameraLensFacing,
    this.videoQuality,
    this.maxFrameRate,
    this.videoStabilization,
    this.autoExposure,
    this.enableFaceDetection,
    this.isFilterEnabled,
  });
}

/// Filter Parameters (TikTok/CapCut style - unified system)
class FilterParameters {
  /// Độ mạnh tổng thể (0.0 - 1.0)
  final double intensity;

  /// Beauty parameters
  final double skinSmoothing; // Làm mịn da
  final double skinBrightening; // Làm sáng da
  final double faceSlimming; // Thon gọn mặt
  final double eyeEnlargement; // To mắt
  final double lipEnhancement; // Tô môi

  /// Color parameters
  final double brightness; // Độ sáng (-1.0 to 1.0)
  final double contrast; // Độ tương phản (-1.0 to 1.0)
  final double saturation; // Độ bão hòa (-1.0 to 1.0)
  final double warmth; // Độ ấm (-1.0 to 1.0)
  final double tint; // Sắc thái (-1.0 to 1.0)
  final double vibrance; // Độ sống động (-1.0 to 1.0)

  /// Artistic parameters
  final double blur; // Độ mờ (0.0 to 1.0)
  final double sharpen; // Độ sắc nét (0.0 to 1.0)
  final double vignette; // Viền tối (0.0 to 1.0)
  final double grain; // Hạt film (0.0 to 1.0)
  final double fade; // Độ phai (0.0 to 1.0)

  /// Advanced parameters
  final double highlights; // Vùng sáng (-1.0 to 1.0)
  final double shadows; // Vùng tối (-1.0 to 1.0)
  final double clarity; // Độ trong (-1.0 to 1.0)
  final double structure; // Cấu trúc (-1.0 to 1.0)

  const FilterParameters({
    this.intensity = 1.0,
    // Beauty
    this.skinSmoothing = 0.0,
    this.skinBrightening = 0.0,
    this.faceSlimming = 0.0,
    this.eyeEnlargement = 0.0,
    this.lipEnhancement = 0.0,
    // Color
    this.brightness = 0.0,
    this.contrast = 0.0,
    this.saturation = 0.0,
    this.warmth = 0.0,
    this.tint = 0.0,
    this.vibrance = 0.0,
    // Artistic
    this.blur = 0.0,
    this.sharpen = 0.0,
    this.vignette = 0.0,
    this.grain = 0.0,
    this.fade = 0.0,
    // Advanced
    this.highlights = 0.0,
    this.shadows = 0.0,
    this.clarity = 0.0,
    this.structure = 0.0,
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

  /// Chiều rộng tương đối của khuôn mặt
  final double width;

  /// Chiều cao tương đối của khuôn mặt
  final double height;

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
    required this.width,
    required this.height,
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

  /// Áp dụng filter với category và type
  @async
  void applyFilter(
      FilterCategory category, FilterType type, FilterParameters parameters);

  /// Đặt kiểu scale cho preview
  @async
  void setScaleType(ScaleType scaleType);

  /// Lấy thông tin về các camera có sẵn trên thiết bị
  @async
  List<CameraInfo> getAvailableCameras();

  /// Bật/tắt filter cho camera
  @async
  void setFilterEnabled(bool enabled);

  /// Điều chỉnh intensity của filter hiện tại
  @async
  void adjustFilterIntensity(double intensity);
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

  /// Thông báo khi filter thay đổi
  @async
  void onFilterChanged(
      FilterCategory category, FilterType type, FilterParameters parameters);

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
