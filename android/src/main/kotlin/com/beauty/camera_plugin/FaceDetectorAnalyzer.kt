package com.beauty.camera_plugin

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

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

    private val faceLandmark: FaceLandmarker

    init {
        try{
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.GPU)
                .setModelAssetPath("face_landmarker.task") // Ensure this model is in your assets
                .build()


            val faceLandmarkErrorListener: (RuntimeException) -> Unit = { error ->
                Log.e(TAG, "MediaPipe Face Landmarker Error: ${error.message}")
            }

            val faceLandmarkResultListener = { result: FaceLandmarkerResult, input: MPImage ->
                onResults(result, input)
            }
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.5F)
                .setMinTrackingConfidence(0.5F)
                .setMinFacePresenceConfidence(0.5F)
                .setOutputFaceBlendshapes(true)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setErrorListener(faceLandmarkErrorListener)
                .setResultListener(faceLandmarkResultListener)
                .build()

            faceLandmark = FaceLandmarker.createFromOptions(context, options)
        }catch (e: IllegalStateException) {
            throw RuntimeException("Error initializing MediaPipe Face Landmark: ${e.message}")
        } catch (e: RuntimeException) {
            throw RuntimeException("Error initializing MediaPipe Face Landmark: ${e.message}")
        }
    }


    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        imageProxy.use { imageProxy ->
            val bitmap = imageProxy.toBitmap() // Converting to bitmap is simpler but less efficient
            val mpImage = BitmapImageBuilder(bitmap).build()

            // Pass timestamp for live stream mode
            faceLandmark.detectAsync(mpImage, imageProxy.imageInfo.timestamp)
        }
    }

    private fun onResults(result: FaceLandmarkerResult, input: MPImage) {
        val faceDataList = result.faceLandmarks().mapIndexed { index, landmarks ->
            // Calculate bounding box from landmarks
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            for (landmark in landmarks) { // 'landmarks' here is List<NormalizedLandmark> for a single face
                minX = minOf(minX, landmark.x())
                minY = minOf(minY, landmark.y())
                maxX = maxOf(maxX, landmark.x())
                maxY = maxOf(maxY, landmark.y())
            }

            // These min/max values are normalized (0.0 to 1.0)
            val boundingBoxWidth = maxX - minX
            val boundingBoxHeight = maxY - minY
            val boundingBoxCenterX = minX + (boundingBoxWidth / 2)
            val boundingBoxCenterY = minY + (boundingBoxHeight / 2)

            // Convert bounding box to the format expected by FaceData
            val centerX = boundingBoxCenterX
            val centerY = boundingBoxCenterY
            val normalizedWidth = boundingBoxWidth
            val normalizedHeight = boundingBoxHeight

            val faceLandmarks = landmarks.map { landmark ->
                FaceLandmark(
                    type = 0, // TODO: Map the landmark type
                    x = landmark.x().toDouble(),
                    y = landmark.y().toDouble()
                )
            }

            FaceData(
                x = centerX.toDouble(),
                y = centerY.toDouble(),
                width = normalizedWidth.toDouble(),
                height = normalizedHeight.toDouble(),
                id = index.toLong(), // Pigeon expects Long
                landmarks = faceLandmarks,
                smileScore = null, // Not available in FaceLandmarker
                eyeOpenScore = null // Not available in FaceLandmarker
            )
        }

        if (faceDataList.isNotEmpty()) {
            listener.onResults(faceDataList)
        }
    }
    fun close() {
        faceLandmark.close()
    }
}
