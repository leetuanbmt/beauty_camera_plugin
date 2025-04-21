import 'package:flutter/material.dart';
import '../widgets/step_indicator.dart';
import '../widgets/guidance_card.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/app_text_styles.dart';
import '../../core/constants/app_strings.dart';

class IDCaptureGuidancePage extends StatelessWidget {
  const IDCaptureGuidancePage({super.key});

  void _handleContinue(BuildContext context) {
    // TODO: Implement navigation to next screen
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.white,
      appBar: _buildAppBar(),
      body: _buildBody(context),
    );
  }

  PreferredSizeWidget _buildAppBar() {
    return AppBar(
      backgroundColor: AppColors.white,
      elevation: 0,
      title: Text(
        AppStrings.idCaptureGuidanceTitle,
        style: AppTextStyles.appBarTitle,
      ),
      centerTitle: true,
    );
  }

  Widget _buildBody(BuildContext context) {
    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Column(
          children: [
            const SizedBox(height: 20),
            const StepIndicator(
              totalSteps: 3,
              currentStep: 1,
            ),
            const SizedBox(height: 16),
            Text(
              AppStrings.idCaptureStep1Title,
              style: AppTextStyles.stepTitle,
            ),
            const SizedBox(height: 24),
            _buildGuidanceCards(),
            const SizedBox(height: 32),
            _buildContinueButton(context),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  Widget _buildGuidanceCards() {
    return Column(
      children: const [
        GuidanceCard(
          text: AppStrings.guidanceValidId,
          iconPath: 'assets/images/valid_id.png',
        ),
        SizedBox(height: 16),
        GuidanceCard(
          text: AppStrings.guidanceClearPhoto,
          iconPath: 'assets/images/clear_photo.png',
        ),
        SizedBox(height: 16),
        GuidanceCard(
          text: AppStrings.guidanceFlatSurface,
          iconPath: 'assets/images/flat_surface.png',
        ),
        SizedBox(height: 16),
        GuidanceCard(
          text: AppStrings.guidanceNoHand,
          iconPath: 'assets/images/no_hand.png',
        ),
        SizedBox(height: 16),
        GuidanceCard(
          text: AppStrings.guidanceNoCopy,
          iconPath: 'assets/images/no_copy.png',
        ),
        SizedBox(height: 16),
        GuidanceCard(
          text: AppStrings.guidanceNoDamage,
          iconPath: 'assets/images/no_damage.png',
        ),
      ],
    );
  }

  Widget _buildContinueButton(BuildContext context) {
    return ElevatedButton(
      onPressed: () => _handleContinue(context),
      style: ElevatedButton.styleFrom(
        backgroundColor: AppColors.primary,
        minimumSize: const Size(double.infinity, 48),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(6),
        ),
      ),
      child: Text(
        AppStrings.continueButton.toUpperCase(),
        style: AppTextStyles.buttonText,
      ),
    );
  }
}
