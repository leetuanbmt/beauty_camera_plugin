#!/bin/bash

# iOS Test Script for Beauty Camera Plugin
echo "🍎 Testing iOS Beauty Camera Plugin with Metal"

# Check if we're on macOS
if [[ "$OSTYPE" != "darwin"* ]]; then
    echo "❌ This script only works on macOS"
    exit 1
fi

# Check if Xcode is installed
if ! command -v xcodebuild &> /dev/null; then
    echo "❌ Xcode is not installed"
    exit 1
fi

# Check if iOS Simulator is available
if ! xcrun simctl list devices | grep -q "iPhone"; then
    echo "❌ No iOS Simulator found"
    exit 1
fi

echo "✅ Environment check passed"

# Navigate to example directory
cd example

# Clean and get dependencies
echo "🧹 Cleaning and getting dependencies..."
flutter clean
flutter pub get

# Install iOS pods
echo "📦 Installing iOS pods..."
cd ios
pod install
cd ..

# Build for iOS Simulator
echo "🔨 Building for iOS Simulator..."
flutter build ios --simulator

# Run on iOS Simulator
echo "🚀 Running on iOS Simulator..."
flutter run -d ios

echo "✅ iOS test completed"
