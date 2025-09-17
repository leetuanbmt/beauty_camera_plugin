import 'camera_api.g.dart';

/// Predefined beauty filter presets for easy use
class BeautyFilterPresets {
  BeautyFilterPresets._();

  /// No beauty filter applied
  static final BeautyFilterParameters none = BeautyFilterParameters(
    type: BeautyFilterType.none,
    smoothingStrength: 0.0,
    brighteningStrength: 0.0,
    intensity: 0.0,
    faceOnly: true,
  );

  /// Light skin smoothing only
  static final BeautyFilterParameters lightSmoothing = BeautyFilterParameters(
    type: BeautyFilterType.skinSmoothing,
    smoothingStrength: 0.2,
    brighteningStrength: 0.0,
    intensity: 0.5,
    faceOnly: true,
  );

  /// Medium skin smoothing
  static final BeautyFilterParameters mediumSmoothing = BeautyFilterParameters(
    type: BeautyFilterType.skinSmoothing,
    smoothingStrength: 0.4,
    brighteningStrength: 0.0,
    intensity: 0.7,
    faceOnly: true,
  );

  /// Strong skin smoothing
  static final BeautyFilterParameters strongSmoothing = BeautyFilterParameters(
    type: BeautyFilterType.skinSmoothing,
    smoothingStrength: 0.7,
    brighteningStrength: 0.0,
    intensity: 1.0,
    faceOnly: true,
  );

  /// Light skin brightening only
  static final BeautyFilterParameters lightBrightening = BeautyFilterParameters(
    type: BeautyFilterType.skinBrightening,
    smoothingStrength: 0.0,
    brighteningStrength: 0.2,
    intensity: 0.5,
    faceOnly: true,
  );

  /// Medium skin brightening
  static final BeautyFilterParameters mediumBrightening =
      BeautyFilterParameters(
    type: BeautyFilterType.skinBrightening,
    smoothingStrength: 0.0,
    brighteningStrength: 0.4,
    intensity: 0.7,
    faceOnly: true,
  );

  /// Strong skin brightening
  static final BeautyFilterParameters strongBrightening =
      BeautyFilterParameters(
    type: BeautyFilterType.skinBrightening,
    smoothingStrength: 0.0,
    brighteningStrength: 0.6,
    intensity: 1.0,
    faceOnly: true,
  );

  /// Natural beauty - light smoothing + light brightening
  static final BeautyFilterParameters natural = BeautyFilterParameters(
    type: BeautyFilterType.skinBeauty,
    smoothingStrength: 0.2,
    brighteningStrength: 0.15,
    intensity: 0.6,
    faceOnly: true,
  );

  /// Balanced beauty - medium smoothing + medium brightening
  static final BeautyFilterParameters balanced = BeautyFilterParameters(
    type: BeautyFilterType.skinBeauty,
    smoothingStrength: 0.35,
    brighteningStrength: 0.25,
    intensity: 0.8,
    faceOnly: true,
  );

  /// Enhanced beauty - strong smoothing + strong brightening
  static final BeautyFilterParameters enhanced = BeautyFilterParameters(
    type: BeautyFilterType.skinBeauty,
    smoothingStrength: 0.5,
    brighteningStrength: 0.35,
    intensity: 1.0,
    faceOnly: true,
  );

  /// Advanced smoothing with minimal brightening
  static final BeautyFilterParameters advancedSmooth = BeautyFilterParameters(
    type: BeautyFilterType.advancedSmoothing,
    smoothingStrength: 0.6,
    brighteningStrength: 0.1,
    intensity: 0.9,
    faceOnly: true,
  );

  /// Get all available presets
  static List<BeautyFilterParameters> get allPresets => [
        none,
        lightSmoothing,
        mediumSmoothing,
        strongSmoothing,
        lightBrightening,
        mediumBrightening,
        strongBrightening,
        natural,
        balanced,
        enhanced,
        advancedSmooth,
      ];

  /// Get preset names for UI display
  static List<String> get presetNames => [
        'None',
        'Light Smoothing',
        'Medium Smoothing',
        'Strong Smoothing',
        'Light Brightening',
        'Medium Brightening',
        'Strong Brightening',
        'Natural',
        'Balanced',
        'Enhanced',
        'Advanced Smooth',
      ];

  /// Get preset by name
  static BeautyFilterParameters? getPresetByName(String name) {
    final index = presetNames.indexOf(name);
    if (index >= 0 && index < allPresets.length) {
      return allPresets[index];
    }
    return null;
  }

  /// Create custom beauty filter parameters
  static BeautyFilterParameters custom({
    BeautyFilterType type = BeautyFilterType.skinBeauty,
    double smoothingStrength = 0.3,
    double brighteningStrength = 0.2,
    double intensity = 1.0,
    bool faceOnly = true,
  }) {
    return BeautyFilterParameters(
      type: type,
      smoothingStrength: smoothingStrength.clamp(0.0, 1.0),
      brighteningStrength: brighteningStrength.clamp(0.0, 1.0),
      intensity: intensity.clamp(0.0, 1.0),
      faceOnly: faceOnly,
    );
  }
}
