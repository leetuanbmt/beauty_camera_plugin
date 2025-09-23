// Passthrough fragment shader
// Simply passes through the input texture without modification

precision mediump float;
varying vec2 texCoord;
uniform sampler2D uTexture;

void main() {
    gl_FragColor = texture2D(uTexture, texCoord);
}
