import 'package:flutter/material.dart';
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';

class FilterSelector extends StatefulWidget {
  final FilterCategory selectedCategory;
  final FilterType selectedType;
  final Function(FilterType) onFilterSelected;

  const FilterSelector({
    super.key,
    required this.selectedCategory,
    required this.selectedType,
    required this.onFilterSelected,
  });

  @override
  State<FilterSelector> createState() => _FilterSelectorState();
}

class _FilterSelectorState extends State<FilterSelector> {
  late ScrollController _scrollController;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  List<FilterType> _getFiltersForCategory(FilterCategory category) {
    switch (category) {
      case FilterCategory.beauty:
        return [
          FilterType.none,
          FilterType.beautyNatural,
          FilterType.beautyGlow,
          FilterType.beautyDoll,
          FilterType.beautyFresh,
          FilterType.beautySmooth,
          FilterType.beautyBright,
        ];
      case FilterCategory.portrait:
        return [
          FilterType.none,
          FilterType.portraitClassic,
          FilterType.portraitDramatic,
          FilterType.portraitSoft,
          FilterType.portraitBW,
        ];
      case FilterCategory.food:
        return [
          FilterType.none,
          FilterType.foodWarm,
          FilterType.foodVibrant,
          FilterType.foodFresh,
          FilterType.foodInstagram,
        ];
      case FilterCategory.landscape:
        return [
          FilterType.none,
          FilterType.landscapeGolden,
          FilterType.landscapeDramatic,
          FilterType.landscapeVibrant,
          FilterType.landscapeMoody,
        ];
      case FilterCategory.vintage:
        return [
          FilterType.none,
          FilterType.vintageFilm,
          FilterType.vintageSepia,
          FilterType.vintageFaded,
          FilterType.vintageRetro,
        ];
      case FilterCategory.vibrant:
        return [
          FilterType.none,
          FilterType.vibrantPop,
          FilterType.vibrantNeon,
          FilterType.vibrantSummer,
          FilterType.vibrantTropical,
        ];
      case FilterCategory.moody:
        return [
          FilterType.none,
          FilterType.moodyDark,
          FilterType.moodyBlue,
          FilterType.moodyCinematic,
          FilterType.moodyNoir,
        ];
      case FilterCategory.film:
        return [
          FilterType.none,
          FilterType.filmKodak,
          FilterType.filmFuji,
          FilterType.filmPolaroid,
          FilterType.filmVHS,
        ];
      case FilterCategory.art:
        return [
          FilterType.none,
          FilterType.artCartoon,
          FilterType.artOilPainting,
          FilterType.artWatercolor,
          FilterType.artSketch,
        ];
      default:
        return [FilterType.none];
    }
  }

