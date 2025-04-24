package com.beauty.camera_plugin.filters

import com.beauty.camera_plugin.models.CameraFilterMode
import jp.co.cyberagent.android.gpuimage.filter.*
import android.graphics.PointF

/**
 * Factory for creating GPU Image filters based on effect mode
 */
object FilterFactory {
    /**
     * Create a filter based on the effect mode
     */
    fun createFilter(mode: CameraFilterMode): GPUImageFilter {
        return when (mode) {
            CameraFilterMode.NONE -> GPUImageFilter()
            CameraFilterMode.BEAUTY -> createBeautyFilter()
            CameraFilterMode.MONO -> GPUImageGrayscaleFilter()
            CameraFilterMode.NEGATIVE -> GPUImageColorInvertFilter()
            CameraFilterMode.SEPIA -> GPUImageSepiaToneFilter()
            CameraFilterMode.SOLARIZE -> createSolarizeFilter()
            CameraFilterMode.POSTERIZE -> GPUImagePosterizeFilter()
            CameraFilterMode.WHITEBOARD -> createWhiteboardFilter()
            CameraFilterMode.BLACKBOARD -> createBlackboardFilter()
            CameraFilterMode.AQUA -> createAquaFilter()
            CameraFilterMode.EMBOSS -> GPUImageEmbossFilter()
            CameraFilterMode.SKETCH -> createSketchFilter()
            CameraFilterMode.NEON -> createNeonFilter()
            CameraFilterMode.VINTAGE -> createVintageFilter()
            CameraFilterMode.BRIGHTNESS -> GPUImageBrightnessFilter(0.0f)
            CameraFilterMode.CONTRAST -> GPUImageContrastFilter(1.0f)
            CameraFilterMode.SATURATION -> GPUImageSaturationFilter(1.0f)
            CameraFilterMode.SHARPEN -> GPUImageSharpenFilter()
            CameraFilterMode.GAUSSIAN_BLUR -> GPUImageGaussianBlurFilter()
            CameraFilterMode.VIGNETTE -> createVignetteFilter()
            CameraFilterMode.HUE -> GPUImageHueFilter(0.0f)
            CameraFilterMode.EXPOSURE -> GPUImageExposureFilter(0.0f)
            CameraFilterMode.HIGHLIGHT_SHADOW -> GPUImageHighlightShadowFilter()
            CameraFilterMode.LEVELS -> GPUImageLevelsFilter()
            CameraFilterMode.COLOR_BALANCE -> createColorBalanceFilter()
            CameraFilterMode.LOOKUP -> GPUImageLookupFilter()
        }
    }
    
    /**
     * Create a beauty filter
     */
    private fun createBeautyFilter(): GPUImageFilterGroup {
        val filters = ArrayList<GPUImageFilter>()
        // Bilateral blur for skin smoothing
        filters.add(GPUImageBilateralBlurFilter())
        // Brightness adjustment
        filters.add(GPUImageBrightnessFilter(0.1f))
        // Subtle contrast
        filters.add(GPUImageContrastFilter(1.1f))
        return GPUImageFilterGroup(filters)
    }
    
    /**
     * Create a solarize filter
     */
    private fun createSolarizeFilter(): GPUImageSolarizeFilter {
        return GPUImageSolarizeFilter()
    }
    
    /**
     * Create a whiteboard filter
     */
    private fun createWhiteboardFilter(): GPUImageFilterGroup {
        val filters = ArrayList<GPUImageFilter>()
        val matrix = floatArrayOf(
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            -0.2f, -0.2f, -0.2f, 1.0f
        )
        filters.add(GPUImageColorMatrixFilter(1.0f, matrix))
        filters.add(GPUImageContrastFilter(1.5f))
        return GPUImageFilterGroup(filters)
    }
    
    /**
     * Create a blackboard filter
     */
    private fun createBlackboardFilter(): GPUImageFilterGroup {
        val filters = ArrayList<GPUImageFilter>()
        val matrix = floatArrayOf(
            -1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, -1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 1.0f, 1.0f
        )
        filters.add(GPUImageColorMatrixFilter(1.0f, matrix))
        filters.add(GPUImageContrastFilter(1.2f))
        return GPUImageFilterGroup(filters)
    }
    
