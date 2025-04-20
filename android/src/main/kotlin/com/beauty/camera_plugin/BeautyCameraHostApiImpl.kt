package com.beauty.camera_plugin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.PluginRegistry
import io.flutter.view.TextureRegistry
import java.io.File
import java.util.concurrent.Semaphore

/** Implementation of BeautyCameraHostApi that handles camera operations */
class BeautyCameraHostApiImpl(
    private val context: Context,
    private val activity: Activity?,
    private val messenger: BinaryMessenger,
    private val textureRegistry: TextureRegistry?,
    private val mainHandler: Handler,
) : BeautyCameraHostApi, PluginRegistry.RequestPermissionsResultListener {
    private val TAG = "BeautyCameraHostApiImpl"
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private val cameraOpenCloseLock = Semaphore(1)
    private var flutterApi: BeautyCameraFlutterApi? = null
    private var surfaceTextureEntry: TextureRegistry.SurfaceTextureEntry? = null
    private var previewSurface: Surface? = null
    private var currentTextureId: Long = -1
    private var pendingPermissionCallback: ((Result<Unit>) -> Unit)? = null
    private var permissionsGranted = false
    private var settings: CameraSettings? = null
    private val CAMERA_PERMISSION_REQUEST_CODE = 9796

    init {
        flutterApi = BeautyCameraFlutterApi(messenger)
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        
        // Chỉ yêu cầu WRITE_EXTERNAL_STORAGE cho Android 9 (API 28) trở xuống
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        return permissions.toTypedArray()
    }

    private fun getMissingPermissions(): Array<String> {
        return getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    private fun checkAndRequestPermissions(callback: (Result<Unit>) -> Unit): Boolean {
        val missingPermissions = getMissingPermissions()
        
        return if (missingPermissions.isEmpty()) {
            Log.d(TAG, "All permissions are granted")
            true
        } else {
            Log.d(TAG, "Missing permissions: ${missingPermissions.joinToString()}")
            pendingPermissionCallback = callback
            requestPermissions(missingPermissions)
            false
        }
    }

    private fun requestPermissions(permissions: Array<String>) {
        Log.d(TAG, "Requesting permissions: ${permissions.joinToString()}")
        activity?.let {
            ActivityCompat.requestPermissions(
                it,
                permissions,
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, initialize camera
                pendingPermissionCallback?.let { callback ->
                    initializeCameraImpl(CameraSettings(), callback)
                }
            } else {
                // Permission denied
                pendingPermissionCallback?.let { callback ->
                    callback(Result.failure(FlutterError(
                        CameraErrorType.PERMISSION_DENIED.name,
                        "Camera permission denied",
                        null
                    )))
                }
            }
            pendingPermissionCallback = null
            return true
        }
        return false
    }

    override fun initializeCamera(settings: CameraSettings, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Initializing camera with settings: $settings")
        
        // Store callback for permission result
        pendingPermissionCallback = callback

        // Check camera permission
        if (activity == null) {
            callback(Result.failure(FlutterError(
                "no_activity", 
                "Activity is null, cannot request camera permission",
                null
            )))
            return
        }

        if (!hasCameraPermission()) {
            requestCameraPermission()
            return
        }

        // Permission granted, proceed with camera initialization
        initializeCameraImpl(settings, callback)
    }

    private fun startCameraInitialization(settings: CameraSettings, callback: (Result<Unit>) -> Unit) {
        Log.d(TAG, "Starting camera initialization")
        startBackgroundThread()
        
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList[0] // Using first camera by default
            Log.d(TAG, "Opening camera with id: $cameraId")

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "Camera opened successfully")
                    cameraDevice = camera
                    cameraOpenCloseLock.release()
                    callback(Result.success(Unit))
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.e(TAG, "Camera disconnected")
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null
                    callback(Result.failure(FlutterError(
                        CameraErrorType.HARDWARE_NOT_AVAILABLE.name,
                        "Camera disconnected",
                        null
                    )))
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null
                    callback(Result.failure(FlutterError(
                        CameraErrorType.INITIALIZATION_FAILED.name,
                        "Failed to open camera: $error",
                        null
                    )))
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access exception", e)
            callback(Result.failure(FlutterError(
                CameraErrorType.INITIALIZATION_FAILED.name,
                "Failed to access camera: ${e.message}",
                null
            )))
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception", e)
            callback(Result.failure(FlutterError(
                CameraErrorType.PERMISSION_DENIED.name,
                "Camera permissions not granted: ${e.message}",
                null
            )))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception during camera initialization", e)
            callback(Result.failure(FlutterError(
                CameraErrorType.INITIALIZATION_FAILED.name,
                "Unexpected error: ${e.message}",
                null
            )))
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    override fun createPreviewTexture(callback: (Result<Long?>) -> Unit) {
        if (activity == null) {
            callback(Result.failure(FlutterError(
                "no_activity",
                "Activity is null, cannot create texture",
                null
            )))
            return
        }
        if (textureRegistry == null) {
            callback(Result.failure(FlutterError(
                "no_texture_registry",
                "TextureRegistry is null, cannot create texture",
                null
            )))
            return
        }
        createTextureOnMainThread(callback)
    }

    private fun createTextureOnMainThread(callback: (Result<Long?>) -> Unit) {
        runOnMainThread {
            try {
                // Create a new SurfaceTexture using the TextureRegistry
                surfaceTextureEntry = textureRegistry?.createSurfaceTexture()
                currentTextureId = surfaceTextureEntry?.id() ?: -1L

                if (currentTextureId == -1L) {
                    throw IllegalStateException("Failed to create texture")
                }

                // Get the SurfaceTexture and create a Surface
                val surfaceTexture = surfaceTextureEntry?.surfaceTexture()
                surfaceTexture?.setDefaultBufferSize(
                    settings?.width?.toInt() ?: 1280,
                    settings?.height?.toInt() ?: 720
                )
                previewSurface = Surface(surfaceTexture)

                Log.d(TAG, "Successfully created texture with id: $currentTextureId")
                callback(Result.success(currentTextureId))
            } catch (e: Exception) {
                val error = FlutterError(
                    "texture_creation_failed",
                    "Failed to create texture on main thread: ${e.message}",
                    null
                )
                Log.e(TAG, "Failed to create texture", e)
                callback(Result.failure(error))
            }
        }
    }

    override fun startPreview(textureId: Long, callback: (Result<Unit>) -> Unit) {
        if (cameraDevice == null || previewSurface == null) {
            callback(Result.failure(FlutterError(
                CameraErrorType.INITIALIZATION_FAILED.name,
                "Camera or preview surface not initialized",
                null
            )))
            return
        }

        try {
            val surfaces = listOf(previewSurface)
            
            cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        val previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        previewRequestBuilder?.addTarget(previewSurface!!)
                        val previewRequest = previewRequestBuilder?.build()
                        session.setRepeatingRequest(previewRequest!!, null, backgroundHandler)
                        callback(Result.success(Unit))
                    } catch (e: CameraAccessException) {
                        callback(Result.failure(FlutterError(
                            CameraErrorType.INITIALIZATION_FAILED.name,
                            "Failed to start camera preview: ${e.message}",
                            null
                        )))
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    callback(Result.failure(FlutterError(
                        CameraErrorType.INITIALIZATION_FAILED.name,
                        "Failed to configure camera session",
                        null
                    )))
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            callback(Result.failure(FlutterError(
                CameraErrorType.INITIALIZATION_FAILED.name,
                "Failed to create camera session: ${e.message}",
                null
            )))
        }
    }

    override fun stopPreview(callback: (Result<Unit>) -> Unit) {
        try {
            captureSession?.stopRepeating()
            captureSession?.close()
            captureSession = null
            callback(Result.success(Unit))
        } catch (e: CameraAccessException) {
            callback(Result.failure(FlutterError(
                CameraErrorType.UNKNOWN.name,
                "Failed to stop preview: ${e.message}",
                null
            )))
        }
    }

    override fun disposeCamera(callback: (Result<Unit>) -> Unit) {
        try {
            cameraOpenCloseLock.acquire()
            runOnMainThread {
                surfaceTextureEntry?.release()
                surfaceTextureEntry = null
                previewSurface?.release()
                previewSurface = null
            }
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            mediaRecorder?.release()
            mediaRecorder = null
            stopBackgroundThread()
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(FlutterError(
                CameraErrorType.UNKNOWN.name,
                "Failed to dispose camera: ${e.message}",
                null
            )))
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    override fun takePicture(path: String, callback: (Result<Unit>) -> Unit) {
        if (cameraDevice == null) {
            callback(Result.failure(FlutterError(
                CameraErrorType.INITIALIZATION_FAILED.name,
                "Camera not initialized",
                null
            )))
            return
        }

        try {
            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)
            
            val surfaces = listOf(imageReader?.surface)
            
            cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        val captureBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                        captureBuilder?.addTarget(imageReader!!.surface)
                        
                        session.capture(captureBuilder?.build()!!, object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult
                            ) {
                                flutterApi?.onTakePictureCompleted(path) { result ->
                                    if (result.isSuccess) {
                                        callback(Result.success(Unit))
                                    } else {
                                        callback(Result.failure(FlutterError(
                                            CameraErrorType.CAPTURE_FAILED.name,
                                            "Failed to save picture",
                                            null
                                        )))
                                    }
                                }
                            }
                        }, backgroundHandler)
                    } catch (e: CameraAccessException) {
                        callback(Result.failure(FlutterError(
                            CameraErrorType.CAPTURE_FAILED.name,
                            "Failed to take picture: ${e.message}",
                            null
                        )))
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    callback(Result.failure(FlutterError(
                        CameraErrorType.CAPTURE_FAILED.name,
                        "Failed to configure camera for picture",
                        null
                    )))
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            callback(Result.failure(FlutterError(
                CameraErrorType.CAPTURE_FAILED.name,
                "Failed to setup picture capture: ${e.message}",
                null
            )))
        }
    }

    override fun startRecording(path: String, callback: (Result<Unit>) -> Unit) {
        if (cameraDevice == null) {
            callback(Result.failure(FlutterError(
                CameraErrorType.INITIALIZATION_FAILED.name,
                "Camera not initialized",
                null
            )))
            return
        }

        try {
            mediaRecorder = MediaRecorder().apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(1920, 1080)
                setVideoFrameRate(30)
                setOutputFile(path)
                prepare()
            }

            val surfaces = listOf(mediaRecorder?.surface)

            cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        val recordBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                        recordBuilder?.addTarget(mediaRecorder!!.surface)
                        session.setRepeatingRequest(recordBuilder?.build()!!, null, backgroundHandler)
                        mediaRecorder?.start()
                        flutterApi?.onRecordingStarted { result ->
                            if (result.isSuccess) {
                                callback(Result.success(Unit))
                            } else {
                                callback(Result.failure(FlutterError(
                                    CameraErrorType.RECORDING_FAILED.name,
                                    "Failed to start recording",
                                    null
                                )))
                            }
                        }
                    } catch (e: Exception) {
                        callback(Result.failure(FlutterError(
                            CameraErrorType.RECORDING_FAILED.name,
                            "Failed to start video recording: ${e.message}",
                            null
                        )))
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    callback(Result.failure(FlutterError(
                        CameraErrorType.RECORDING_FAILED.name,
                        "Failed to configure camera for recording",
                        null
                    )))
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            callback(Result.failure(FlutterError(
                CameraErrorType.RECORDING_FAILED.name,
                "Failed to setup video recording: ${e.message}",
                null
            )))
        }
    }

    override fun stopRecording(callback: (Result<Unit>) -> Unit) {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            captureSession?.stopRepeating()
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(FlutterError(
                CameraErrorType.RECORDING_FAILED.name,
                "Failed to stop recording: ${e.message}",
                null
            )))
        }
    }

    override fun applyFilter(textureId: Long, filterConfig: FilterConfig, callback: (Result<Unit>) -> Unit) {
        // Implement filter logic here
        callback(Result.success(Unit))
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper!!)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Failed to stop background thread", e)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return activity?.checkSelfPermission(Manifest.permission.CAMERA) == 
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        activity?.requestPermissions(
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    private fun initializeCameraImpl(settings: CameraSettings, callback: (Result<Unit>) -> Unit) {
        try {
            // Store settings for later use
            this.settings = settings
            
            // Create texture on main thread first
            createTextureOnMainThread { textureResult ->
                textureResult.fold(
                    onSuccess = { textureId ->
                        // Start camera initialization on background thread after texture is created
                        startCameraInitialization(settings) { result ->
                            result.fold(
                                onSuccess = {
                                    // Notify Flutter that camera is initialized with actual texture ID
                                    runOnMainThread {
                                        flutterApi?.onCameraInitialized(
                                            textureId ?: 0L,
                                            settings.width ?: 1280,
                                            settings.height ?: 720
                                        ) { notifyResult ->
                                            notifyResult.fold(
                                                onSuccess = {
                                                    Log.d(TAG, "Camera initialization completed and notified to Flutter")
                                                    callback(Result.success(Unit))
                                                },
                                                onFailure = { error ->
                                                    Log.e(TAG, "Failed to notify camera initialization", error)
                                                    callback(Result.failure(error))
                                                }
                                            )
                                        }
                                    }
                                },
                                onFailure = { error ->
                                    Log.e(TAG, "Failed to initialize camera", error)
                                    callback(Result.failure(error))
                                }
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to create preview texture", error)
                        callback(Result.failure(error))
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera", e)
            callback(Result.failure(FlutterError(
                CameraErrorType.INITIALIZATION_FAILED.name,
                "Failed to initialize camera: ${e.message}",
                null
            )))
        }
    }

    companion object {
        private const val CAMERA_PERMISSIONS_REQUEST_CODE = 1001
    }
} 