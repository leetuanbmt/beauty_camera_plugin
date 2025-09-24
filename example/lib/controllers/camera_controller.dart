import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:permission_handler/permission_handler.dart';
import 'dart:io';

class CameraState {
  final bool isInitialized;
  final bool isInitializing;
  final FilterCategory currentEffect;
  final String? lastImagePath;
  final bool hasPermission;
  final bool isFilterEnabled;

  const CameraState({
    this.isInitialized = false,
    this.isInitializing = true,
    this.currentEffect = FilterCategory.none,
    this.lastImagePath,
    this.hasPermission = false,
    this.isFilterEnabled = false,
  });

  CameraState copyWith({
    bool? isInitialized,
    bool? isInitializing,
    FilterCategory? currentEffect,
    String? lastImagePath,
    bool? hasPermission,
    bool? isFilterEnabled,
  }) {
    return CameraState(
      isInitialized: isInitialized ?? this.isInitialized,
      isInitializing: isInitializing ?? this.isInitializing,
      currentEffect: currentEffect ?? this.currentEffect,
      lastImagePath: lastImagePath ?? this.lastImagePath,
      hasPermission: hasPermission ?? this.hasPermission,
      isFilterEnabled: isFilterEnabled ?? this.isFilterEnabled,
    );
  }

  CameraState clearLastImage() {
    return copyWith(lastImagePath: null);
  }
}

class CameraControllerNotifier extends StateNotifier<CameraState> {
  final BeautyCameraController _cameraController;

  CameraControllerNotifier()
      : _cameraController = BeautyCameraController(),
        super(
          const CameraState(
            currentEffect: FilterCategory.beauty,
            isFilterEnabled: true,
          ),
        );

  BeautyCameraController get cameraController => _cameraController;

  Future<void> checkPermissions() async {
    final cameraStatus = await Permission.camera.request();
    final microphoneStatus = await Permission.microphone.request();

    final hasPermission = cameraStatus.isGranted && microphoneStatus.isGranted;
    state = state.copyWith(hasPermission: hasPermission);

    if (hasPermission) {
      await initializeCamera();
    }
  }

  Future<void> initializeCamera() async {
    if (!state.hasPermission) return;

    try {
      state = state.copyWith(isInitializing: true);

      await _cameraController.initialize(
        settings: AdvancedCameraSettings(
          videoQuality: VideoQuality.high,
          videoStabilization: true,
          autoExposure: true,
          enableFaceDetection: true,
          cameraLensFacing: CameraFacing.front,
          isFilterEnabled: state.isFilterEnabled,
        ),
      );

      state = state.copyWith(
        isInitialized: true,
        isInitializing: false,
      );
    } catch (e) {
      Logger.log('Error initializing camera: $e');
      state = state.copyWith(
        isInitializing: false,
        isInitialized: false,
      );
    }
  }

  Future<void> takePhoto() async {
    try {
      final imagePath = await _cameraController.takePhoto();

      // Kiểm tra tệp
      final file = File(imagePath);
      final exists = await file.exists();
      final size = exists ? await file.length() : 0;
      Logger.log('takePhoto: File path = $imagePath');
      Logger.log('takePhoto: File exists = $exists, size = $size bytes');

      if (!exists || size == 0) {
        Logger.log('takePhoto: File is invalid or empty');
        return;
      }

      state = state.copyWith(lastImagePath: imagePath);
    } catch (e) {
      Logger.log('Error taking photo: $e');
      // Handle error - could use a separate error state property
    }
  }

  void handleCapturedImage(String path) {
    state = state.copyWith(lastImagePath: path);
  }

  Future<void> switchCamera() async {
    try {
      await _cameraController.switchCamera();
    } catch (e) {
      Logger.log('Error switching camera: $e');
    }
  }

  Future<void> setEffectMode(FilterCategory effect) async {
    try {
      // TODO: Implement applyFilter when native code is ready
      // await _cameraController.applyFilter(effect, FilterType.none, FilterParameters());
      state = state.copyWith(currentEffect: effect);
    } catch (e) {
      Logger.log('Error changing effect: $e');
    }
  }

  /// Toggle filter on/off - this will also enable/disable OpenGL
  Future<void> toggleFilter() async {
    try {
      final newFilterEnabled = !state.isFilterEnabled;
      await setFilterEnabled(newFilterEnabled);
    } catch (e) {
      Logger.log('Error toggling filter: $e');
    }
  }

  Future<void> setFlashMode(FlashMode mode) async {
    try {
      await _cameraController.setFlashMode(mode);
    } catch (e) {
      Logger.log('Error setting flash mode: $e');
    }
  }

  Future<void> setFilterEnabled(bool enabled) async {
    try {
      // Update state only - no need to reinitialize camera
      Logger.log("Set filter enabled: $enabled");
      state = state.copyWith(isFilterEnabled: enabled);
      await _cameraController.setFilterEnabled(enabled);
    } catch (e) {
      Logger.log('Error setting filter enabled: $e');
    }
  }

  Future<void> setBeautyFilter(FilterParameters parameters) async {
    try {
      await _cameraController.setBeautyFilter(parameters);
      state = state.copyWith(isFilterEnabled: parameters.intensity > 0.0);
      Logger.log(
          'Beauty filter applied with intensity: ${parameters.intensity}');
    } catch (e) {
      Logger.log('Error setting beauty filter: $e');
    }
  }

  void discardImage() {
    state = state.clearLastImage();
  }

  void saveImage() {
    // Here you could add code to permanently save the image if needed
    state = state.clearLastImage();
  }

  @override
  void dispose() {
    _cameraController.dispose();
    super.dispose();
  }
}

final cameraControllerProvider =
    StateNotifierProvider.autoDispose<CameraControllerNotifier, CameraState>(
        (ref) {
  return CameraControllerNotifier();
});

final cameraProvider = Provider<BeautyCameraController>((ref) {
  return ref.watch(cameraControllerProvider.notifier).cameraController;
});
