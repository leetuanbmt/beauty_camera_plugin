import 'camera_api.g.dart';

/// Predefined beauty filter presets for easy use
class BeautyFilterPresets {
  BeautyFilterPresets._();

  /// Helper function to create FilterParameters with default values
  static FilterParameters _create({
    double intensity = 0.0,
    double skinSmoothing = 0.0,
    double skinBrightening = 0.0,
    double faceSlimming = 0.0,
    double eyeEnlargement = 0.0,
    double lipEnhancement = 0.0,
    double brightness = 0.0,
    double contrast = 0.0,
    double saturation = 0.0,
    double warmth = 0.0,
    double tint = 0.0,
    double vibrance = 0.0,
    double blur = 0.0,
    double sharpen = 0.0,
    double vignette = 0.0,
    double grain = 0.0,
    double fade = 0.0,
    double highlights = 0.0,
    double shadows = 0.0,
    double clarity = 0.0,
    double structure = 0.0,
  }) {
    return FilterParameters(
      intensity: intensity,
      skinSmoothing: skinSmoothing,
      skinBrightening: skinBrightening,
      faceSlimming: faceSlimming,
      eyeEnlargement: eyeEnlargement,
      lipEnhancement: lipEnhancement,
      brightness: brightness,
      contrast: contrast,
      saturation: saturation,
      warmth: warmth,
      tint: tint,
      vibrance: vibrance,
      blur: blur,
      sharpen: sharpen,
      vignette: vignette,
      grain: grain,
      fade: fade,
      highlights: highlights,
      shadows: shadows,
      clarity: clarity,
      structure: structure,
    );
  }

  /// No filter applied
  static final FilterParameters none = _create();

  /// Light skin smoothing only
  static final FilterParameters lightSmoothing = _create(
    intensity: 0.3,
    skinSmoothing: 0.2,
  );

  /// Medium skin smoothing
  static final FilterParameters mediumSmoothing = _create(
    intensity: 0.5,
    skinSmoothing: 0.4,
  );

  /// Strong skin smoothing
  static final FilterParameters strongSmoothing = _create(
    intensity: 0.7,
    skinSmoothing: 0.6,
  );

  /// Light skin brightening
  static final FilterParameters lightBrightening = _create(
    intensity: 0.3,
    skinBrightening: 0.2,
    brightness: 0.1,
  );

  /// Medium skin brightening
  static final FilterParameters mediumBrightening = _create(
    intensity: 0.5,
    skinBrightening: 0.4,
    brightness: 0.2,
    contrast: 0.1,
  );

  /// Strong skin brightening
  static final FilterParameters strongBrightening = _create(
    intensity: 0.7,
    skinBrightening: 0.6,
    brightness: 0.3,
    contrast: 0.2,
    saturation: 0.1,
  );

  /// Natural beauty filter (balanced smoothing + brightening)
  static final FilterParameters natural = _create(
    intensity: 0.4,
    skinSmoothing: 0.3,
    skinBrightening: 0.2,
    brightness: 0.1,
    contrast: 0.1,
  );

  /// Balanced beauty filter
  static final FilterParameters balanced = _create(
    intensity: 0.5,
    skinSmoothing: 0.4,
    skinBrightening: 0.3,
    brightness: 0.2,
    contrast: 0.1,
    saturation: 0.1,
  );

  /// Enhanced beauty filter (stronger effects)
  static final FilterParameters enhanced = _create(
    intensity: 0.6,
    skinSmoothing: 0.5,
    skinBrightening: 0.4,
    brightness: 0.3,
    contrast: 0.2,
    saturation: 0.2,
  );

  /// Advanced smooth filter (maximum smoothing)
  static final FilterParameters advancedSmooth = _create(
    intensity: 0.8,
    skinSmoothing: 0.7,
    skinBrightening: 0.3,
    brightness: 0.2,
    contrast: 0.1,
  );

  /// Get all available presets
  static List<FilterParameters> get allPresets => [
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

  /// Get preset names
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
  static FilterParameters? getPresetByName(String name) {
    final index = presetNames.indexOf(name);
    if (index >= 0 && index < allPresets.length) {
      return allPresets[index];
    }
    return null;
  }

  /// Create custom beauty filter parameters
  static FilterParameters custom({
    double intensity = 0.5,
    double skinSmoothing = 0.3,
    double skinBrightening = 0.2,
    double brightness = 0.1,
    double contrast = 0.1,
    double saturation = 0.1,
  }) {
    return _create(
      intensity: intensity,
      skinSmoothing: skinSmoothing,
      skinBrightening: skinBrightening,
      brightness: brightness,
      contrast: contrast,
      saturation: saturation,
    );
  }
}
