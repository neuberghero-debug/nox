#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform NoxSky {
    vec4 Look;
    vec4 Amounts;
    vec4 ColorLow;
    vec4 ColorHigh;
    vec4 ColorBand;
    vec4 Zenith;
    vec4 Horizon;
};

layout(location = 0) in vec3 Position;

layout(location = 0) out vec3 skyDirection;
layout(location = 1) out vec2 ndc;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    ndc = Position.xy;

    // cinematic camera — hole parked upper-right of the menu column so the
    // button stack reads on clean space. Barely-visible drift, no normalize
    // here (fragment normalizes; the interpolated length feeds fwidth AA).
    float t = Look.z * 0.014;
    vec3 fwd = normalize(vec3(0.10, 0.30, 0.95));
    vec3 rgt = normalize(cross(fwd, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(rgt, fwd);
    float aspect = max(Amounts.w, 0.5);
    vec2 cam = Position.xy - vec2(0.43 + 0.015 * sin(t), 0.18 + 0.012 * cos(t));
    skyDirection = fwd + rgt * cam.x * aspect * 0.42 + up * cam.y * 0.42;
}
