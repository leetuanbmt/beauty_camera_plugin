package com.beauty.camera_plugin

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import java.lang.reflect.Method

/**
 * Helper class to access Flutter lifecycle from the plugin
 * Used as a replacement for FlutterLifecycleAdapter
 */
object CameraLifecycleAdapter {
    /**
     * Returns the lifecycle owner for a given activity binding
     * Uses reflection to access the lifecycle owner from the Flutter activity
     *
     * @param activityBinding The Flutter activity plugin binding
     * @return The lifecycle owner, or null if it couldn't be retrieved
     */
    fun getActivityLifecycle(activityBinding: ActivityPluginBinding): LifecycleOwner? {
        try {
            // First try to use the standard method if FlutterLifecycleAdapter is available
            try {
                val flutterLifecycleAdapterClass = Class.forName("io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter")
                val getActivityLifecycleMethod = flutterLifecycleAdapterClass.getMethod("getActivityLifecycle", ActivityPluginBinding::class.java)
                return getActivityLifecycleMethod.invoke(null, activityBinding) as? LifecycleOwner
            } catch (e: ClassNotFoundException) {
                // FlutterLifecycleAdapter not found, use reflection to get the lifecycle
            }
            
            // Get the activity from binding
            val activity = activityBinding.activity
            
            // Check if the activity implements LifecycleOwner (most modern activities do)
            if (activity is LifecycleOwner) {
                return activity
            }
            
            // If not, try to use reflection to get the lifecycle
            val activityClass = activity.javaClass
            
            // Try to get the getLifecycle method
            val getLifecycleMethod: Method? = try {
                activityClass.getMethod("getLifecycle")
            } catch (e: NoSuchMethodException) {
                null
            }
            
            // If the method exists, create a LifecycleOwner that delegates to it
            if (getLifecycleMethod != null) {
                return object : LifecycleOwner {
                    override val lifecycle: Lifecycle
                        get() = getLifecycleMethod.invoke(activity) as Lifecycle
                }
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }
} 