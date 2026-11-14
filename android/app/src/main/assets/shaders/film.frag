#version 300 es
precision highp float;
precision highp int;
uniform highp sampler2D sourceImage;
uniform highp sampler3D filmLut;
uniform vec3 domainMin;
uniform vec3 domainMax;
uniform int lutSize;
uniform float strength;
uniform float exposureEv;
uniform float temperature;
out vec4 result;
vec3 decodeSrgb(vec3 s) {
    return mix(pow((s + 0.055) / 1.055, vec3(2.4)), s / 12.92, lessThanEqual(s, vec3(0.04045)));
}
vec3 encodeSrgb(vec3 l) {
    return mix(1.055 * pow(l, vec3(1.0 / 2.4)) - 0.055, 12.92 * l, lessThanEqual(l, vec3(0.0031308)));
}
vec3 sampleFilm(vec3 encoded) {
    vec3 p = clamp((encoded - domainMin) / (domainMax - domainMin), 0.0, 1.0) * float(lutSize - 1);
    ivec3 low = ivec3(floor(p));
    ivec3 high = min(low + 1, ivec3(lutSize - 1));
    vec3 f = p - vec3(low);
    vec3 sum = vec3(0.0);
    for (int b = 0; b < 2; ++b) for (int g = 0; g < 2; ++g) for (int r = 0; r < 2; ++r) {
        ivec3 at = ivec3(r == 0 ? low.x : high.x, g == 0 ? low.y : high.y, b == 0 ? low.z : high.z);
        vec3 w = vec3(r == 0 ? 1.0-f.x : f.x, g == 0 ? 1.0-f.y : f.y, b == 0 ? 1.0-f.z : f.z);
        sum += texelFetch(filmLut, at, 0).rgb * w.x * w.y * w.z;
    }
    return sum;
}
void main() {
    // Plain RGBA8 textures/FBO: all transfer functions are explicit here.
    vec3 s = texelFetch(sourceImage, ivec2(gl_FragCoord.xy), 0).rgb;
    float t = temperature / 100.0;
    vec3 corrected = clamp(decodeSrgb(s) * vec3(exp2(0.25*t), 1.0, exp2(-0.25*t)) * exp2(exposureEv), 0.0, 1.0);
    vec3 film = clamp(sampleFilm(encodeSrgb(corrected)), 0.0, 1.0);
    vec3 mixed = mix(corrected, decodeSrgb(film), strength);
    result = vec4(clamp(encodeSrgb(mixed), 0.0, 1.0), 1.0);
}
