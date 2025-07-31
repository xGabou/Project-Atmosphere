#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float Time;

void main() {
    float angle = Position.y * 2.0 + Time;
    float radius = length(Position.xz);
    vec3 pos = vec3(cos(angle) * radius, Position.y, sin(angle) * radius);
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
}