  String _getFilterDisplayName(FilterType type) {
    switch (type) {
      case FilterType.none:
        return 'None';
      // Beauty filters
      case FilterType.beautyNatural:
        return 'Natural';
      case FilterType.beautyGlow:
        return 'Glow';
      case FilterType.beautyDoll:
        return 'Doll';
      case FilterType.beautyFresh:
        return 'Fresh';
      case FilterType.beautySmooth:
        return 'Smooth';
      case FilterType.beautyBright:
        return 'Bright';
      // Portrait filters
      case FilterType.portraitClassic:
        return 'Classic';
      case FilterType.portraitDramatic:
        return 'Dramatic';
      case FilterType.portraitSoft:
        return 'Soft';
      case FilterType.portraitBW:
        return 'B&W';
      // Food filters
      case FilterType.foodWarm:
        return 'Warm';
      case FilterType.foodVibrant:
        return 'Vibrant';
      case FilterType.foodFresh:
        return 'Fresh';
      case FilterType.foodInstagram:
        return 'Instagram';
      // Landscape filters
      case FilterType.landscapeGolden:
        return 'Golden';
      case FilterType.landscapeDramatic:
        return 'Dramatic';
      case FilterType.landscapeVibrant:
        return 'Vibrant';
      case FilterType.landscapeMoody:
        return 'Moody';
      // Vintage filters
      case FilterType.vintageFilm:
        return 'Film';
      case FilterType.vintageSepia:
        return 'Sepia';
      case FilterType.vintageFaded:
        return 'Faded';
      case FilterType.vintageRetro:
        return 'Retro';
      // Vibrant filters
      case FilterType.vibrantPop:
        return 'Pop';
      case FilterType.vibrantNeon:
        return 'Neon';
      case FilterType.vibrantSummer:
        return 'Summer';
      case FilterType.vibrantTropical:
        return 'Tropical';
      // Moody filters
      case FilterType.moodyDark:
        return 'Dark';
      case FilterType.moodyBlue:
        return 'Blue';
      case FilterType.moodyCinematic:
        return 'Cinematic';
      case FilterType.moodyNoir:
        return 'Noir';
      // Film filters
      case FilterType.filmKodak:
        return 'Kodak';
      case FilterType.filmFuji:
        return 'Fuji';
      case FilterType.filmPolaroid:
        return 'Polaroid';
      case FilterType.filmVHS:
        return 'VHS';
      // Art filters
      case FilterType.artCartoon:
        return 'Cartoon';
      case FilterType.artOilPainting:
        return 'Oil Paint';
      case FilterType.artWatercolor:
        return 'Watercolor';
      case FilterType.artSketch:
        return 'Sketch';
    }
  }

  IconData _getFilterIcon(FilterType type) {
    switch (type) {
      case FilterType.none:
        return Icons.block;
      // Beauty filters
      case FilterType.beautyNatural:
        return Icons.face;
      case FilterType.beautyGlow:
        return Icons.brightness_high;
      case FilterType.beautyDoll:
        return Icons.face_retouching_natural;
      case FilterType.beautyFresh:
        return Icons.spa;
      case FilterType.beautySmooth:
        return Icons.blur_on;
      case FilterType.beautyBright:
        return Icons.wb_sunny;
      // Portrait filters
      case FilterType.portraitClassic:
        return Icons.portrait;
      case FilterType.portraitDramatic:
        return Icons.theater_comedy;
      case FilterType.portraitSoft:
        return Icons.blur_circular;
      case FilterType.portraitBW:
        return Icons.filter_b_and_w;
      // Food filters
      case FilterType.foodWarm:
        return Icons.local_fire_department;
      case FilterType.foodVibrant:
        return Icons.restaurant;
      case FilterType.foodFresh:
        return Icons.eco;
      case FilterType.foodInstagram:
        return Icons.camera_alt;
      // Other categories - using generic icons
      default:
        return Icons.photo_filter;
    }
  }

  @override
  Widget build(BuildContext context) {
    final filters = _getFiltersForCategory(widget.selectedCategory);

    return Container(
      height: 120,
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: ListView.builder(
        controller: _scrollController,
        scrollDirection: Axis.horizontal,
        itemCount: filters.length,
        itemBuilder: (context, index) {
          final filter = filters[index];
          final isSelected = filter == widget.selectedType;

          return Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: GestureDetector(
              onTap: () => widget.onFilterSelected(filter),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Filter preview thumbnail
                  Container(
                    width: 64,
                    height: 64,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: isSelected ? Colors.blue : Colors.white24,
                        width: isSelected ? 3 : 1,
                      ),
                      gradient: LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [
                          Colors.grey[800]!,
                          Colors.grey[900]!,
                        ],
                      ),
                    ),
                    child: Icon(
                      _getFilterIcon(filter),
                      color: isSelected ? Colors.blue : Colors.white70,
                      size: 28,
                    ),
                  ),
                  const SizedBox(height: 8),
                  // Filter name
                  Text(
                    _getFilterDisplayName(filter),
                    style: TextStyle(
                      color: isSelected ? Colors.blue : Colors.white,
                      fontSize: 12,
                      fontWeight:
                          isSelected ? FontWeight.bold : FontWeight.normal,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
