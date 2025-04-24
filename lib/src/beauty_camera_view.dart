import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'beauty_camera_controller.dart';
import 'camera_api.g.dart';
import 'utils/logger.dart';

/// Widget that displays a beauty camera preview with various effects and controls
class BeautyCameraView extends StatefulWidget {
  /// The controller for this camera
  final BeautyCameraController controller;

  /// Callback when an image is captured
  final Function(String imagePath)? onImageCaptured;

  /// Callback when a video recording is completed
  final Function(String videoPath)? onVideoRecorded;

  /// Callback when a face is detected
  final Function(List<FaceData> faces)? onFaceDetected;

  /// Whether to overlay face detection indicators
  final bool showFaceDetection;

  /// Whether to show camera controls
  final bool showControls;

  /// Creates a new BeautyCameraView
  const BeautyCameraView({
    super.key,
    required this.controller,
    this.onImageCaptured,
    this.onVideoRecorded,
    this.onFaceDetected,
    this.showFaceDetection = false,
    this.showControls = true,
  });

  @override
  State<BeautyCameraView> createState() => _BeautyCameraViewState();
}

class _BeautyCameraViewState extends State<BeautyCameraView>
    with WidgetsBindingObserver {
  StreamSubscription<CameraEvent>? _eventsSubscription;
  List<FaceData> _detectedFaces = [];
  int? _textureId;
  Size _previewSize = Size(1, 1);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _subscribeToEvents();
    _initPreview();
  }

  @override
  void didUpdateWidget(BeautyCameraView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      _unsubscribeFromEvents();
      _subscribeToEvents();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _unsubscribeFromEvents();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Handle app lifecycle changes for camera
    if (widget.controller.isInitialized) {
      if (state == AppLifecycleState.inactive) {
        // App is inactive, pause camera preview
      } else if (state == AppLifecycleState.resumed) {
        // App is resumed, resume camera preview
      }
    }
  }

  void _subscribeToEvents() {
    _eventsSubscription = widget.controller.events.listen((event) {
      switch (event.type) {
        case CameraEventType.faceDetected:
          if (event.data is List<FaceData>) {
            final List<FaceData> faces = event.data as List<FaceData>;
            setState(() {
              _detectedFaces = faces;
            });

            if (widget.onFaceDetected != null) {
              widget.onFaceDetected!(faces);
            }
          }
          break;
        case CameraEventType.photoTaken:
          if (event.data is String && widget.onImageCaptured != null) {
            widget.onImageCaptured!(event.data as String);
          }
          break;
        case CameraEventType.recordingStopped:
          if (event.data is String && widget.onVideoRecorded != null) {
            widget.onVideoRecorded!(event.data as String);
          }
          break;
        default:
          break;
      }
    });
  }

  void _unsubscribeFromEvents() {
    _eventsSubscription?.cancel();
    _eventsSubscription = null;
  }

  Future<void> _initPreview() async {
    if (widget.controller.isInitialized) {
      try {
        final textureId = await widget.controller.getPreviewTexture();
        final previewSize = await widget.controller.getPreviewSize();
        Logger.log('Preview texture ID: $textureId');
        Logger.log('Preview size: $previewSize');

        if (mounted) {
          setState(() {
            _textureId = textureId;
            _previewSize = previewSize;
          });
        }
      } catch (e) {
        Logger.error('Error initializing camera preview: $e');
      }
    }
  }

  void _handleTap(TapDownDetails details) {
    if (!widget.controller.isInitialized) return;

    final RenderBox box = context.findRenderObject() as RenderBox;
    final Offset localPoint = box.globalToLocal(details.globalPosition);

    final int x = localPoint.dx.toInt();
    final int y = localPoint.dy.toInt();

    widget.controller.focusOnPoint(x, y);
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: _handleTap,
      child: Stack(
        fit: StackFit.expand,
        children: [
          // Camera preview
          ClipRect(
            child: _buildCameraPreview(),
          ),

          // Face detection overlays
          if (widget.showFaceDetection) _buildFaceDetectionOverlay(),

          // Camera controls
          if (widget.showControls) _buildCameraControls(),
        ],
      ),
    );
  }

  Widget _buildCameraPreview() {
    if (!widget.controller.isInitialized) {
      return const Center(
        child: CircularProgressIndicator(),
      );
    }

    if (_textureId == null) {
      return const Center(
        child: Text(
          'Initializing camera...',
          style: TextStyle(color: Colors.white),
        ),
      );
    }

    // Use OrientationBuilder to rebuild when device orientation changes
    return Center(
      child: AspectRatio(
        aspectRatio: _previewSize.width / _previewSize.height,
        child: Texture(textureId: _textureId!),
      ),
    );
  }

  Widget _buildFaceDetectionOverlay() {
    if (_detectedFaces.isEmpty) {
      return Container(); // Return empty container if no faces detected
    }

    return CustomPaint(
      painter: FaceDetectionPainter(
        faces: _detectedFaces,
        previewSize: _previewSize,
      ),
    );
  }

  Widget _buildCameraControls() {
    return Positioned(
      bottom: 20,
      left: 0,
      right: 0,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          // Flash toggle
          IconButton(
            icon: Icon(
              _getFlashIcon(widget.controller.flashMode),
              color: Colors.white,
              size: 28,
            ),
            onPressed: _cycleFlashMode,
          ),

          // Capture button
          GestureDetector(
            onTap: _capturePhoto,
            onLongPress: _startVideoRecording,
            onLongPressUp: _stopVideoRecording,
            child: Container(
              height: 70,
              width: 70,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withAlpha(76),
                border: Border.all(color: Colors.white, width: 3),
              ),
              child: widget.controller.isRecording
                  ? const Icon(Icons.stop, color: Colors.red, size: 30)
                  : const Icon(Icons.camera_alt, color: Colors.white, size: 30),
            ),
          ),

          // Switch camera
          IconButton(
            icon: const Icon(
              Icons.flip_camera_ios,
              color: Colors.white,
              size: 28,
            ),
            onPressed: () => widget.controller.switchCamera(),
          ),
        ],
      ),
    );
  }

  IconData _getFlashIcon(FlashMode mode) {
    switch (mode) {
      case FlashMode.off:
        return Icons.flash_off;
      case FlashMode.on:
        return Icons.flash_on;
      case FlashMode.auto:
        return Icons.flash_auto;
      case FlashMode.torch:
        return Icons.flashlight_on;
    }
  }

  void _cycleFlashMode() {
    FlashMode newMode;

    switch (widget.controller.flashMode) {
      case FlashMode.off:
        newMode = FlashMode.on;
        break;
      case FlashMode.on:
        newMode = FlashMode.auto;
        break;
      case FlashMode.auto:
        newMode = FlashMode.torch;
        break;
      case FlashMode.torch:
        newMode = FlashMode.off;
        break;
    }

    widget.controller.setFlashMode(newMode);
  }

  void _capturePhoto() async {
    if (!widget.controller.isInitialized || widget.controller.isRecording) {
      return;
    }

    try {
      await widget.controller.takePhoto();
    } catch (e) {
      // Handle capture error
      Logger.log('Failed to capture photo: $e');
    }
  }

  void _startVideoRecording() async {
    if (!widget.controller.isInitialized || widget.controller.isRecording) {
      return;
    }

    try {
      await widget.controller.startVideoRecording();
    } catch (e) {
      // Handle recording error
      Logger.log('Failed to start recording: $e');
    }
  }

  void _stopVideoRecording() async {
    if (!widget.controller.isRecording) {
      return;
    }

    try {
      await widget.controller.stopVideoRecording();
    } catch (e) {
      // Handle stop recording error
      Logger.log('Failed to stop recording: $e');
    }
  }
}

/// Custom painter for face detection overlays
class FaceDetectionPainter extends CustomPainter {
  final List<FaceData> faces;
  final Size previewSize;

  FaceDetectionPainter({
    required this.faces,
    required this.previewSize,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (faces.isEmpty) return;

    final Paint paint = Paint()
      ..color = Colors.green
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3.0;

    // Calculate scale factors
    final double scaleX = size.width / previewSize.width;
    final double scaleY = size.height / previewSize.height;

    for (final face in faces) {
      // Scale the face coordinates to match the screen size
      final double left = face.x * scaleX;
      final double top = face.y * scaleY;
      final double faceSize = face.size * math.min(scaleX, scaleY);

      // Draw face rectangle
      canvas.drawRect(
        Rect.fromLTWH(left, top, faceSize, faceSize),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(FaceDetectionPainter oldDelegate) {
    return oldDelegate.faces != faces;
  }
}

/// Math utility class
class Math {
  static double min(double a, double b) {
    return a < b ? a : b;
  }
}
