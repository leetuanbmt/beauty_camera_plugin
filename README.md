# Beauty Camera Plugin

A Flutter plugin for implementing beauty camera functionality with filters and effects. This plugin uses CameraX for camera operations and GPUImage for applying real-time filters on Android.

## Features

- Camera preview with texture rendering
- Take pictures with beauty filters
- Record videos with real-time filters
- Multiple filter types (beauty, vintage, etc.)
- Face detection support
- Camera controls (flash, zoom, focus)
- Video quality settings
- Error handling and state management
- Cross-platform support (Android & iOS)

## Installation

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  beauty_camera_plugin: ^1.0.0
```

### Android Setup

Add the following permissions to your Android Manifest (`android/app/src/main/AndroidManifest.xml`):

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />

<uses-feature android:name="android.hardware.camera" android:required="true" />
<uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
```

### iOS Setup

Add the following keys to your `ios/Runner/Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>This app needs camera access to take photos and record videos</string>
<key>NSMicrophoneUsageDescription</key>
<string>This app needs microphone access to record videos</string>
```

## Usage

### Basic Setup

1. Import the package:

```dart
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
```

2. Initialize the camera controller:

```dart
final controller = BeautyCameraController();

// Initialize with settings
await controller.initialize(
  settings: AdvancedCameraSettings(
    videoQuality: VideoQuality.high,
    maxFrameRate: 30,
    videoStabilization: true,
    autoExposure: true,
    enableFaceDetection: true,
  ),
);
```

3. Create a camera view:

```dart
BeautyCameraView(
  controller: controller,
  onImageCaptured: (String path) {
    print('Image captured: $path');
  },
  onVideoRecorded: (String path) {
    print('Video recorded: $path');
  },
  onFaceDetected: (List<FaceData> faces) {
    print('Faces detected: ${faces.length}');
  },
  showFaceDetection: true,
  showControls: true,
)
```

### Taking Pictures

```dart
// Take a picture
final imagePath = await controller.takePhoto();
print('Image saved to: $imagePath');
```

### Recording Videos

```dart
// Start recording
await controller.startVideoRecording();

// Stop recording
final videoPath = await controller.stopVideoRecording();
print('Video saved to: $videoPath');
```

### Applying Filters

```dart
// Apply a filter with intensity level
await controller.setFilterMode(
  mode: CameraFilterMode.beauty,
  level: 5.0, // Range: 0.0 to 10.0
);
```

### Camera Controls

```dart
// Switch camera
await controller.switchCamera();

// Set zoom level
await controller.setZoom(2.0);

// Set flash mode
await controller.setFlashMode(FlashMode.auto);

// Focus on point
await controller.focusOnPoint(x: 100, y: 100);
```

### Error Handling

```dart
try {
  await controller.initialize();
} on CameraException catch (e) {
  print('Camera error: ${e.code} - ${e.message}');
}
```

### Listening to Events

```dart
controller.events.listen((event) {
  switch (event.type) {
    case CameraEventType.initialized:
      print('Camera initialized');
      break;
    case CameraEventType.error:
      print('Camera error: ${event.data}');
      break;
    // Handle other events...
  }
});
```

## Available Filters

The plugin supports various filters including:

- Beauty
- Mono
- Negative
- Sepia
- Solarize
- Posterize
- Whiteboard
- Blackboard
- Aqua
- Emboss
- Sketch
- Neon
- Vintage
- Brightness
- Contrast
- Saturation
- Sharpen
- Gaussian Blur
- Vignette
- Hue
- Exposure
- Highlight Shadow
- Levels
- Color Balance
- Lookup (Custom LUT)

## Contributing

Feel free to contribute to this project by submitting issues and/or pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

<h1 align="center">Hi 👋, I'm Van Minh Tuan</h1>
<h3 align="center">Looking for a ambitious and professional working environment to perform and exlpore my more than 8-year experience in fullstack and mobile development.</h3>

- 📫 How to reach me **leetuanbmt@gmail.com**

