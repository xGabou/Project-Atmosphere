#version 330

uniform sampler2D DiffuseSampler;
uniform sampler2D DownsampledDepthSampler;
uniform sampler2D DepthSampler;
uniform sampler2D CloudSampler;
uniform float downsample;
uniform float glowFix;
uniform float doBlur;

uniform vec2 OutSize;

in vec2 texCoord;

out vec4 fragColor;

#define near 0.05
#define far 6000.0
float linearizeDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (2.0 * near * far) / (far + near - z * (far - near));
}

vec4 textureBilinear(sampler2D s, vec2 uv){
    vec4 tl = texture(s, uv);
    vec4 tr = textureOffset(s, uv, ivec2(1,0));
    vec4 bl = textureOffset(s, uv, ivec2(0,1));
    vec4 br = textureOffset(s, uv, ivec2(1,1));

    vec2 f = fract(uv*OutSize);

    vec4 ta = mix(tl, tr, f.x);
    vec4 tb = mix(bl, br, f.x);

    return mix(ta, tb, f.y);
}

vec4 textureBilinearOffset(sampler2D s, vec2 uv, ivec2 offset){
    vec2 perc = vec2(1.0)/OutSize;
    uv += perc*vec2(offset);
    vec4 tl = texture(s, uv);
    vec4 tr = textureOffset(s, uv, ivec2(1,0));
    vec4 bl = textureOffset(s, uv, ivec2(0,1));
    vec4 br = textureOffset(s, uv, ivec2(1,1));

    vec2 f = fract(uv*OutSize);

    vec4 ta = mix(tl, tr, f.x);
    vec4 tb = mix(bl, br, f.x);

    return mix(ta, tb, f.y);
}

void main(){
    vec4 texel = texture(DiffuseSampler, texCoord);
    vec4 cloud = textureBilinear(CloudSampler, texCoord/downsample);
    float odepth = linearizeDepth(texture(DepthSampler, texCoord).r);

    ivec2 off = ivec2(0,0);
    if(downsample > 1.0 && glowFix > 0.5){
        vec4 a = textureBilinearOffset(CloudSampler, texCoord/downsample, ivec2(1,0));
        if(a.r > cloud.r){
            off = ivec2(1,0);
        }
        cloud = max(cloud, a);

        a = textureBilinearOffset(CloudSampler, texCoord/downsample, ivec2(-1,0));
        if(a.r > cloud.r){
            off = ivec2(-1,0);
        }
        cloud = max(cloud, a);

        a = textureBilinearOffset(CloudSampler, texCoord/downsample, ivec2(0,1));
        if(a.r > cloud.r){
            off = ivec2(0,1);
        }
        cloud = max(cloud, a);

        a = textureBilinearOffset(CloudSampler, texCoord/downsample, ivec2(0,-1));
        if(a.r > cloud.r){
            off = ivec2(0,-1);
        }
        cloud = max(cloud, a);
    }

    int res = 3;
    float size = 0.0012;
    int count = 1;

    if(glowFix < 0.5 && doBlur > 0.5){
        for(int x=-res; x <= res; x++){
            for(int y=-res; y <= res; y++){
                if(x != 0 || y != 0){
                    vec2 offset = (vec2(float(x), float(y))/float(res))*size;

                    float depth = linearizeDepth(texture(DepthSampler, texCoord + offset).r);

                    if(abs(depth-odepth) < 1){
                        cloud += textureBilinear(CloudSampler, (texCoord/downsample) + offset);
                        count++;
                    }
                }
            }
        }
    }

    cloud /= count;

    vec3 col = texel.rgb;
    vec4 renderOut = cloud;

    col = col * (1.0-renderOut.a) + renderOut.rgb;

    fragColor = vec4(col, 1.0);
}