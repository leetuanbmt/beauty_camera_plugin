import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'package:flutter/material.dart';

class EffectsGrid extends StatelessWidget {
  final CameraFilterMode currentEffect;
  final Function(CameraFilterMode) onEffectSelected;

  const EffectsGrid({
    required this.currentEffect,
    required this.onEffectSelected,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.7,
      padding: const EdgeInsets.symmetric(vertical: 20.0),
      child: Column(
        children: [
          const Text(
            'Select Effect',
            style: TextStyle(
              color: Colors.white,
              fontSize: 18,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 20),
          Expanded(
            child: GridView.count(
              crossAxisCount: 3,
              padding: const EdgeInsets.symmetric(horizontal: 20),
              mainAxisSpacing: 20,
              crossAxisSpacing: 20,
              childAspectRatio: 0.8,
              children: _buildEffectItems(),
            ),
          ),
        ],
      ),
    );
  }

  List<Widget> _buildEffectItems() {
    return [
      _buildEffectItem(CameraFilterMode.aqua, 'Normal'),
      _buildEffectItem(CameraFilterMode.mono, 'Mono'),
      _buildEffectItem(CameraFilterMode.negative, 'Negative'),
      _buildEffectItem(CameraFilterMode.sepia, 'Sepia'),
      _buildEffectItem(CameraFilterMode.solarize, 'Solarize'),
      _buildEffectItem(CameraFilterMode.posterize, 'Posterize'),
      _buildEffectItem(CameraFilterMode.whiteboard, 'Whiteboard'),
      _buildEffectItem(CameraFilterMode.blackboard, 'Blackboard'),
      _buildEffectItem(CameraFilterMode.aqua, 'Aqua'),
      _buildEffectItem(CameraFilterMode.emboss, 'Emboss'),
      _buildEffectItem(CameraFilterMode.sketch, 'Sketch'),
      _buildEffectItem(CameraFilterMode.neon, 'Neon'),
      _buildEffectItem(CameraFilterMode.vintage, 'Vintage'),
      _buildEffectItem(CameraFilterMode.brightness, 'Brightness'),
      _buildEffectItem(CameraFilterMode.contrast, 'Contrast'),
      _buildEffectItem(CameraFilterMode.saturation, 'Saturation'),
      _buildEffectItem(CameraFilterMode.sharpen, 'Sharpen'),
      _buildEffectItem(CameraFilterMode.gaussianBlur, 'Blur'),
      _buildEffectItem(CameraFilterMode.vignette, 'Vignette'),
      _buildEffectItem(CameraFilterMode.hue, 'Hue'),
      _buildEffectItem(CameraFilterMode.exposure, 'Exposure'),
      _buildEffectItem(CameraFilterMode.highlightShadow, 'Highlight'),
      _buildEffectItem(CameraFilterMode.levels, 'Levels'),
      _buildEffectItem(CameraFilterMode.colorBalance, 'Color Balance'),
      _buildEffectItem(CameraFilterMode.lookup, 'Lookup'),
    ];
  }

  Widget _buildEffectItem(CameraFilterMode effect, String name) {
    final bool isSelected = currentEffect == effect;

    return GestureDetector(
      onTap: () => onEffectSelected(effect),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            height: 70,
            width: 70,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(
                color: isSelected ? Colors.blue : Colors.transparent,
                width: 3,
              ),
              color: Colors.grey[800],
            ),
            child: Center(
              child: Text(
                name.substring(0, 1),
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 24,
                ),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            name,
            style: TextStyle(
              color: isSelected ? Colors.blue : Colors.white,
              fontSize: 12,
            ),
          ),
        ],
      ),
    );
  }
}
