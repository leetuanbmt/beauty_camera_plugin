import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'dart:developer' as developer;
import 'package:flutter/material.dart';
import 'dart:async';

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
      theme: ThemeData(
        primarySwatch: Colors.blue,
        useMaterial3: true,
      ),
      home: const CameraScreen(),
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
      await _controller.initialize(
        width: 1920,
        height: 1080,
        defaultFilter: 'none',
      );
    } catch (e) {
      setState(() => _errorMessage = e.toString());
    }
  }

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  Future<void> _takePicture() async {
    try {
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final path = '/storage/emulated/0/Pictures/beauty_camera_$timestamp.jpg';
      await _controller.takePicture(path);
      _showSnackBar('Picture saved to: $path');
    } catch (e) {
      _showSnackBar('Failed to take picture: $e');
    }
  }

  Future<void> _toggleRecording() async {
    try {
      if (_controller.isRecording) {
        await _controller.stopRecording();
      } else {
        final timestamp = DateTime.now().millisecondsSinceEpoch;
        final path = '/storage/emulated/0/Movies/beauty_camera_$timestamp.mp4';
        await _controller.startRecording(path);
      }
    } catch (e) {
      _showSnackBar('Recording error: $e');
    }
  }

  Future<void> _applyFilter(String filterType) async {
    try {
      await _controller.applyFilter(filterType, {
        'intensity': 0.5,
        'smoothness': 0.7,
      });
    } catch (e) {
      _showSnackBar('Failed to apply filter: $e');
    }
  }

  @override
  void dispose() {
    _errorSubscription?.cancel();
    _stateSubscription?.cancel();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    Logger.log(_errorMessage ?? '');
    return Scaffold(
      appBar: AppBar(
        title: const Text('Beauty Camera'),
        actions: [
          IconButton(
            icon: Icon(_controller.isRecording
                ? Icons.stop
                : Icons.fiber_manual_record),
            onPressed: _controller.isInitialized ? _toggleRecording : null,
          ),
          IconButton(
            icon: const Icon(Icons.camera_alt),
            onPressed: _controller.isInitialized ? _takePicture : null,
          ),
        ],
      ),
      body: Column(
        children: [
          if (_errorMessage != null)
            Container(
              color: Colors.red[100],
              padding: const EdgeInsets.all(8),
              child: Text(
                _errorMessage!,
                style: const TextStyle(color: Colors.red),
              ),
            ),
          Expanded(
            child: _controller.textureId != null
                ? Texture(textureId: _controller.textureId!)
                : const Center(child: CircularProgressIndicator()),
          ),
          Container(
            padding: const EdgeInsets.all(8),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _FilterButton(
                  filterType: 'none',
                  currentFilter: _controller.currentFilter,
                  onPressed: _controller.isInitialized ? _applyFilter : null,
                ),
                _FilterButton(
                  filterType: 'beauty',
                  currentFilter: _controller.currentFilter,
                  onPressed: _controller.isInitialized ? _applyFilter : null,
                ),
                _FilterButton(
                  filterType: 'vintage',
                  currentFilter: _controller.currentFilter,
                  onPressed: _controller.isInitialized ? _applyFilter : null,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _FilterButton extends StatelessWidget {
  final String filterType;
  final String currentFilter;
  final Function(String)? onPressed;

  const _FilterButton({
    required this.filterType,
    required this.currentFilter,
    this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    final isSelected = currentFilter == filterType;
    return ElevatedButton(
      onPressed: onPressed != null ? () => onPressed!(filterType) : null,
      style: ElevatedButton.styleFrom(
        backgroundColor: isSelected ? Colors.blue : null,
        foregroundColor: isSelected ? Colors.white : null,
      ),
      child: Text(filterType.toUpperCase()),
    );
  }
}
