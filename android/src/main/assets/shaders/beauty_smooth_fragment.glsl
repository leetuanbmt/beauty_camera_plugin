// Beauty Smooth Fragment Shader
// Implements bilateral filtering for skin smoothing

precision mediump float;
varying vec2 texCoord;
uniform sampler2D uTexture;
uniform float uSmoothingStrength;
uniform float uIntensity;

// Bilateral filter for skin smoothing
vec3 bilateralFilter(vec2 uv, float sigmaSpace, float sigmaColor) {
    vec2 texelSize = 1.0 / vec2(textureSize(uTexture, 0));
    vec3 centerColor = texture2D(uTexture, uv).rgb;
    vec3 result = vec3(0.0);
    float weightSum = 0.0;
    
    // 5x5 kernel for bilateral filtering
    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            vec2 offset = vec2(float(x), float(y)) * texelSize;
            vec2 sampleUV = uv + offset;
            
            // Spatial weight (distance-based)
            float spatialWeight = exp(-(float(x*x + y*y)) / (2.0 * sigmaSpace * sigmaSpace));
            
            // Color weight (color difference-based)
            vec3 sampleColor = texture2D(uTexture, sampleUV).rgb;
            float colorDiff = length(sampleColor - centerColor);
            float colorWeight = exp(-(colorDiff * colorDiff) / (2.0 * sigmaColor * sigmaColor));
            
            float weight = spatialWeight * colorWeight;
            result += sampleColor * weight;
            weightSum += weight;
        }
    }
    
    return result / weightSum;
}

void main() {
    vec3 originalColor = texture2D(uTexture, texCoord).rgb;
    
    // Apply bilateral filter for skin smoothing
    float sigmaSpace = 1.0 + uSmoothingStrength * 3.0;
    float sigmaColor = 0.1 + uSmoothingStrength * 0.3;
    vec3 smoothedColor = bilateralFilter(texCoord, sigmaSpace, sigmaColor);
    
    // Mix original and smoothed color based on intensity
    vec3 finalColor = mix(originalColor, smoothedColor, uIntensity);
    
    gl_FragColor = vec4(finalColor, 1.0);
}
