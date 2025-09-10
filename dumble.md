package com.beauty.camera_plugin

import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.view.TextureRegistry

/\*\*

- Plugin chính để quản lý beauty camera và tương tác với Flutter.
- Class này đăng ký các API Pigeon và quản lý vòng đời của plugin.
  \*/
  class BeautyCameraPlugin : FlutterPlugin, ActivityAware {
  companion object {
  private const val TAG = "BeautyCameraPlugin"
  }

      private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null
      private var activityBinding: ActivityPluginBinding? = null
      private var binaryMessenger: BinaryMessenger? = null
      private var textureRegistry: TextureRegistry? = null

      // Implementations của các API Pigeon
      private var beautyCameraHostApiImpl: BeautyCameraHostApiImpl? = null
      private var cameraApiImpl: CameraApiImpl? = null

      // Flutter API handler để gửi thông báo từ native về Flutter
      private var beautyCameraFlutterApi: BeautyCameraFlutterApi? = null

      override fun onAttachedToEngine( flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
          Log.d(TAG, "onAttachedToEngine")
          this.flutterPluginBinding = flutterPluginBinding
          this.binaryMessenger = flutterPluginBinding.binaryMessenger
          this.textureRegistry = flutterPluginBinding.textureRegistry

          setupApis()
      }

      override fun onDetachedFromEngine( binding: FlutterPlugin.FlutterPluginBinding) {
          Log.d(TAG, "onDetachedFromEngine")
          tearDownApis()

          binaryMessenger = null
          textureRegistry = null
          flutterPluginBinding = null
      }

      override fun onAttachedToActivity(binding: ActivityPluginBinding) {
          Log.d(TAG, "onAttachedToActivity")
          activityBinding = binding

          // Đảm bảo rằng các implementers có context và activity
          beautyCameraHostApiImpl?.setActivityBinding(binding)
          cameraApiImpl?.setActivityBinding(binding)

          // Log để debug
          Log.d(TAG, "Activity attached: ${binding.activity.javaClass.simpleName}")
      }

      override fun onDetachedFromActivityForConfigChanges() {
          Log.d(TAG, "onDetachedFromActivityForConfigChanges")
          activityBinding = null
          beautyCameraHostApiImpl?.setActivityBinding(null)
          cameraApiImpl?.setActivityBinding(null)
      }

      override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
          Log.d(TAG, "onReattachedToActivityForConfigChanges")
          activityBinding = binding
          beautyCameraHostApiImpl?.setActivityBinding(binding)
          cameraApiImpl?.setActivityBinding(binding)
          Log.d(TAG, "Activity reattached: ${binding.activity.javaClass.simpleName}")
      }

      override fun onDetachedFromActivity() {
          Log.d(TAG, "onDetachedFromActivity")
          activityBinding = null
          beautyCameraHostApiImpl?.setActivityBinding(null)
          cameraApiImpl?.setActivityBinding(null)
      }

      private fun setupApis() {
          Log.d(TAG, "Setting up APIs")

          // Khởi tạo Flutter API để gửi events ngược về Flutter
          beautyCameraFlutterApi = BeautyCameraFlutterApi(binaryMessenger!!)

          // Khởi tạo filter processor - một instance duy nhất
          val filterProcessor = FilterProcessor()
          Log.d(TAG, "Created filter processor")

          // Khởi tạo camera manager với filter processor
          val cameraManager = BeautyCameraManager(
              textureRegistry = textureRegistry!!,
              flutterApi = beautyCameraFlutterApi!!,
              filterProcessor = filterProcessor
          )
          Log.d(TAG, "Created camera manager")

          // Setup các API implementers
          beautyCameraHostApiImpl = BeautyCameraHostApiImpl(
              cameraManager = cameraManager,
              filterProcessor = filterProcessor
          )

          cameraApiImpl = CameraApiImpl(
              cameraManager = cameraManager
          )
          Log.d(TAG, "Created API implementers")

          // Đăng ký các API với Pigeon
          binaryMessenger?.let { messenger ->
              BeautyCameraHostApi.setUp(messenger, beautyCameraHostApiImpl)
              CameraApi.setUp(messenger, cameraApiImpl)
              Log.d(TAG, "Registered APIs with Pigeon")
          }
      }

      private fun tearDownApis() {
          Log.d(TAG, "Tearing down APIs")

          // Hủy đăng ký các API
          binaryMessenger?.let { messenger ->
              BeautyCameraHostApi.setUp(messenger, null)
              CameraApi.setUp(messenger, null)
              Log.d(TAG, "Unregistered APIs from Pigeon")
          }

          // Giải phóng tài nguyên
          beautyCameraHostApiImpl?.dispose()
          cameraApiImpl?.dispose()
          Log.d(TAG, "Disposed API implementations")

          beautyCameraHostApiImpl = null
          cameraApiImpl = null
          beautyCameraFlutterApi = null
      }

  }
