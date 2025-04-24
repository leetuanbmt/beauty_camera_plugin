package com.beauty.camera_plugin.filters

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.*
import com.beauty.camera_plugin.models.CameraFilterMode

class CameraFilterManager(context: Context) {
    companion object {
        private const val TAG = "CameraFilterManager"
    }

    private var gpuImage: GPUImage? = null
    private var currentFilter: GPUImageFilter? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var currentFilterMode = CameraFilterMode.NONE
    private var filterLevel = 0.0

    init {
        gpuImage = GPUImage(context)
        currentFilter = FilterFactory.createFilter(CameraFilterMode.NONE)
        gpuImage?.setFilter(currentFilter)
    }

    fun setFilter(mode: CameraFilterMode, level: Double) {
        try {
            currentFilterMode = mode
            filterLevel = level.coerceIn(0.0, 10.0)
            
            currentFilter?.let { oldFilter ->
                gpuImage?.deleteImage()
                oldFilter.destroy()
            }
            
            currentFilter = FilterFactory.createFilter(mode)
            gpuImage?.setFilter(currentFilter)
            
            // Apply filter level
            applyFilterLevel()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting filter", e)
        }
    }

    private fun applyFilterLevel() {
        try{
            if(currentFilter == null) return
            FilterFactory.applyFilterLevel(currentFilter, filterLevel)
        }catch (e: Exception) {
            Log.e(TAG, "Error applying filter level", e)
        }
    }

//    fun getCurrentFilter(): CameraFilterMode {
//        return currentFilterMode
//    }
//
//    fun getFilterLevel(): Double {
//        return filterLevel
//    }

    fun release() {
        try {
            currentFilter?.destroy()
            currentFilter = null
            gpuImage?.deleteImage()
            gpuImage = null
            surfaceTexture?.release()
            surfaceTexture = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }
} 