package com.beauty.camera_plugin.camera.filters

import android.opengl.GLES20

/**
 * Interface for camera filters
 */
interface Filter {
    val name: String
    val vertexShader: String
    val fragmentShader: String
    fun setParameters(program: Int)
}

/**
 * Base class for all filters
 */
abstract class BaseFilter(
    override val name: String,
    override val vertexShader: String = DEFAULT_VERTEX_SHADER,
    override val fragmentShader: String
) : Filter {
    override fun setParameters(program: Int) {
        // Override in subclasses to set specific parameters
    }

    companion object {
        const val DEFAULT_VERTEX_SHADER = """
            uniform mat4 uTransform;
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uTransform * aPosition;
                vTextureCoord = aTextureCoord;
            }
        """
    }
}

/**
 * Beauty filter implementation
 */
class BeautyFilter(
    private val blurSize: Float = 1.0f,
    private val brightness: Float = 1.0f,
    private val smoothness: Float = 0.5f
) : BaseFilter(
    name = "Beauty",
    fragmentShader = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;
        uniform float uBlurSize;
        uniform float uBrightness;
        uniform float uSmoothness;

        void main() {
            vec4 color = texture2D(sTexture, vTextureCoord);
            
            // Apply brightness
            color.rgb *= uBrightness;
            
            // Apply smoothness (simple blur)
            vec2 offset = vec2(uBlurSize * uSmoothness) / vec2(textureSize(sTexture, 0));
            vec4 blur = texture2D(sTexture, vTextureCoord + offset) +
                        texture2D(sTexture, vTextureCoord - offset);
            color = mix(color, blur * 0.5, uSmoothness);
            
            gl_FragColor = color;
        }
    """
) {
    override fun setParameters(program: Int) {
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBlurSize"), blurSize)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBrightness"), brightness)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSmoothness"), smoothness)
    }
}

/**
 * Vintage filter implementation
 */
class VintageFilter(
    private val sepia: Float = 0.5f,
    private val vignette: Float = 0.3f
) : BaseFilter(
    name = "Vintage",
    fragmentShader = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;
        uniform float uSepia;
        uniform float uVignette;

        void main() {
            vec4 color = texture2D(sTexture, vTextureCoord);
            
            // Apply sepia
            float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            vec3 sepiaColor = vec3(
                gray * 1.2 + 0.1,
                gray * 1.0 + 0.1,
                gray * 0.8 + 0.1
            );
            color.rgb = mix(color.rgb, sepiaColor, uSepia);
            
            // Apply vignette
            vec2 center = vec2(0.5, 0.5);
            float dist = distance(vTextureCoord, center);
            color.rgb *= 1.0 - smoothstep(0.4, 0.7, dist * uVignette);
            
            gl_FragColor = color;
        }
    """
) {
    override fun setParameters(program: Int) {
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSepia"), sepia)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uVignette"), vignette)
    }
}

/**
 * Mono filter implementation
 */
class MonoFilter(
    private val contrast: Float = 1.2f
) : BaseFilter(
    name = "Mono",
    fragmentShader = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;
        uniform float uContrast;

        void main() {
            vec4 color = texture2D(sTexture, vTextureCoord);
            
            // Convert to grayscale
            float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            
            // Apply contrast
            gray = (gray - 0.5) * uContrast + 0.5;
            
            gl_FragColor = vec4(vec3(gray), color.a);
        }
    """
) {
    override fun setParameters(program: Int) {
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uContrast"), contrast)
    }
}

/**
 * Saturation filter implementation
 */
class SaturationFilter(
    private val saturation: Float = 1.5f
) : BaseFilter(
    name = "Saturation",
    fragmentShader = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;
        uniform float uSaturation;

        void main() {
            vec4 color = texture2D(sTexture, vTextureCoord);
            
            // Convert to grayscale
            float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            
            // Apply saturation
            color.rgb = mix(vec3(gray), color.rgb, uSaturation);
            
            gl_FragColor = color;
        }
    """
) {
    override fun setParameters(program: Int) {
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSaturation"), saturation)
    }
} 