// Color Correction Fragment Shader
// Implements brightness, contrast, saturation, and other color adjustments

precision mediump float;
varying vec2 texCoord;
uniform sampler2D uTexture;
uniform float uBrightness;
uniform float uContrast;
uniform float uSaturation;
uniform float uWarmth;
uniform float uTint;
uniform float uVibrance;
uniform float uIntensity;

// Convert RGB to HSV
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

// Convert HSV to RGB
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Apply brightness adjustment
vec3 adjustBrightness(vec3 color, float brightness) {
    return color + brightness;
}

// Apply contrast adjustment
vec3 adjustContrast(vec3 color, float contrast) {
    return (color - 0.5) * contrast + 0.5;
}

// Apply saturation adjustment
vec3 adjustSaturation(vec3 color, float saturation) {
    vec3 hsv = rgb2hsv(color);
    hsv.y = clamp(hsv.y * saturation, 0.0, 1.0);
    return hsv2rgb(hsv);
}

// Apply warmth adjustment (shift towards orange/blue)
vec3 adjustWarmth(vec3 color, float warmth) {
    vec3 hsv = rgb2hsv(color);
    hsv.x = hsv.x + warmth * 0.1; // Shift hue
    hsv.x = mod(hsv.x, 1.0); // Wrap around
    return hsv2rgb(hsv);
}

// Apply tint adjustment (shift towards magenta/green)
vec3 adjustTint(vec3 color, float tint) {
    vec3 hsv = rgb2hsv(color);
    hsv.x = hsv.x + tint * 0.05; // Shift hue
    hsv.x = mod(hsv.x, 1.0); // Wrap around
    return hsv2rgb(hsv);
}

// Apply vibrance adjustment (selective saturation boost)
vec3 adjustVibrance(vec3 color, float vibrance) {
    vec3 hsv = rgb2hsv(color);
    
    // Boost saturation more for less saturated colors
    float boostFactor = 1.0 - hsv.y;
    hsv.y = hsv.y + vibrance * boostFactor * 0.5;
    hsv.y = clamp(hsv.y, 0.0, 1.0);
    
    return hsv2rgb(hsv);
}

void main() {
    vec3 originalColor = texture2D(uTexture, texCoord).rgb;
    vec3 adjustedColor = originalColor;
    
    // Apply color corrections
    adjustedColor = adjustBrightness(adjustedColor, uBrightness);
    adjustedColor = adjustContrast(adjustedColor, uContrast);
    adjustedColor = adjustSaturation(adjustedColor, uSaturation);
    adjustedColor = adjustWarmth(adjustedColor, uWarmth);
    adjustedColor = adjustTint(adjustedColor, uTint);
    adjustedColor = adjustVibrance(adjustedColor, uVibrance);
    
    // Mix original and adjusted color based on intensity
    vec3 finalColor = mix(originalColor, adjustedColor, uIntensity);
    
    gl_FragColor = vec4(finalColor, 1.0);
}
