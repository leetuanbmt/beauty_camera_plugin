import 'package:beauty_camera_plugin/src/camera_api.g.dart';

/// Validates camera settings and throws appropriate errors if invalid
void validateCameraSettings(CameraSettings settings) {
  if (settings.width != null && settings.width! <= 0) {
    throw ArgumentError('Width must be greater than 0');
  }
  if (settings.height != null && settings.height! <= 0) {
    throw ArgumentError('Height must be greater than 0');
  }
}

/// Validates filter configuration and throws appropriate errors if invalid
void validateFilterConfig(FilterConfig config) {
  // Filter type can be null to indicate no specific filter
  // but using individual parameters

  // Validate brightness range if provided
  if (config.brightness != null &&
      (config.brightness! < -1.0 || config.brightness! > 1.0)) {
    throw ArgumentError('Brightness must be between -1.0 and 1.0');
  }

  // Validate smoothness range if provided
  if (config.smoothness != null &&
      (config.smoothness! < 0.0 || config.smoothness! > 1.0)) {
    throw ArgumentError('Smoothness must be between 0.0 and 1.0');
  }

  // Validate contrast range if provided
  if (config.contrast != null &&
      (config.contrast! < 0.0 || config.contrast! > 2.0)) {
    throw ArgumentError('Contrast must be between 0.0 and 2.0');
  }

  // Validate saturation range if provided
  if (config.saturation != null &&
      (config.saturation! < 0.0 || config.saturation! > 2.0)) {
    throw ArgumentError('Saturation must be between 0.0 and 2.0');
  }

  // Validate sharpness range if provided
  if (config.sharpness != null &&
      (config.sharpness! < 0.0 || config.sharpness! > 1.0)) {
    throw ArgumentError('Sharpness must be between 0.0 and 1.0');
  }
}