<h3 align="left">Connect with me:</h3>
<p align="left">
<a href="https://twitter.com/leetuanbmtgmail" target="blank"><img align="center" src="https://raw.githubusercontent.com/rahuldkjain/github-profile-readme-generator/master/src/images/icons/Social/twitter.svg" alt="leetuanbmtgmail" height="30" width="40" /></a>
<a href="https://linkedin.com/in/leetuanbmt" target="blank"><img align="center" src="https://raw.githubusercontent.com/rahuldkjain/github-profile-readme-generator/master/src/images/icons/Social/linked-in-alt.svg" alt="leetuanbmt" height="30" width="40" /></a>
<a href="https://fb.com/leetuanbmt" target="blank"><img align="center" src="https://raw.githubusercontent.com/rahuldkjain/github-profile-readme-generator/master/src/images/icons/Social/facebook.svg" alt="leetuanbmt" height="30" width="40" /></a>
<a href="https://instagram.com/leetuanbmt2019" target="blank"><img align="center" src="https://raw.githubusercontent.com/rahuldkjain/github-profile-readme-generator/master/src/images/icons/Social/instagram.svg" alt="leetuanbmt2019" height="30" width="40" /></a>
<a href="https://www.hackerrank.com/leetuanbmt" target="blank"><img align="center" src="https://raw.githubusercontent.com/rahuldkjain/github-profile-readme-generator/master/src/images/icons/Social/hackerrank.svg" alt="leetuanbmt" height="30" width="40" /></a>
</p>

<h3 align="left">Languages and Tools:</h3>
<p align="left"> <a href="https://aws.amazon.com/amplify/" target="_blank" rel="noreferrer"> <img src="https://docs.amplify.aws/assets/logo-dark.svg" alt="amplify" width="40" height="40"/> </a> <a href="https://angular.io" target="_blank" rel="noreferrer"> <img src="https://angular.io/assets/images/logos/angular/angular.svg" alt="angular" width="40" height="40"/> </a> <a href="https://aws.amazon.com" target="_blank" rel="noreferrer"> <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/amazonwebservices/amazonwebservices-original-wordmark.svg" alt="aws" width="40" height="40"/> </a> <a href="https://azure.microsoft.com/en-in/" target="_blank" rel="noreferrer"> <img src="https://www.vectorlogo.zone/logos/microsoft_azure/microsoft_azure-icon.svg" alt="azure" width="40" height="40"/> </a> <a href="https://dart.dev" target="_blank" rel="noreferrer"> <img src="https://www.vectorlogo.zone/logos/dartlang/dartlang-icon.svg" alt="dart" width="40" height="40"/> </a> <a href="https://www.docker.com/" target="_blank" rel="noreferrer"> <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/docker/docker-original-wordmark.svg" alt="docker" width="40" height="40"/> </a> <a href="https://firebase.google.com/" target="_blank" rel="noreferrer"> <img src="https://www.vectorlogo.zone/logos/firebase/firebase-icon.svg" alt="firebase" width="40" height="40"/> </a> <a href="https://flutter.dev" target="_blank" rel="noreferrer"> <img src="https://www.vectorlogo.zone/logos/flutterio/flutterio-icon.svg" alt="flutter" width="40" height="40"/> </a> <a href="https://git-scm.com/" target="_blank" rel="noreferrer"> <img src="https://www.vectorlogo.zone/logos/git-scm/git-scm-icon.svg" alt="git" width="40" height="40"/> </a> <a href="https://ionicframework.com" target="_blank" rel="noreferrer"> <img src="https://upload.wikimedia.org/wikipedia/commons/d/d1/Ionic_Logo.svg" alt="ionic" width="40" height="40"/> </a> <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript" target="_blank" rel="noreferrer"> <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/javascript/javascript-original.svg" alt="javascript" width="40" height="40"/> </a> <a href="https://materializecss.com/" target="_blank" rel="noreferrer"> <img src="https://raw.githubusercontent.com/prplx/svg-logos/5585531d45d294869c4eaab4d7cf2e9c167710a9/svg/materialize.svg" alt="materialize" width="40" height="40"/> </a> <a href="https://www.mongodb.com/" target="_blank" rel="noreferrer"> <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/mongodb/mongodb-original-wordmark.svg" alt="mongodb" width="40" height="40"/> </a> <a href="https://www.microsoft.com/en-us/sql-server" target="_blank" rel="noreferrer"> <img src="https://www.svgrepo.com/show/303229/microsoft-sql-server-logo.svg" alt="mssql" width="40" height="40"/> </a> <a href="https://www.mysql.com/" target="_blank" rel="noreferrer"> <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/mysql/mysql-original-wordmark.svg" alt="mysql" width="40" height="40"/> </a> <a href="https://postman.com" target="_blank" rel="noreferrer"> <img src="https://www.vectorlogo.zone/logos/getpostman/getpostman-icon.svg" alt="postman" width="40" height="40"/> </a> <a href="https://www.python.org" target="_blank" rel="noreferrer"> <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/python/python-original.svg" alt="python" width="40" height="40"/> </a> <a href="https://tailwindcss.com/" target="_blank" rel="noreferrer"> <img src="https://www.vectorlogo.zone/logos/tailwindcss/tailwindcss-icon.svg" alt="tailwind" width="40" height="40"/> </a> </p>
