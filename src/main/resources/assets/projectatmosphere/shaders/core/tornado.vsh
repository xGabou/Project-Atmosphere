#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float Time;

out vec2 texCoord;

void main() {
    texCoord = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
