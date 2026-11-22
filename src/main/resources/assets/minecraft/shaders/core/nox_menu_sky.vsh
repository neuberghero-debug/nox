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

    // cinematic camera — hole sits slightly right + below center (reference
    // composition: Gargantua filling ~2/3 of frame, slow drift only)
    float t = Look.z * 0.018;
    vec3 fwd = normalize(vec3(0.02 + sin(t) * 0.03, 0.46 + cos(t * 0.63) * 0.02, 0.885));
    vec3 rgt = normalize(cross(fwd, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(rgt, fwd);
    float aspect = max(Amounts.w, 0.5);
    skyDirection = normalize(fwd + rgt * Position.x * aspect * 0.55 + up * Position.y * 0.55);
}
