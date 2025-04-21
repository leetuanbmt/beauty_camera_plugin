package com.beauty.camera_plugin

import android.app.Activity
import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.view.TextureRegistry

/**
 * Plugin entry point for the Beauty Camera Plugin.
 * Handles initialization and lifecycle of the camera functionality.
 */
class BeautyCameraPlugin : FlutterPlugin, ActivityAware {
    private var activity: Activity? = null
    private var context: Context? = null
    private var messenger: BinaryMessenger? = null
    private var textureRegistry: TextureRegistry? = null
    private var hostApi: BeautyCameraHostApiImpl? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        messenger = binding.binaryMessenger
        textureRegistry = binding.textureRegistry
        context = binding.applicationContext
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        messenger = null
        textureRegistry = null
        context = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        setupHostApi()
    }

    override fun onDetachedFromActivity() {
        activity = null
        hostApi = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        setupHostApi()
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
        hostApi = null
    }

    private fun setupHostApi() {
        val activity = activity ?: return
        val messenger = messenger ?: return
        val textureRegistry = textureRegistry ?: return

        hostApi = BeautyCameraHostApiImpl(
            activity = activity,
            messenger = messenger,
            textureRegistry = textureRegistry
        )

        BeautyCameraHostApi.setUp(messenger, hostApi)
    }
} 