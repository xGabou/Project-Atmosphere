#version 150

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 VolumeMin;
uniform vec3 VolumeMax;

in vec3 Position;
in vec2 UV0;

out vec2 texCoord;
out vec3 fragPos;

void main() {
    vec3 worldPos = mix(VolumeMin, VolumeMax, Position);
    gl_Position = ProjMat * ModelViewMat * vec4(worldPos, 1.0);
    texCoord = UV0;
    fragPos = worldPos;
}
