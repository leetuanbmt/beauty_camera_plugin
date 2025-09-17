import 'package:flutter/material.dart';

class CameraControls extends StatelessWidget {
  final VoidCallback onTakePhoto;
  final VoidCallback onSwitchCamera;
  final VoidCallback onShowEffects;
  final VoidCallback? onOpenGallery;
  final VoidCallback? onToggleFilter;
  final VoidCallback? onShowBeautyFilter;
  final VoidCallback? onShowMainFilter;
  final bool isFilterEnabled;

  const CameraControls({
    required this.onTakePhoto,
    required this.onSwitchCamera,
    required this.onShowEffects,
    this.onOpenGallery,
    this.onToggleFilter,
    this.onShowBeautyFilter,
    this.onShowMainFilter,
    this.isFilterEnabled = false,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        children: [
          _buildTopControls(),
          const Spacer(),
          _buildBottomControls(),
        ],
      ),
    );
  }

  Widget _buildTopControls() {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          IconButton(
            icon: const Icon(Icons.arrow_back),
            color: Colors.white,
            onPressed: () =>
                Navigator.of(NavigationHelper.navigatorKey.currentContext!)
                    .pop(),
          ),
          Row(
            children: [
              IconButton(
                icon: const Icon(Icons.flash_off),
                color: Colors.white,
                onPressed: () => {},
              ),
              if (onToggleFilter != null)
                IconButton(
                  icon: Icon(
                    isFilterEnabled ? Icons.filter_alt : Icons.filter_alt_off,
                    color: isFilterEnabled ? Colors.blue : Colors.white,
                  ),
                  onPressed: onToggleFilter,
                ),
              if (onShowBeautyFilter != null)
                IconButton(
                  icon: const Icon(Icons.face_retouching_natural),
                  color: Colors.white,
                  onPressed: onShowBeautyFilter,
                ),
              IconButton(
                icon: const Icon(Icons.switch_camera),
                color: Colors.white,
                onPressed: onSwitchCamera,
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildBottomControls() {
    return Padding(
      padding: const EdgeInsets.all(24.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          // Main filter button (TikTok style)
          if (onShowMainFilter != null)
            IconButton(
              icon: const Icon(Icons.auto_fix_high),
              color: Colors.white,
              onPressed: onShowMainFilter,
            ),
          GestureDetector(
            onTap: onTakePhoto,
            child: Container(
              height: 80,
              width: 80,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(color: Colors.white, width: 3),
              ),
              child: Container(
                margin: const EdgeInsets.all(3),
                decoration: const BoxDecoration(
                  color: Colors.white,
                  shape: BoxShape.circle,
                ),
              ),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.photo_library),
            color: Colors.white,
            onPressed: onOpenGallery,
          ),
        ],
      ),
    );
  }
}

// Helper để truy cập navigator từ mọi nơi
class NavigationHelper {
  static final GlobalKey<NavigatorState> navigatorKey =
      GlobalKey<NavigatorState>();
}
