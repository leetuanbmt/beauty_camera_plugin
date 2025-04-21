package com.beauty.camera_plugin.camera.recording

import android.content.Context
import android.hardware.camera2.CameraDevice
import android.media.MediaRecorder
import android.os.Handler
import android.view.Surface
import com.beauty.camera_plugin.camera.utils.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Handles video recording functionality
 */
class VideoRecordHandler(
    private val context: Context,
    private val cameraDevice: CameraDevice,
    private val backgroundHandler: Handler,
    private val errorUtils: ErrorUtils
) {
    private var mediaRecorder: MediaRecorder? = null
    private var recordingSurface: Surface? = null
    private var isRecording = false
    private val recordingLock = CountDownLatch(1)

    /**
     * Start video recording
     */
    fun startRecording(path: String, width: Int, height: Int, bitRate: Int, callback: (Result<Unit>) -> Unit) {
        try {
            if (isRecording) {
                throw CameraRecordingException("Recording is already in progress")
            }

            // Create media recorder
            mediaRecorder = MediaRecorder().apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(bitRate)
                setVideoSize(width, height)
                setOutputFile(path)
                prepare()
            }

            // Get recording surface
            recordingSurface = mediaRecorder?.surface

            // Start recording
            mediaRecorder?.start()
            isRecording = true
            callback(Result.success(Unit))
        } catch (e: Exception) {
            release()
            callback(Result.failure(CameraRecordingException("Failed to start recording: ${e.message}")))
        }
    }

    /**
     * Stop video recording
     */
    fun stopRecording(callback: (Result<Unit>) -> Unit) {
        try {
            if (!isRecording) {
                throw CameraRecordingException("No recording in progress")
            }

            if (!recordingLock.await(CameraConstants.CAMERA_RECORDING_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw CameraTimeoutException()
            }

            mediaRecorder?.apply {
                stop()
                reset()
            }
            isRecording = false
            recordingLock.countDown()
            callback(Result.success(Unit))
        } catch (e: Exception) {
            recordingLock.countDown()
            callback(Result.failure(CameraRecordingException("Failed to stop recording: ${e.message}")))
        } finally {
            release()
        }
    }

    /**
     * Get recording surface
     */
    fun getRecordingSurface(): Surface? {
        return recordingSurface
    }

    /**
     * Check if recording is in progress
     */
    fun isRecording(): Boolean {
        return isRecording
    }

    /**
     * Release resources
     */
    fun release() {
        try {
            mediaRecorder?.apply {
                if (isRecording) {
                    stop()
                }
                reset()
                release()
            }
            mediaRecorder = null
            recordingSurface = null
            isRecording = false
        } catch (e: Exception) {
            errorUtils.logError("VideoRecordHandler", "Failed to release resources", e)
        }
    }
} 