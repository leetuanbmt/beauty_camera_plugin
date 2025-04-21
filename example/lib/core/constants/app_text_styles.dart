import 'package:flutter/material.dart';
import 'app_colors.dart';

class AppTextStyles {
  static const appBarTitle = TextStyle(
    color: AppColors.titleOrange,
    fontSize: 17,
    fontFamily: 'Roboto',
  );

  static const stepTitle = TextStyle(
    fontSize: 18,
    fontFamily: 'Roboto',
    color: AppColors.textBlack,
  );

  static const buttonText = TextStyle(
    color: AppColors.white,
    fontSize: 16,
    fontWeight: FontWeight.bold,
    fontFamily: 'Roboto',
  );

  static const cardText = TextStyle(
    fontSize: 14,
    color: AppColors.textGrey,
    fontFamily: 'Roboto',
  );

  static const stepNumber = TextStyle(
    color: AppColors.white,
    fontSize: 18,
    fontWeight: FontWeight.bold,
    fontFamily: 'Roboto',
  );

  static const inactiveStepNumber = TextStyle(
    color: AppColors.white,
    fontSize: 18,
    fontFamily: 'Roboto',
  );
}
