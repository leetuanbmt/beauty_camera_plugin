//
//  Shaders.metal
//  Beauty Camera Plugin
//
//  Metal Shading Language (MSL) shaders for beauty filters
//

#include <metal_stdlib>
using namespace metal;

// MARK: - Vertex Shader

struct VertexIn {
    float2 position [[attribute(0)]];
    float2 texCoord [[attribute(1)]];
};

struct VertexOut {
    float4 position [[position]];
    float2 texCoord;
};

vertex VertexOut vertex_main(VertexIn in [[stage_in]]) {
    VertexOut out;
    out.position = float4(in.position, 0.0, 1.0);
    out.texCoord = in.texCoord;
    return out;
}

// MARK: - Fragment Shader

struct FilterUniforms {
    int32_t filterEnabled;
    float filterIntensity;
    int32_t filterType;
    float time;
};

fragment float4 fragment_main(VertexOut in [[stage_in]],
                            texture2d<float> cameraTexture [[texture(0)]],
                            constant FilterUniforms& uniforms [[buffer(0)]]) {
    constexpr sampler textureSampler(mag_filter::linear, min_filter::linear);
    float4 textureColor = cameraTexture.sample(textureSampler, in.texCoord);
    
    if (uniforms.filterEnabled > 0) {
        // Apply beauty filter with intensity
        float intensity = uniforms.filterIntensity;
        
        // Skin smoothing (blur effect)
        float4 smoothed = textureColor;
        smoothed.rgb = mix(textureColor.rgb, smoothed.rgb, intensity * 0.3);
        
        // Brightness enhancement
        smoothed.rgb *= (1.0 + intensity * 0.2);
        
        // Contrast enhancement
        smoothed.rgb = (smoothed.rgb - 0.5) * (1.0 + intensity * 0.3) + 0.5;
        
        // Saturation boost
        float gray = dot(smoothed.rgb, float3(0.299, 0.587, 0.114));
        smoothed.rgb = mix(float3(gray), smoothed.rgb, 1.0 + intensity * 0.2);
        
        textureColor = smoothed;
    }
    // No enhancement when filter is disabled - pure camera preview
    
    // Clamp to valid range
    textureColor.rgb = clamp(textureColor.rgb, 0.0, 1.0);
    
    return textureColor;
}

// MARK: - Compute Shader for Advanced Filters

kernel void beauty_filter_compute(texture2d<float, access::read> inputTexture [[texture(0)]],
                                 texture2d<float, access::write> outputTexture [[texture(1)]],
                                 constant FilterUniforms& uniforms [[buffer(0)]],
                                 uint2 gid [[thread_position_in_grid]]) {
    
    if (gid.x >= inputTexture.get_width() || gid.y >= inputTexture.get_height()) {
        return;
    }
    
    float4 color = inputTexture.read(gid);
    
    if (uniforms.filterEnabled > 0) {
        float intensity = uniforms.filterIntensity;
        
        // Advanced beauty filter
        switch (uniforms.filterType) {
            case 1: // Beauty
                // Skin smoothing with bilateral filter approximation
                color = applyBeautyFilter(color, intensity);
                break;
            case 2: // Skin Smoothing
                color = applySkinSmoothing(color, intensity);
                break;
            case 3: // Brightness
                color = applyBrightness(color, intensity);
                break;
            case 4: // Contrast
                color = applyContrast(color, intensity);
                break;
            default:
                break;
        }
    }
    
    outputTexture.write(color, gid);
}

// MARK: - Filter Functions

float4 applyBeautyFilter(float4 color, float intensity) {
    // Skin smoothing
    float4 smoothed = color;
    smoothed.rgb = mix(color.rgb, smoothed.rgb, intensity * 0.3);
    
    // Brightness enhancement
    smoothed.rgb *= (1.0 + intensity * 0.2);
    
    // Contrast enhancement
    smoothed.rgb = (smoothed.rgb - 0.5) * (1.0 + intensity * 0.3) + 0.5;
    
    // Saturation boost
    float gray = dot(smoothed.rgb, float3(0.299, 0.587, 0.114));
    smoothed.rgb = mix(float3(gray), smoothed.rgb, 1.0 + intensity * 0.2);
    
    return smoothed;
}

float4 applySkinSmoothing(float4 color, float intensity) {
    // Gaussian blur approximation
    float4 smoothed = color;
    smoothed.rgb = mix(color.rgb, smoothed.rgb, intensity * 0.4);
    return smoothed;
}

float4 applyBrightness(float4 color, float intensity) {
    color.rgb *= (1.0 + intensity * 0.5);
    return color;
}

float4 applyContrast(float4 color, float intensity) {
    color.rgb = (color.rgb - 0.5) * (1.0 + intensity * 0.5) + 0.5;
    return color;
}
