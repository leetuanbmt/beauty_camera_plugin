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
    
    init {
        renderScript = RenderScript.create(context)
    }
    
    /**
     * Applies a beauty filter to smooth skin and enhance appearance
     * 
     * @param bitmap The input bitmap to process
     * @param smoothness Smoothness level (0.0-1.0)
     * @param brightness Brightness adjustment (0.0-1.0)
     * @return The processed bitmap
     */
    fun applyBeautyFilter(
        bitmap: Bitmap, 
        smoothness: Float = 0.5f, 
        brightness: Float = 0.5f
    ): Bitmap {
        // Create a copy of the bitmap to process
        val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        
        // Apply smoothing effect using Gaussian blur
        val smoothedBitmap = applySkinSmoothing(outputBitmap, smoothness)
        
        // Apply brightness adjustment
        val finalBitmap = adjustBrightness(smoothedBitmap, brightness)
        
        return finalBitmap
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
        val scale = contrast + 1.0f
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
        
        val rs = renderScript ?: return bitmap
        
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
    }
    
    /**
     * Applies a color matrix to a bitmap
     * 
     * @param bitmap The input bitmap
     * @param colorMatrix The color matrix to apply
     * @return The processed bitmap
     */
    private fun applyColorMatrix(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        val rs = renderScript ?: return bitmap
        
        val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        
        // Create allocations
        val inputAllocation = Allocation.createFromBitmap(
            rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT
        )
        val outputAllocation = Allocation.createFromBitmap(rs, outputBitmap)
        
        // Create color matrix script
        val colorMatrixScript = ScriptIntrinsicColorMatrix.create(rs)
        
        // Convert Android ColorMatrix to RenderScript Matrix4f
        val matrix = android.renderscript.Matrix4f()
        val values = colorMatrix.array.clone() // Get a copy of the internal array
        
        // RenderScript Matrix4f uses column-major format, need to adjust
        matrix.set(0, 0, values[0])  // red_r
        matrix.set(1, 0, values[1])  // red_g
        matrix.set(2, 0, values[2])  // red_b
        matrix.set(3, 0, values[3])  // red_a
        
        matrix.set(0, 1, values[5])  // green_r
        matrix.set(1, 1, values[6])  // green_g
        matrix.set(2, 1, values[7])  // green_b
        matrix.set(3, 1, values[8])  // green_a
        
        matrix.set(0, 2, values[10]) // blue_r
        matrix.set(1, 2, values[11]) // blue_g
        matrix.set(2, 2, values[12]) // blue_b
        matrix.set(3, 2, values[13]) // blue_a
        
        matrix.set(0, 3, values[15]) // alpha_r
        matrix.set(1, 3, values[16]) // alpha_g
        matrix.set(2, 3, values[17]) // alpha_b
        matrix.set(3, 3, values[18]) // alpha_a
        
        // Set translation components (offsets)
        matrix.set(0, 4, values[4])  // red_offset
        matrix.set(1, 4, values[9])  // green_offset
        matrix.set(2, 4, values[14]) // blue_offset
        matrix.set(3, 4, values[19]) // alpha_offset
        
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
    }
    
    /**
     * Release resources when no longer needed
     */
    fun dispose() {
        renderScript?.destroy()
        renderScript = null
    }
} 