#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float Time;

void main() {
    vec3 animatedPos = Position;
    animatedPos.y += sin(Time * 3.0) * 0.1;
    gl_Position = ProjMat * ModelViewMat * vec4(animatedPos, 1.0);
}
