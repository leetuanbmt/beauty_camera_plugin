package com.beauty.camera_plugin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.renderscript.ScriptIntrinsicColorMatrix

/**
 * Processor for applying various image filters and effects to camera feed
 */
class ImageFilterProcessor(private val context: Context) {
    
    private var renderScript: RenderScript? = null
    private var useRenderScript = true
    
    init {
        try {
            renderScript = RenderScript.create(context)
            android.util.Log.d("ImageFilterProcessor", "RenderScript initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("ImageFilterProcessor", "Failed to initialize RenderScript: ${e.message}")
            android.util.Log.e("ImageFilterProcessor", "Will fallback to Canvas implementation")
            useRenderScript = false
        }
    }
    
    /**
     * Applies a filter based on the FilterType enum
     *
     * @param bitmap The input bitmap to process
     * @param filterType The type of filter to apply
     * @param smoothness Smoothness level (0.0-1.0)
     * @param brightness Brightness adjustment (-1.0-1.0)
     * @param contrast Contrast adjustment (0.0-2.0)
     * @return The processed bitmap
     */
    fun applyFilter(
        bitmap: Bitmap,
        filterType: FilterType,
        smoothness: Float = 0.5f,
        brightness: Float = 0.0f,
        contrast: Float = 1.0f
    ): Bitmap {
        android.util.Log.d("ImageFilterProcessor", "Applying filter: $filterType with smoothness: $smoothness, brightness: $brightness, contrast: $contrast")
        
        try {
            // Validate input parameters
            val validatedSmoothness = smoothness.coerceIn(0f, 1f)
            val validatedBrightness = brightness.coerceIn(-1f, 1f)
            val validatedContrast = contrast.coerceIn(0f, 2f)
            
            // Check bitmap validity
            if (bitmap.isRecycled) {
                android.util.Log.e("ImageFilterProcessor", "Input bitmap is recycled")
                return bitmap
            }
            
            // Check bitmap size
            val maxSize = 2048
            var processedBitmap = bitmap
            if (bitmap.width > maxSize || bitmap.height > maxSize) {
                android.util.Log.w("ImageFilterProcessor", "Input bitmap too large, scaling down")
                val scale = maxSize.toFloat() / Math.max(bitmap.width, bitmap.height)
                val scaledWidth = (bitmap.width * scale).toInt()
                val scaledHeight = (bitmap.height * scale).toInt()
                processedBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            }
            
            val result = when (filterType) {
                FilterType.NONE -> {
                    // For none filter, just apply any individual adjustments
                    var adjustedBitmap = processedBitmap.copy(processedBitmap.config ?: Bitmap.Config.ARGB_8888, true)
                    
                    try {
                        // Apply brightness if it's not the default
                        if (validatedBrightness != 0.0f) {
                            android.util.Log.d("ImageFilterProcessor", "Applying brightness: $validatedBrightness")
                            adjustedBitmap = adjustBrightness(adjustedBitmap, validatedBrightness)
                        }
                        
                        // Apply contrast if it's not the default
                        if (validatedContrast != 1.0f) {
                            android.util.Log.d("ImageFilterProcessor", "Applying contrast: $validatedContrast")
                            adjustedBitmap = adjustContrast(adjustedBitmap, validatedContrast)
                        }
                        
                        adjustedBitmap
                    } catch (e: Exception) {
                        android.util.Log.e("ImageFilterProcessor", "Error applying adjustments: ${e.message}")
                        adjustedBitmap.recycle()
                        processedBitmap
                    }
                }
                
                FilterType.BEAUTY -> {
                    android.util.Log.d("ImageFilterProcessor", "Applying beauty filter with smoothness: $validatedSmoothness, brightness: $validatedBrightness")
                    try {
                        applyBeautyFilter(processedBitmap, validatedSmoothness, validatedBrightness)
                    } catch (e: Exception) {
                        android.util.Log.e("ImageFilterProcessor", "Error applying beauty filter: ${e.message}")
                        processedBitmap
                    }
                }
                
                FilterType.BLACK_AND_WHITE -> {
                    android.util.Log.d("ImageFilterProcessor", "Applying B&W filter with contrast: $validatedContrast")
                    try {
                        applyBlackAndWhiteFilter(processedBitmap, validatedContrast)
                    } catch (e: Exception) {
                        android.util.Log.e("ImageFilterProcessor", "Error applying B&W filter: ${e.message}")
                        processedBitmap
                    }
                }
            }
            
            // If we scaled down the bitmap, scale back up to original size
            return if (processedBitmap !== bitmap) {
                try {
                    val finalBitmap = Bitmap.createScaledBitmap(result, bitmap.width, bitmap.height, true)
                    processedBitmap.recycle() // Clean up the scaled down bitmap
                    finalBitmap
                } catch (e: Exception) {
                    android.util.Log.e("ImageFilterProcessor", "Error scaling back to original size: ${e.message}")
                    result
                }
            } else {
                result
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ImageFilterProcessor", "Error applying filter: ${e.message}")
            return bitmap
        }
    }
    
    /**
     * Applies a beauty filter to smooth skin and enhance appearance
     * 
     * @param bitmap The input bitmap to process
     * @param smoothness Smoothness level (0.0-1.0)
     * @param brightness Brightness adjustment (-1.0-1.0)
     * @return The processed bitmap
     */
    fun applyBeautyFilter(
        bitmap: Bitmap, 
        smoothness: Float = 0.5f, 
        brightness: Float = 0.0f
    ): Bitmap {
        // Create a copy of the bitmap to process
        val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        
        try {
            // Apply smoothing effect using Gaussian blur
            val finalBitmap = applySkinSmoothing(outputBitmap, smoothness)
            
            // Apply brightness adjustment if needed
            val finalBitmapWithBrightness = if (brightness != 0.0f) {
                adjustBrightness(finalBitmap, brightness)
            } else {
                finalBitmap
            }
            
            android.util.Log.d("ImageFilterProcessor", "Beauty filter applied with smoothness: $smoothness, brightness: $brightness")
            return finalBitmapWithBrightness
        } catch (e: Exception) {
            android.util.Log.e("ImageFilterProcessor", "Error in applyBeautyFilter: ${e.message}")
            return outputBitmap
        }
    }
    
    /**
     * Applies a vintage filter effect
     * 
     * @param bitmap The input bitmap to process
     * @param intensity The intensity of the effect (0.0-1.0)
     * @return The processed bitmap
     */
    fun applyVintageFilter(bitmap: Bitmap, intensity: Float = 0.7f): Bitmap {
        val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        
        // Create a sepia-like effect
        val colorMatrix = ColorMatrix()
        
        // Reduce saturation
        val saturation = 1.0f - intensity * 0.9f
        colorMatrix.setSaturation(saturation)
        
        // Apply warm tone
        val warmMatrix = ColorMatrix(floatArrayOf(
            1.0f + intensity * 0.2f, 0f, 0f, 0f, intensity * 10,
            0f, 1.0f, 0f, 0f, intensity * 5,
            0f, 0f, 1.0f - intensity * 0.2f, 0f, 0f,
            0f, 0f, 0f, 1.0f, 0f
        ))
        
        colorMatrix.postConcat(warmMatrix)
        
        // Apply vignette effect
        // This would require Canvas operations; simplified here
        
        return applyColorMatrix(outputBitmap, colorMatrix)
    }
    
    /**
     * Applies a black and white filter
     * 
     * @param bitmap The input bitmap to process
     * @param contrast Contrast adjustment (0.0-2.0)
     * @return The processed bitmap
     */
    fun applyBlackAndWhiteFilter(bitmap: Bitmap, contrast: Float = 1.2f): Bitmap {
        val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        
        // Create a black and white effect
        val colorMatrix = ColorMatrix()
        
        // Remove saturation (make greyscale)
        colorMatrix.setSaturation(0f)
        
        // Apply contrast
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        colorMatrix.postConcat(contrastMatrix)
        
        return applyColorMatrix(outputBitmap, colorMatrix)
    }
    
    /**
     * Applies a camera effect mode based on the CameraEffectMode enum
     * 
     * @param bitmap The input bitmap to process
     * @param effectMode The camera effect mode to apply
     * @return The processed bitmap
     */
    fun applyCameraEffect(bitmap: Bitmap, effectMode: CameraEffectMode): Bitmap {
        val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        
        val colorMatrix = ColorMatrix()
        
        when (effectMode) {
            CameraEffectMode.NONE -> return bitmap
            
            CameraEffectMode.MONO -> {
                colorMatrix.setSaturation(0f)
            }
            
            CameraEffectMode.NEGATIVE -> {
                colorMatrix.set(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            
            CameraEffectMode.SEPIA -> {
                colorMatrix.set(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            
            CameraEffectMode.POSTERIZE -> {
                // This would typically use Canvas operations
                // Simplified here with a color matrix approximation
                val steps = 5f
                val scale = 255f / steps
                colorMatrix.set(floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                
                // Apply posterize effect in RenderScript if needed
            }
            
            else -> {
                // Other effects would need more complex implementations
                // or could use Android's built-in ColorFilters
                return bitmap
            }
        }
        
        return applyColorMatrix(outputBitmap, colorMatrix)
    }
    
    /**
     * Adjusts image brightness
     * 
     * @param bitmap The input bitmap
     * @param brightness Brightness factor (-1.0 to 1.0)
     * @return The processed bitmap
     */
    fun adjustBrightness(bitmap: Bitmap, brightness: Float): Bitmap {
        val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        
        // Convert to range of brightness adjustment (0-255)
        val brightnessValue = brightness * 255f
        
        val colorMatrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, brightnessValue,
            0f, 1f, 0f, 0f, brightnessValue,
            0f, 0f, 1f, 0f, brightnessValue,
            0f, 0f, 0f, 1f, 0f
        ))
        
        return applyColorMatrix(outputBitmap, colorMatrix)
    }
    
    /**
     * Adjusts image saturation
     * 
     * @param bitmap The input bitmap
     * @param saturation Saturation factor (0.0 to 2.0)
     * @return The processed bitmap
     */
    fun adjustSaturation(bitmap: Bitmap, saturation: Float): Bitmap {
        val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(saturation)
        
        return applyColorMatrix(outputBitmap, colorMatrix)
    }
    
    /**
     * Adjusts image contrast
     * 
     * @param bitmap The input bitmap
     * @param contrast Contrast factor (0.0 to 2.0)
     * @return The processed bitmap
     */
    fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        
        // Apply contrast matrix
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f
        
        val colorMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        return applyColorMatrix(outputBitmap, colorMatrix)
    }
    
    /**
     * Applies skin smoothing effect
     * 
     * @param bitmap The input bitmap
     * @param smoothness Smoothness level (0.0-1.0)
     * @return The processed bitmap
     */
    private fun applySkinSmoothing(bitmap: Bitmap, smoothness: Float): Bitmap {
        // Skip if smoothness is zero
        if (smoothness <= 0) return bitmap
        
        try {
            // Check bitmap size to avoid OutOfMemory
            val maxSize = 2048
            if (bitmap.width > maxSize || bitmap.height > maxSize) {
                android.util.Log.w("ImageFilterProcessor", "Bitmap too large, scaling down for processing")
                val scale = maxSize.toFloat() / Math.max(bitmap.width, bitmap.height)
                val scaledWidth = (bitmap.width * scale).toInt()
                val scaledHeight = (bitmap.height * scale).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                return applySkinSmoothingInternal(scaledBitmap, smoothness)
            }
            
            return applySkinSmoothingInternal(bitmap, smoothness)
        } catch (e: Exception) {
            android.util.Log.e("ImageFilterProcessor", "Error in skin smoothing: ${e.message}")
            return bitmap
        }
    }
    
    private fun applySkinSmoothingInternal(bitmap: Bitmap, smoothness: Float): Bitmap {
        try {
            val rs = renderScript ?: return applyFallbackSmoothing(bitmap, smoothness)
            
            val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            
            // Create allocations for input and output
            val inputAllocation = Allocation.createFromBitmap(
                rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT
            )
            val outputAllocation = Allocation.createFromBitmap(rs, outputBitmap)
            
            // Create blur script and set radius (1-25)
            val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            val radius = 1f + smoothness * 24f // Convert to 1-25 range
            blurScript.setRadius(radius)
            
            // Apply the blur effect
            blurScript.setInput(inputAllocation)
            blurScript.forEach(outputAllocation)
            
            // Copy the result back to the output bitmap
            outputAllocation.copyTo(outputBitmap)
            
            // Cleanup
            inputAllocation.destroy()
            outputAllocation.destroy()
            blurScript.destroy()
            
            return outputBitmap
        } catch (e: Exception) {
            android.util.Log.e("ImageFilterProcessor", "Error in RenderScript smoothing: ${e.message}")
            return applyFallbackSmoothing(bitmap, smoothness)
        }
    }
    
    private fun applyFallbackSmoothing(bitmap: Bitmap, smoothness: Float): Bitmap {
        try {
            val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            val paint = android.graphics.Paint()
            val canvas = android.graphics.Canvas(outputBitmap)
            
            // Apply a simple blur using Paint
            val blurRadius = smoothness * 25
            paint.maskFilter = android.graphics.BlurMaskFilter(blurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            return outputBitmap
        } catch (e: Exception) {
            android.util.Log.e("ImageFilterProcessor", "Fallback smoothing failed: ${e.message}")
            return bitmap
        }
    }
    
    /**
     * Applies a color matrix to a bitmap
     * 
     * @param bitmap The input bitmap
     * @param colorMatrix The color matrix to apply
     * @return The processed bitmap
     */
    private fun applyColorMatrix(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        try {
            // Check bitmap size to avoid OutOfMemory
            val maxSize = 2048
            if (bitmap.width > maxSize || bitmap.height > maxSize) {
                android.util.Log.w("ImageFilterProcessor", "Bitmap too large for color matrix, scaling down")
                val scale = maxSize.toFloat() / Math.max(bitmap.width, bitmap.height)
                val scaledWidth = (bitmap.width * scale).toInt()
                val scaledHeight = (bitmap.height * scale).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                return applyColorMatrixInternal(scaledBitmap, colorMatrix)
            }
            
            return applyColorMatrixInternal(bitmap, colorMatrix)
        } catch (e: Exception) {
            android.util.Log.e("ImageFilterProcessor", "Error in applyColorMatrix: ${e.message}")
            return bitmap
        }
    }
    
    private fun applyColorMatrixInternal(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        try {
            val rs = renderScript
            val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            
            if (rs != null) {
                try {
                    // Create allocations for input and output
                    val inputAllocation = Allocation.createFromBitmap(
                        rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT
                    )
                    val outputAllocation = Allocation.createFromBitmap(rs, outputBitmap)
                    
                    // Create color matrix script
                    val colorMatrixScript = ScriptIntrinsicColorMatrix.create(rs)
                    
                    // Convert Android ColorMatrix to RenderScript Matrix4f
                    val matrix = android.renderscript.Matrix4f()
                    val values = colorMatrix.array.clone()
                    
                    // Set matrix values in column-major order
                    for (i in 0..3) {
                        for (j in 0..3) {
                            matrix.set(i, j, values[i + j * 5])
                        }
                    }
                    
                    // Set translation components
                    matrix.set(0, 4, values[4])   // red offset
                    matrix.set(1, 4, values[9])   // green offset
                    matrix.set(2, 4, values[14])  // blue offset
                    matrix.set(3, 4, values[19])  // alpha offset
                    
                    // Apply the color matrix
                    colorMatrixScript.setColorMatrix(matrix)
                    colorMatrixScript.forEach(inputAllocation, outputAllocation)
                    
                    // Copy the result back to the output bitmap
                    outputAllocation.copyTo(outputBitmap)
                    
                    // Cleanup
                    inputAllocation.destroy()
                    outputAllocation.destroy()
                    colorMatrixScript.destroy()
                    
                    return outputBitmap
                } catch (e: Exception) {
                    android.util.Log.e("ImageFilterProcessor", "RenderScript color matrix failed: ${e.message}")
                    // Fall through to Canvas implementation
                }
            }
            
            // Fallback to Canvas implementation
            return applyColorMatrixWithCanvas(bitmap, colorMatrix)
        } catch (e: Exception) {
            android.util.Log.e("ImageFilterProcessor", "Error in color matrix processing: ${e.message}")
            return bitmap
        }
    }
    
    private fun applyColorMatrixWithCanvas(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        try {
            val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            val paint = android.graphics.Paint()
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            
            val canvas = android.graphics.Canvas(outputBitmap)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            return outputBitmap
        } catch (e: Exception) {
            android.util.Log.e("ImageFilterProcessor", "Canvas color matrix failed: ${e.message}")
            return bitmap
        }
    }
    
    /**
     * Release resources when no longer needed
     */
    fun dispose() {
        renderScript?.destroy()
        renderScript = null
    }
} 