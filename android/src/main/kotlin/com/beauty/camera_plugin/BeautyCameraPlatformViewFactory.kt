package com.beauty.camera_plugin

import android.content.Context
import android.view.View
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

/**
 * Factory for creating BeautyCameraPlatformView instances.
 */
class BeautyCameraPlatformViewFactory : PlatformViewFactory {
    private val messenger: BinaryMessenger
    private val registrar: Registrar?
    
    /**
     * Constructor using BinaryMessenger for new embedding API
     */
    constructor(messenger: BinaryMessenger) : super(StandardMessageCodec.INSTANCE) {
        this.messenger = messenger
        this.registrar = null
    }
    
    /**
     * Constructor using Registrar for old embedding API (pre-Flutter 1.12)
     */
    constructor(registrar: Registrar) : super(StandardMessageCodec.INSTANCE) {
        this.messenger = registrar.messenger()
        this.registrar = registrar
    }
    
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val creationParams = args as? Map<String, Any>
        return BeautyCameraPlatformView(context, viewId, creationParams, messenger)
    }
}

/**
 * PlatformView implementation for the camera preview.
 */
class BeautyCameraPlatformView(
    private val context: Context,
    id: Int,
    creationParams: Map<String, Any>?,
    messenger: BinaryMessenger
) : PlatformView {
    
    private val previewView = androidx.camera.view.PreviewView(context)
    private val methodChannel = MethodChannel(messenger, "com.beauty.camera_plugin/cameraview_$id")
    
    init {
        // Set up the camera view
        previewView.implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
        
        // Setup camera when view is created
        initializeCamera(creationParams)
    }
    
    private fun initializeCamera(params: Map<String, Any>?) {
        // Here you would initialize the camera using CameraXManager
        // For now, this is a simplified implementation
        // In a real implementation, you would use the CameraXManager class
    }
    
    override fun getView(): View {
        return previewView
    }
    
    override fun dispose() {
        // Clean up resources
        methodChannel.setMethodCallHandler(null)
    }
} 