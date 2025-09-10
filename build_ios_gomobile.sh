#!/bin/bash
set -e  # Exit on error

# Set up directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Configuration
FRAMEWORK_NAME="${IOS_FRAMEWORK_NAME:-GoImage}"
MIN_IOS_VERSION="${IOS_MIN_VERSION:-11.0}"
BUNDLE_ID="${IOS_BUNDLE_ID:-com.kansuke.app.goimage}"
OUTPUT_DIR="build/ios"

echo "Building iOS Framework using gomobile bind: $FRAMEWORK_NAME"
echo "Minimum iOS Version: $MIN_IOS_VERSION"
echo "Bundle ID: $BUNDLE_ID"
echo "Output directory: $OUTPUT_DIR"
echo ""

# Ensure gomobile is installed
if ! command -v gomobile &> /dev/null; then
    echo "Installing gomobile..."
    go install golang.org/x/mobile/cmd/gomobile@latest
    gomobile init
else
    echo "gomobile is already installed"
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Go to src directory
cd src

# Build the framework using gomobile bind
echo "Building framework with gomobile bind..."
gomobile bind \
    -target=ios \
    -iosversion=$MIN_IOS_VERSION \
    -ldflags="-s -w" \
    -o ../$OUTPUT_DIR/$FRAMEWORK_NAME.xcframework \
    -bundleid=$BUNDLE_ID \
    -v .

# Return to original directory
cd ..

# Check if build was successful
if [ -d "$OUTPUT_DIR/$FRAMEWORK_NAME.xcframework" ]; then
    echo ""
    echo "✅ iOS Framework created successfully with gomobile bind!"
    echo ""
    echo "📦 Framework location: $OUTPUT_DIR/$FRAMEWORK_NAME.xcframework"
    echo ""
    echo "🔧 To use in your Xcode project:"
    echo "   1. Drag $OUTPUT_DIR/$FRAMEWORK_NAME.xcframework into your project"
    echo "   2. Make sure 'Copy items if needed' is checked"
    echo "   3. Add the framework to your target's 'Frameworks, Libraries, and Embedded Content' section"
    echo "   4. Set Embed to 'Embed & Sign'"
    echo ""
    echo "👩‍💻 In your Swift code, import it with: import $FRAMEWORK_NAME"
    echo "👨‍💻 In Objective-C, import it with: @import $FRAMEWORK_NAME;"
else
    echo "❌ Failed to build framework"
    exit 1
fi