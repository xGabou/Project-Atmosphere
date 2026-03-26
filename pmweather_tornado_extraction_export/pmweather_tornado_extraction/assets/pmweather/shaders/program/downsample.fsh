#version 330

uniform sampler2D DiffuseSampler;
uniform sampler2D MainSampler;
uniform float downsample;

in vec2 texCoord;
out vec4 fragColor;

void main(){
    vec2 uv = texCoord*downsample;
    if(uv.x > 1 || uv.y > 1){
        discard;
    }

    vec4 texel = texture(MainSampler, uv);
    fragColor = vec4(texel.rgb, 1.0);
}