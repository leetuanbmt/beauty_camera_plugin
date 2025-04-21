import 'dart:async';

import '../beauty_camera_plugin.dart';

/// A controller class for managing beauty camera operations
class BeautyCameraController {
  final BeautyCameraPlugin _plugin;
  final _errorController = StreamController<CameraError>.broadcast();
  final _stateController = StreamController<CameraState>.broadcast();

  int? _textureId;
  bool _isInitialized = false;
  bool _isRecording = false;
  String _currentFilter = 'none';

  /// Stream of camera errors
  Stream<CameraError> get errorStream => _errorController.stream;

  /// Stream of camera state changes
  Stream<CameraState> get stateStream => _stateController.stream;

  /// Current texture ID for camera preview
  int? get textureId => _textureId;

  /// Whether the camera is initialized
  bool get isInitialized => _isInitialized;

  /// Whether the camera is currently recording
  bool get isRecording => _isRecording;

  /// Current applied filter
  String get currentFilter => _currentFilter;

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

      if (_textureId == null) {
        throw CameraException(
          CameraErrorType.initializationFailed,
          'Failed to create preview texture',
        );
      }

      await _plugin.startPreview(_textureId!);
      await applyFilter(defaultFilter);

      _isInitialized = true;
      _currentFilter = defaultFilter;
      _notifyStateChange();
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.initializationFailed,
        message: e.toString(),
      ));
      rethrow;
    }
  }

  /// Take a picture and save it to the specified path
  Future<void> takePicture(String path) async {
    if (!_isInitialized) {
      throw CameraException(
        CameraErrorType.initializationFailed,
        'Camera not initialized',
      );
    }

    try {
      await _plugin.takePicture(path);
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.captureFailed,
        message: e.toString(),
      ));
      rethrow;
    }
  }

  /// Start recording video to the specified path
  Future<void> startRecording(String path) async {
    if (!_isInitialized) {
      throw CameraException(
        CameraErrorType.initializationFailed,
        'Camera not initialized',
      );
    }

    try {
      await _plugin.startRecording(path);
      _isRecording = true;
      _notifyStateChange();
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.recordingFailed,
        message: e.toString(),
      ));
      rethrow;
    }
  }

  /// Stop recording video
  Future<void> stopRecording() async {
    if (!_isRecording) return;

    try {
      await _plugin.stopRecording();
      _isRecording = false;
      _notifyStateChange();
    } catch (e) {
      _handleError(CameraError(
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
      await _plugin.applyFilter(_textureId!, filterType, parameters);
      _currentFilter = filterType;
      _notifyStateChange();
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.initializationFailed,
        message: 'Failed to apply filter: ${e.toString()}',
      ));
      rethrow;
    }
  }

  /// Dispose of all camera resources
  Future<void> dispose() async {
    try {
      await _plugin.stopPreview();
      await _plugin.disposeCamera();
      _isInitialized = false;
      _isRecording = false;
      _textureId = null;
      _notifyStateChange();
    } catch (e) {
      _handleError(CameraError(
        type: CameraErrorType.initializationFailed,
        message: 'Failed to dispose camera: ${e.toString()}',
      ));
    } finally {
      await _errorController.close();
      await _stateController.close();
    }
  }

  void _handleError(CameraError error) {
    _errorController.add(error);
  }

  void _notifyStateChange() {
    _stateController.add(CameraState(
      isInitialized: _isInitialized,
      isRecording: _isRecording,
      currentFilter: _currentFilter,
      textureId: _textureId,
    ));
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
    _controller._textureId = textureId;
    _controller._isInitialized = true;
    _controller._notifyStateChange();
  }

  @override
  Future<void> onTakePictureCompleted(String path) async {
    // Notify UI of successful picture capture
    _controller._notifyStateChange();
  }

  @override
  Future<void> onRecordingStarted() async {
    _controller._isRecording = true;
    _controller._notifyStateChange();
  }

  @override
  Future<void> onRecordingStopped(String path) async {
    _controller._isRecording = false;
    _controller._notifyStateChange();
  }

  @override
  Future<void> onCameraError(CameraError error) async {
    _controller._handleError(error);
  }
}
