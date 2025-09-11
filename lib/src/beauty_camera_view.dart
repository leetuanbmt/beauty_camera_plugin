import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'beauty_camera_controller.dart';
import 'camera_api.g.dart';
import 'utils/logger.dart';

class BeautyCameraView extends StatefulWidget {
  final BeautyCameraController controller;
  final Function(String imagePath)? onImageCaptured;
  final Function(String videoPath)? onVideoRecorded;
  final Function(List<FaceData> faces)? onFaceDetected;
  final bool showFaceDetection;
  final bool showControls;

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
  Size _previewSize = const Size(1, 1);
  bool _isInitializing = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _subscribeToEvents();
    _initializeCamera();
  }

  Future<void> _initializeCamera() async {
    try {
      // 1. Gọi initialize từ controller
      if (!widget.controller.isInitialized) return;

      // 2. Nếu thành công, lấy textureId và preview size
      final textureId = await widget.controller.getPreviewTexture();
      final previewSize = await widget.controller.getPreviewSize();
      Logger.log('textureId:$textureId');
      Logger.log('previewSize:$previewSize');
      if (mounted) {
        setState(() {
          _textureId = textureId;
          _previewSize = previewSize;
          _isInitializing = false;
        });
      }
    } catch (e) {
      Logger.error('Error initializing camera: $e');
      if (mounted) {
        setState(() {
          _error = e.toString();
          _isInitializing = false;
        });
      }
    }
  }

  @override
  void didUpdateWidget(BeautyCameraView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      _unsubscribeFromEvents();
      _subscribeToEvents();
      // Re-initialize if the controller changes
      _initializeCamera();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _unsubscribeFromEvents();
    // Không cần gọi dispose controller ở đây, nó nên được quản lý ở nơi tạo ra nó
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (!widget.controller.isInitialized) return;

    if (state == AppLifecycleState.inactive) {
      // Tạm dừng camera khi app không active
    } else if (state == AppLifecycleState.resumed) {
      // Khởi động lại camera khi app được resume
      _initializeCamera();
    }
  }

  void _subscribeToEvents() {
    _eventsSubscription = widget.controller.events.listen((event) {
      if (!mounted) return;
      switch (event.type) {
        case CameraEventType.faceDetected:
          if (event.data is List<FaceData>) {
            final List<FaceData> faces = event.data as List<FaceData>;
            setState(() {
              _detectedFaces = faces;
            });
            widget.onFaceDetected?.call(faces);
          }
          break;
        case CameraEventType.photoTaken:
          if (event.data is String) {
            widget.onImageCaptured?.call(event.data as String);
          }
          break;
        case CameraEventType.recordingStopped:
          if (event.data is String) {
            widget.onVideoRecorded?.call(event.data as String);
          }
          break;
        default:
          setState(() {}); // Rebuild for other events like flash, zoom etc.
          break;
      }
    });
  }

  void _unsubscribeFromEvents() {
    _eventsSubscription?.cancel();
    _eventsSubscription = null;
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
          _buildCameraPreview(),
          if (widget.showFaceDetection) _buildFaceDetectionOverlay(),
          if (widget.showControls) _buildCameraControls(),
        ],
      ),
    );
  }

  Widget _buildCameraPreview() {
    if (_error != null) {
      return Center(
        child: Text(
          'Error: $_error',
          style: const TextStyle(color: Colors.red),
        ),
      );
    }

    if (_isInitializing || _textureId == null) {
      return const Center(
        child: CircularProgressIndicator(),
      );
    }

    return AspectRatio(
        aspectRatio: _previewSize.width / _previewSize.height,
        child: Texture(textureId: _textureId!),
      );
  }

  Widget _buildFaceDetectionOverlay() {
    if (_detectedFaces.isEmpty) {
      return Container();
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
          IconButton(
            icon: Icon(_getFlashIcon(widget.controller.flashMode),
                color: Colors.white, size: 28),
            onPressed: _cycleFlashMode,
          ),
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
          IconButton(
            icon: const Icon(Icons.flip_camera_ios,
                color: Colors.white, size: 28),
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
    final currentMode = widget.controller.flashMode;
    final nextMode =
        FlashMode.values[(currentMode.index + 1) % FlashMode.values.length];
    widget.controller.setFlashMode(nextMode);
  }

  void _capturePhoto() async {
    if (!widget.controller.isInitialized || widget.controller.isRecording) {
      return;
    }
    try {
      await widget.controller.takePhoto();
    } catch (e) {
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
      Logger.log('Failed to start recording: $e');
    }
  }

  void _stopVideoRecording() async {
    if (!widget.controller.isRecording) return;
    try {
      await widget.controller.stopVideoRecording();
    } catch (e) {
      Logger.log('Failed to stop recording: $e');
    }
  }
}

class FaceDetectionPainter extends CustomPainter {
  final List<FaceData> faces;
  final Size previewSize;

  FaceDetectionPainter({required this.faces, required this.previewSize});

  @override
  void paint(Canvas canvas, Size size) {
    if (faces.isEmpty || previewSize.isEmpty) return;

    final Paint paint = Paint()
      ..color = Colors.green
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3.0;

    final double scaleX = size.width / previewSize.width;
    final double scaleY = size.height / previewSize.height;

    for (final face in faces) {
      final double left = face.x * scaleX;
      final double top = face.y * scaleY;
      final double faceSize = face.size * math.min(scaleX, scaleY);
      canvas.drawRect(Rect.fromLTWH(left, top, faceSize, faceSize), paint);
    }
  }

  @override
  bool shouldRepaint(FaceDetectionPainter oldDelegate) {
    return oldDelegate.faces != faces || oldDelegate.previewSize != previewSize;
  }
}
