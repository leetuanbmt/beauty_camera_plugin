import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'camera_controller.dart';

/// State cho filter system
class FilterState {
  final FilterCategory selectedCategory;
  final FilterType selectedType;
  final FilterParameters parameters;
  final bool isFilterEnabled;
  final double intensity;

  const FilterState({
    this.selectedCategory = FilterCategory.beauty,
    this.selectedType = FilterType.none,
    required this.parameters,
    this.isFilterEnabled = false,
    this.intensity = 1.0,
  });

  factory FilterState.initial() {
    return FilterState(
      parameters: FilterParameters(
        intensity: 1.0,
        skinSmoothing: 0.0,
        skinBrightening: 0.0,
        faceSlimming: 0.0,
        eyeEnlargement: 0.0,
        lipEnhancement: 0.0,
        brightness: 0.0,
        contrast: 0.0,
        saturation: 0.0,
        warmth: 0.0,
        tint: 0.0,
        vibrance: 0.0,
        blur: 0.0,
        sharpen: 0.0,
        vignette: 0.0,
        grain: 0.0,
        fade: 0.0,
        highlights: 0.0,
        shadows: 0.0,
        clarity: 0.0,
        structure: 0.0,
      ),
    );
  }

  FilterState copyWith({
    FilterCategory? selectedCategory,
    FilterType? selectedType,
    FilterParameters? parameters,
    bool? isFilterEnabled,
    double? intensity,
  }) {
    return FilterState(
      selectedCategory: selectedCategory ?? this.selectedCategory,
      selectedType: selectedType ?? this.selectedType,
      parameters: parameters ?? this.parameters,
      isFilterEnabled: isFilterEnabled ?? this.isFilterEnabled,
      intensity: intensity ?? this.intensity,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is FilterState &&
        other.selectedCategory == selectedCategory &&
        other.selectedType == selectedType &&
        other.parameters == parameters &&
        other.isFilterEnabled == isFilterEnabled &&
        other.intensity == intensity;
  }

  @override
  int get hashCode {
    return selectedCategory.hashCode ^
        selectedType.hashCode ^
        parameters.hashCode ^
        isFilterEnabled.hashCode ^
        intensity.hashCode;
  }
}

/// Controller để quản lý filter state và giao tiếp với camera
class FilterController extends StateNotifier<FilterState> {
  final BeautyCameraController _cameraController;

  FilterController(this._cameraController) : super(FilterState.initial());

  /// Chọn category filter
  void selectCategory(FilterCategory category) {
    if (state.selectedCategory != category) {
      state = state.copyWith(
        selectedCategory: category,
        selectedType: FilterType.none, // Reset về none khi đổi category
      );
    }
  }

  /// Chọn filter type và apply
  Future<void> selectFilter(FilterType type) async {
    try {
      // Cập nhật state trước
      state = state.copyWith(
        selectedType: type,
        isFilterEnabled: type != FilterType.none,
      );

      // Tạo parameters dựa trên filter type
      final parameters = _createParametersForFilter(type);

      // Apply filter
      await _cameraController.applyFilter(
        state.selectedCategory,
        type,
        parameters,
      );

      // Cập nhật parameters trong state
      state = state.copyWith(parameters: parameters);

      debugPrint('Filter applied: ${state.selectedCategory} - $type');
    } catch (e) {
      debugPrint('Error applying filter: $e');
      // Revert state on error
      state = state.copyWith(
        selectedType: FilterType.none,
        isFilterEnabled: false,
      );
    }
  }

  /// Điều chỉnh intensity của filter hiện tại
  Future<void> adjustIntensity(double intensity) async {
    try {
      state = state.copyWith(intensity: intensity);

      // Tạo parameters mới với intensity được điều chỉnh
      final adjustedParameters = _adjustParametersIntensity(
        state.parameters,
        intensity,
      );

      // Apply filter với parameters mới
      await _cameraController.applyFilter(
        state.selectedCategory,
        state.selectedType,
        adjustedParameters,
      );

      // Cập nhật state
      state = state.copyWith(parameters: adjustedParameters);

      debugPrint('Filter intensity adjusted to: $intensity');
    } catch (e) {
      debugPrint('Error adjusting filter intensity: $e');
    }
  }

  /// Bật/tắt filter
  Future<void> toggleFilter() async {
    try {
      final newEnabled = !state.isFilterEnabled;
      state = state.copyWith(isFilterEnabled: newEnabled);

      if (newEnabled && state.selectedType != FilterType.none) {
        // Apply current filter
        await _cameraController.applyFilter(
          state.selectedCategory,
          state.selectedType,
          state.parameters,
        );
      } else {
        // Apply none filter to disable
        await _cameraController.applyFilter(
          FilterCategory.none,
          FilterType.none,
          _createDefaultParameters(),
        );
      }

      debugPrint('Filter toggled: $newEnabled');
    } catch (e) {
      debugPrint('Error toggling filter: $e');
      // Revert state on error
      state = state.copyWith(isFilterEnabled: !state.isFilterEnabled);
    }
  }

