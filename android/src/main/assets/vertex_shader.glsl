#version 100
attribute vec4 aPosition;
attribute vec2 aTextureCoord;
uniform mat4 uTextureMatrix;
varying vec2 vTextureCoord;
void main() {
    gl_Position = aPosition;
    vTextureCoord = (uTextureMatrix * vec4(aTextureCoord, 0.0, 1.0)).xy;
}
