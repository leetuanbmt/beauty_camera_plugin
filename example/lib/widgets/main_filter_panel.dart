import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import '../controllers/filter_controller.dart';
// import '../controllers/camera_controller.dart'; // Not needed
import 'filter_selector.dart';

class MainFilterPanel extends ConsumerStatefulWidget {
  const MainFilterPanel({super.key});

  @override
  ConsumerState<MainFilterPanel> createState() => _MainFilterPanelState();
}

class _MainFilterPanelState extends ConsumerState<MainFilterPanel>
    with TickerProviderStateMixin {
  late TabController _tabController;
  bool _showIntensitySlider = false;

  final List<FilterCategory> _categories = [
    FilterCategory.beauty,
    FilterCategory.portrait,
    FilterCategory.food,
    FilterCategory.landscape,
    FilterCategory.vintage,
    FilterCategory.vibrant,
    FilterCategory.moody,
    FilterCategory.film,
    FilterCategory.art,
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(
      length: _categories.length,
      vsync: this,
      initialIndex: 0,
    );

    _tabController.addListener(() {
      if (!_tabController.indexIsChanging) {
        final category = _categories[_tabController.index];
        ref.read(filterProvider).selectCategory(category);
      }
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  String _getCategoryDisplayName(FilterCategory category) {
    switch (category) {
      case FilterCategory.beauty:
        return 'Beauty';
      case FilterCategory.portrait:
        return 'Portrait';
      case FilterCategory.food:
        return 'Food';
      case FilterCategory.landscape:
        return 'Landscape';
      case FilterCategory.vintage:
        return 'Vintage';
      case FilterCategory.vibrant:
        return 'Vibrant';
      case FilterCategory.moody:
        return 'Moody';
      case FilterCategory.film:
        return 'Film';
      case FilterCategory.art:
        return 'Art';
      default:
        return category.toString().split('.').last;
    }
  }

  IconData _getCategoryIcon(FilterCategory category) {
    switch (category) {
      case FilterCategory.beauty:
        return Icons.face;
      case FilterCategory.portrait:
        return Icons.portrait;
      case FilterCategory.food:
        return Icons.restaurant;
      case FilterCategory.landscape:
        return Icons.landscape;
      case FilterCategory.vintage:
        return Icons.photo_camera;
      case FilterCategory.vibrant:
        return Icons.color_lens;
      case FilterCategory.moody:
        return Icons.dark_mode;
      case FilterCategory.film:
        return Icons.movie;
      case FilterCategory.art:
        return Icons.brush;
      default:
        return Icons.photo_filter;
    }
  }

  @override
  Widget build(BuildContext context) {
    final filterState = ref.watch(filterStateProvider);
    final filterController = ref.watch(filterProvider);

    return Container(
      height: MediaQuery.of(context).size.height * 0.4,
      decoration: const BoxDecoration(
        color: Colors.black87,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        children: [
          // Handle bar
          Container(
            margin: const EdgeInsets.only(top: 8),
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: Colors.white54,
              borderRadius: BorderRadius.circular(2),
            ),
          ),

          // Header with close button and filter toggle
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.close, color: Colors.white),
                  onPressed: () => Navigator.pop(context),
                ),
                const Spacer(),
                Text(
                  'Filters',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const Spacer(),
                // Filter toggle button
                IconButton(
                  icon: Icon(
                    filterState.isFilterEnabled
                        ? Icons.check_circle
                        : Icons.radio_button_unchecked,
                    color: filterState.isFilterEnabled
                        ? Colors.blue
                        : Colors.white54,
                  ),
                  onPressed: () => filterController.toggleFilter(),
                ),
              ],
            ),
          ),

          // Category tabs
          SizedBox(
            height: 50,
            child: TabBar(
              controller: _tabController,
              isScrollable: true,
              indicatorColor: Colors.blue,
              indicatorWeight: 3,
              labelColor: Colors.blue,
              unselectedLabelColor: Colors.white70,
              labelStyle:
                  const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
              unselectedLabelStyle: const TextStyle(fontSize: 12),
              tabs: _categories.map((category) {
                return Tab(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(_getCategoryIcon(category), size: 20),
                      const SizedBox(height: 2),
                      Text(_getCategoryDisplayName(category)),
                    ],
                  ),
                );
              }).toList(),
            ),
          ),

          const SizedBox(height: 8),

          // Filter selector
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: _categories.map((category) {
                return FilterSelector(
                  selectedCategory: category,
                  selectedType: filterState.selectedType,
                  onFilterSelected: (filterType) async {
                    await filterController.selectFilter(filterType);
                    setState(() {
                      _showIntensitySlider = filterType != FilterType.none;
                    });
                  },
                );
              }).toList(),
            ),
          ),

          // Intensity slider (show when filter is selected)
          if (_showIntensitySlider &&
              filterState.selectedType != FilterType.none)
            _buildIntensitySlider(filterState, filterController),

          const SizedBox(height: 16),
        ],
      ),
    );
  }

  Widget _buildIntensitySlider(FilterState state, FilterController controller) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Column(
        children: [
          Row(
            children: [
              const Icon(
                Icons.tune,
                color: Colors.white,
                size: 20,
              ),
              const SizedBox(width: 8),
              const Text(
                'Intensity',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                ),
              ),
              Expanded(
                child: Slider(
                  value: state.intensity,
                  min: 0.0,
                  max: 1.0,
                  divisions: 20,
                  activeColor: Colors.blue,
                  inactiveColor: Colors.white24,
                  onChanged: (value) async {
                    await controller.adjustIntensity(value);
                  },
                ),
              ),
              Text(
                '${(state.intensity * 100).round()}%',
                style: const TextStyle(
                  color: Colors.white70,
                  fontSize: 12,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
