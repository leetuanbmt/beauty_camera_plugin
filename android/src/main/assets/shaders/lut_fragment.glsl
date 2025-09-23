// LUT (Look-Up Table) Fragment Shader
// Applies color grading using 3D LUT textures

precision mediump float;
varying vec2 texCoord;
uniform sampler2D uTexture;
uniform sampler2D uLUT;
uniform float uIntensity;
uniform float uLUTSize;

// 3D LUT lookup function
vec3 lut3D(vec3 color) {
    // Clamp color to [0, 1]
    color = clamp(color, 0.0, 1.0);
    
    // Scale to LUT coordinates
    color = color * (uLUTSize - 1.0) / uLUTSize + 0.5 / uLUTSize;
    
    // Calculate 3D texture coordinates
    float sliceSize = 1.0 / uLUTSize;
    float slice = floor(color.b * uLUTSize);
    float nextSlice = min(slice + 1.0, uLUTSize - 1.0);
    
    // Interpolate between slices
    float sliceOffset = slice * sliceSize;
    float nextSliceOffset = nextSlice * sliceSize;
    
    vec2 lutCoord1 = vec2(
        color.r * sliceSize + sliceOffset,
        color.g
    );
    vec2 lutCoord2 = vec2(
        color.r * sliceSize + nextSliceOffset,
        color.g
    );
    
    vec3 color1 = texture2D(uLUT, lutCoord1).rgb;
    vec3 color2 = texture2D(uLUT, lutCoord2).rgb;
    
    // Interpolate between the two slices
    float factor = fract(color.b * uLUTSize);
    return mix(color1, color2, factor);
}

void main() {
    vec3 originalColor = texture2D(uTexture, texCoord).rgb;
    
    // Apply LUT color grading
    vec3 lutColor = lut3D(originalColor);
    
    // Mix original and LUT color based on intensity
    vec3 finalColor = mix(originalColor, lutColor, uIntensity);
    
    gl_FragColor = vec4(finalColor, 1.0);
}
