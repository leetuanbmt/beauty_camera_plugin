import 'package:flutter_test/flutter_test.dart';
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';

void main() {
  late BeautyCameraPlugin plugin;
  late MockBeautyCameraHostApi mockHostApi;
  late MockBeautyCameraFlutterApi mockFlutterApi;

  setUp(() {
    mockHostApi = MockBeautyCameraHostApi();
    mockFlutterApi = MockBeautyCameraFlutterApi();
    plugin = BeautyCameraPlugin();
    BeautyCameraPlugin.setup(mockFlutterApi);
  });

  group('BeautyCameraPlugin', () {
    test('initializeCamera validates settings', () async {
      // Test with invalid width
      expect(
        () => plugin.initializeCamera(width: -1),
        throwsA(isA<ArgumentError>()),
      );

      // Test with invalid height
      expect(
        () => plugin.initializeCamera(height: 0),
        throwsA(isA<ArgumentError>()),
      );

      // Test with valid settings
      await plugin.initializeCamera(width: 1920, height: 1080);
      expect(mockHostApi.initializeCameraCalled, true);
    });

    test('startPreview validates textureId', () async {
      // Test with invalid textureId
      expect(
        () => plugin.startPreview(0),
        throwsA(isA<ArgumentError>()),
      );

      // Test with valid textureId
      await plugin.startPreview(1);
      expect(mockHostApi.startPreviewCalled, true);
    });

    test('takePicture validates path', () async {
      // Test with empty path
      expect(
        () => plugin.takePicture(''),
        throwsA(isA<ArgumentError>()),
      );

      // Test with valid path
      await plugin.takePicture('/path/to/image.jpg');
      expect(mockHostApi.takePictureCalled, true);
    });

    test('applyFilter validates parameters', () async {
      // Test with invalid textureId
      expect(
        () => plugin.applyFilter(0, 'beauty'),
        throwsA(isA<ArgumentError>()),
      );

      // Test with empty filterType
      expect(
        () => plugin.applyFilter(1, ''),
        throwsA(isA<ArgumentError>()),
      );

      // Test with valid parameters
      await plugin.applyFilter(1, 'beauty', {'smoothness': 0.5});
      expect(mockHostApi.applyFilterCalled, true);
    });

    test('handles camera errors', () async {
      final error = CameraError(
        type: CameraErrorType.initializationFailed,
        message: 'Failed to initialize camera',
      );
      mockHostApi.simulateError(error);
      expect(mockFlutterApi.lastError, error);
    });
  });
}

/// Mock implementation of BeautyCameraHostApi for testing
class MockBeautyCameraHostApi extends BeautyCameraHostApi {
  bool initializeCameraCalled = false;
  bool startPreviewCalled = false;
  bool takePictureCalled = false;
  bool applyFilterCalled = false;
  CameraError? lastError;

  @override
  Future<void> initializeCamera(CameraSettings settings) async {
    initializeCameraCalled = true;
  }

  @override
  Future<int?> createPreviewTexture() async {
    return 1;
  }

  @override
  Future<void> startPreview(int textureId) async {
    startPreviewCalled = true;
  }

  @override
  Future<void> stopPreview() async {}

  @override
  Future<void> disposeCamera() async {}

  @override
  Future<void> takePicture(String path) async {
    takePictureCalled = true;
  }

  @override
  Future<void> startRecording(String path) async {}

  @override
  Future<void> stopRecording() async {}

  @override
  Future<void> applyFilter(int textureId, FilterConfig filterConfig) async {
    applyFilterCalled = true;
  }

  void simulateError(CameraError error) {
    lastError = error;
  }
}

/// Mock implementation of BeautyCameraFlutterApi for testing
class MockBeautyCameraFlutterApi extends BeautyCameraFlutterApi {
  CameraError? lastError;

  @override
  void onCameraInitialized(int textureId, int width, int height) {}

  @override
  void onTakePictureCompleted(String path) {}

  @override
  void onRecordingStarted() {}

  @override
  void onRecordingStopped(String path) {}

  @override
  void onCameraError(CameraError error) {
    lastError = error;
  }
}