  /// Tạo parameters mặc định cho từng loại filter
  FilterParameters _createParametersForFilter(FilterType type) {
    switch (type) {
      case FilterType.none:
        return _createDefaultParameters();

      // Beauty filters
      case FilterType.beautyNatural:
        return _createDefaultParameters(
          intensity: 0.6,
          skinSmoothing: 0.3,
          skinBrightening: 0.2,
        );
      case FilterType.beautyGlow:
        return _createDefaultParameters(
          intensity: 0.8,
          skinSmoothing: 0.2,
          skinBrightening: 0.5,
          brightness: 0.1,
        );
      case FilterType.beautyDoll:
        return _createDefaultParameters(
          intensity: 1.0,
          skinSmoothing: 0.7,
          skinBrightening: 0.4,
          faceSlimming: 0.2,
        );
      case FilterType.beautyFresh:
        return _createDefaultParameters(
          intensity: 0.7,
          skinSmoothing: 0.4,
          skinBrightening: 0.3,
          saturation: 0.1,
        );
      case FilterType.beautySmooth:
        return _createDefaultParameters(
          intensity: 0.9,
          skinSmoothing: 0.8,
          skinBrightening: 0.1,
        );
      case FilterType.beautyBright:
        return _createDefaultParameters(
          intensity: 0.8,
          skinSmoothing: 0.2,
          skinBrightening: 0.6,
          brightness: 0.2,
        );

      // Portrait filters
      case FilterType.portraitClassic:
        return _createDefaultParameters(
          intensity: 0.7,
          contrast: 0.2,
          saturation: -0.1,
          warmth: 0.1,
        );
      case FilterType.portraitDramatic:
        return _createDefaultParameters(
          intensity: 0.9,
          contrast: 0.4,
          shadows: -0.2,
          highlights: 0.1,
        );
      case FilterType.portraitSoft:
        return _createDefaultParameters(
          intensity: 0.6,
          blur: 0.1,
          brightness: 0.1,
          contrast: -0.1,
        );
      case FilterType.portraitBW:
        return _createDefaultParameters(
          intensity: 1.0,
          saturation: -1.0,
          contrast: 0.2,
        );

      // Other filters - basic parameters
      default:
        return _createDefaultParameters(
          intensity: 0.7,
          brightness: 0.1,
          contrast: 0.1,
          saturation: 0.1,
        );
    }
  }

  /// Điều chỉnh parameters theo intensity
  FilterParameters _adjustParametersIntensity(
    FilterParameters baseParams,
    double intensity,
  ) {
    return FilterParameters(
      intensity: intensity,
      skinSmoothing: baseParams.skinSmoothing * intensity,
      skinBrightening: baseParams.skinBrightening * intensity,
      faceSlimming: baseParams.faceSlimming * intensity,
      eyeEnlargement: baseParams.eyeEnlargement * intensity,
      lipEnhancement: baseParams.lipEnhancement * intensity,
      brightness: baseParams.brightness * intensity,
      contrast: baseParams.contrast * intensity,
      saturation: baseParams.saturation * intensity,
      warmth: baseParams.warmth * intensity,
      tint: baseParams.tint * intensity,
      vibrance: baseParams.vibrance * intensity,
      blur: baseParams.blur * intensity,
      sharpen: baseParams.sharpen * intensity,
      vignette: baseParams.vignette * intensity,
      grain: baseParams.grain * intensity,
      fade: baseParams.fade * intensity,
      highlights: baseParams.highlights * intensity,
      shadows: baseParams.shadows * intensity,
      clarity: baseParams.clarity * intensity,
      structure: baseParams.structure * intensity,
    );
  }

  /// Tạo FilterParameters với tất cả values mặc định
  FilterParameters _createDefaultParameters({
    double intensity = 1.0,
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
}

/// Provider cho FilterController
final filterControllerProvider = StateNotifierProvider.autoDispose
    .family<FilterController, FilterState, BeautyCameraController>(
  (ref, cameraController) => FilterController(cameraController),
);

/// Provider để lấy filter controller dễ dàng
final filterProvider = Provider<FilterController>((ref) {
  final cameraController = ref.watch(cameraProvider);
  return ref.watch(filterControllerProvider(cameraController).notifier);
});

/// Provider để lấy filter state
final filterStateProvider = Provider<FilterState>((ref) {
  final cameraController = ref.watch(cameraProvider);
  return ref.watch(filterControllerProvider(cameraController));
});
