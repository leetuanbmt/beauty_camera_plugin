// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
// Autogenerated from Pigeon (v25.3.1), do not edit directly.
// See also: https://pub.dev/packages/pigeon

import Foundation

#if os(iOS)
  import Flutter
#elseif os(macOS)
  import FlutterMacOS
#else
  #error("Unsupported platform.")
#endif

/// Error class for passing custom error details to Dart side.
final class PigeonError: Error {
  let code: String
  let message: String?
  let details: Sendable?

  init(code: String, message: String?, details: Sendable?) {
    self.code = code
    self.message = message
    self.details = details
  }

  var localizedDescription: String {
    return
      "PigeonError(code: \(code), message: \(message ?? "<nil>"), details: \(details ?? "<nil>")"
  }
}

private func wrapResult(_ result: Any?) -> [Any?] {
  return [result]
}

private func wrapError(_ error: Any) -> [Any?] {
  if let pigeonError = error as? PigeonError {
    return [
      pigeonError.code,
      pigeonError.message,
      pigeonError.details,
    ]
  }
  if let flutterError = error as? FlutterError {
    return [
      flutterError.code,
      flutterError.message,
      flutterError.details,
    ]
  }
  return [
    "\(error)",
    "\(type(of: error))",
    "Stacktrace: \(Thread.callStackSymbols)",
  ]
}

private func createConnectionError(withChannelName channelName: String) -> PigeonError {
  return PigeonError(code: "channel-error", message: "Unable to establish connection on channel: '\(channelName)'.", details: "")
}

private func isNullish(_ value: Any?) -> Bool {
  return value is NSNull || value == nil
}

private func nilOrValue<T>(_ value: Any?) -> T? {
  if value is NSNull { return nil }
  return value as! T?
}

func deepEqualsBeautyCameraPlugin(_ lhs: Any?, _ rhs: Any?) -> Bool {
  let cleanLhs = nilOrValue(lhs) as Any?
  let cleanRhs = nilOrValue(rhs) as Any?
  switch (cleanLhs, cleanRhs) {
  case (nil, nil):
    return true

  case (nil, _), (_, nil):
    return false

  case is (Void, Void):
    return true

  case let (cleanLhsHashable, cleanRhsHashable) as (AnyHashable, AnyHashable):
    return cleanLhsHashable == cleanRhsHashable

  case let (cleanLhsArray, cleanRhsArray) as ([Any?], [Any?]):
    guard cleanLhsArray.count == cleanRhsArray.count else { return false }
    for (index, element) in cleanLhsArray.enumerated() {
      if !deepEqualsBeautyCameraPlugin(element, cleanRhsArray[index]) {
        return false
      }
    }
    return true

  case let (cleanLhsDictionary, cleanRhsDictionary) as ([AnyHashable: Any?], [AnyHashable: Any?]):
    guard cleanLhsDictionary.count == cleanRhsDictionary.count else { return false }
    for (key, cleanLhsValue) in cleanLhsDictionary {
      guard cleanRhsDictionary.index(forKey: key) != nil else { return false }
      if !deepEqualsBeautyCameraPlugin(cleanLhsValue, cleanRhsDictionary[key]!) {
        return false
      }
    }
    return true

  default:
    // Any other type shouldn't be able to be used with pigeon. File an issue if you find this to be untrue.
    return false
  }
}

func deepHashBeautyCameraPlugin(value: Any?, hasher: inout Hasher) {
  if let valueList = value as? [AnyHashable] {
     for item in valueList { deepHashBeautyCameraPlugin(value: item, hasher: &hasher) }
     return
  }

  if let valueDict = value as? [AnyHashable: AnyHashable] {
    for key in valueDict.keys { 
      hasher.combine(key)
      deepHashBeautyCameraPlugin(value: valueDict[key]!, hasher: &hasher)
    }
    return
  }

  if let hashableValue = value as? AnyHashable {
    hasher.combine(hashableValue.hashValue)
  }

  return hasher.combine(String(describing: value))
}

    

/// Represents the available filter types for camera effects
enum FilterType: Int {
  /// No filter applied
  case none = 0
  /// Beauty filter for skin smoothing
  case beauty = 1
  /// Vintage effect filter
  case vintage = 2
  /// Black and white filter
  case blackAndWhite = 3
  /// Custom filter with parameters
  case custom = 4
}

