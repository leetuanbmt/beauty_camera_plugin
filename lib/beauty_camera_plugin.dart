library;

import 'beauty_camera_plugin.dart';

export 'src/beauty_camera_controller.dart';
export 'src/camera_api.g.dart';
export 'src/validators.dart';

/// A plugin for handling camera operations with beauty filters
class BeautyCameraPlugin {
  static final BeautyCameraHostApi _api = BeautyCameraHostApi();
  static BeautyCameraFlutterApi? _flutterApi;

  /// Initialize the camera with specified settings
  ///
  /// [width] and [height] are optional. If both are null,
  /// the camera will use its default resolution.
  ///
  /// Throws [ArgumentError] if width or height is invalid.
  /// Throws [CameraException] if camera initialization fails.
  Future<void> initializeCamera({int? width, int? height}) async {
    try {
      final settings = CameraSettings(width: width, height: height);
      validateCameraSettings(settings);
      await _api.initializeCamera(settings);
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.initializationFailed,
        message: e.toString(),
      ));
      rethrow;
    }
  }

  /// Create a preview texture and return its ID
  ///
  /// Returns the texture ID that can be used to display the camera preview.
  /// Returns null if texture creation fails.
  Future<int?> createPreviewTexture() async {
    try {
      return await _api.createPreviewTexture();
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.initializationFailed,
        message: 'Failed to create preview texture: ${e.toString()}',
      ));
      rethrow;
    }
  }

  /// Start the camera preview on the specified texture
  ///
  /// [textureId] must be a valid texture ID returned by [createPreviewTexture].
  /// Throws [CameraException] if preview fails to start.
  Future<void> startPreview(int textureId) async {
    try {
      if (textureId < 0) {
        throw ArgumentError('Invalid texture ID');
      }
      await _api.startPreview(textureId);
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.initializationFailed,
        message: 'Failed to start preview: ${e.toString()}',
      ));
      rethrow;
    }
  }

  /// Stop the camera preview
  ///
  /// Throws [CameraException] if preview fails to stop.
  Future<void> stopPreview() async {
    try {
      await _api.stopPreview();
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.initializationFailed,
        message: 'Failed to stop preview: ${e.toString()}',
      ));
      rethrow;
    }
  }

  /// Dispose the camera resources
  ///
  /// This should be called when the camera is no longer needed.
  /// Throws [CameraException] if disposal fails.
  Future<void> disposeCamera() async {
    try {
      await _api.disposeCamera();
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.initializationFailed,
        message: 'Failed to dispose camera: ${e.toString()}',
      ));
      rethrow;
    }
  }

  /// Take a picture and save it to the specified path
  ///
  /// [path] must be a valid file path where the image will be saved.
  /// Throws [ArgumentError] if path is invalid.
  /// Throws [CameraException] if picture capture fails.
  Future<void> takePicture(String path) async {
    try {
      if (path.isEmpty) {
        throw ArgumentError('Path cannot be empty');
      }
      await _api.takePicture(path);
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.captureFailed,
        message: 'Failed to take picture: ${e.toString()}',
      ));
      rethrow;
    }
  }

  /// Start recording video to the specified path
  ///
  /// [path] must be a valid file path where the video will be saved.
  /// Throws [ArgumentError] if path is invalid.
  /// Throws [CameraException] if recording fails to start.
  Future<void> startRecording(String path) async {
    try {
      if (path.isEmpty) {
        throw ArgumentError('Path cannot be empty');
      }
      await _api.startRecording(path);
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.recordingFailed,
        message: 'Failed to start recording: ${e.toString()}',
      ));
      rethrow;
    }
  }

  /// Stop recording video
  ///
  /// Throws [CameraException] if recording fails to stop.
  Future<void> stopRecording() async {
    try {
      await _api.stopRecording();
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.recordingFailed,
        message: 'Failed to stop recording: ${e.toString()}',
      ));
      rethrow;
    }
  }

  /// Apply a filter to the camera preview
  ///
  /// [textureId] must be a valid texture ID.
  /// [filterType] specifies the type of filter to apply.
  /// [parameters] contains additional configuration for the filter.
  ///
  /// Throws [ArgumentError] if textureId is invalid or filterType is null.
  /// Throws [CameraException] if filter application fails.
  ///
  /// @deprecated Use applyFilterWithConfig instead
  Future<void> applyFilter(int textureId, String filterType,
      [Map<String, Object?>? parameters]) async {
    try {
      if (textureId < 0) {
        throw ArgumentError('Invalid texture ID');
      }

      // Convert string to FilterType if possible
      FilterType? enumFilterType;
      switch (filterType) {
        case 'none':
          enumFilterType = FilterType.none;
          break;
        case 'beauty':
          enumFilterType = FilterType.beauty;
          break;
        case 'black_and_white':
          enumFilterType = FilterType.blackAndWhite;
          break;
        default:
          // Keep as null for any other filter type
          break;
      }

      final filterConfig = FilterConfig(
        filterType: enumFilterType,
      );

      validateFilterConfig(filterConfig);
      await _api.applyFilter(textureId, filterConfig);
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.initializationFailed,
        message: 'Failed to apply filter: ${e.toString()}',
      ));
      rethrow;
    }
  }

  /// Apply a filter to the camera preview using a FilterConfig
  ///
  /// [textureId] must be a valid texture ID.
  /// [filterConfig] specifies the filter configuration to apply.
  ///
  /// Throws [ArgumentError] if textureId is invalid.
  /// Throws [CameraException] if filter application fails.
  Future<void> applyFilterWithConfig(
      int textureId, FilterConfig filterConfig) async {
    try {
      if (textureId < 0) {
        throw ArgumentError('Invalid texture ID');
      }
      validateFilterConfig(filterConfig);
      await _api.applyFilter(textureId, filterConfig);
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.initializationFailed,
        message: 'Failed to apply filter: ${e.toString()}',
      ));
      rethrow;
    }
  }

  /// Set up the Flutter API handlers
  ///
  /// This must be called before using the plugin to handle callbacks
  /// from the native platform.
  static void setup(BeautyCameraFlutterApi api) {
    _flutterApi = api;
    BeautyCameraFlutterApi.setUp(api);
  }

  /// Handle camera errors
  static void _handleError(CameraError error) {
    _flutterApi?.onCameraError(error);
  }
}

/// Exception thrown when a camera operation fails
class CameraException implements Exception {
  final CameraErrorType type;
  final String message;

  CameraException(this.type, this.message);

  @override
  String toString() => 'CameraException: $message (Type: $type)';
}
