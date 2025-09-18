#version 100
#extension GL_OES_EGL_image_external : require

precision mediump float;
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;

void main() {
    // Chỉ lấy mẫu màu từ texture của camera và hiển thị
    gl_FragColor = texture2D(sTexture, vTextureCoord);
}