/// Represents possible camera error types
enum CameraErrorType: Int {
  /// Camera initialization failed
  case initializationFailed = 0
  /// Camera permission denied
  case permissionDenied = 1
  /// Camera hardware not available
  case hardwareNotAvailable = 2
  /// Invalid camera settings
  case invalidSettings = 3
  /// Recording failed
  case recordingFailed = 4
  /// Picture capture failed
  case captureFailed = 5
  /// Unknown error occurred
  case unknown = 6
}

/// Configuration settings for camera initialization
///
/// Generated class from Pigeon that represents data sent in messages.
struct CameraSettings: Hashable {
  /// The desired width of the camera preview in pixels
  /// If null, the default camera resolution will be used
  var width: Int64? = nil
  /// The desired height of the camera preview in pixels
  /// If null, the default camera resolution will be used
  var height: Int64? = nil


  // swift-format-ignore: AlwaysUseLowerCamelCase
  static func fromList(_ pigeonVar_list: [Any?]) -> CameraSettings? {
    let width: Int64? = nilOrValue(pigeonVar_list[0])
    let height: Int64? = nilOrValue(pigeonVar_list[1])

    return CameraSettings(
      width: width,
      height: height
    )
  }
  func toList() -> [Any?] {
    return [
      width,
      height,
    ]
  }
  static func == (lhs: CameraSettings, rhs: CameraSettings) -> Bool {
    return deepEqualsBeautyCameraPlugin(lhs.toList(), rhs.toList())  }
  func hash(into hasher: inout Hasher) {
    deepHashBeautyCameraPlugin(value: toList(), hasher: &hasher)
  }
}

/// Configuration for applying filters to the camera preview
///
/// Generated class from Pigeon that represents data sent in messages.
struct FilterConfig: Hashable {
  /// The type of filter to apply
  var filterType: String? = nil
  /// Additional parameters for the filter
  /// The structure depends on the filter type:
  /// - For beauty filter: {'smoothness': 0.0-1.0, 'brightness': 0.0-1.0}
  /// - For vintage: {'intensity': 0.0-1.0}
  /// - For custom: varies based on implementation
  var parameters: [String: Any?]? = nil


  // swift-format-ignore: AlwaysUseLowerCamelCase
  static func fromList(_ pigeonVar_list: [Any?]) -> FilterConfig? {
    let filterType: String? = nilOrValue(pigeonVar_list[0])
    let parameters: [String: Any?]? = nilOrValue(pigeonVar_list[1])

    return FilterConfig(
      filterType: filterType,
      parameters: parameters
    )
  }
  func toList() -> [Any?] {
    return [
      filterType,
      parameters,
    ]
  }
  static func == (lhs: FilterConfig, rhs: FilterConfig) -> Bool {
    return deepEqualsBeautyCameraPlugin(lhs.toList(), rhs.toList())  }
  func hash(into hasher: inout Hasher) {
    deepHashBeautyCameraPlugin(value: toList(), hasher: &hasher)
  }
}

/// Represents a camera error with type and message
///
/// Generated class from Pigeon that represents data sent in messages.
struct CameraError: Hashable {
  /// The type of error that occurred
  var type: CameraErrorType
  /// A human-readable description of the error
  var message: String


  // swift-format-ignore: AlwaysUseLowerCamelCase
  static func fromList(_ pigeonVar_list: [Any?]) -> CameraError? {
    let type = pigeonVar_list[0] as! CameraErrorType
    let message = pigeonVar_list[1] as! String

    return CameraError(
      type: type,
      message: message
    )
  }
  func toList() -> [Any?] {
    return [
      type,
      message,
    ]
  }
  static func == (lhs: CameraError, rhs: CameraError) -> Bool {
    return deepEqualsBeautyCameraPlugin(lhs.toList(), rhs.toList())  }
  func hash(into hasher: inout Hasher) {
    deepHashBeautyCameraPlugin(value: toList(), hasher: &hasher)
  }
}

