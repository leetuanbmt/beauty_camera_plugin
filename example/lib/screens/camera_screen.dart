import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../controllers/camera_controller.dart';
import '../widgets/camera_controls.dart';
import '../widgets/effects_grid.dart';
import '../widgets/image_preview.dart';
import 'dart:io';

class CameraScreen extends ConsumerStatefulWidget {
  const CameraScreen({super.key});

  @override
  ConsumerState<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends ConsumerState<CameraScreen>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(cameraControllerProvider.notifier).checkPermissions();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final controller = ref.read(cameraControllerProvider.notifier);

    if (state == AppLifecycleState.inactive) {
      // Không cần gọi dispose vì provider sẽ tự động quản lý
    } else if (state == AppLifecycleState.resumed) {
      controller.initializeCamera();
    }
  }

  void _showEffectsSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.black87,
      isScrollControlled: true,
      builder: (context) => Consumer(
        builder: (context, ref, _) {
          final currentState = ref.watch(cameraControllerProvider);

          return EffectsGrid(
            currentEffect: currentState.currentEffect,
            onEffectSelected: (effect) {
              ref.read(cameraControllerProvider.notifier).setEffectMode(effect);
              Navigator.pop(context);
            },
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(cameraControllerProvider);
    final controller = ref.read(cameraControllerProvider.notifier);

    if (!state.hasPermission) {
      return _buildPermissionRequest();
    }

    return Scaffold(
      backgroundColor: Colors.black,
      body: state.lastImagePath != null
          ? FutureBuilder<bool>(
              future: _checkImageFile(state.lastImagePath!),
              builder: (context, snapshot) {
                if (snapshot.connectionState == ConnectionState.waiting) {
                  return const Center(
                    child: CircularProgressIndicator(color: Colors.white),
                  );
                }

                final fileValid = snapshot.data ?? false;

                if (!fileValid) {
                  // File không tồn tại hoặc không hợp lệ
                  // Tự động quay lại chế độ máy ảnh
                  WidgetsBinding.instance.addPostFrameCallback((_) {
                    controller.discardImage();
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Image file not found or invalid'),
                        backgroundColor: Colors.red,
                      ),
                    );
                  });

                  return const Center(
                    child: CircularProgressIndicator(color: Colors.white),
                  );
                }

                return ImagePreview(
                  imagePath: state.lastImagePath!,
                  onDiscard: () => controller.discardImage(),
                  onSave: () {
                    controller.saveImage();
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                          content: Text('Image saved: ${state.lastImagePath}')),
                    );
                  },
                );
              },
            )
          : Stack(
              children: [
                if (state.isInitializing)
                  const Center(
                    child: CircularProgressIndicator(
                      color: Colors.white,
                    ),
                  )
                else
                  BeautyCameraView(
                    controller: controller.cameraController,
                    onImageCaptured: (path) {
                      controller.handleCapturedImage(path);
                    },
                    showFaceDetection: true,
                  ),
                if (!state.isInitializing)
                  CameraControls(
                    onTakePhoto: () => controller.takePhoto(),
                    onSwitchCamera: () => controller.switchCamera(),
                    onShowEffects: _showEffectsSheet,
                  ),
              ],
            ),
    );
  }

  Widget _buildPermissionRequest() {
    return Scaffold(
      appBar: AppBar(title: const Text('Beauty Camera')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text(
              'Camera and microphone permissions are required',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: () => ref
                  .read(cameraControllerProvider.notifier)
                  .checkPermissions(),
              child: const Text('Grant Permissions'),
            ),
          ],
        ),
      ),
    );
  }

  Future<bool> _checkImageFile(String path) async {
    try {
      final file = File(path);
      final exists = await file.exists();
      final size = exists ? await file.length() : 0;

      debugPrint('_checkImageFile: $path - exists=$exists, size=$size bytes');

      return exists && size > 0;
    } catch (e) {
      debugPrint('Error checking image file: $e');
      return false;
    }
  }
}
