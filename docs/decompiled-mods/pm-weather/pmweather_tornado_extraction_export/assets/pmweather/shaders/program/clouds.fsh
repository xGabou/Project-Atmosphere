#version 330

#define PI 3.1415926535897932384626433832795

struct CloudReturn{
    float cloudDensity;
    float rainDensity;
    float dustDensity;
    float brighten;
};

struct Render{
    vec4 col;
    float lightEnergy;
    float lightningEnergy;
    float depth;
};

float hash( float p ) {
    p = fract(p * .1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float onoise(vec3 pos) {
    // The noise function returns a value in the range -1.0f -> 1.0f

    vec3 x = pos * 2.0;
    vec3 p = floor(x);
    vec3 f = fract(x);

    f       = f*f*(3.0-2.0*f);
    float n = p.x + p.y*57.0 + 113.0*p.z;

    return mix(mix(mix( hash(n+0.0), hash(n+1.0),f.x),
                   mix( hash(n+57.0), hash(n+58.0),f.x),f.y),
               mix(mix( hash(n+113.0), hash(n+114.0),f.x),
                     mix( hash(n+170.0), hash(n+171.0),f.x),f.y),f.z);
}

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;
uniform sampler2D NoiseSamplerX;
uniform sampler2D NoiseSamplerY;
uniform sampler2D NoiseSamplerZ;

uniform sampler2D dhDepthTex0;
uniform sampler2D dhDepthTex1;
uniform float hasDHDepth;
uniform float dhNearPlane;
uniform float dhFarPlane;
uniform float dhRenderDistance;
uniform mat4 dhProjection;
uniform mat4 dhProjectionInverse;

float noise2d(vec2 x){
    x /= 512.0;
    return (texture(NoiseSamplerX, x, -1.0).r-0.5)*2.0;
}

float noise(vec3 x){
    x /= vec3(100.0,180.0,100.0)*3.0;

    x.y = fract(x.y)*512.0;
    float iz = floor(x.y);
    float fz = fract(x.y);
    vec2 a_off = vec2(23.0, 29.0)*(iz)/512.0;
    vec2 b_off = vec2(23.0, 29.0)*(iz+1.0)/512.0;
    float a = texture(NoiseSampler, x.xz + a_off, -1.0).r;
    float b = texture(NoiseSampler, x.xz + b_off, -1.0).r;
    return (mix(a,b,fz)-0.5)*2.0;
}

in vec2 texCoord;

uniform float layer0height;
uniform float layerCheight;
uniform float stormSize;
uniform float rain;
uniform float snow;
uniform float rainStrength;
uniform int stormCount;
uniform float stormPositions[48];
uniform float stormVelocities[32];
uniform float stormStages[16];
uniform float stormEnergies[16];
uniform float stormTypes[16];
uniform float stormOcclusions[16];
uniform float tornadoWindspeeds[16];
uniform float tornadoWidths[16];
uniform float tornadoTouchdownSpeeds[16];
uniform float visualOnlys[16];
uniform float stormDyings[16];
uniform float stormSpins[16];
uniform float tornadoShapes[16];
uniform int lightningCount;
uniform float lightningStrikes[192];
uniform float lightningBrightness[64];

uniform vec2 OutSize;
uniform int maxSteps;
uniform float downsample;
uniform float stepSize;
uniform float time;
uniform float worldTime;
uniform float overcastPerc;

out vec4 fragColor;

uniform vec3 pos;
uniform vec2 scroll;
uniform vec3 sunDir;
uniform vec3 lightingColor;
uniform vec3 skyColor;
uniform mat4 proj;
uniform mat4 viewmat;
uniform mat4 vmat;
uniform float lightIntensity;
uniform float simpleLighting;

uniform int quality;

uniform float fogStart;
uniform float fogEnd;

uniform float _FOV;

const float inf = uintBitsToFloat(0x7F800000u);

uniform float nearPlane;
uniform float farPlane;
uniform float renderDistance;

float linearizeDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (2.0 * nearPlane * farPlane) / (farPlane + nearPlane - z * (farPlane - nearPlane));
}

float fbm(vec3 x, int octaves, float lacunarity, float gain, float amplitude){
    float y = 0.0;

    octaves -= int(floor(pow(4.0-quality, 0.75)));

    for(int i = 0; i < max(octaves, 1); i++){
        y += amplitude * noise(x);
        x *= lacunarity;
        amplitude *= gain;
    }
    return y;
}

vec3 getRayDir(vec2 screenUV){
    vec2 uv = (screenUV*2.0)-1.0;
    vec4 n = vec4(uv, 0.0, 1.0);
    vec4 f = vec4(uv, 1.0, 1.0);
    n = proj * n;
    f = proj * f;
    n.xyz /= n.w;
    f.xyz /= f.w;
    return normalize((viewmat*f).xyz - (viewmat*n).xyz);
}

mat2 spin(float angle){
    return mat2(cos(angle),-sin(angle),sin(angle),cos(angle));
}

float distanceSqr(vec2 a, vec2 b){
    vec2 c = a-b;
    return dot(c,c);
}

vec2 nearestPoint(vec2 v, vec2 w, vec2 p){
    float l2 = distanceSqr(v, w);
    float t = clamp(dot(p-v, w-v) / l2, 0, 1);
    return v + t * (w - v);
}

float minimumDistance(vec2 v, vec2 w, vec2 p){
    float l2 = distanceSqr(v, w);
    if(l2 == 0.0) return distance(p, v);

    vec2 proj = nearestPoint(v,w,p);
    return distance(p, proj);
}

float atan2(float y, float x){
    return x == 0.0 ? sign(y)*PI/2 : atan(y, x);
}

CloudReturn getClouds(vec3 position, int octaveReduction){
    float totalCloud = 0.0;
    float totalBGCloud = 0.0;
    float totalRain = 0.0;
    float totalDust = 0.0;
    float upperLevel = 0.0;

    float nearestStorm = inf;

    vec2 s = vec2(worldTime);

    vec3 noisePos = position + vec3(s.x, -worldTime/2.0, s.y);
    vec3 cloudNoisePos = position + vec3(worldTime*0.5, 0, worldTime*0.5);

    vec3 noisePos2 = position + vec3(s.x*4.0, -worldTime/2.0, s.y*4.0);
    vec3 cloudNoisePos2 = position + vec3(worldTime*1.5, 0, worldTime*1.5);

    vec3 noisePosIT = position + vec3(s.x, worldTime/2.0, s.y);

    float noise1 = -10.0;//fbm(noisePos/90.0, 5-octaveReduction, 2.0, 0.5, 1.0);
    float noise2 = -10.0;//fbm(noisePosIT/120.0, 2-octaveReduction, 2.0, 0.3, 1.0);
    float noise3 = -10.0;//noise2d(noisePos.xz/25.0)+(noise1*0.4);

    if(octaveReduction == 0 && quality == 4){
        octaveReduction = -2;
    }

    float bdensityNoise = min(noise2d(cloudNoisePos.xz/400.0), 1.0);
    float bcloudNoise = min(noise2d(noisePos.xz/30.0), 1.0);
    float bbaseNoise = noise2d(noisePos.xz/90.0);
    float bheightNoise = noise2d(noisePos.zx/90.0);

    float bgc = 0.0;
    float bv = clamp(bdensityNoise-(1.0-overcastPerc), 0.0, 1.0);
    bgc += max(pow(bv, 0.25), 0.0);
    float bgCloudBase = mix(mix(0.0, 150.0, clamp((bbaseNoise+1)*0.5, 0.0, 1.0)), 0.0, bv*bv*bv)+layer0height;
    float bgCloudHeight = mix(300.0, 850.0, clamp((bheightNoise+1)*0.5, 0.0, 1.0));
    bgc *= mix(clamp((bcloudNoise-0.1)+bv, 0, 1), 1, bv);
    bgc = pow(bgc, 0.5)*0.5;
    bgc *= mix(bgCloudHeight/850.0, 1.0, sqrt(bv));

    if(position.y > layer0height && position.y < layer0height+1000 && overcastPerc > 0){
        float bgClouds = 0;
        float detailNoise = fbm(noisePos/90.0, 5-octaveReduction, 2.0, 0.5, 1.0);
        float densityNoise = min(bdensityNoise+(detailNoise*0.1), 1.0);
        float cloudNoise = min(bcloudNoise+(detailNoise*0.3), 1.0);
        float baseNoise = bbaseNoise+(detailNoise*0.1);
        float heightNoise = bheightNoise+(detailNoise*0.1);

        float v = clamp(densityNoise-(1.0-overcastPerc), 0.0, 1.0);
        bgClouds += max(pow(v, 0.25), 0.0);

        float base = mix(mix(0.0, 150.0, clamp((baseNoise+1)*0.5, 0.0, 1.0)), 0.0, v*v*v)+layer0height;
        float height = mix(300.0, 850.0, clamp((heightNoise+1)*0.5, 0.0, 1.0));

        bgClouds *= mix(clamp((cloudNoise-0.1)+(v*v), 0, 1), 1, v*v*v);

        bgClouds *= 1.0 + detailNoise*0.2;

        v = clamp((position.y-base)/height, 0.0, 1.0);
        bgClouds -= v*v;
        bgClouds *= 1-clamp((base-position.y)/50.0, 0.0, 1.0);

        bgClouds = pow(bgClouds, 0.1)*0.5;

        totalBGCloud = max(totalBGCloud, bgClouds);
        upperLevel = max(upperLevel, bgClouds);
    }

    if(position.y > layerCheight && position.y < layerCheight+2000 && overcastPerc > 0){
        float bgClouds = 0.0;
        float detailNoise = fbm(noisePos2/150.0, 3-octaveReduction, 2.0, 0.5, 1.0);
        float densityNoise = min(noise2d(cloudNoisePos2.xz/800.0)+(detailNoise*0.1), 1.0);
        float warpNoiseX = noise2d(noisePos2.zx/400.0);
        float warpNoiseY = noise2d(noisePos2.xz/400.0);
        float cloudNoise = min(noise2d((noisePos2.xz/200.0) + (vec2(warpNoiseX, warpNoiseY)*100.0))+(detailNoise*0.2), 1.0);
        float baseNoise = noise2d(noisePos2.xz/400.0)+(detailNoise*0.1);
        float heightNoise = noise2d(noisePos2.zx/150.0)+(detailNoise*0.1);

        float bandNoise = noise2d(noisePos2.xz/800.0)+(detailNoise*0.1);
        float bandNoise2 = noise2d(noisePos2.zx/400.0)+(detailNoise*0.1);
        float bandNoiseX = noise2d(noisePos2.xz/150.0)+(detailNoise*0.1);
        float bandNoiseZ = noise2d(noisePos2.zx/150.0)+(detailNoise*0.1);

        float v = clamp(densityNoise-(1.0-overcastPerc), 0.0, 1.0);
        bgClouds += max(pow(v, 0.25), 0.0);

        float base = mix(mix(0, 1000.0, clamp((baseNoise+1)*0.5, 0.0, 1.0)), 0.0, v*v*v)+layerCheight;
        float height = mix(200.0, 500.0, clamp((heightNoise+1)*0.5, 0.0, 1.0));

        bgClouds *= mix(clamp(sqrt(cloudNoise+0.3)+(v*v), 0.0, 1.0), 1, v*v*v);

        bgClouds *= 1.0 + detailNoise*0.2;

        vec3 bandingPos = noisePos2 + vec3(bandNoiseX, 0, bandNoiseZ)*1200.0;
        float banding = sin((bandingPos.x+bandingPos.z)/(600.0 * (1 + bandNoise2*0.3)));
        banding = abs(banding * banding * banding * banding);

        bgClouds = mix(bgClouds, bgClouds*banding, clamp((bandNoise+1)*0.5, 0.0, 1.0));

        v = clamp((position.y-base)/height, 0.0, 1.0);
        bgClouds -= v*v;
        bgClouds *= 1-clamp((base-position.y)/50.0, 0.0, 1.0);

        v = clamp(densityNoise, 0.0, 1.0);
        bgClouds = pow(bgClouds, 0.25)*mix(0.35, 0.0, v*v*v*v*v);

        totalBGCloud = max(totalBGCloud, bgClouds);
    }

    for(int i = 0; i < stormCount; i++){
        float clouds = 0.0;
        float rainam = 0.0;
        vec3 pos = vec3(stormPositions[i*3], stormPositions[(i*3)+1], stormPositions[(i*3)+2]);
        vec2 vel = vec2(stormVelocities[i*2], stormVelocities[(i*2)+1]);
        float stage = stormStages[i];
        float energy = stormEnergies[i];
        float windspeed = tornadoWindspeeds[i];
        float width = max(tornadoWidths[i], 15.0);
        float touchdownSpeed = tornadoTouchdownSpeeds[i];
        float tornadoShape = tornadoShapes[i];
        float stormSpin = stormSpins[i];
        float stormType = stormTypes[i];
        float occlusion = stormOcclusions[i];
        bool visualOnly = false;
        bool dying = false;
        bool tornadic = false;

        if(visualOnlys[i] > 0.0){
            visualOnly = true;
        }

        if(stormDyings[i] > 0.0){
            dying = true;
        }

        float smoothStage = stage + (energy/100.0);

        // Hurricane
        if(abs(stormType-2) < 0.1 && windspeed > 0){
            float hHeight = layer0height+1000.0;
            float heightP = (position.y-layer0height)/(hHeight-layer0height);
            float sze = width;
            pos += vec3(pow(heightP, 2.0)*sze*0.1, 0, -pow(heightP, 2.0)*sze*0.05);
            float dist = distance(position.xz, pos.xz);

            sze *= 1.0+(pow(heightP, 2.0)*0.4*(1.0+(clamp(dist/sze, 0.0, 1.0)*1.0)));

            float eyeSize = 0.1;
            float eyeCutSize = 0.1 + (heightP*0.2);

            if(dist > sze*1.2 || position.y > hHeight){
                continue;
            }

            if(noise1 < -9.0 && (position.y <= hHeight)){
                noise1 = fbm(noisePos/90.0, 4-octaveReduction, 2.0, 0.5, 1.0);
                noise2 = fbm(noisePosIT/120.0, 2-octaveReduction, 2.0, 0.3, 1.0);
                noise3 = noise2d(noisePos.xz/25.0)+(noise1*0.4);
            }

            if(noise1 > -9.0){
                dist *= 1.0+(((noise3+1.0)/2.0)*0.2);
            }

            float intensity = pow(clamp(windspeed/65.0, 0.0, 1.0), 0.85);

            vec2 relPos = pos.xz-position.xz;

            float d = sze/(3.0+(windspeed/12.0));
            float d2 = sze/(1.15+(windspeed/12.0));
            float dE = (sze*0.3)/(1.75+(windspeed/12.0));

            float fac = 1.0+(max((dist-(sze*(eyeSize*2.0)))/sze, 0.0)*2.0);
            d *= fac;
            d2 *= fac;

            float angle = ((atan2(relPos.y, relPos.x)-(dist/d)));
            float angle2 = ((atan2(relPos.y, relPos.x)-(dist/d2)));
            float angleE = ((atan2(relPos.y, relPos.x)-(dist/dE)));

            float weak = 0.0;
            float strong = 0.0;
            float intense = 0.0;

            float staticBands = sin(angle-(PI/2.0));
            staticBands *= pow(clamp(dist/(sze*0.25), 0.0, 1.0), 0.1);
            staticBands *= 1.25*pow(intensity, 0.75);
            if(staticBands < 0){
                weak += abs(staticBands);
            }else{
                weak += abs(staticBands) * pow(1.0-clamp(dist/(sze*0.65), 0.0, 1.0), 0.5);
                weak *= clamp((windspeed-70.0)/40.0, 0.0, 1.0);
            }

            float rotatingBands = sin((angle2+radians(worldTime/8.0))*6.0);
            rotatingBands *= pow(clamp(dist/(sze*0.25), 0.0, 1.0), 0.1);
            rotatingBands *= 1.25*pow(intensity, 0.75);
            strong += mix((abs(rotatingBands)*0.3)+0.7, weak, 0.45);
            intense += mix((abs(rotatingBands)*0.2)+0.8, weak, 0.3);
            weak = ((abs(rotatingBands)*0.3)+0.6)*weak;

            float localCloud = 0.0;

            localCloud += mix(mix(weak, strong, clamp((windspeed-40.0)/90.0, 0.0, 1.0)), intense, clamp((windspeed-120.0)/60.0, 0.0, 1.0));

            float eye = sin((angleE+radians(worldTime/4.0))*2.0);
            eye = mix(eye, 1.0, 1.0-clamp(dist/(sze*eyeSize), 0, 1));
            localCloud = max(pow(1.0-clamp(dist/(sze*eyeSize*2.0), 0.0, 1.0), 0.5)*(abs(eye*0.3)+0.7)*1.4*intensity, localCloud);

            localCloud *= pow(1.0-clamp(dist/sze, 0.0, 1.0), 0.5);
            localCloud *= mix(1.0, pow(clamp(dist/(sze*eyeCutSize), 0.0, 1.0), 2.0), 0.5+(clamp((windspeed-65.0)/60.0, 0.0, 1.0)*0.5));

            float noiseMain = (1.0+noise1)/2.0;
            localCloud *= mix(0.8 + noiseMain*0.4, 1.0, clamp((windspeed-75.0)/50.0, 0.0, 1.0));
            localCloud *= 0.8 + noiseMain*0.4;

            float eyeBG = mix(1.0, pow(clamp(dist/(sze*eyeCutSize), 0, 1), 2), clamp((windspeed-65.0)/60.0, 0.0, 1.0));
            totalBGCloud *= eyeBG;
            bgc *= eyeBG;

            if(localCloud > 0.7){
                float dif = (localCloud-0.7)/3.0;
                localCloud -= dif;
            }

            if(heightP >= 0.0 && heightP <= 1.0){
                clouds += localCloud-(0.4*(1.0-pow((1.4*heightP)-0.4, 2.0)));
                clouds *= pow(1.0-heightP, 0.5);
            }

            if(heightP <= 1.0){
                float rain = max(localCloud-0.15, 0.0)*2.0;
                rain *= pow(1.0-clamp(dist/width, 0.0, 1.0), 0.35);
                rain *= 0.6 + noise2*0.5;
                rain *= pow(1.0-heightP, 0.5);
                rainam += rain*rainStrength;
            }

            clouds = sqrt(clouds)*0.8;
        }

        // Squall
        if(abs(stormType-1) < 0.1){
            if(stage >= 2.99){
                smoothStage = 3.0;
            }

            float subcellular = clamp(smoothStage, 0.0, 1.0);
            float cellular = clamp(smoothStage-0.5, 0.0, 1.0);

            float stormHeight = mix(400.0, 1600.0, cellular);
            float baseHeight = layer0height;

            float v = clamp((position.y-layer0height)/1600.0, 0, 1);
            v *= v;
            vec2 right = normalize(vel.yx*vec2(1,-1));
            vec2 fwd = normalize(vel.xy);
            vec3 right3 = vec3(right.x, 0, right.y);

            float rawDist = distance(position.xz, pos.xz);

            vec3 l = right3*-(stormSize*5);
            vec3 r = right3*(stormSize*5);

            vec3 offset = vec3(-fwd.x, 0.0, -fwd.y)*pow(clamp(rawDist/(stormSize*5.0), 0.0, 1.0), 2.0)*(stormSize*1.5);
            l += offset;
            r += offset;

            l += vec3(2000.0*v, 0, -900.0*v);
            r += vec3(2000.0*v, 0, -900.0*v);

            l += pos;
            r += pos;

            float dist = minimumDistance(l.xz, r.xz, position.xz);

            float sze = stormSize*10.0;

            if(position.y > layer0height+1000.0){
                sze *= 8.0;
            }

            if(dist > sze){
                continue;
            }

            if(noise1 < -9.0 && (dist < stormSize*1.6 || smoothStage > 1) && (position.y < stormHeight*1.5)){
                noise1 = fbm(noisePos/90.0, 4-octaveReduction, 2.0, 0.5, 1.0);
                noise2 = fbm(noisePosIT/120.0, 2-octaveReduction, 2.0, 0.3, 1.0);
                noise3 = noise2d(noisePos.xz/25.0)+(noise1*0.4);
            }

            if(noise1 > -9){
                baseHeight += noise1*15.0;
            }

            float noiseMain = noise1;

            v = clamp((position.y-layer0height)/1600.0, 0, 1);
            noiseMain = mix(noiseMain, noise3+(noise1*0.25), cellular*v);

            float size = stormSize*1.5;
            stormHeight *= 1 + noiseMain*0.1;

            offset = vec3(fwd.x, 0, fwd.y)*stormSize*0.5;
            l += offset;
            r += offset;
            vec2 nearPoint = nearestPoint(l.xz, r.xz, position.xz);
            vec2 facing = position.xz-nearPoint;
            float behind = -dot(facing, fwd);
            behind += noise3*stormSize*0.2;

            if(behind > 0.0){
                baseHeight *= 1.0 - (pow(clamp(1.0-((behind-20.0)/stormSize), 0, 1), 4.0)*0.45*clamp(smoothStage-1.0, 0, 1.0));

                float heightFromBase = position.y-baseHeight;

                if(position.y < layer0height){
                    clouds -= clamp((heightFromBase-30.0)/30.0, 0, 1)*2.0;
                }

                float baseChange = pow(clamp((behind-stormSize)/(stormSize*4.0), 0, 1), 0.4)*300.0;
                baseHeight += baseChange;
                stormHeight -= baseChange;
            }

            float heightFromBase = position.y-baseHeight;
            float heightPerc = clamp(heightFromBase/stormHeight, 0.0, 1.0);
            float currentHeight = heightPerc*stormHeight;
            float heightFromTop = stormHeight-currentHeight;

            v = clamp(currentHeight/max(stormHeight, 1600.0), 0, 1.0);
            v *= v;
            pos += vec3(2000.0*v, 0, -900.0*v);

            size = mix(size, mix(mix(size, size/2.0, cellular), size, sqrt(1.0-clamp(currentHeight/300.0, 0.0, 1.0))), pow(heightPerc, 0.25));

            v = 1-clamp(heightFromTop/1600.0, 0.0, 1.0);
            size = mix(size, mix(size, size*8.0, cellular), v*v*v*v*v);
            size = mix(size, size/4.0, clamp(1.0-smoothStage, 0.0, 1.0));

            size = mix(size, size*4, clamp(behind/(stormSize/4), 0.0, 1.0)*clamp(smoothStage-1.0, 0.0, 1.0));

            size *= 1.0 + noiseMain*0.4;

            float distPerc = clamp(dist/size, 0.0, 1.0);

            if(distPerc >= 0.999){
                continue;
            }

            if(position.y < baseHeight){
                totalBGCloud *= distPerc*distPerc*distPerc;
            }

            float distBased = 1.0-distPerc;
            float cloudField = (noise3-clamp(1.0-(smoothStage-1.0), 0.0, 1.0))*clamp(heightFromTop/65.0, 0.0, 1.0)*pow(1-distPerc, 0.25);
            cloudField *= clamp(rawDist/mix(stormSize, stormSize*5.0, clamp(smoothStage/1.25, 0.0, 1.0)), 0.0, 1.0);

            clouds += mix(0.0, mix(cloudField, distBased, clamp(smoothStage/2.0, 0.0, 1.0)), step(baseHeight, position.y)*step(position.y, baseHeight+stormHeight));

            clouds *= clamp(heightFromTop/mix(100.0, 300.0, clamp(smoothStage*0.8, 0.0, 1.0)), 0.0, 1.0);

            size = stormSize*0.75*clamp(smoothStage, 0.0, 1.0);
            size = mix(size, size*8.0, clamp(behind/(stormSize/4.0), 0.0, 1.0)*clamp(smoothStage-1.0, 0.0, 1.0));
            size *= 1.0 + noise2*0.4;
            distPerc = clamp(dist/size, 0.0, 1.0);

            clouds = sqrt(clouds)*0.8;

            float rain = step(position.y, baseHeight);
            rain *= 1.0-pow(distPerc, 1.35);
            rain *= 0.6 + noise2*0.5;
            rain *= clamp(smoothStage-0.5, 0.0, 1.0);

            if(behind > 0.0){
                rain = pow(rain, 2.5);
            }

            behind -= stormSize/3;

            if(behind < 0.0){
                rain *= 1.0-clamp(abs(behind)/(stormSize/3), 0.0, 1.0);
            }

            rainam += rain*rainStrength;
        }

        // Supercell
        if(abs(stormType) < 0.1){
            if(stage >= 2.99){
                tornadic = true;
                smoothStage = 3.0;
            }

            float visualOnlyStep = step(float(visualOnly), 0.5);
            float subceullular = clamp(smoothStage-0.5, 0.0, visualOnlyStep);
            float supercellular = clamp(smoothStage-1.5, 0.0, visualOnlyStep);

            float stormHeight = mix(400.0, 1600.0, subceullular);
            float baseHeight = layer0height;

            float v = clamp((position.y-layer0height)/1600.0, 0, 1);
            v *= v;
            float dist = distance(position.xz, pos.xz + vec2(2000.0*v, -900.0*v));
            float coreDist = distance(position.xz, pos.xz + vec2(2000.0, -900.0));
            float sze = stormSize*2.0;

            float clearBG = mix(1.0, pow(clamp(dist/(sze*2.0), 0.0, 1.0), 2.0), clamp(smoothStage, 0.0, 1.0));
            totalBGCloud *= clearBG;
            bgc *= clearBG;

            if(position.y > layer0height+1000.0){
                sze *= 8.0;
            }else if(coreDist <= sze*2.0){
                sze *= 2.5;
            }

            if(min(coreDist, dist) > sze){
                continue;
            }

            if(noise1 < -9.0 && (dist < mix(stormSize*1.6, stormSize*0.85, 1-visualOnlyStep) || smoothStage > 1.0) && (position.y < stormHeight*1.5)){
                noise1 = fbm(noisePos/90.0, 4-octaveReduction, 2.0, 0.5, 1.0);
                noise2 = fbm(noisePosIT/120.0, 2-octaveReduction, 2.0, 0.3, 1.0);
                noise3 = noise2d(noisePos.xz/25.0)+(noise1*0.4);
            }

            if(noise1 > -9.0){
                baseHeight += noise1*15.0;
            }

            vec3 localPos = position-pos;
            mat2 speen = spin((-time/(20.0*50.0))+(dist/(stormSize*2.0)));
            mat2 speen2 = spin((-time/(20.0*15.0))+(dist/(stormSize/2.0)));

            vec3 spinNoisePos = vec3(speen*localPos.xz, position.y-time);
            vec3 spinNoisePos2 = vec3(speen2*localPos.xz, position.y-time);

            float spinNoise1 = 0.0;//fbm(spinNoisePos/120.0, 5-octaveReduction, 2.0, 0.5, 1.0);
            float spinNoise2 = 0.0;//fbm(spinNoisePos2/80.0, 5-octaveReduction, 2.0, 0.5, 1.0);

            float heightFromBase = position.y-baseHeight;

            if(position.y < baseHeight && smoothStage > 1.75 && position.y > baseHeight-120.0 && dist < stormSize){
                spinNoise2 = fbm(spinNoisePos2/80.0, 4-octaveReduction, 2.0, 0.5, 1.0);
            }else if(position.y > baseHeight && heightFromBase < 200.0 && smoothStage > 1.75 && dist < stormSize*2.5){
                spinNoise1 = fbm(spinNoisePos/120.0, 4-octaveReduction, 2.0, 0.5, 1.0);
            }

            float noiseMain = mix(noise1, mix(spinNoise1, spinNoise2, step(position.y, baseHeight)), clamp((smoothStage-1.75)*3.0, 0.0, 1.0)*clamp(1.0-(heightFromBase/200.0), 0.0, 1.0));

            v = clamp((position.y-layer0height)/1600.0, 0, 1);
            noiseMain = mix(noiseMain, noise3+(noise1*0.25), supercellular*v);

            float size = stormSize*1.5;
            stormHeight *= 1.0 + noiseMain*0.1;

            float heightPerc = clamp(heightFromBase/stormHeight, 0.0, 1.0);
            float currentHeight = heightPerc*stormHeight;
            float heightFromTop = stormHeight-currentHeight;

            v = clamp(currentHeight/max(stormHeight, 1600.0), 0.0, 1.0);
            v *= v;
            pos += vec3(2000.0*v, 0, -900.0*v);
            dist = distance(position.xz, pos.xz);

            //float rawDistPerc = max(dist/size, 0);

            size = mix(size, mix(mix(size, size/2.0, supercellular), size, sqrt(1-clamp(currentHeight/300, 0.0, 1.0))), pow(heightPerc, 0.25));

            v = 1-clamp(heightFromTop/1600.0, 0.0, 1.0);
            size = mix(size, mix(size, size*8.0, supercellular), v*v*v*v*v);
            size = mix(size, size/4.0, clamp(1.0-smoothStage, 0.0, 1.0)*visualOnlyStep);
            size = mix(size, size/2.0, 1.0-visualOnlyStep);

            size *= 1.0 + noiseMain*0.4;
            float distPerc = clamp(dist/size, 0.0, 1.0);
            float coreDistPerc = clamp(coreDist/(size*4.0), 0.0, 1.0);

            if(min(distPerc, coreDistPerc) >= 0.999){
                continue;
            }

            float distBased = 1.0-distPerc;
            float cloudField = (noise3-clamp(1.0-smoothStage, 0.0, 1.0))*clamp(heightFromTop/65.0, 0.0, 1.0)*pow(1.0-distPerc, 0.25);

            clouds += mix(0, mix(cloudField, distBased, clamp(smoothStage*1.2, 0.0, visualOnlyStep)), step(baseHeight, position.y)*step(position.y, baseHeight+stormHeight));

            clouds *= clamp(heightFromTop/mix(100.0, 300.0, clamp(smoothStage*0.8, 0.0, 1.0)), 0.0, 1.0);

            //clouds *= 1 + clamp(noiseMain*2*clamp(1-smoothStage, 1-visualOnlyStep, 1), -1, 0.4);
            //clouds -= clamp(1-smoothStage, 0, 1)*2;

            size *= 0.35;
            distPerc = clamp(dist/size, 0.0, 1.0);

            float wallcloudLower = 120.0*pow(1.0-distPerc, 0.25)*clamp((smoothStage-2.0)*3.0, 0.0, 1.0);
            float wallcloud = mix(0.0, 1.0-distPerc, step(position.y, baseHeight)*step(baseHeight-wallcloudLower, position.y));
            wallcloud *= clamp((smoothStage-2.0)*5.0, 0.0, 1.0);
            clouds += wallcloud;

            float fnlTop = max(baseHeight-105.0, pos.y+30.0);
            float torPerc = clamp(windspeed/touchdownSpeed, 0.0, 1.0);
            float tornadoHeight = mix(fnlTop, pos.y-50.0, torPerc);

            if(tornadic && position.y < baseHeight-wallcloudLower && position.y > pos.y-50.0 && dist < max(width*4.5, stormSize/3.0)){
                float tornado = 1.0;
                float percFnlHeight = clamp((position.y-pos.y)/(fnlTop-pos.y), 0.0, 1.0);
                float percCos = (-cos(percFnlHeight*3.141592)+1.0)*0.5;

                float torShape = mix(tornadoShape, 20.0, pow(clamp(width/500.0, 0.0, 1.0), 1.75));

                float wid = (width/2.5) + ((width/2.5)*percFnlHeight*torPerc) + ((stormSize/mix(torShape+2.0, torShape, torPerc)) * percFnlHeight*percFnlHeight*percFnlHeight*percFnlHeight);
                wid = mix(wid, 0.0, (1.0-percFnlHeight)*(1.0-torPerc));
                float th = 1-clamp((position.y-tornadoHeight)/30.0, 0.0, 1.0);
                wid = mix(wid, 0.0, th*th*th);
                float maxWid = (width/4.0) + ((width/4.0)*torPerc) + ((stormSize/8.0) * torPerc);
                vec3 torPos = pos;

                float ropeMod = mix(3.0, 1.0, clamp(width/30.0, 0.0, 1.0));
                ropeMod = mix(ropeMod, 1.0, clamp((windspeed-65.0)/30.0, 0.0, 1.0));
                ropeMod = mix(0.1, ropeMod, clamp((windspeed/touchdownSpeed)*1.35, 0.0, 1.0));

                float nx = onoise(vec3(pos.xz/500.0, time/200.0))*40.0*ropeMod;
                float nz = onoise(vec3(time/200.0, pos.zx/500.0))*40.0*ropeMod;
                vec3 attachmentPoint = vec3(nx, 0.0, nz);

                float xAdd = onoise(vec3(pos.xz/250.0, (time/200.0)+((position.y*ropeMod)/50.0)))*20.0*ropeMod;
                float zAdd = onoise(vec3((time/200.0)+((position.y*ropeMod)/50.0), pos.zx/250.0))*20.0*ropeMod;

                float a = pow(percFnlHeight, 0.75);
                xAdd *= a;
                zAdd *= a;

                torPos += mix(vec3(0), vec3(attachmentPoint.x, 0, attachmentPoint.z), percCos);
                torPos += vec3(xAdd, 0, zAdd);

                float torDist = distance(torPos.xz, position.xz);
                vec3 localTorPos = position-torPos;

                float widPerc = 1-clamp(torDist/wid, 0.0, 1.0);
                float widMaxPerc = clamp(wid/maxWid, 0.0, 1.0);
                float rotation = -stormSpin*3;
                float rotation2 = -stormSpin/1.5;

                mat2 torSpin = spin(rotation+(torDist/50.0));
                mat2 torSpin2 = spin(rotation2+(torDist/150.0));
                mat2 torSpin3 = spin(rotation2+(torDist/60.0));
                vec3 torSpinPos = vec3(torSpin*localTorPos.xz, position.y-(time/2.0));
                vec3 torSpinPos2 = vec3(torSpin2*localTorPos.xz, position.y-(time/2.0));
                vec3 torSpinPos3 = vec3(torSpin3*localTorPos.xz, position.y-(time/2.0));

                float nComp1 = fbm(torSpinPos/20.0, 3-octaveReduction, 2.0, 0.5, 1.0);
                float nComp2 = fbm(torSpinPos2/40.0, 3-octaveReduction, 2.0, 0.5, 1.0);

                float torNoise1 = mix(nComp1, nComp2, sqrt(widMaxPerc));

                wid *= mix(0.8 + (torNoise1*0.2), 0.9, clamp(width/1000, 0.0, 1.0)*0.9);

                widPerc = 1-clamp(torDist/wid, 0.0, 1.0);

                tornado *= widPerc;
                tornado = pow(tornado, 1.5)*4.0;
                tornado *= clamp((position.y-tornadoHeight)/20.0, 0, 1.0);
                tornado *= 0.8 + (torNoise1*0.2);

                float dust = 1.0;
                float dcNoise1 = fbm(torSpinPos3/20.0, 3-octaveReduction, 2.0, 0.5, 1.0);

                float dcPerc = clamp((windspeed-45.0)/30.0, 0.0, 1.0);
                float h = 40.0 + (dcNoise1*15.0);
                float dcTop = pos.y+(max(dcPerc, 0.35)*h);
                float percDCHeight = clamp((position.y-(pos.y-10.0))/(dcTop-pos.y), 0.0, 1.0);

                wid = ((width/1.5) + ((width/1.5)*percFnlHeight*torPerc) + 25.0) + (25.0*pow(percDCHeight, 1.5)*pow(dcPerc, 0.75));
                wid *= mix(0.6 + (dcNoise1*0.5), 0.85, clamp(width/500, 0.0, 1.0)*0.9);
                widPerc = 1-clamp(torDist/wid, 0.0, 1.0);
                widPerc = pow(widPerc, 0.25);
                v = clamp(torDist/(wid*0.9), 0.0, 1.0);
                widPerc *= v*v*v;
                dust *= widPerc;
                dust = pow(dust, 1.5)*0.15;
                dust *= clamp((dcTop-position.y)/20, 0.0, 1.0);
                dust *= clamp((position.y-(pos.y-20))/20, 0.0, 1.0);
                dust *= 0.8 + (dcNoise1*0.2);
                dust *= dcPerc;
                dust *= 1.0-clamp((width-50.0)/200.0, 0.0, 1.0);
                tornado = max(tornado, dust);
                totalDust = max(totalDust, dust);

                clouds = max(clouds, tornado);
            }

            size = stormSize*0.75*clamp(smoothStage, 0.0, 1.0);
            size *= 1.0 + noise2*0.4;
            distPerc = clamp(dist/size, 0.0, 1.0);
            coreDistPerc = clamp(coreDist/(size*4.0), 0.0, 1.0);

            clouds = sqrt(clouds)*0.8;

            float rain = step(position.y, baseHeight)*visualOnlyStep;
            rain *= 1.0-pow(distPerc, 1.35);
            rain *= 0.6 + noise2*0.5;
            rain *= clamp(smoothStage, 0.0, 1.0);
            rainam += rain*rainStrength*mix(1.0, (occlusion*0.5)+0.5, clamp(smoothStage-2.0, 0.0, 1.0));

            rain = step(position.y, baseHeight+1300.0)*visualOnlyStep;
            rain *= 1.0-pow(coreDistPerc, 1.35);
            rain *= 0.6 + noise2*0.5;
            rain *= clamp((smoothStage-2.0)*2.0, 0.0, 1.0);
            rainam = max(rainam, rain*rainStrength);
        }

        totalCloud = max(totalCloud, clouds);
        totalRain = max(totalRain, rainam);
    }

    if(position.y <= bgCloudBase && bgc > 0.15){
        if(noise2 < -9.0){
            noise2 = fbm(noisePosIT/120.0, 2-octaveReduction, 2.0, 0.3, 1.0);
        }

        float rain = (bgc-0.15)*1.65;
        rain *= 0.6 + noise2*0.5;
        totalRain = max(totalRain, rain*rainStrength);
    }

    return CloudReturn(max(max(totalCloud, totalBGCloud), 0.0), max(totalRain, 0.0), max(totalDust, 0.0), max(upperLevel, 0.0));
}

vec3 worldPos(vec2 uv, float depth, mat4 invProj){
    vec4 ndc;
    ndc.xy = (uv-0.5)*2.0;
    ndc.z = (depth-0.5)*2.0;
    ndc.w = 1.0;

    vec4 clip = invProj*ndc;
    vec4 view = viewmat*(clip/clip.w);
    vec3 result = view.xyz;

    return result;
}

float BeersLaw(float dist, float absorbtion){
    return exp(-dist * absorbtion);
}

float HenyeyGreenstein(float g, float mu){
    float denom = 1.0 + g * (g - 2.0 * mu);
    denom = sqrt(denom * denom * denom);
    float heyey = (1.0 - g * g) / (4.0 * 3.14 * denom);
    return mix(heyey, 1.0, 0.1);
}

float lightmarch(vec3 ro, vec3 dir, int steps, float marchSize){
    float totalDensity = 0.0;

    float d0 = 0.0;
    for(int i = 0; i < steps; i++){
        vec3 p = ro+(dir*d0);

        CloudReturn d = getClouds(p, 8);
        totalDensity += d.cloudDensity*0.005*marchSize;
        d0 += marchSize;
        marchSize += marchSize/4.0;

        if(BeersLaw(totalDensity, 0.9) < 0.05){
            break;
        }
    }

    return BeersLaw(totalDensity, 0.9);
}

Render render(vec2 uv, float maxDepth){
    float light = clamp((sunDir.y+0.1)/0.3, 0, 1);
    vec3 ro = pos;
    vec3 rd = getRayDir(uv);
    float offset = onoise(vec3(uv*800.0, 0))*1;
    float density = 0;
    float totalRain = 0;
    float rainDampen = 0;

    vec4 res = vec4(0.0);

    float d0 = 1.0;//nearPlane;

    float ms = float(maxSteps);

    bool inCloud = false;
    int cyclesNotInCloud = 0;
    int count = 0;

    float totalTransmittance = 1.0;
    float lightEnergy = 0.0;
    float lightningEnergy = 0.0;

    float phase = HenyeyGreenstein(0.3, dot(rd, sunDir));
    float moonLight = 0.0;
    float moonPhase = 0.0;

    float depth = maxDepth;

    if(sunDir.y < 0.1){
        moonPhase = HenyeyGreenstein(0.3, dot(rd, sunDir*-1.0));
        moonLight = clamp((sunDir.y-0.1)/-0.1, 0.0, 1.0)*0.25;
        moonPhase = clamp(moonPhase*moonPhase*moonPhase*4.0, 0.0, 1.0);
    }

    for(int i = 0; i < ms; i++){
        vec3 p = ro+(rd*d0);

        if(d0 >= maxDepth || p.y > max(layerCheight+1000.0, layer0height+2000.0) || p.y < -64.0){
            break;
        }

        float v = (count+5.0)/60.0;

        float stepMult = v*v*v;
        float multiplier = 1.0;

        stepMult /= 2.0;

        if(p.y > layer0height+500.0){
            //stepMult *= 4;
        }

        if(quality == 0){
            stepMult *= 4.0;
            multiplier = 3.0;
        }else if(quality == 1){
            stepMult *= 2.0;
            multiplier = 1.5;
        }else if(quality == 4){
            stepMult /= 2.0;
            multiplier = 0.75;
        }

        if(!inCloud){
            stepMult *= 4.0;
        }

        float smult = 1.0;
        float s = max(((8.0+offset)*smult*stepMult), 0.5);

        float rDist = renderDistance*2.0;

        if(d0 < rDist){
            CloudReturn clouds = getClouds(p, 0);

            clouds.cloudDensity *= multiplier;
            clouds.rainDensity *= multiplier;
            clouds.dustDensity *= multiplier;

            float sf = (snow*1.5*clamp(1.0-(d0/256.0), 0.0, 1.0));
            float d = clouds.cloudDensity+sf;
            float r = clouds.rainDensity;

            d *= clamp(d0/25.0, 0.0, 1.0);
            r *= clamp(d0/35.0, 0.0, 1.0);

            d *= 1-clamp((d0-(rDist-1000.0))/1000.0, 0.0, 1.0);
            r *= 1-clamp((d0-(rDist-1000.0))/1000.0, 0.0, 1.0);

            float rd = d+(r*0.25*(s/8.0));

            count++;

            if(rd > 0){
                if(d0 < depth){
                    depth = d0;
                }

                cyclesNotInCloud = 0;

                if(!inCloud && d > 0){
                    inCloud = true;
                    d0 -= s;
                    count--;
                    continue;
                }

                density += rd*step(0.1, rd);
                totalRain += r;

                if(density > 5.0){
                    rd = max(rd, 2.0);
                    if(r > d){
                        r = 1.0;
                    }
                }

                if(d > 0.0 && totalRain < 0.01){
                    rainDampen += sqrt(d)*1.25;
                }

                float transmittance = mix(1.0, max(light, moonLight)*0.3, clamp(rd*4, 0.0, 1.0));

                if(light > 0 && d-sf > 0 && totalTransmittance > 0.015 && simpleLighting < 0.5){
                    int steps = 0;

                    switch(quality){
                        case 0:
                            steps = 2;
                            break;

                        case 1:
                            steps = 4;
                            break;

                        case 2:
                            steps = 6;
                            break;

                        case 3:
                            steps = 8;
                            break;

                        case 4:
                            steps = 10;
                            break;

                        default:
                            steps = 4;
                            break;
                    }

                    transmittance = lightmarch(p, sunDir, steps, (((10.0-float(steps))/10.0)*20.0)+4.0);
                }

                if(moonLight > 0 && light <= 0 && d-sf > 0 && totalTransmittance > 0.015 && simpleLighting < 0.5){
                    int steps = 0;

                    switch(quality){
                        case 0:
                            steps = 2;
                            break;

                        case 1:
                            steps = 4;
                            break;

                        case 2:
                            steps = 6;
                            break;

                        case 3:
                            steps = 8;
                            break;

                        case 4:
                            steps = 10;
                            break;

                        default:
                            steps = 4;
                            break;
                    }

                    transmittance = lightmarch(p, sunDir*-1, steps, (((10.0-float(steps))/10.0)*20.0)+4.0);
                }

                if(lightningCount > 0 && totalTransmittance > 0.015){
                    for(int j = 0; j < lightningCount; j++){
                        vec3 lPos = vec3(lightningStrikes[j*3], layer0height, lightningStrikes[(j*3)+2]);
                        float bright = lightningBrightness[j];
                        float dist = distance(lPos.xz, p.xz);

                        float l = pow(1-clamp(dist/750.0, 0.0, 1.0), 2.0)*bright;

                        l *= 1-clamp((p.y-lPos.y)/150.0, 0.0, 1.0);
                        l *= rd;

                        lightningEnergy += totalTransmittance * l;
                    }
                }

                float luminance = 1.25 * (pow(d, 0.65)*(clamp(s, 10.0, 35.0)/35.0)) * max(phase, moonPhase);

                vec3 cloudColor = mix(vec3(mix(0.5, 0.2, clamp(rain, 0.0, 1.0))), vec3(0.22, 0.302, 0.278), clamp(r, 0.0, 1.0));
                vec3 dustColor = vec3(0.2, 0.125, 0.071);
                float dustP = clamp(pow(clouds.dustDensity, 0.3), 0.0, 1.0);
                dustColor = mix(dustColor*2.5, dustColor*0.5, hash(dustP*100.015));

                cloudColor = mix(cloudColor, dustColor, clamp(pow(clouds.dustDensity, 0.1), 0.0, 1.0));
                cloudColor = mix(cloudColor, skyColor*0.5, 0.6);
                if(clouds.dustDensity <= 0.0){
                    cloudColor = mix(lightingColor*max(light, moonLight), cloudColor, clamp(pow(d, 0.65)+(r*5.0)+snow, 0, 1));
                }
                vec4 col = vec4(mix(cloudColor, vec3(0.0), clamp(rd, 0.0, 1.0))*light, clamp(rd, 0.0, 1.0));

                col.rgb *= col.a;

                res += col * (1.0-res.a);

                lightEnergy += totalTransmittance * luminance;
                totalTransmittance *= transmittance;

                if(totalTransmittance < 0.2){
                    totalTransmittance = 0.0;
                }
            }else{
                if(inCloud){
                    cyclesNotInCloud++;
                }

                if(cyclesNotInCloud >= 10.0){
                    inCloud = false;
                    cyclesNotInCloud = 0;
                }
            }

            if(density > 4.0 || rd >= 1.5){
                break;
            }
        }

        d0 += s;
    }

    totalRain -= rainDampen;

    float rv = clamp(1.0-rain, 0.0, 1.0);
    return Render(res, clamp(lightEnergy, mix(0.3, 0.1, clamp(max(rain*4, totalRain*0.15), 0, 1)), clamp(density, 0, 1))*max(light, moonLight)*clamp(density, 0, 1)*rv*rv, lightningEnergy, depth);
}

float getDistFromDepth(float depth, vec2 uv, mat4 invProj) {
    if (depth >= 1.0) return 1e10; // for sky
    vec3 viewPos = worldPos(uv, depth, invProj);
    return length(viewPos);
}

void main(){
    vec2 uv = texCoord*downsample;
    if(uv.x > 1.0 || uv.y > 1.0){
        discard;
    }

    float maxTotalRenderDist = hasDHDepth > 0.5 ? max(renderDistance, dhRenderDistance) : renderDistance;
    float sceneDistance = maxTotalRenderDist;
    float vanillaDepth = texture(DepthSampler, uv).r;

    if(vanillaDepth < 1.0){
        sceneDistance = getDistFromDepth(vanillaDepth, uv, proj);
    }

    if(hasDHDepth > 0.5){
        float dhDepth = texture(dhDepthTex0, uv).r;
        if(dhDepth < 1.0){
            float dhDistance = getDistFromDepth(dhDepth, uv, inverse(dhProjection));
            sceneDistance = min(sceneDistance, dhDistance);
        }

        if(vanillaDepth >= 1.0 && dhDepth >= 1.0){
            sceneDistance = maxTotalRenderDist*4.0;
        }
    }else{
        if(vanillaDepth >= 1.0){
            sceneDistance = maxTotalRenderDist*4.0;
        }
    }

    /*vec3 wPos = worldPos(uv, rawDepth);
    float depthCirc = min(length(wPos), farPlane);

    if(rawDepth >= 1.0){
        depthCirc = renderDistance*4.0;
    }*/

    sceneDistance = min(sceneDistance, maxTotalRenderDist*4.0);

    Render renderOut = render(uv, sceneDistance);

    fragColor = mix(mix(renderOut.col, vec4(lightingColor, renderOut.col.a), renderOut.lightEnergy*renderOut.col.a), vec4(vec3(1.0), renderOut.col.a), renderOut.lightningEnergy);
}