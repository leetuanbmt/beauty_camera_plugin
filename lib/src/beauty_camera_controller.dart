import 'dart:async';
import 'package:flutter/services.dart';

import 'camera_api.g.dart';

/// Controller for managing a beauty camera with various effects and settings
class BeautyCameraController {
  /// The native API interface for communicating with platform-specific code
  final BeautyCameraHostApi _api = BeautyCameraHostApi();

  /// Stream controller for camera events
  final _eventStreamController = StreamController<CameraEvent>.broadcast();

  /// Stream of camera events
  Stream<CameraEvent> get events => _eventStreamController.stream;

  /// Current flash mode of the camera
  FlashMode _currentFlashMode = FlashMode.off;

  /// Current camera effect mode
  CameraFilterMode _currentEffectMode = CameraFilterMode.none;

  /// Current zoom level
  double _currentZoomLevel = 1.0;

  /// Flag indicating if the camera is initialized
  bool _isInitialized = false;

  /// Flag indicating if the camera is currently recording
  bool _isRecording = false;

  /// Flag indicating which camera is currently active (true = front, false = back)
  bool _isFrontCamera = false;

  /// Gets the current flash mode
  FlashMode get flashMode => _currentFlashMode;

  /// Gets the current effect mode
  CameraFilterMode get effectMode => _currentEffectMode;

  /// Gets the current zoom level
  double get zoomLevel => _currentZoomLevel;

  /// Gets whether the camera is initialized
  bool get isInitialized => _isInitialized;

  /// Gets whether the camera is currently recording
  bool get isRecording => _isRecording;

  /// Gets whether the front camera is active
  bool get isFrontCamera => _isFrontCamera;

  /// Face detected data
  FaceData? _faceDetected;

  /// Gets the face detected data
  FaceData? get faceDetected => _faceDetected;

  /// Creates a new camera controller
  BeautyCameraController() {
    _setupFlutterApi();
  }

  /// Sets up the Flutter API to receive callbacks from the platform
  void _setupFlutterApi() {
    final flutterApi = BeautyCameraPlugin(
      onEvent: (event) {
        _eventStreamController.add(event);
        switch (event.type) {
          case CameraEventType.zoomChanged:
            _currentZoomLevel = event.data;
            break;
          case CameraEventType.flashModeChanged:
            _currentFlashMode = event.data;
            break;
          case CameraEventType.cameraSwitched:
            _isFrontCamera = event.data == 'front';
            break;
          case CameraEventType.effectChanged:
            _currentEffectMode = event.data;
            break;
          case CameraEventType.recordingStarted:
            _isRecording = true;
            break;
          case CameraEventType.recordingStopped:
            _isRecording = false;
            break;
          case CameraEventType.faceDetected:
            _faceDetected = event.data;
            break;
          default:
            break;
        }
      },
    );

    BeautyCameraFlutterApi.setUp(flutterApi);
  }

  /// Initializes the camera with the specified settings
  Future<void> initialize({
    AdvancedCameraSettings? settings,
  }) async {
    try {
      await _api.initialize(settings ?? AdvancedCameraSettings());

      _isInitialized = true;

      _eventStreamController.add(CameraEvent(
        type: CameraEventType.initialized,
      ));

      return;
    } on PlatformException catch (e) {
      throw CameraException(
        e.code,
        e.message ?? 'An unknown camera error occurred',
      );
    }
  }

  /// Switches between front and back camera
  Future<void> switchCamera() async {
    try {
      await _api.switchCamera();
    } on PlatformException catch (e) {
      throw CameraException(
        e.code,
        e.message ?? 'Failed to switch camera',
      );
    }
  }

  /// Sets the zoom level of the camera
  Future<void> setZoom(double zoomLevel) async {
    try {
      if (zoomLevel < 1.0) zoomLevel = 1.0;
      if (zoomLevel > 10.0) zoomLevel = 10.0;

      await _api.setZoom(zoomLevel);
      _currentZoomLevel = zoomLevel;
    } on PlatformException catch (e) {
      throw CameraException(
        e.code,
        e.message ?? 'Failed to set zoom level',
      );
    }
  }

  /// Sets the flash mode of the camera
  Future<void> setFlashMode(FlashMode mode) async {
    try {
      await _api.setFlashMode(mode);
      _currentFlashMode = mode;
    } on PlatformException catch (e) {
      throw CameraException(
        e.code,
        e.message ?? 'Failed to set flash mode',
      );
    }
  }

  /// Sets the effect mode of the camera
  Future<void> setEffectMode(CameraFilterMode mode) async {
    try {
      await _api.setFilterMode(mode);
      _currentEffectMode = mode;
      _eventStreamController.add(CameraEvent(
        type: CameraEventType.effectChanged,
        data: mode,
      ));
    } on PlatformException catch (e) {
      throw CameraException(
        e.code,
        e.message ?? 'Failed to set effect mode',
      );
    }
  }

  /// Focuses the camera on a specific point in the preview
  Future<void> focusOnPoint(int x, int y) async {
    try {
      await _api.focusOnPoint(x, y);
    } on PlatformException catch (e) {
      throw CameraException(
        e.code,
        e.message ?? 'Failed to focus on point',
      );
    }
  }

