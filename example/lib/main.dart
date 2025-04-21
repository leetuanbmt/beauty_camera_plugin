import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'dart:developer' as developer;
import 'package:flutter/material.dart';
import 'dart:async';
import 'dart:io';
import 'package:video_player/video_player.dart';
import 'package:share_plus/share_plus.dart';
import 'package:path_provider/path_provider.dart';

class Logger {
  static void log(String message) {
    developer.log(message);
  }
}

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Beauty Camera Demo',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primarySwatch: Colors.blue,
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Beauty Camera Demo'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text(
              'Welcome to Beauty Camera',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: () {
                Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (context) => const CameraScreen(),
                  ),
                );
              },
              icon: const Icon(Icons.camera_alt),
              label: const Text('Open Camera'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  horizontal: 32,
                  vertical: 16,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class CameraScreen extends StatefulWidget {
  const CameraScreen({super.key});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  late final BeautyCameraController _controller;
  String? _errorMessage;
  StreamSubscription? _errorSubscription;
  StreamSubscription? _stateSubscription;
  String? _currentVideoPath;
  bool _isResuming = false;

  // Define available filters
  final List<FilterConfig> filters = [
    FilterConfig(
      type: 'none',
      name: 'Normal',
      icon: Icons.lens_outlined,
    ),
    FilterConfig(
      type: 'beauty',
      name: 'Beauty',
      icon: Icons.face,
      params: {'smoothness': 0.7, 'brightness': 0.3},
    ),
    FilterConfig(
      type: 'vintage',
      name: 'Vintage',
      icon: Icons.camera_roll,
      params: {'intensity': 0.6},
    ),
    FilterConfig(
      type: 'mono',
      name: 'Mono',
      icon: Icons.monochrome_photos,
      params: {'intensity': 0.5},
    ),
    FilterConfig(
      type: 'sepia',
      name: 'Sepia',
      icon: Icons.filter_vintage,
      params: {'intensity': 0.5},
    ),
    FilterConfig(
      type: 'custom',
      name: 'Custom',
      icon: Icons.tune,
      params: {
        'edge_mode': 1,
        'noise_reduction': 2,
        'saturation': 1.2,
      },
    ),
  ];

  @override
  void initState() {
    super.initState();
    _controller = BeautyCameraController();
    _setupSubscriptions();
    _initializeCamera();
  }

  void _setupSubscriptions() {
    _errorSubscription = _controller.errorStream.listen((error) {
      setState(() => _errorMessage = '${error.type}: ${error.message}');
    });

    _stateSubscription = _controller.stateStream.listen((state) {
      setState(() {}); // Rebuild UI when state changes
    });
  }

  Future<void> _initializeCamera() async {
    try {
      if (_controller.isInitialized) {
        await _controller.dispose();
      }

      // Wait for any previous camera resources to be released
      await Future.delayed(const Duration(milliseconds: 1000));

      await _controller.initialize(
        width: 1920,
        height: 1080,
        defaultFilter: 'none',
      );

      // Preview should start automatically after initialization
      if (_controller.textureId == null) {
        throw Exception('Failed to get texture ID after initialization');
      }
    } catch (e) {
      if (mounted) {
        setState(() => _errorMessage = e.toString());
      }
      // Try to restart camera on error
      await _restartCamera();
    }
  }

  Future<void> _restartCamera() async {
    if (_isResuming) return;

    try {
      _isResuming = true;

      // Ensure proper cleanup
      await _controller.dispose();

      // Wait longer for resources to be released
      await Future.delayed(const Duration(milliseconds: 1500));

      _controller = BeautyCameraController();
      await _initializeCamera();
    } catch (e) {
      if (mounted) {
        setState(() => _errorMessage = 'Failed to restart camera: $e');
      }
    } finally {
      if (mounted) {
        setState(() => _isResuming = false);
      }
    }
  }

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  Future<void> _takePicture() async {
    if (_isResuming) return;

    try {
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final directory = await getExternalStorageDirectory();
      final path = '${directory?.path}/beauty_camera_$timestamp.jpg';

      await _controller.takePicture(path);
      if (mounted) {
        final deleted = await Navigator.of(context).push<bool>(
          MaterialPageRoute(
            builder: (context) => MediaPreviewScreen(
              mediaPath: path,
              isVideo: false,
            ),
          ),
        );

        // Restart camera when returning from preview
        await _restartCamera();

        if (deleted == true) {
          _showSnackBar('Photo deleted');
        } else {
          _showSnackBar('Photo saved to: $path');
        }
      }
    } catch (e) {
      _showSnackBar('Failed to take picture: $e');
      // Try to restart camera on error
      await _restartCamera();
    }
  }

  Future<void> _toggleRecording() async {
    try {
      if (_controller.isRecording) {
        await _controller.stopRecording();
        if (_currentVideoPath != null && mounted) {
          final deleted = await Navigator.of(context).push<bool>(
            MaterialPageRoute(
              builder: (context) => MediaPreviewScreen(
                mediaPath: _currentVideoPath!,
                isVideo: true,
              ),
            ),
          );

          if (deleted == true) {
            _showSnackBar('Video deleted');
          } else {
            _showSnackBar('Video saved to: $_currentVideoPath');
          }
        }
      } else {
        final timestamp = DateTime.now().millisecondsSinceEpoch;
        final directory = await getExternalStorageDirectory();
        _currentVideoPath = '${directory?.path}/beauty_camera_$timestamp.mp4';
        await _controller.startRecording(_currentVideoPath!);
        _showSnackBar('Recording started');
      }
    } catch (e) {
      _showSnackBar('Recording error: $e');
    }
  }

  Future<void> _applyFilter(FilterConfig filter) async {
    try {
      await _controller.applyFilter(filter.type, filter.params ?? {});
      setState(() {});
    } catch (e) {
      _showSnackBar('Failed to apply filter: $e');
    }
  }

  @override
  void dispose() {
    _errorSubscription?.cancel();
    _stateSubscription?.cancel();

    // Ensure camera is properly disposed
    if (_controller.isInitialized) {
      _controller.dispose().then((_) {
        Logger.log('Camera disposed successfully');
      }).catchError((error) {
        Logger.log('Error disposing camera: $error');
      });
    }

    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Stack(
          children: [
            // Camera Preview
            if (_controller.textureId != null)
              Transform.scale(
                scale: 1.0,
                child: AspectRatio(
                  aspectRatio: 9 / 16, // Portrait mode aspect ratio
                  child: Texture(textureId: _controller.textureId!),
                ),
              )
            else
              const Center(child: CircularProgressIndicator()),

            // Error Message
            if (_errorMessage != null)
              Positioned(
                top: 0,
                left: 0,
                right: 0,
                child: Container(
                  color: Colors.red[100],
                  padding: const EdgeInsets.all(8),
                  child: Text(
                    _errorMessage!,
                    style: const TextStyle(color: Colors.red),
                  ),
                ),
              ),

            // Top Controls
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              child: Container(
                padding: const EdgeInsets.all(8),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    IconButton(
                      icon: const Icon(Icons.arrow_back),
                      color: Colors.white,
                      onPressed: () => Navigator.of(context).pop(),
                    ),
                    Row(
                      children: [
                        IconButton(
                          icon: const Icon(Icons.flip_camera_ios),
                          color: Colors.white,
                          onPressed: _controller.isInitialized
                              ? () {
                                  // TODO: Implement camera switch
                                }
                              : null,
                        ),
                        IconButton(
                          icon: const Icon(Icons.flash_off),
                          color: Colors.white,
                          onPressed: _controller.isInitialized
                              ? () {
                                  // TODO: Implement flash toggle
                                }
                              : null,
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),

            // Bottom Controls
            Positioned(
              bottom: 0,
              left: 0,
              right: 0,
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.bottomCenter,
                    end: Alignment.topCenter,
                    colors: [
                      Colors.black.withValues(alpha: 0.8),
                      Colors.transparent,
                    ],
                  ),
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // Filter List
                    SizedBox(
                      height: 100,
                      child: ListView.builder(
                        scrollDirection: Axis.horizontal,
                        itemCount: filters.length,
                        itemBuilder: (context, index) {
                          final filter = filters[index];
                          final isSelected =
                              _controller.currentFilter == filter.type;
                          return Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 8),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                InkWell(
                                  onTap: _controller.isInitialized
                                      ? () => _applyFilter(filter)
                                      : null,
                                  child: Container(
                                    width: 60,
                                    height: 60,
                                    decoration: BoxDecoration(
                                      shape: BoxShape.circle,
                                      border: Border.all(
                                        color: isSelected
                                            ? Colors.blue
                                            : Colors.white,
                                        width: 2,
                                      ),
                                    ),
                                    child: Icon(
                                      filter.icon,
                                      color: isSelected
                                          ? Colors.blue
                                          : Colors.white,
                                      size: 30,
                                    ),
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  filter.name,
                                  style: TextStyle(
                                    color:
                                        isSelected ? Colors.blue : Colors.white,
                                    fontSize: 12,
                                  ),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
                    ),
                    const SizedBox(height: 20),
                    // Camera Controls
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        IconButton(
                          icon: const Icon(Icons.photo_library),
                          color: Colors.white,
                          iconSize: 32,
                          onPressed: () {
                            // TODO: Implement gallery access
                          },
                        ),
                        GestureDetector(
                          onTap:
                              _controller.isInitialized ? _takePicture : null,
                          onLongPress: _controller.isInitialized
                              ? _toggleRecording
                              : null,
                          onLongPressUp: _controller.isInitialized &&
                                  _controller.isRecording
                              ? _toggleRecording
                              : null,
                          child: Container(
                            width: 80,
                            height: 80,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: Colors.white,
                                width: 4,
                              ),
                              color: _controller.isRecording
                                  ? Colors.red
                                  : Colors.transparent,
                            ),
                          ),
                        ),
                        IconButton(
                          icon: const Icon(Icons.settings),
                          color: Colors.white,
                          iconSize: 32,
                          onPressed: () {
                            // TODO: Implement settings
                          },
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class FilterConfig {
  final String type;
  final String name;
  final IconData icon;
  final Map<String, dynamic>? params;

  const FilterConfig({
    required this.type,
    required this.name,
    required this.icon,
    this.params,
  });
}

class MediaPreviewScreen extends StatefulWidget {
  final String mediaPath;
  final bool isVideo;

  const MediaPreviewScreen({
    super.key,
    required this.mediaPath,
    required this.isVideo,
  });

  @override
  State<MediaPreviewScreen> createState() => _MediaPreviewScreenState();
}

class _MediaPreviewScreenState extends State<MediaPreviewScreen> {
  VideoPlayerController? _videoController;
  bool _isPlaying = false;

  @override
  void initState() {
    super.initState();
    if (widget.isVideo) {
      _initializeVideoPlayer();
    }
  }

  Future<void> _initializeVideoPlayer() async {
    _videoController = VideoPlayerController.file(File(widget.mediaPath));
    await _videoController?.initialize();
    setState(() {});
  }

  Future<void> _shareMedia() async {
    try {
      await Share.shareXFiles([XFile(widget.mediaPath)]);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to share: $e')),
        );
      }
    }
  }

  Future<void> _deleteMedia() async {
    try {
      final file = File(widget.mediaPath);
      await file.delete();
      if (mounted) {
        Navigator.of(context).pop(true); // Return true to indicate deletion
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to delete: $e')),
        );
      }
    }
  }

  @override
  void dispose() {
    _videoController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            // Media Preview
            Center(
              child:
                  widget.isVideo ? _buildVideoPreview() : _buildImagePreview(),
            ),

            // Top Controls
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              child: Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Colors.black.withValues(alpha: 0.7),
                      Colors.transparent,
                    ],
                  ),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    IconButton(
                      icon: const Icon(Icons.close),
                      color: Colors.white,
                      onPressed: () => Navigator.of(context).pop(),
                    ),
                    Row(
                      children: [
                        IconButton(
                          icon: const Icon(Icons.share),
                          color: Colors.white,
                          onPressed: _shareMedia,
                        ),
                        IconButton(
                          icon: const Icon(Icons.delete),
                          color: Colors.white,
                          onPressed: () => _showDeleteConfirmation(context),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),

            // Video Controls (if video)
            if (widget.isVideo && _videoController?.value.isInitialized == true)
              Positioned(
                bottom: 0,
                left: 0,
                right: 0,
                child: Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.bottomCenter,
                      end: Alignment.topCenter,
                      colors: [
                        Colors.black.withValues(alpha: 0.7),
                        Colors.transparent,
                      ],
                    ),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      IconButton(
                        icon: Icon(
                          _isPlaying ? Icons.pause : Icons.play_arrow,
                          size: 32,
                        ),
                        color: Colors.white,
                        onPressed: _toggleVideo,
                      ),
                      if (_videoController != null) ...[
                        const SizedBox(width: 16),
                        Expanded(
                          child: VideoProgressIndicator(
                            _videoController!,
                            allowScrubbing: true,
                            colors: const VideoProgressColors(
                              playedColor: Colors.blue,
                              bufferedColor: Colors.grey,
                              backgroundColor: Colors.white,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildVideoPreview() {
    if (_videoController?.value.isInitialized != true) {
      return const CircularProgressIndicator();
    }
    return AspectRatio(
      aspectRatio: _videoController!.value.aspectRatio,
      child: VideoPlayer(_videoController!),
    );
  }

  Widget _buildImagePreview() {
    return InteractiveViewer(
      minScale: 0.5,
      maxScale: 4.0,
      child: Image.file(
        File(widget.mediaPath),
        fit: BoxFit.contain,
      ),
    );
  }

  Future<void> _toggleVideo() async {
    if (_videoController == null) return;

    setState(() {
      _isPlaying = !_isPlaying;
    });

    if (_isPlaying) {
      await _videoController?.play();
      _videoController?.addListener(_onVideoProgress);
    } else {
      await _videoController?.pause();
      _videoController?.removeListener(_onVideoProgress);
    }
  }

  void _onVideoProgress() {
    if (_videoController?.value.position == _videoController?.value.duration) {
      setState(() {
        _isPlaying = false;
      });
      _videoController?.removeListener(_onVideoProgress);
      _videoController?.seekTo(Duration.zero);
    }
  }

  Future<void> _showDeleteConfirmation(BuildContext context) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Media'),
        content: Text(
            'Are you sure you want to delete this ${widget.isVideo ? 'video' : 'photo'}?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('CANCEL'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('DELETE'),
          ),
        ],
      ),
    );

    if (result == true) {
      await _deleteMedia();
    }
  }
}
