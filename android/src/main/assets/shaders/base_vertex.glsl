// Base vertex shader for all filters
// Provides common vertex processing functionality

attribute vec4 vPosition;
attribute vec2 vTexCoord;
varying vec2 texCoord;
uniform mat4 uMVPMatrix;

void main() {
    gl_Position = uMVPMatrix * vPosition;
    texCoord = vTexCoord;
}