  /// Takes a photo and returns the path to the saved image
  Future<String> takePhoto({String? savePath}) async {
    try {
      // Call to native implementation to take photo
      final path = await _api.takePhoto();

      // Notify about photo taken event
      _eventStreamController.add(CameraEvent(
        type: CameraEventType.photoTaken,
        data: path,
      ));

      return path;
    } on PlatformException catch (e) {
      throw CameraException(
        e.code,
        e.message ?? 'Failed to take photo',
      );
    }
  }

  /// Starts recording a video
  Future<void> startVideoRecording({String? savePath}) async {
    try {
      if (_isRecording) {
        return;
      }

      await _api.startVideoRecording();

      _isRecording = true;
      _eventStreamController.add(CameraEvent(
        type: CameraEventType.recordingStarted,
      ));
    } on PlatformException catch (e) {
      throw CameraException(
        e.code,
        e.message ?? 'Failed to start video recording',
      );
    }
  }

  /// Stops recording a video and returns the path to the saved video
  Future<String> stopVideoRecording() async {
    try {
      if (!_isRecording) {
        throw CameraException(
          'recording_not_started',
          'Cannot stop recording if recording was not started',
        );
      }

      // In a real implementation, we would call a method on the API
      // to stop recording a video and get the path

      final path = await _api.stopVideoRecording();

      _isRecording = false;

      _eventStreamController.add(CameraEvent(
        type: CameraEventType.recordingStopped,
        data: path,
      ));

      return path;
    } on PlatformException catch (e) {
      throw CameraException(
        e.code,
        e.message ?? 'Failed to stop video recording',
      );
    }
  }

  /// Sets the display orientation of the camera
  Future<void> setDisplayOrientation(int degrees) async {
    try {
      await _api.setDisplayOrientation(degrees);
    } on PlatformException catch (e) {
      throw CameraException(
        e.code,
        e.message ?? 'Failed to set display orientation',
      );
    }
  }

  /// Gets the preview texture ID for rendering the camera feed
  Future<int> getPreviewTexture() async {
    try {
      final textureId = await _api.getPreviewTexture();
      return textureId;
    } on PlatformException catch (e) {
      throw CameraException(
        e.code,
        e.message ?? 'Failed to get preview texture',
      );
    }
  }

  /// Gets the size of the preview
  Future<Size> getPreviewSize() async {
    try {
      final size = await _api.getPreviewSize();
      return Size(size.width.toDouble(), size.height.toDouble());
    } on PlatformException catch (e) {
      throw CameraException(
        e.code,
        e.message ?? 'Failed to get preview size',
      );
    }
  }

  /// Get the camera sensor's aspect ratio
  /// Returns a float value representing width/height
  Future<double> getCameraSensorAspectRatio() async {
    try {
      final aspectRatio = await _api.getCameraSensorAspectRatio();
      return aspectRatio;
    } on PlatformException catch (e) {
      throw CameraException(
        e.code,
        e.message ?? 'Failed to get camera aspect ratio',
      );
    }
  }

  /// Disposes of the controller and releases resources
  Future<void> dispose() async {
    await _api.dispose();
    await _eventStreamController.close();
  }
}

/// Implementation of the BeautyCameraFlutterApi for receiving callbacks from the platform
class BeautyCameraPlugin implements BeautyCameraFlutterApi {
  final Function(CameraEvent) _onEvent;
  BeautyCameraPlugin({
    required Function(CameraEvent) onEvent,
  }) : _onEvent = onEvent;

  @override
  Future<void> onZoomChanged(double zoomLevel) async {
    final event = CameraEvent(
      type: CameraEventType.zoomChanged,
      data: zoomLevel,
    );
    _onEvent(event);
  }

  @override
  Future<void> onFlashModeChanged(FlashMode mode) async {
    final event = CameraEvent(
      type: CameraEventType.flashModeChanged,
      data: mode,
    );
    _onEvent(event);
  }

  @override
  Future<void> onCameraSwitched(String cameraId) async {
    final event = CameraEvent(
      type: CameraEventType.cameraSwitched,
      data: cameraId,
    );
    _onEvent(event);
  }

  @override
  Future<void> onFaceDetected(List<FaceData> faces) async {
    final event = CameraEvent(
      type: CameraEventType.faceDetected,
      data: faces,
    );
    _onEvent(event);
  }

  @override
  Future<void> onFilterModeChanged(CameraFilterMode mode) async {
    final event = CameraEvent(
      type: CameraEventType.effectChanged,
      data: mode,
    );
    _onEvent(event);
  }

  @override
  Future<void> onVideoRecordingStarted() async {
    final event = CameraEvent(
      type: CameraEventType.recordingStarted,
    );
    _onEvent(event);
  }

  @override
  Future<void> onVideoRecordingStopped(String path) async {
    final event = CameraEvent(
      type: CameraEventType.recordingStopped,
      data: path,
    );
    _onEvent(event);
  }
}

/// Exception thrown when a camera operation fails
class CameraException implements Exception {
  final String code;
  final String message;

  CameraException(this.code, this.message);

  @override
  String toString() => 'CameraException($code, $message)';
}

/// Types of camera events
enum CameraEventType {
  initialized,
  zoomChanged,
  flashModeChanged,
  effectChanged,
  whiteBalanceChanged,
  cameraSwitched,
  faceDetected,
  photoTaken,
  recordingStarted,
  recordingStopped,
}

/// Camera event data
class CameraEvent {
  final CameraEventType type;
  final dynamic data;

  CameraEvent({
    required this.type,
    this.data,
  });
}