private class BeautyCameraPluginPigeonCodecReader: FlutterStandardReader {
  override func readValue(ofType type: UInt8) -> Any? {
    switch type {
    case 129:
      let enumResultAsInt: Int? = nilOrValue(self.readValue() as! Int?)
      if let enumResultAsInt = enumResultAsInt {
        return FilterType(rawValue: enumResultAsInt)
      }
      return nil
    case 130:
      let enumResultAsInt: Int? = nilOrValue(self.readValue() as! Int?)
      if let enumResultAsInt = enumResultAsInt {
        return CameraErrorType(rawValue: enumResultAsInt)
      }
      return nil
    case 131:
      return CameraSettings.fromList(self.readValue() as! [Any?])
    case 132:
      return FilterConfig.fromList(self.readValue() as! [Any?])
    case 133:
      return CameraError.fromList(self.readValue() as! [Any?])
    default:
      return super.readValue(ofType: type)
    }
  }
}

private class BeautyCameraPluginPigeonCodecWriter: FlutterStandardWriter {
  override func writeValue(_ value: Any) {
    if let value = value as? FilterType {
      super.writeByte(129)
      super.writeValue(value.rawValue)
    } else if let value = value as? CameraErrorType {
      super.writeByte(130)
      super.writeValue(value.rawValue)
    } else if let value = value as? CameraSettings {
      super.writeByte(131)
      super.writeValue(value.toList())
    } else if let value = value as? FilterConfig {
      super.writeByte(132)
      super.writeValue(value.toList())
    } else if let value = value as? CameraError {
      super.writeByte(133)
      super.writeValue(value.toList())
    } else {
      super.writeValue(value)
    }
  }
}

private class BeautyCameraPluginPigeonCodecReaderWriter: FlutterStandardReaderWriter {
  override func reader(with data: Data) -> FlutterStandardReader {
    return BeautyCameraPluginPigeonCodecReader(data: data)
  }

  override func writer(with data: NSMutableData) -> FlutterStandardWriter {
    return BeautyCameraPluginPigeonCodecWriter(data: data)
  }
}

class BeautyCameraPluginPigeonCodec: FlutterStandardMessageCodec, @unchecked Sendable {
  static let shared = BeautyCameraPluginPigeonCodec(readerWriter: BeautyCameraPluginPigeonCodecReaderWriter())
}


/// Host API for camera operations
///
/// Generated protocol from Pigeon that represents a handler of messages from Flutter.
protocol BeautyCameraHostApi {
  /// Initializes the camera with the specified settings
  ///
  /// Returns a Future that completes when the camera is initialized
  /// Throws [CameraException] if initialization fails
  func initializeCamera(settings: CameraSettings, completion: @escaping (Result<Void, Error>) -> Void)
  /// Creates a new preview texture and returns its ID
  ///
  /// Returns the texture ID that can be used to display the camera preview
  /// Returns null if texture creation fails
  func createPreviewTexture(completion: @escaping (Result<Int64?, Error>) -> Void)
  /// Starts the camera preview on the specified texture
  ///
  /// [textureId] must be a valid texture ID returned by [createPreviewTexture]
  func startPreview(textureId: Int64, completion: @escaping (Result<Void, Error>) -> Void)
  /// Stops the camera preview
  func stopPreview(completion: @escaping (Result<Void, Error>) -> Void)
  /// Disposes of all camera resources
  func disposeCamera(completion: @escaping (Result<Void, Error>) -> Void)
  /// Takes a picture and saves it to the specified path
  ///
  /// [path] must be a valid file path where the image will be saved
  func takePicture(path: String, completion: @escaping (Result<Void, Error>) -> Void)
  /// Starts recording video to the specified path
  ///
  /// [path] must be a valid file path where the video will be saved
  func startRecording(path: String, completion: @escaping (Result<Void, Error>) -> Void)
  /// Stops the current video recording
  func stopRecording(completion: @escaping (Result<Void, Error>) -> Void)
  /// Applies a filter to the camera preview
  ///
  /// [textureId] must be a valid texture ID
  /// [filterConfig] specifies the filter type and parameters
  func applyFilter(textureId: Int64, filterConfig: FilterConfig, completion: @escaping (Result<Void, Error>) -> Void)
}

