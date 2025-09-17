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

// Optimized blur function for better performance
vec4 optimizedBlur(samplerExternalOES tex, vec2 coord, float strength) {
    vec4 center = texture2D(tex, coord);
    
    // Simple 5-sample blur for performance
    vec4 color = center;
    color += texture2D(tex, coord + vec2(0.002, 0.0) * strength);
    color += texture2D(tex, coord + vec2(-0.002, 0.0) * strength);
    color += texture2D(tex, coord + vec2(0.0, 0.002) * strength);
    color += texture2D(tex, coord + vec2(0.0, -0.002) * strength);
    
    return color / 5.0;
}

// Optimized landmark check - only check key facial points for performance
bool isNearFaceLandmarks(vec2 coord) {
    if (uLandmarkCount == 0) return false;
    
    // Only check every 10th landmark for performance (still covers face well)
    for (int i = 0; i < 468; i += 10) {
        if (i >= uLandmarkCount) break;
        
        vec2 landmark = uLandmarks[i];
        float dist = distance(coord, landmark);
        
        // Larger radius since we're checking fewer points
        if (dist < 0.08) {
            return true;
        }
    }
    
    // Also check some key facial points specifically
    if (uLandmarkCount > 50) {
        // Check nose tip, cheeks, forehead areas (hardcoded for GLSL ES 100 compatibility)
        if (1 < uLandmarkCount) {
            float dist = distance(coord, uLandmarks[1]);
            if (dist < 0.06) return true;
        }
        if (9 < uLandmarkCount) {
            float dist = distance(coord, uLandmarks[9]);
            if (dist < 0.06) return true;
        }
        if (10 < uLandmarkCount) {
            float dist = distance(coord, uLandmarks[10]);
            if (dist < 0.06) return true;
        }
        if (151 < uLandmarkCount) {
            float dist = distance(coord, uLandmarks[151]);
            if (dist < 0.06) return true;
        }
        if (175 < uLandmarkCount) {
            float dist = distance(coord, uLandmarks[175]);
            if (dist < 0.06) return true;
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
        vec4 blurredColor = optimizedBlur(sTexture, vTextureCoord, uSmoothingStrength);
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

