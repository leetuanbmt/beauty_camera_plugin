#!/bin/bash
set -e  # Exit on error

# Set up directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Configuration
FRAMEWORK_NAME="${IOS_FRAMEWORK_NAME:-GoImage}"
MIN_IOS_VERSION="${IOS_MIN_VERSION:-11.0}"
BUNDLE_ID="${IOS_BUNDLE_ID:-com.kansuke.app.goimage}"

echo "Creating iOS Framework: $FRAMEWORK_NAME"
echo "Minimum iOS Version: $MIN_IOS_VERSION"
echo "Bundle ID: $BUNDLE_ID"

# Make sure Go libraries are built
if [ ! -f "build/ios/libgoimage.dylib" ] || [ ! -f "build/ios/libgoimage-simulator.dylib" ]; then
    echo "iOS libraries not found. Building them first..."
    SKIP_ANDROID=1 ./build.sh
fi

# Clean existing frameworks
rm -rf "build/ios/$FRAMEWORK_NAME.framework"
rm -rf "build/ios/$FRAMEWORK_NAME.xcframework"

# Create framework directories
mkdir -p "build/ios/$FRAMEWORK_NAME.framework/Headers"

# Create Info.plist for framework
cat > "build/ios/$FRAMEWORK_NAME.framework/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>$FRAMEWORK_NAME</string>
    <key>CFBundleIdentifier</key>
    <string>$BUNDLE_ID</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>$FRAMEWORK_NAME</string>
    <key>CFBundlePackageType</key>
    <string>FMWK</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>MinimumOSVersion</key>
    <string>$MIN_IOS_VERSION</string>
</dict>
</plist>
EOF

# Create device and simulator framework directories
FRAMEWORK_ROOT="build/ios"
DEVICE_FRAMEWORK_DIR="$FRAMEWORK_ROOT/device/$FRAMEWORK_NAME.framework"
SIMULATOR_FRAMEWORK_DIR="$FRAMEWORK_ROOT/simulator/$FRAMEWORK_NAME.framework"

mkdir -p "$DEVICE_FRAMEWORK_DIR/Headers"
mkdir -p "$SIMULATOR_FRAMEWORK_DIR/Headers"

# Copy Info.plist to both frameworks
cp "build/ios/$FRAMEWORK_NAME.framework/Info.plist" "$DEVICE_FRAMEWORK_DIR"
cp "build/ios/$FRAMEWORK_NAME.framework/Info.plist" "$SIMULATOR_FRAMEWORK_DIR"

# Copy the header file
if [ -f "build/ios/libgoimage.h" ]; then
    echo "Using generated header file..."
    cp "build/ios/libgoimage.h" "$DEVICE_FRAMEWORK_DIR/Headers/$FRAMEWORK_NAME.h"
    cp "build/ios/libgoimage.h" "$SIMULATOR_FRAMEWORK_DIR/Headers/$FRAMEWORK_NAME.h"
else
    echo "WARNING: Header file not found. Creating empty header..."
    echo "// $FRAMEWORK_NAME Framework Header" > "$DEVICE_FRAMEWORK_DIR/Headers/$FRAMEWORK_NAME.h"
    echo "// $FRAMEWORK_NAME Framework Header" > "$SIMULATOR_FRAMEWORK_DIR/Headers/$FRAMEWORK_NAME.h"
fi

# Create modulemap for Swift interoperability
mkdir -p "$DEVICE_FRAMEWORK_DIR/Modules"
mkdir -p "$SIMULATOR_FRAMEWORK_DIR/Modules"

cat > "$DEVICE_FRAMEWORK_DIR/Modules/module.modulemap" <<EOF
framework module $FRAMEWORK_NAME {
    header "$FRAMEWORK_NAME.h"
    export *
}
EOF

cp "$DEVICE_FRAMEWORK_DIR/Modules/module.modulemap" "$SIMULATOR_FRAMEWORK_DIR/Modules/"

# Copy the libraries and rename them to match framework name
cp "build/ios/libgoimage.dylib" "$DEVICE_FRAMEWORK_DIR/$FRAMEWORK_NAME"
cp "build/ios/libgoimage-simulator.dylib" "$SIMULATOR_FRAMEWORK_DIR/$FRAMEWORK_NAME"

# Create XCFramework
echo "Creating XCFramework..."
xcodebuild -create-xcframework \
    -framework "$DEVICE_FRAMEWORK_DIR" \
    -framework "$SIMULATOR_FRAMEWORK_DIR" \
    -output "$FRAMEWORK_ROOT/$FRAMEWORK_NAME.xcframework"

# Show success message with usage instructions
echo ""
echo "✅ iOS Framework created successfully!"
echo ""
echo "📦 Framework locations:"
echo "   - XCFramework: $FRAMEWORK_ROOT/$FRAMEWORK_NAME.xcframework"
echo "   - Device Framework: $DEVICE_FRAMEWORK_DIR"
echo "   - Simulator Framework: $SIMULATOR_FRAMEWORK_DIR"
echo ""
echo "🔧 To use in your Xcode project:"
echo "   1. Drag $FRAMEWORK_ROOT/$FRAMEWORK_NAME.xcframework into your project"
echo "   2. Make sure 'Copy items if needed' is checked"
echo "   3. Add the framework to your target's 'Frameworks, Libraries, and Embedded Content' section"
echo "   4. Set Embed to 'Embed & Sign'"
echo ""
echo "👩‍💻 In your Swift code, import it with: import $FRAMEWORK_NAME"
echo "👨‍💻 In Objective-C, import it with: #import <$FRAMEWORK_NAME/$FRAMEWORK_NAME.h>"