import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:path_provider/path_provider.dart';

import '../beauty_camera_plugin.dart';
import 'utils/logger.dart';

/// A controller class for managing beauty camera operations
class BeautyCameraController extends ChangeNotifier {
  final BeautyCameraPlugin _plugin;
  CameraError? _lastError;

  int? _textureId;
  bool _isInitialized = false;
  bool _isRecording = false;
  String _currentFilter = 'none';
  String? _lastMediaPath;

  Size? _previewSize;

  double get aspectRatio => _previewSize?.aspectRatio ?? 1.0;

  /// Last camera error
  CameraError? get lastError => _lastError;

  /// Current texture ID for camera preview
  int? get textureId => _textureId;

  /// Whether the camera is initialized
  bool get isInitialized => _isInitialized;

  /// Whether the camera is currently recording
  bool get isRecording => _isRecording;

  /// Current applied filter
  String get currentFilter => _currentFilter;

  /// Path to the last captured media (photo or video)
  String? get lastMediaPath => _lastMediaPath;

  /// Current camera state
  CameraState get state => CameraState(
        isInitialized: _isInitialized,
        isRecording: _isRecording,
        currentFilter: _currentFilter,
        textureId: _textureId,
      );

  /// Creates a new [BeautyCameraController]
  BeautyCameraController() : _plugin = BeautyCameraPlugin() {
    _setupCallbacks();
  }

  void _setupCallbacks() {
    BeautyCameraPlugin.setup(_CameraCallbacks(this));
  }

  /// Initialize the camera with specified settings
  Future<void> initialize({
    int? width,
    int? height,
    String defaultFilter = 'none',
  }) async {
    try {
      await _plugin.initializeCamera(width: width, height: height);

      _textureId = await _plugin.createPreviewTexture();

      Logger.log("BeautyCameraController - textureId: $_textureId");

      if (_textureId == null) {
        throw CameraException(
          CameraErrorType.initializationFailed,
          'Failed to create preview texture',
        );
      }

      // TextureId 0 is valid, so we continue
      Logger.log(
          "BeautyCameraController - Starting preview with textureId: $_textureId");
      await _plugin.startPreview(_textureId!);

      await applyFilter(defaultFilter);

      _isInitialized = true;
      _currentFilter = defaultFilter;
      notifyListeners();
    } catch (e) {
      Logger.log("BeautyCameraController - Initialization error: $e");
      onCameraError(CameraError(
        type: CameraErrorType.initializationFailed,
        message: e.toString(),
      ));
      rethrow;
    }
  }

  /// Generate a path for saving a photo
  Future<String> generatePhotoPath() async {
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    final directory = await getTemporaryDirectory();
    return '${directory.path}/beauty_camera_$timestamp.jpg';
  }

  /// Generate a path for saving a video
  Future<String> generateVideoPath() async {
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    final directory = await getTemporaryDirectory();
    return '${directory.path}/beauty_camera_$timestamp.mp4';
  }

  /// Take a picture and save it
  Future<String> takePicture() async {
    if (!_isInitialized) {
      throw CameraException(
        CameraErrorType.initializationFailed,
        'Camera not initialized',
      );
    }

    try {
      final path = await generatePhotoPath();
      await _plugin.takePicture(path);
      _lastMediaPath = path;
      return path;
    } catch (e) {
      onCameraError(CameraError(
        type: CameraErrorType.captureFailed,
        message: e.toString(),
      ));
      rethrow;
    }
  }

  /// Take a picture and save it to the specified path
  Future<void> takePictureWithPath(String path) async {
    if (!_isInitialized) {
      throw CameraException(
        CameraErrorType.initializationFailed,
        'Camera not initialized',
      );
    }

    try {
      await _plugin.takePicture(path);
      _lastMediaPath = path;
    } catch (e) {
      onCameraError(CameraError(
        type: CameraErrorType.captureFailed,
        message: e.toString(),
      ));
      rethrow;
    }
  }

  /// Start recording video
  Future<String> startRecording() async {
    if (!_isInitialized) {
      throw CameraException(
        CameraErrorType.initializationFailed,
        'Camera not initialized',
      );
    }

    try {
      final path = await generateVideoPath();
      await _plugin.startRecording(path);
      _isRecording = true;
      _lastMediaPath = path;
      notifyListeners();
      return path;
    } catch (e) {
      onCameraError(CameraError(
        type: CameraErrorType.recordingFailed,
        message: e.toString(),
      ));
      rethrow;
    }
  }

