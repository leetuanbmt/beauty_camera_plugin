package com.beauty.camera_plugin

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.PluginRegistry
import io.flutter.view.TextureRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar

/** Implementation of BeautyCameraPlugin that handles plugin registration and lifecycle */
class BeautyCameraPluginImpl: FlutterPlugin, ActivityAware {
    private var pluginBinding: FlutterPlugin.FlutterPluginBinding? = null
    private var activityBinding: ActivityPluginBinding? = null
    private var api: BeautyCameraHostApiImpl? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var textureRegistry: TextureRegistry? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        // Always run plugin setup on main thread
        runOnMainThread {
            pluginBinding = binding
            textureRegistry = binding.textureRegistry
            setupPlugin()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        runOnMainThread {
            pluginBinding = null
            textureRegistry = null
            teardownPlugin()
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        runOnMainThread {
            activityBinding = binding
            setupPlugin()
            // Register permission result listener
            api?.let { binding.addRequestPermissionsResultListener(it) }
        }
    }

    override fun onDetachedFromActivity() {
        runOnMainThread {
            // Unregister permission result listener
            api?.let { activityBinding?.removeRequestPermissionsResultListener(it) }
            activityBinding = null
            teardownPlugin()
        }
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        runOnMainThread {
            activityBinding = binding
            setupPlugin()
            // Re-register permission result listener
            api?.let { binding.addRequestPermissionsResultListener(it) }
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {
        runOnMainThread {
            // Unregister permission result listener
            api?.let { activityBinding?.removeRequestPermissionsResultListener(it) }
            activityBinding = null
            teardownPlugin()
        }
    }

    private fun setupPlugin() {
        val binding = pluginBinding ?: return
        val activity = activityBinding?.activity

        api = BeautyCameraHostApiImpl(
            binding.applicationContext,
            activity,
            binding.binaryMessenger,
            textureRegistry,
            mainHandler
        )
        BeautyCameraHostApi.setUp(binding.binaryMessenger, api)
        
        // Register permission result listener if activity is attached
        activityBinding?.let { binding ->
            api?.let { binding.addRequestPermissionsResultListener(it) }
        }
    }

    private fun teardownPlugin() {
        val messenger = pluginBinding?.binaryMessenger
        if (api != null && messenger != null) {
            BeautyCameraHostApi.setUp(messenger, null)
            api = null
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val impl = BeautyCameraPluginImpl()
            val mainHandler = Handler(Looper.getMainLooper())
            
            mainHandler.post {
                val api = BeautyCameraHostApiImpl(
                    registrar.context(),
                    registrar.activity(),
                    registrar.messenger(),
                    registrar.textures(),
                    mainHandler
                )
                BeautyCameraHostApi.setUp(registrar.messenger(), api)
                // Register permission result listener for legacy version
                registrar.addRequestPermissionsResultListener(api)
            }
        }
    }
} 