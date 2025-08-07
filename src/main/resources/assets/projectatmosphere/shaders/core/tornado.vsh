#version 150

// --- vertex inputs
in vec3  Position;
in vec2  UV0;

// --- your declared matrices & swirl params
uniform mat4 ModelMat;
uniform mat4 ViewMat;
uniform mat4 ProjMat;

uniform float Time;
uniform float TwistSpeed;
uniform float BaseRadius;
uniform float TopRadius;
uniform float Height;

// --- pass these to the fragment shader
out vec2  texCoord;
out float vRadius;

const float PI = 3.14159265;

void main() {
    // normalized height along the funnel [0..1]
    float h = clamp(Position.y / Height, 0.0, 1.0);

    // compute swirl rotation angle
    float sway = Time * TwistSpeed + h * 5.0;
    float cosA = cos(sway);
    float sinA = sin(sway);

    // rotate original XZ
    float x0 = Position.x;
    float z0 = Position.z;
    vec3 pos = Position;
    pos.x = x0 * cosA - z0 * sinA;
    pos.z = x0 * sinA + z0 * cosA;

    // compute funnel radius at this height (used for shading, not geometry)
    vRadius = mix(BaseRadius, TopRadius, h);

    // pass through the original UV0 (so your Java‐computed UVs still work)
    texCoord = UV0;

    // final clip‐space transform
    gl_Position = ProjMat * ViewMat * ModelMat * vec4(pos, 1.0);
}