/// Generated setup class from Pigeon to handle messages through the `binaryMessenger`.
class BeautyCameraHostApiSetup {
  static var codec: FlutterStandardMessageCodec { BeautyCameraPluginPigeonCodec.shared }
  /// Sets up an instance of `BeautyCameraHostApi` to handle messages through the `binaryMessenger`.
  static func setUp(binaryMessenger: FlutterBinaryMessenger, api: BeautyCameraHostApi?, messageChannelSuffix: String = "") {
    let channelSuffix = messageChannelSuffix.count > 0 ? ".\(messageChannelSuffix)" : ""
    /// Initializes the camera with the specified settings
    ///
    /// Returns a Future that completes when the camera is initialized
    /// Throws [CameraException] if initialization fails
    let initializeCameraChannel = FlutterBasicMessageChannel(name: "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraHostApi.initializeCamera\(channelSuffix)", binaryMessenger: binaryMessenger, codec: codec)
    if let api = api {
      initializeCameraChannel.setMessageHandler { message, reply in
        let args = message as! [Any?]
        let settingsArg = args[0] as! CameraSettings
        api.initializeCamera(settings: settingsArg) { result in
          switch result {
          case .success:
            reply(wrapResult(nil))
          case .failure(let error):
            reply(wrapError(error))
          }
        }
      }
    } else {
      initializeCameraChannel.setMessageHandler(nil)
    }
    /// Creates a new preview texture and returns its ID
    ///
    /// Returns the texture ID that can be used to display the camera preview
    /// Returns null if texture creation fails
    let createPreviewTextureChannel = FlutterBasicMessageChannel(name: "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraHostApi.createPreviewTexture\(channelSuffix)", binaryMessenger: binaryMessenger, codec: codec)
    if let api = api {
      createPreviewTextureChannel.setMessageHandler { _, reply in
        api.createPreviewTexture { result in
          switch result {
          case .success(let res):
            reply(wrapResult(res))
          case .failure(let error):
            reply(wrapError(error))
          }
        }
      }
    } else {
      createPreviewTextureChannel.setMessageHandler(nil)
    }
    /// Starts the camera preview on the specified texture
    ///
    /// [textureId] must be a valid texture ID returned by [createPreviewTexture]
    let startPreviewChannel = FlutterBasicMessageChannel(name: "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraHostApi.startPreview\(channelSuffix)", binaryMessenger: binaryMessenger, codec: codec)
    if let api = api {
      startPreviewChannel.setMessageHandler { message, reply in
        let args = message as! [Any?]
        let textureIdArg = args[0] as! Int64
        api.startPreview(textureId: textureIdArg) { result in
          switch result {
          case .success:
            reply(wrapResult(nil))
          case .failure(let error):
            reply(wrapError(error))
          }
        }
      }
    } else {
      startPreviewChannel.setMessageHandler(nil)
    }
    /// Stops the camera preview
    let stopPreviewChannel = FlutterBasicMessageChannel(name: "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraHostApi.stopPreview\(channelSuffix)", binaryMessenger: binaryMessenger, codec: codec)
    if let api = api {
      stopPreviewChannel.setMessageHandler { _, reply in
        api.stopPreview { result in
          switch result {
          case .success:
            reply(wrapResult(nil))
          case .failure(let error):
            reply(wrapError(error))
          }
        }
      }
    } else {
      stopPreviewChannel.setMessageHandler(nil)
    }
    /// Disposes of all camera resources
    let disposeCameraChannel = FlutterBasicMessageChannel(name: "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraHostApi.disposeCamera\(channelSuffix)", binaryMessenger: binaryMessenger, codec: codec)
    if let api = api {
      disposeCameraChannel.setMessageHandler { _, reply in
        api.disposeCamera { result in
          switch result {
          case .success:
            reply(wrapResult(nil))
          case .failure(let error):
            reply(wrapError(error))
          }
        }
      }
    } else {
      disposeCameraChannel.setMessageHandler(nil)
    }
    /// Takes a picture and saves it to the specified path
    ///
    /// [path] must be a valid file path where the image will be saved
    let takePictureChannel = FlutterBasicMessageChannel(name: "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraHostApi.takePicture\(channelSuffix)", binaryMessenger: binaryMessenger, codec: codec)
    if let api = api {
      takePictureChannel.setMessageHandler { message, reply in
        let args = message as! [Any?]
        let pathArg = args[0] as! String
        api.takePicture(path: pathArg) { result in
          switch result {
          case .success:
            reply(wrapResult(nil))
          case .failure(let error):
            reply(wrapError(error))
          }
        }
      }
    } else {
      takePictureChannel.setMessageHandler(nil)
    }
    /// Starts recording video to the specified path
    ///
    /// [path] must be a valid file path where the video will be saved
    let startRecordingChannel = FlutterBasicMessageChannel(name: "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraHostApi.startRecording\(channelSuffix)", binaryMessenger: binaryMessenger, codec: codec)
    if let api = api {
      startRecordingChannel.setMessageHandler { message, reply in
        let args = message as! [Any?]
        let pathArg = args[0] as! String
        api.startRecording(path: pathArg) { result in
          switch result {
          case .success:
            reply(wrapResult(nil))
          case .failure(let error):
            reply(wrapError(error))
          }
        }
      }
    } else {
      startRecordingChannel.setMessageHandler(nil)
    }
    /// Stops the current video recording
    let stopRecordingChannel = FlutterBasicMessageChannel(name: "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraHostApi.stopRecording\(channelSuffix)", binaryMessenger: binaryMessenger, codec: codec)
    if let api = api {
      stopRecordingChannel.setMessageHandler { _, reply in
        api.stopRecording { result in
          switch result {
          case .success:
            reply(wrapResult(nil))
          case .failure(let error):
            reply(wrapError(error))
          }
        }
      }
    } else {
      stopRecordingChannel.setMessageHandler(nil)
    }
    /// Applies a filter to the camera preview
    ///
    /// [textureId] must be a valid texture ID
    /// [filterConfig] specifies the filter type and parameters
    let applyFilterChannel = FlutterBasicMessageChannel(name: "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraHostApi.applyFilter\(channelSuffix)", binaryMessenger: binaryMessenger, codec: codec)
    if let api = api {
      applyFilterChannel.setMessageHandler { message, reply in
        let args = message as! [Any?]
        let textureIdArg = args[0] as! Int64
        let filterConfigArg = args[1] as! FilterConfig
        api.applyFilter(textureId: textureIdArg, filterConfig: filterConfigArg) { result in
          switch result {
          case .success:
            reply(wrapResult(nil))
          case .failure(let error):
            reply(wrapError(error))
          }
        }
      }
    } else {
      applyFilterChannel.setMessageHandler(nil)
    }
  }
}
/// Flutter API for camera events
///
/// Generated protocol from Pigeon that represents Flutter messages that can be called from Swift.
protocol BeautyCameraFlutterApiProtocol {
  /// Called when the camera is successfully initialized
  ///
  /// [textureId] is the ID of the preview texture
  /// [width] and [height] are the actual dimensions of the camera preview
  func onCameraInitialized(textureId textureIdArg: Int64, width widthArg: Int64, height heightArg: Int64, completion: @escaping (Result<Void, PigeonError>) -> Void)
  /// Called when a picture has been taken and saved
  ///
  /// [path] is the path where the image was saved
  func onTakePictureCompleted(path pathArg: String, completion: @escaping (Result<Void, PigeonError>) -> Void)
  /// Called when video recording has started
  func onRecordingStarted(completion: @escaping (Result<Void, PigeonError>) -> Void)
  /// Called when video recording has stopped
  ///
  /// [path] is the path where the video was saved
  func onRecordingStopped(path pathArg: String, completion: @escaping (Result<Void, PigeonError>) -> Void)
  /// Called when a camera error occurs
  ///
  /// [error] contains both the type and message of the error
  func onCameraError(error errorArg: CameraError, completion: @escaping (Result<Void, PigeonError>) -> Void)
}
class BeautyCameraFlutterApi: BeautyCameraFlutterApiProtocol {
  private let binaryMessenger: FlutterBinaryMessenger
  private let messageChannelSuffix: String
  init(binaryMessenger: FlutterBinaryMessenger, messageChannelSuffix: String = "") {
    self.binaryMessenger = binaryMessenger
    self.messageChannelSuffix = messageChannelSuffix.count > 0 ? ".\(messageChannelSuffix)" : ""
  }
  var codec: BeautyCameraPluginPigeonCodec {
    return BeautyCameraPluginPigeonCodec.shared
  }
  /// Called when the camera is successfully initialized
  ///
  /// [textureId] is the ID of the preview texture
  /// [width] and [height] are the actual dimensions of the camera preview
  func onCameraInitialized(textureId textureIdArg: Int64, width widthArg: Int64, height heightArg: Int64, completion: @escaping (Result<Void, PigeonError>) -> Void) {
    let channelName: String = "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraFlutterApi.onCameraInitialized\(messageChannelSuffix)"
    let channel = FlutterBasicMessageChannel(name: channelName, binaryMessenger: binaryMessenger, codec: codec)
    channel.sendMessage([textureIdArg, widthArg, heightArg] as [Any?]) { response in
      guard let listResponse = response as? [Any?] else {
        completion(.failure(createConnectionError(withChannelName: channelName)))
        return
      }
      if listResponse.count > 1 {
        let code: String = listResponse[0] as! String
        let message: String? = nilOrValue(listResponse[1])
        let details: String? = nilOrValue(listResponse[2])
        completion(.failure(PigeonError(code: code, message: message, details: details)))
      } else {
        completion(.success(()))
      }
    }
  }
  /// Called when a picture has been taken and saved
  ///
  /// [path] is the path where the image was saved
  func onTakePictureCompleted(path pathArg: String, completion: @escaping (Result<Void, PigeonError>) -> Void) {
    let channelName: String = "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraFlutterApi.onTakePictureCompleted\(messageChannelSuffix)"
    let channel = FlutterBasicMessageChannel(name: channelName, binaryMessenger: binaryMessenger, codec: codec)
    channel.sendMessage([pathArg] as [Any?]) { response in
      guard let listResponse = response as? [Any?] else {
        completion(.failure(createConnectionError(withChannelName: channelName)))
        return
      }
      if listResponse.count > 1 {
        let code: String = listResponse[0] as! String
        let message: String? = nilOrValue(listResponse[1])
        let details: String? = nilOrValue(listResponse[2])
        completion(.failure(PigeonError(code: code, message: message, details: details)))
      } else {
        completion(.success(()))
      }
    }
  }
  /// Called when video recording has started
  func onRecordingStarted(completion: @escaping (Result<Void, PigeonError>) -> Void) {
    let channelName: String = "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraFlutterApi.onRecordingStarted\(messageChannelSuffix)"
    let channel = FlutterBasicMessageChannel(name: channelName, binaryMessenger: binaryMessenger, codec: codec)
    channel.sendMessage(nil) { response in
      guard let listResponse = response as? [Any?] else {
        completion(.failure(createConnectionError(withChannelName: channelName)))
        return
      }
      if listResponse.count > 1 {
        let code: String = listResponse[0] as! String
        let message: String? = nilOrValue(listResponse[1])
        let details: String? = nilOrValue(listResponse[2])
        completion(.failure(PigeonError(code: code, message: message, details: details)))
      } else {
        completion(.success(()))
      }
    }
  }
  /// Called when video recording has stopped
  ///
  /// [path] is the path where the video was saved
  func onRecordingStopped(path pathArg: String, completion: @escaping (Result<Void, PigeonError>) -> Void) {
    let channelName: String = "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraFlutterApi.onRecordingStopped\(messageChannelSuffix)"
    let channel = FlutterBasicMessageChannel(name: channelName, binaryMessenger: binaryMessenger, codec: codec)
    channel.sendMessage([pathArg] as [Any?]) { response in
      guard let listResponse = response as? [Any?] else {
        completion(.failure(createConnectionError(withChannelName: channelName)))
        return
      }
      if listResponse.count > 1 {
        let code: String = listResponse[0] as! String
        let message: String? = nilOrValue(listResponse[1])
        let details: String? = nilOrValue(listResponse[2])
        completion(.failure(PigeonError(code: code, message: message, details: details)))
      } else {
        completion(.success(()))
      }
    }
  }
  /// Called when a camera error occurs
  ///
  /// [error] contains both the type and message of the error
  func onCameraError(error errorArg: CameraError, completion: @escaping (Result<Void, PigeonError>) -> Void) {
    let channelName: String = "dev.flutter.pigeon.com.beauty.camera_plugin.BeautyCameraFlutterApi.onCameraError\(messageChannelSuffix)"
    let channel = FlutterBasicMessageChannel(name: channelName, binaryMessenger: binaryMessenger, codec: codec)
    channel.sendMessage([errorArg] as [Any?]) { response in
      guard let listResponse = response as? [Any?] else {
        completion(.failure(createConnectionError(withChannelName: channelName)))
        return
      }
      if listResponse.count > 1 {
        let code: String = listResponse[0] as! String
        let message: String? = nilOrValue(listResponse[1])
        let details: String? = nilOrValue(listResponse[2])
        completion(.failure(PigeonError(code: code, message: message, details: details)))
      } else {
        completion(.success(()))
      }
    }
  }
}
