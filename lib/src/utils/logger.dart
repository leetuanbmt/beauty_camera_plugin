import 'package:flutter/foundation.dart';

import 'dart:developer' as developer;

/// A simple logger class to replace print statements in production code
class Logger {
  /// Log a message with optional tag
  static void log(String message, {String tag = 'BeautyCamera'}) {
    if (kDebugMode) {
      developer.log('[$tag] $message');
    }
  }

  /// Log an error with optional tag and exception
  static void error(String message, {String tag = 'BeautyCamera'}) {
    if (kDebugMode) {
      developer.log('[$tag] ERROR: $message');
    }
  }
}