  /// Start recording video to the specified path
  Future<void> startRecordingWithPath(String path) async {
    if (!_isInitialized) {
      throw CameraException(
        CameraErrorType.initializationFailed,
        'Camera not initialized',
      );
    }

    try {
      await _plugin.startRecording(path);
      _isRecording = true;
      _lastMediaPath = path;
      notifyListeners();
    } catch (e) {
      onCameraError(CameraError(
        type: CameraErrorType.recordingFailed,
        message: e.toString(),
      ));
      rethrow;
    }
  }

  /// Stop recording video
  Future<String?> stopRecording() async {
    if (!_isRecording) return null;

    try {
      await _plugin.stopRecording();
      _isRecording = false;
      notifyListeners();
      return _lastMediaPath;
    } catch (e) {
      onCameraError(CameraError(
        type: CameraErrorType.recordingFailed,
        message: e.toString(),
      ));
      rethrow;
    }
  }

  /// Apply a filter to the camera preview
  Future<void> applyFilter(String filterType,
      [Map<String, Object?>? parameters]) async {
    if (!_isInitialized) {
      throw CameraException(
        CameraErrorType.initializationFailed,
        'Camera not initialized',
      );
    }

    try {
      if (_textureId == null) {
        throw CameraException(
          CameraErrorType.initializationFailed,
          'TextureId is null',
        );
      }

      Logger.log(
          "BeautyCameraController - Applying filter $filterType with textureId: $_textureId");
      await _plugin.applyFilter(_textureId!, filterType, parameters);
      _currentFilter = filterType;
      notifyListeners();
    } catch (e) {
      Logger.log("BeautyCameraController - Error applying filter: $e");
      onCameraError(CameraError(
        type: CameraErrorType.initializationFailed,
        message: 'Failed to apply filter: ${e.toString()}',
      ));
      rethrow;
    }
  }

  Future<void> resetCamera() async {
    _isInitialized = false;
    _isRecording = false;
    _textureId = null;
    _previewSize = null;
    await _plugin.stopPreview();

    await _plugin.disposeCamera();

    await initialize();

    notifyListeners();
  }

  /// Dispose of all camera resources
  @override
  Future<void> dispose() async {
    try {
      await _plugin.stopPreview();

      await _plugin.disposeCamera();

      _isInitialized = false;
      _isRecording = false;
      _textureId = null;
    } catch (e) {
      onCameraError(CameraError(
        type: CameraErrorType.initializationFailed,
        message: 'Failed to dispose camera: ${e.toString()}',
      ));
    } finally {
      super.dispose();
    }
  }

  void onCameraInitialized(int textureId, int width, int height) {
    Logger.log(
      "BeautyCameraController - onCameraInitialized: $textureId, $width, $height",
    );
    _textureId = textureId;
    _previewSize = Size(width.toDouble(), height.toDouble());
    _isInitialized = true;
    notifyListeners();
  }

  void onTakePictureCompleted(String path) {
    _lastMediaPath = path;
    notifyListeners();
  }

  void onRecordingStarted() {
    _isRecording = true;
    notifyListeners();
  }

  void onRecordingStopped(String path) {
    _isRecording = false;
    _lastMediaPath = path;
    notifyListeners();
  }

  void onCameraError(CameraError error) {
    _lastError = error;
    notifyListeners();
  }
}

/// Represents the current state of the camera
class CameraState {
  final bool isInitialized;
  final bool isRecording;
  final String currentFilter;
  final int? textureId;

  CameraState({
    required this.isInitialized,
    required this.isRecording,
    required this.currentFilter,
    this.textureId,
  });
}

/// Implementation of BeautyCameraFlutterApi for handling callbacks
class _CameraCallbacks extends BeautyCameraFlutterApi {
  final BeautyCameraController _controller;

  _CameraCallbacks(this._controller);

  @override
  Future<void> onCameraInitialized(int textureId, int width, int height) async {
    _controller.onCameraInitialized(textureId, width, height);
  }

  @override
  Future<void> onTakePictureCompleted(String path) async {
    _controller.onTakePictureCompleted(path);
  }

  @override
  Future<void> onRecordingStarted() async {
    _controller.onRecordingStarted();
  }

  @override
  Future<void> onRecordingStopped(String path) async {
    _controller.onRecordingStopped(path);
  }

  @override
  Future<void> onCameraError(CameraError error) async {
    _controller.onCameraError(error);
  }
}
