import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    swiftOptions: SwiftOptions(),
    swiftOut: 'ios/Classes/BeautyCameraPlugin.swift',
    kotlinOptions: KotlinOptions(package: 'com.beauty.camera_plugin'),
    kotlinOut:
        'android/src/main/kotlin/com/beauty/camera_plugin/BeautyCameraPluginPigeon.kt',
    copyrightHeader: 'pigeons/copyright.txt',
    dartPackageName: 'com.beauty.camera_plugin',
    dartOut: 'lib/src/camera_api.g.dart',
  ),
)
enum CameraFilterMode {
  none,
  beauty,
  mono,
  negative,
  sepia,
  solarize,
  posterize,
  whiteboard,
  blackboard,
  aqua,
  emboss,
  sketch,
  neon,
  vintage,
  brightness,
  contrast,
  saturation,
  sharpen,
  gaussianBlur,
  vignette,
  hue,
  exposure,
  highlightShadow,
  levels,
  colorBalance,
  lookup;
}

class AdvancedCameraSettings {
  VideoQuality? videoQuality;

  int? maxFrameRate;

  bool? videoStabilization;

  bool? autoExposure;

  bool? enableFaceDetection;

  AdvancedCameraSettings({
    this.videoQuality,
    this.maxFrameRate,
    this.videoStabilization,
    this.autoExposure,
    this.enableFaceDetection,
  });
}

enum VideoQuality {
  low,
  medium,
  high,
  veryHigh,
  ultra,
}

@HostApi()
abstract class BeautyCameraHostApi {
  @async
  void initialize(AdvancedCameraSettings settings);

  @async
  void dispose();

  @async
  void switchCamera();

  @async
  void setZoom(double zoomLevel);

  @async
  void focusOnPoint(int x, int y);

  @async
  void setFlashMode(FlashMode mode);

  @async
  void setDisplayOrientation(int degrees);

  @async
  int getPreviewTexture();

  @async
  PreviewSize getPreviewSize();

  @async
  String takePhoto();

  @async
  void startVideoRecording();

  @async
  String stopVideoRecording();

  @async
  double getCameraSensorAspectRatio();

  @async
  void setFilterMode(CameraFilterMode mode, {double level = 5});
}

enum FlashMode {
  off,
  on,
  auto,
  torch,
}

@FlutterApi()
abstract class BeautyCameraFlutterApi {
  @async
  void onZoomChanged(double zoomLevel);

  @async
  void onFlashModeChanged(FlashMode mode);

  @async
  void onCameraSwitched(String cameraId);

  @async
  void onFaceDetected(List<FaceData> faces);

  @async
  void onVideoRecordingStarted();

  @async
  void onVideoRecordingStopped(String path);

  @async
  void onFilterModeChanged(CameraFilterMode mode);
}

class FaceData {
  double x;

  double y;

  double size;

  int id;

  FaceData({
    required this.x,
    required this.y,
    required this.size,
    required this.id,
  });
}

class PreviewSize {
  int width;

  int height;

  PreviewSize({
    required this.width,
    required this.height,
  });
}
