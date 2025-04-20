import 'package:pigeon/pigeon.dart';

/// Configuration for the Pigeon code generation
@ConfigurePigeon(
  PigeonOptions(
    swiftOptions: SwiftOptions(),
    swiftOut: 'ios/Classes/BeautyCameraPlugin.swift',
    kotlinOptions: KotlinOptions(package: 'com.beauty.camera_plugin'),
    kotlinOut:
        'android/src/main/kotlin/com/beauty/camera_plugin/BeautyCameraPlugin.kt',
    copyrightHeader: 'pigeons/copyright.txt',
    dartPackageName: 'com.beauty.camera_plugin',
    dartOut: 'lib/src/camera_api.g.dart',
  ),
)

/// Represents the available filter types for camera effects
enum FilterType {
  /// No filter applied
  none,

  /// Beauty filter for skin smoothing
  beauty,

  /// Vintage effect filter
  vintage,

  /// Black and white filter
  blackAndWhite,

  /// Custom filter with parameters
  custom
}

/// Represents possible camera error types
enum CameraErrorType {
  /// Camera initialization failed
  initializationFailed,

  /// Camera permission denied
  permissionDenied,

  /// Camera hardware not available
  hardwareNotAvailable,

  /// Invalid camera settings
  invalidSettings,

  /// Recording failed
  recordingFailed,

  /// Picture capture failed
  captureFailed,

  /// Unknown error occurred
  unknown
}

/// Configuration settings for camera initialization
class CameraSettings {
  /// The desired width of the camera preview in pixels
  /// If null, the default camera resolution will be used
  int? width;

  /// The desired height of the camera preview in pixels
  /// If null, the default camera resolution will be used
  int? height;

  /// Creates a new [CameraSettings] instance
  ///
  /// [width] and [height] are optional. If both are null,
  /// the camera will use its default resolution.
  CameraSettings({this.width, this.height});
}

/// Configuration for applying filters to the camera preview
class FilterConfig {
  /// The type of filter to apply
  String? filterType;

  /// Additional parameters for the filter
  /// The structure depends on the filter type:
  /// - For beauty filter: {'smoothness': 0.0-1.0, 'brightness': 0.0-1.0}
  /// - For vintage: {'intensity': 0.0-1.0}
  /// - For custom: varies based on implementation
  Map<String, Object?>? parameters;

  /// Creates a new [FilterConfig] instance
  ///
  /// [filterType] specifies the type of filter to apply
  /// [parameters] contains additional configuration for the filter
  FilterConfig({this.filterType, this.parameters});
}

/// Represents a camera error with type and message
class CameraError {
  /// The type of error that occurred
  CameraErrorType type;

  /// A human-readable description of the error
  String message;

  /// Creates a new [CameraError] instance
  ///
  /// [type] specifies the category of the error
  /// [message] provides additional details about the error
  CameraError({required this.type, required this.message});
}

/// Host API for camera operations
@HostApi()
abstract class BeautyCameraHostApi {
  /// Initializes the camera with the specified settings
  ///
  /// Returns a Future that completes when the camera is initialized
  /// Throws [CameraException] if initialization fails
  @async
  void initializeCamera(CameraSettings settings);

  /// Creates a new preview texture and returns its ID
  ///
  /// Returns the texture ID that can be used to display the camera preview
  /// Returns null if texture creation fails
  @async
  int? createPreviewTexture();

  /// Starts the camera preview on the specified texture
  ///
  /// [textureId] must be a valid texture ID returned by [createPreviewTexture]
  @async
  void startPreview(int textureId);

  /// Stops the camera preview
  @async
  void stopPreview();

  /// Disposes of all camera resources
  @async
  void disposeCamera();

  /// Takes a picture and saves it to the specified path
  ///
  /// [path] must be a valid file path where the image will be saved
  @async
  void takePicture(String path);

  /// Starts recording video to the specified path
  ///
  /// [path] must be a valid file path where the video will be saved
  @async
  void startRecording(String path);

  /// Stops the current video recording
  @async
  void stopRecording();

  /// Applies a filter to the camera preview
  ///
  /// [textureId] must be a valid texture ID
  /// [filterConfig] specifies the filter type and parameters
  @async
  void applyFilter(int textureId, FilterConfig filterConfig);
}

/// Flutter API for camera events
@FlutterApi()
abstract class BeautyCameraFlutterApi {
  /// Called when the camera is successfully initialized
  ///
  /// [textureId] is the ID of the preview texture
  /// [width] and [height] are the actual dimensions of the camera preview
  void onCameraInitialized(int textureId, int width, int height);

  /// Called when a picture has been taken and saved
  ///
  /// [path] is the path where the image was saved
  void onTakePictureCompleted(String path);

  /// Called when video recording has started
  void onRecordingStarted();

  /// Called when video recording has stopped
  ///
  /// [path] is the path where the video was saved
  void onRecordingStopped(String path);

  /// Called when a camera error occurs
  ///
  /// [error] contains both the type and message of the error
  void onCameraError(CameraError error);
}
