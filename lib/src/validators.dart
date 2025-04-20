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
  if (config.filterType == null || config.filterType!.isEmpty) {
    throw ArgumentError('Filter type cannot be null or empty');
  }
}