    /**
     * Create an aqua filter
     */
    private fun createAquaFilter(): GPUImageFilterGroup {
        val filters = ArrayList<GPUImageFilter>()
        val matrix = floatArrayOf(
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.2f, 0.0f,
            0.0f, 0.0f, 1.2f, 0.0f,
            0.0f, 0.0f, 0.2f, 1.0f
        )
        filters.add(GPUImageColorMatrixFilter(1.0f, matrix))
        filters.add(GPUImageSaturationFilter(1.3f))
        return GPUImageFilterGroup(filters)
    }
    
    /**
     * Create a sketch filter
     */
    private fun createSketchFilter(): GPUImageFilterGroup {
        val filters = ArrayList<GPUImageFilter>()
        filters.add(GPUImageGrayscaleFilter())
        filters.add(GPUImageSketchFilter())
        return GPUImageFilterGroup(filters)
    }
    
    /**
     * Create a neon filter
     */
    private fun createNeonFilter(): GPUImageFilterGroup {
        val filters = ArrayList<GPUImageFilter>()
        val matrix = floatArrayOf(
            1.2f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.2f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.2f, 0.0f,
            0.2f, 0.2f, 0.2f, 1.0f
        )
        filters.add(GPUImageColorMatrixFilter(1.0f, matrix))
        filters.add(GPUImageHighlightShadowFilter(1.0f, 0.0f))
        filters.add(GPUImageMonochromeFilter(1.0f, floatArrayOf(0.6f, 0.4f, 1.0f, 1.0f)))
        return GPUImageFilterGroup(filters)
    }
    
    /**
     * Create a vintage filter
     */
    private fun createVintageFilter(): GPUImageFilterGroup {
        val filters = ArrayList<GPUImageFilter>()
        val matrix = floatArrayOf(
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.6f, 0.0f,
            0.1f, 0.1f, 0.0f, 1.0f
        )
        filters.add(GPUImageColorMatrixFilter(1.0f, matrix))
        filters.add(GPUImageVignetteFilter(
            PointF(0.5f, 0.5f),
            floatArrayOf(0.0f, 0.0f, 0.0f),
            0.3f,
            0.75f
        ))
        return GPUImageFilterGroup(filters)
    }

    /**
     * Create a vignette filter
     */
    private fun createVignetteFilter(): GPUImageVignetteFilter {
        return GPUImageVignetteFilter(
            PointF(0.5f, 0.5f),
            floatArrayOf(0.0f, 0.0f, 0.0f),
            0.3f,
            0.75f
        )
    }
    
    /**
     * Create a color balance filter
     */
    private fun createColorBalanceFilter(): GPUImageColorBalanceFilter {
        return GPUImageColorBalanceFilter()
    }

    /**
     * Apply filter level to a specific filter
     * @param filter The filter to apply level to
     * @param level Level value between 0.0 and 10.0
     */
    fun applyFilterLevel(filter: GPUImageFilter?, level: Double) {
        if (filter == null) return
        
        val normalizedLevel = (level.coerceIn(0.0, 10.0) / 10.0).toFloat()
        
        when (filter) {
            is GPUImageBrightnessFilter -> filter.setBrightness(normalizedLevel * 2 - 1)
            is GPUImageContrastFilter -> filter.setContrast(normalizedLevel * 3)
            is GPUImageSaturationFilter -> filter.setSaturation(normalizedLevel * 2)
            is GPUImageSharpenFilter -> filter.setSharpness(normalizedLevel * 4)
            is GPUImageGaussianBlurFilter -> filter.setBlurSize(normalizedLevel * 2)
            is GPUImageVignetteFilter -> filter.setVignetteStart(normalizedLevel)
            is GPUImageHueFilter -> filter.setHue(normalizedLevel * 360)
            is GPUImageExposureFilter -> filter.setExposure(normalizedLevel * 2 - 1)
            is GPUImageFilterGroup -> {
                // For filter groups like beauty filter, apply to each component
                filter.filters.forEach { subFilter ->
                    applyFilterLevel(subFilter, level)
                }
            }
        }
    }
} 