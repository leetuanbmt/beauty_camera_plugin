# Beauty Camera Plugin

A Flutter plugin for implementing beauty camera functionality with filters and effects.

## Features

- Camera preview with texture
- Take pictures with beauty filters
- Record videos with real-time filters
- Multiple filter types (beauty, vintage, etc.)
- Error handling and state management
- Cross-platform support (iOS & Android)

## Installation

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  beauty_camera_plugin: ^1.0.0
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
  width: 1920,
  height: 1080,
  defaultFilter: 'none',
);
```

3. Display the camera preview:

```dart
Texture(textureId: controller.textureId!)
```

### Taking Pictures

```dart
// Take a picture and save it
final path = '/storage/emulated/0/Pictures/beauty_camera_${timestamp}.jpg';
await controller.takePicture(path);
```

### Recording Videos

```dart
// Start recording
final path = '/storage/emulated/0/Movies/beauty_camera_${timestamp}.mp4';
await controller.startRecording(path);

// Stop recording
await controller.stopRecording();
```

### Applying Filters

```dart
// Apply a filter with parameters
await controller.applyFilter('beauty', {
  'intensity': 0.5,
  'smoothness': 0.7,
});
```

### Error Handling

```dart
// Listen to error stream
controller.errorStream.listen((error) {
  print('Camera error: ${error.type} - ${error.message}');
});

// Listen to state changes
controller.stateStream.listen((state) {
  print('Camera state changed: ${state.isInitialized}');
});
```

### Complete Example

Here's a complete example of using the plugin in a Flutter app:

```dart
class CameraScreen extends StatefulWidget {
  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  late final BeautyCameraController _controller;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _controller = BeautyCameraController();
    _initializeCamera();
  }

  Future<void> _initializeCamera() async {
    try {
      await _controller.initialize(
        width: 1920,
        height: 1080,
        defaultFilter: 'none',
      );
    } catch (e) {
      setState(() => _errorMessage = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Column(
        children: [
          if (_errorMessage != null)
            Text(_errorMessage!, style: TextStyle(color: Colors.red)),
          Expanded(
            child: _controller.textureId != null
                ? Texture(textureId: _controller.textureId!)
                : CircularProgressIndicator(),
          ),
          Row(
            children: [
              IconButton(
                icon: Icon(Icons.camera_alt),
                onPressed: _controller.isInitialized ? _takePicture : null,
              ),
              IconButton(
                icon: Icon(Icons.fiber_manual_record),
                onPressed: _controller.isInitialized ? _toggleRecording : null,
              ),
            ],
          ),
        ],
      ),
    );
  }
}
```

## API Reference

### BeautyCameraController

The main controller class for managing camera operations.

#### Properties

- `textureId`: Current texture ID for camera preview
- `isInitialized`: Whether the camera is initialized
- `isRecording`: Whether the camera is currently recording
- `currentFilter`: Current applied filter
- `errorStream`: Stream of camera errors
- `stateStream`: Stream of camera state changes

#### Methods

- `initialize({int? width, int? height, String defaultFilter})`: Initialize the camera
- `takePicture(String path)`: Take a picture and save it
- `startRecording(String path)`: Start recording video
- `stopRecording()`: Stop recording video
- `applyFilter(String filterType, [Map<String, Object?>? parameters])`: Apply a filter
- `dispose()`: Dispose of all camera resources

### CameraError

Represents a camera error with type and message.

### CameraState

Represents the current state of the camera.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

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
