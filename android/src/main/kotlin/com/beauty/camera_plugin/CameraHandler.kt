package com.beauty.camera_plugin

import android.annotation.SuppressLint
import android.content.Context
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture

class CameraHandler(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var preview: Preview? = null

    fun initialize(callback: () -> Unit) {
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            callback()
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("RestrictedApi")
    fun startCamera(surface: Surface) {
        val provider = cameraProvider ?: return

        val previewUseCase = Preview.Builder().build()
        // Hướng camera output vào Surface của OpenGL renderer
        previewUseCase.setSurfaceProvider { request ->
            request.provideSurface(surface, ContextCompat.getMainExecutor(context), {})
        }

        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, cameraSelector, previewUseCase)
        this.preview = previewUseCase
    }

    @SuppressLint("RestrictedApi")
    fun getPreviewSize(): PreviewSize? {
        val resolution = preview?.resolutionInfo?.resolution ?: return null
        return PreviewSize(width = resolution.width.toLong(), height = resolution.height.toLong())
    }

    fun dispose() {
        cameraProvider?.unbindAll()
        preview = null
    }
}