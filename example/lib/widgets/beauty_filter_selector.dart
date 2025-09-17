import 'package:flutter/material.dart';
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';

class BeautyFilterSelector extends StatefulWidget {
  final BeautyFilterParameters currentFilter;
  final Function(BeautyFilterParameters) onFilterChanged;

  const BeautyFilterSelector({
    super.key,
    required this.currentFilter,
    required this.onFilterChanged,
  });

  @override
  State<BeautyFilterSelector> createState() => _BeautyFilterSelectorState();
}

class _BeautyFilterSelectorState extends State<BeautyFilterSelector> {
  int selectedPresetIndex = 0;
  bool showCustomControls = false;
  double customSmoothing = 0.3;
  double customBrightening = 0.2;
  double customIntensity = 1.0;

  @override
  void initState() {
    super.initState();
    _findCurrentPresetIndex();
  }

  void _findCurrentPresetIndex() {
    final presets = BeautyFilterPresets.allPresets;
    for (int i = 0; i < presets.length; i++) {
      final preset = presets[i];
      if (preset.type == widget.currentFilter.type &&
          preset.smoothingStrength == widget.currentFilter.smoothingStrength &&
          preset.brighteningStrength ==
              widget.currentFilter.brighteningStrength) {
        selectedPresetIndex = i;
        return;
      }
    }
    // If no preset matches, show custom controls
    showCustomControls = true;
    customSmoothing = widget.currentFilter.smoothingStrength;
    customBrightening = widget.currentFilter.brighteningStrength;
    customIntensity = widget.currentFilter.intensity;
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 300,
      decoration: const BoxDecoration(
        color: Colors.black87,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        children: [
          // Handle bar
          Container(
            margin: const EdgeInsets.symmetric(vertical: 8),
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: Colors.white54,
              borderRadius: BorderRadius.circular(2),
            ),
          ),

          // Title
          const Text(
            'Beauty Filters',
            style: TextStyle(
              color: Colors.white,
              fontSize: 18,
              fontWeight: FontWeight.bold,
            ),
          ),

          const SizedBox(height: 16),

          // Toggle between presets and custom
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              _buildToggleButton('Presets', !showCustomControls, () {
                setState(() => showCustomControls = false);
              }),
              const SizedBox(width: 8),
              _buildToggleButton('Custom', showCustomControls, () {
                setState(() => showCustomControls = true);
              }),
            ],
          ),

          const SizedBox(height: 16),

          Expanded(
            child: showCustomControls
                ? _buildCustomControls()
                : _buildPresetSelector(),
          ),
        ],
      ),
    );
  }

  Widget _buildToggleButton(String text, bool isSelected, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: isSelected ? Colors.blue : Colors.transparent,
          border: Border.all(color: Colors.blue),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          text,
          style: TextStyle(
            color: isSelected ? Colors.white : Colors.blue,
            fontWeight: FontWeight.w500,
          ),
        ),
      ),
    );
  }

  Widget _buildPresetSelector() {
    return Column(
      children: [
        // Preset grid
        Expanded(
          child: GridView.builder(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 3,
              childAspectRatio: 2.5,
              crossAxisSpacing: 8,
              mainAxisSpacing: 8,
            ),
            itemCount: BeautyFilterPresets.allPresets.length,
            itemBuilder: (context, index) {
              final preset = BeautyFilterPresets.allPresets[index];
              final name = BeautyFilterPresets.presetNames[index];
              final isSelected = selectedPresetIndex == index;

              return GestureDetector(
                onTap: () {
                  setState(() => selectedPresetIndex = index);
                  widget.onFilterChanged(preset);
                },
                child: Container(
                  decoration: BoxDecoration(
                    color: isSelected ? Colors.blue : Colors.white24,
                    borderRadius: BorderRadius.circular(8),
                    border: isSelected
                        ? Border.all(color: Colors.blue, width: 2)
                        : null,
                  ),
                  child: Center(
                    child: Text(
                      name,
                      style: TextStyle(
                        color: isSelected ? Colors.white : Colors.white70,
                        fontSize: 12,
                        fontWeight:
                            isSelected ? FontWeight.bold : FontWeight.normal,
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildCustomControls() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        children: [
          _buildSlider(
            'Skin Smoothing',
            customSmoothing,
            (value) {
              setState(() => customSmoothing = value);
              _applyCustomFilter();
            },
          ),

          _buildSlider(
            'Skin Brightening',
            customBrightening,
            (value) {
              setState(() => customBrightening = value);
              _applyCustomFilter();
            },
          ),

          _buildSlider(
            'Intensity',
            customIntensity,
            (value) {
              setState(() => customIntensity = value);
              _applyCustomFilter();
            },
          ),

          const SizedBox(height: 16),

          // Reset button
          ElevatedButton(
            onPressed: () {
              setState(() {
                customSmoothing = 0.3;
                customBrightening = 0.2;
                customIntensity = 1.0;
              });
              _applyCustomFilter();
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.white24,
              foregroundColor: Colors.white,
            ),
            child: const Text('Reset to Default'),
          ),
        ],
      ),
    );
  }

  Widget _buildSlider(String label, double value, Function(double) onChanged) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '$label: ${(value * 100).round()}%',
          style: const TextStyle(color: Colors.white, fontSize: 14),
        ),
        Slider(
          value: value,
          onChanged: onChanged,
          activeColor: Colors.blue,
          inactiveColor: Colors.white24,
        ),
      ],
    );
  }

  void _applyCustomFilter() {
    final customFilter = BeautyFilterPresets.custom(
      type: BeautyFilterType.skinBeauty,
      smoothingStrength: customSmoothing,
      brighteningStrength: customBrightening,
      intensity: customIntensity,
    );
    widget.onFilterChanged(customFilter);
  }
}
