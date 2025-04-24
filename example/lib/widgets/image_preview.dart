import 'dart:io';
import 'package:flutter/material.dart';

class ImagePreview extends StatelessWidget {
  final String imagePath;
  final VoidCallback onDiscard;
  final VoidCallback onSave;

  const ImagePreview({
    required this.imagePath,
    required this.onDiscard,
    required this.onSave,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final file = File(imagePath);

    // Kiểm tra xem tệp có tồn tại không
    final fileExists = file.existsSync();

    return Stack(
      fit: StackFit.expand,
      children: [
        if (fileExists)
          Image.file(
            file,
            fit: BoxFit.contain,
            errorBuilder: (context, error, stackTrace) {
              debugPrint('Error loading image: $error');
              return Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.error, color: Colors.red, size: 48),
                    const SizedBox(height: 16),
                    Text('Error loading image: ${error.toString()}'),
                    const SizedBox(height: 8),
                    Text('Path: $imagePath'),
                  ],
                ),
              );
            },
          )
        else
          Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.error, color: Colors.red, size: 48),
                const SizedBox(height: 16),
                const Text('Image file not found'),
                const SizedBox(height: 8),
                Text('Path: $imagePath'),
              ],
            ),
          ),
        SafeArea(
          child: Align(
            alignment: Alignment.bottomCenter,
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.red,
                    ),
                    onPressed: onDiscard,
                    child: const Text('Discard'),
                  ),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.green,
                    ),
                    onPressed: onSave,
                    child: const Text('Save'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}
