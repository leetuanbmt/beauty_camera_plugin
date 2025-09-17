#version 100
#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;

// Face landmarks data
uniform vec2 uLandmarks[468];
uniform int uLandmarkCount;

// Beauty filter parameters
uniform float uSmoothingStrength;
uniform float uBrighteningStrength;

// Gaussian blur function for skin smoothing
vec4 gaussianBlur(samplerExternalOES tex, vec2 coord, float radius) {
    vec4 color = vec4(0.0);
    float total = 0.0;
    
    for (float x = -2.0; x <= 2.0; x += 1.0) {
        for (float y = -2.0; y <= 2.0; y += 1.0) {
            vec2 offset = vec2(x, y) * radius / 512.0;
            float weight = exp(-0.5 * (x*x + y*y) / (radius*radius));
            color += texture2D(tex, coord + offset) * weight;
            total += weight;
        }
    }
    
    return color / total;
}

// Check if current pixel is near face landmarks (skin area)
bool isNearFaceLandmarks(vec2 coord) {
    if (uLandmarkCount == 0) return false;
    
    // Check distance to key facial landmarks (skin areas)
    for (int i = 0; i < 468; i++) {
        if (i >= uLandmarkCount) break;
        
        vec2 landmark = uLandmarks[i];
        float dist = distance(coord, landmark);
        
        // If within skin smoothing radius
        if (dist < 0.05) {
            return true;
        }
    }
    return false;
}

void main() {
    vec4 originalColor = texture2D(sTexture, vTextureCoord);
    
    // If no landmarks or filter disabled, show original
    if (uLandmarkCount == 0) {
        gl_FragColor = originalColor;
        return;
    }
    
    // Apply beauty filters only to skin areas
    if (isNearFaceLandmarks(vTextureCoord)) {
        // Skin smoothing: blend original with blurred version
        vec4 blurredColor = gaussianBlur(sTexture, vTextureCoord, 2.0);
        vec4 smoothedColor = mix(originalColor, blurredColor, uSmoothingStrength);
        
        // Skin brightening: increase brightness slightly
        vec3 brightenedColor = smoothedColor.rgb + vec3(uBrighteningStrength * 0.1);
        brightenedColor = clamp(brightenedColor, 0.0, 1.0);
        
        gl_FragColor = vec4(brightenedColor, smoothedColor.a);
    } else {
        // Non-skin areas: keep original
        gl_FragColor = originalColor;
    }
}
