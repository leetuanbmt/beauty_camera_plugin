package com.beauty.camera_plugin

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.PluginRegistry

/**
 * Plugin registrant for BeautyCameraPlugin
 * 
 * This class is responsible for registering the BeautyCameraPlugin with a Flutter application.
 * This is necessary when using the legacy embedding system (pre-Flutter 1.12).
 */
object BeautyCameraPluginRegistrant {
    @JvmStatic
    fun registerWith(registry: PluginRegistry.Registrar) {
        val plugin = BeautyCameraPlugin()
        
        // Set up the plugin
        val binaryMessenger = registry.messenger()
        BeautyCameraHostApi.setUp(binaryMessenger, plugin)
        
        // Register the platform view factory
        registry.platformViewRegistry().registerViewFactory(
            "com.beauty.camera_plugin/cameraview",
            BeautyCameraPlatformViewFactory(binaryMessenger)
        )
    }
    
    @JvmStatic
    fun registerWith(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        // Register the platform view factory for camera preview
        flutterPluginBinding
            .platformViewRegistry
            .registerViewFactory(
                "com.beauty.camera_plugin/cameraview", 
                BeautyCameraPlatformViewFactory(flutterPluginBinding.binaryMessenger)
            )
    }
} 