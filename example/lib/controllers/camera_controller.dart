import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:permission_handler/permission_handler.dart';
import 'dart:io';

class CameraState {
  final bool isInitialized;
  final bool isInitializing;
  final CameraFilterMode currentEffect;
  final String? lastImagePath;
  final bool hasPermission;

  const CameraState({
    this.isInitialized = false,
    this.isInitializing = true,
    this.currentEffect = CameraFilterMode.none,
    this.lastImagePath,
    this.hasPermission = false,
  });

  CameraState copyWith({
    bool? isInitialized,
    bool? isInitializing,
    CameraFilterMode? currentEffect,
    String? lastImagePath,
    bool? hasPermission,
  }) {
    return CameraState(
      isInitialized: isInitialized ?? this.isInitialized,
      isInitializing: isInitializing ?? this.isInitializing,
      currentEffect: currentEffect ?? this.currentEffect,
      lastImagePath: lastImagePath ?? this.lastImagePath,
      hasPermission: hasPermission ?? this.hasPermission,
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
        super(const CameraState());

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
          videoQuality: VideoQuality.low,
          videoStabilization: true,
          autoExposure: true,
          enableFaceDetection: true,
        ),
      );

      state = state.copyWith(
        isInitialized: true,
        isInitializing: false,
      );
    } catch (e) {
      debugPrint('Error initializing camera: $e');
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
      debugPrint('takePhoto: File path = $imagePath');
      debugPrint('takePhoto: File exists = $exists, size = $size bytes');

      if (!exists || size == 0) {
        debugPrint('takePhoto: File is invalid or empty');
        return;
      }

      state = state.copyWith(lastImagePath: imagePath);
    } catch (e) {
      debugPrint('Error taking photo: $e');
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
      debugPrint('Error switching camera: $e');
    }
  }

  Future<void> setEffectMode(CameraFilterMode effect) async {
    try {
      await _cameraController.setEffectMode(effect);
      state = state.copyWith(currentEffect: effect);
    } catch (e) {
      debugPrint('Error changing effect: $e');
    }
  }

  Future<void> setFlashMode(FlashMode mode) async {
    try {
      await _cameraController.setFlashMode(mode);
    } catch (e) {
      debugPrint('Error setting flash mode: $e');
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
