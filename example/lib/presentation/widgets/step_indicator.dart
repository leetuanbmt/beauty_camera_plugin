import 'package:flutter/material.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/app_text_styles.dart';

class StepIndicator extends StatelessWidget {
  final int totalSteps;
  final int currentStep;

  const StepIndicator({
    super.key,
    required this.totalSteps,
    required this.currentStep,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: List.generate(totalSteps * 2 - 1, (index) {
        if (index.isEven) {
          final stepNumber = (index ~/ 2) + 1;
          return _buildStepCircle(stepNumber);
        } else {
          return _buildStepLine((index ~/ 2) < currentStep - 1);
        }
      }),
    );
  }

  Widget _buildStepCircle(int number) {
    final isActive = number <= currentStep;
    return Container(
      width: 32,
      height: 32,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: isActive ? AppColors.primary : AppColors.inactive,
      ),
      child: Center(
        child: Text(
          number.toString(),
          style: isActive
              ? AppTextStyles.stepNumber
              : AppTextStyles.inactiveStepNumber,
        ),
      ),
    );
  }

  Widget _buildStepLine(bool isActive) {
    return Container(
      width: 60,
      height: 2,
      color: isActive ? AppColors.primary : AppColors.inactiveLine,
    );
  }
}
