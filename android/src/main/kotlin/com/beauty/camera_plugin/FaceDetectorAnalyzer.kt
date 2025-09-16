package com.beauty.camera_plugin

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult

class FaceDetectorAnalyzer(
    context: Context,
    private val listener: DetectorListener
) : ImageAnalysis.Analyzer {

    interface DetectorListener {
        fun onResults(faces: List<FaceData>)
    }

    companion object {
        private const val TAG = "FaceDetectorAnalyzer"
    }

    private val faceDetector: FaceDetector

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("blaze_face_short_range.tflite") // Ensure this model is in your assets
            .build()

        val options = FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setMinDetectionConfidence(0.5f)
            .setResultListener(this::onResults)
            .setErrorListener { error ->
                Log.e(TAG, "MediaPipe Face Detector Error: ${error.message}")
            }
            .build()

        faceDetector = FaceDetector.createFromOptions(context, options)
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        imageProxy.use { imageProxy ->
            val bitmap = imageProxy.toBitmap() // Converting to bitmap is simpler but less efficient
            val mpImage = BitmapImageBuilder(bitmap).build()

            // Pass timestamp for live stream mode
            faceDetector.detectAsync(mpImage, imageProxy.imageInfo.timestamp)
        }
    }

    private fun onResults(result: FaceDetectorResult, input: MPImage) {
        val faceDataList = result.detections().mapIndexed { index, detection ->
            val boundingBox = detection.boundingBox()

            // Convert bounding box to the format expected by FaceData
            val centerX = boundingBox.centerX() / input.width
            val centerY = boundingBox.centerY() / input.height
            val size = boundingBox.width() / input.width // Use width as relative size

            // Note: MediaPipe does not provide a stable ID for faces in the same way as MLKit.
            // We use the index as a temporary ID for this frame.
            FaceData(
                x = centerX.toDouble(),
                y = centerY.toDouble(),
                size = size.toDouble(),
                id = index.toLong(), // Pigeon expects Long
                landmarks = null, // FaceDetector doesn't provide landmarks, FaceLandmarker does
                smileScore = null, // Not available in FaceDetector
                eyeOpenScore = null // Not available in FaceDetector
            )
        }

        if (faceDataList.isNotEmpty()) {
            listener.onResults(faceDataList)
        }
    }
    fun close() {
        faceDetector.close()
    }
}
