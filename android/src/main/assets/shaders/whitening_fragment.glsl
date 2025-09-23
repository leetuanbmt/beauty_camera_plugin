// Whitening Fragment Shader
// Implements selective brightening for skin areas

precision mediump float;
varying vec2 texCoord;
uniform sampler2D uTexture;
uniform float uBrighteningStrength;
uniform float uIntensity;

// Skin tone detection based on HSV color space
bool isSkinTone(vec3 color) {
    // Convert RGB to HSV
    float r = color.r;
    float g = color.g;
    float b = color.b;
    
    float maxVal = max(max(r, g), b);
    float minVal = min(min(r, g), b);
    float delta = maxVal - minVal;
    
    // Hue calculation
    float hue = 0.0;
    if (delta != 0.0) {
        if (maxVal == r) {
            hue = 60.0 * mod(((g - b) / delta), 6.0);
        } else if (maxVal == g) {
            hue = 60.0 * ((b - r) / delta + 2.0);
        } else {
            hue = 60.0 * ((r - g) / delta + 4.0);
        }
    }
    
    // Skin tone range: 0-50 degrees (red to yellow)
    return hue >= 0.0 && hue <= 50.0;
}

// Selective brightening for skin tones
vec3 brightenSkin(vec3 color) {
    if (isSkinTone(color)) {
        // Apply brightening to skin areas
        float brightness = uBrighteningStrength * uIntensity;
        
        // Increase luminance while preserving color ratios
        float luminance = dot(color, vec3(0.299, 0.587, 0.114));
        float newLuminance = luminance + brightness * (1.0 - luminance);
        
        // Scale color to new luminance
        if (luminance > 0.0) {
            return color * (newLuminance / luminance);
        }
    }
    return color;
}

void main() {
    vec3 originalColor = texture2D(uTexture, texCoord).rgb;
    
    // Apply selective brightening
    vec3 brightenedColor = brightenSkin(originalColor);
    
    // Mix original and brightened color based on intensity
    vec3 finalColor = mix(originalColor, brightenedColor, uIntensity);
    
    gl_FragColor = vec4(finalColor, 1.0);
}
