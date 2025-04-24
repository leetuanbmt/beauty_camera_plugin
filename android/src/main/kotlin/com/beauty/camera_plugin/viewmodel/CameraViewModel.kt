package com.beauty.camera_plugin.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import com.beauty.camera_plugin.BeautyCameraFlutterApi
import com.beauty.camera_plugin.repository.CameraRepository
import java.lang.ref.WeakReference

/**
 * ViewModel for the camera functionality
 */
class CameraViewModel(context: Context) : DefaultLifecycleObserver {
    
    private val appContext = context.applicationContext
    private val repository = CameraRepository(appContext)
    private var activityRef: WeakReference<Activity>? = null
    private var flutterApi: BeautyCameraFlutterApi? = null
    
    /**
     * Set the Flutter API for callbacks
     */
    fun setFlutterApi(api: BeautyCameraFlutterApi?) {
        flutterApi = api
    }
    
    /**
     * Set the activity for lifecycle management
     */
    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }
    
    /**
     * Release activity reference
     */
    fun releaseActivity() {
        activityRef?.clear()
        activityRef = null
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        repository.cleanup()
    }
} 