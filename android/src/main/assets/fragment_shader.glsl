#extension GL_OES_EGL_image_external : require

precision mediump float;
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;

// Thêm uniform landmark (giả sử tối đa 468 điểm)
uniform vec2 uLandmarks[468];
uniform int uLandmarkCount;

void main() {
    vec4 color = texture2D(sTexture, vTextureCoord);
    // Ví dụ: làm sáng vùng quanh landmark miệng (landmark 0-10)
    float highlight = 0.0;
    for (int i = 0; i < 10; ++i) {
        if (i >= uLandmarkCount) break;
        float dist = distance(vTextureCoord, uLandmarks[i]);
        if (dist < 0.05) {
            highlight += 1.0 - dist * 20.0;
        }
    }
    highlight = clamp(highlight, 0.0, 1.0);
    color.rgb += highlight * 0.2;
    gl_FragColor = color;
}