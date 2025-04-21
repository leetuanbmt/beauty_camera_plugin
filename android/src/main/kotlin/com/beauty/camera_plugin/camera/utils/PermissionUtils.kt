package com.beauty.camera_plugin.camera.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Utility class for handling camera permissions
 */
object PermissionUtils {
    // Camera permissions
    private val CAMERA_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA
    )
    
    // Audio permissions for video recording
    private val AUDIO_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )
    
    // All required permissions
    private val ALL_PERMISSIONS = CAMERA_PERMISSIONS + AUDIO_PERMISSIONS

    /**
     * Check if camera permission is granted
     */
    fun hasCameraPermission(context: Context): Boolean {
        return CAMERA_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if audio permission is granted
     */
    fun hasAudioPermission(context: Context): Boolean {
        return AUDIO_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(context: Context): Boolean {
        return ALL_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request camera permission
     */
    fun requestCameraPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            CAMERA_PERMISSIONS,
            CameraConstants.PERMISSION_REQUEST_CODE_ALL
        )
    }

    /**
     * Request audio permission
     */
    fun requestAudioPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            AUDIO_PERMISSIONS,
            CameraConstants.PERMISSION_REQUEST_CODE_AUDIO
        )
    }

    /**
     * Request all required permissions
     */
    fun requestAllPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            ALL_PERMISSIONS,
            CameraConstants.PERMISSION_REQUEST_CODE_ALL
        )
    }

    /**
     * Check if all permissions in a request were granted
     */
    fun areAllPermissionsGranted(grantResults: IntArray): Boolean {
        return grantResults.all { it == PackageManager.PERMISSION_GRANTED }
    }
} 