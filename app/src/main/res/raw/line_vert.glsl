precision mediump float;

attribute vec3 position;
attribute vec3 previous;
attribute vec3 next;
attribute vec3 color;
attribute float side;
attribute float width;
attribute float counters;

uniform mat4 projectionMatrix;
uniform mat4 modelViewMatrix;
uniform vec2 resolution;
uniform float lineWidth;
uniform float lineDepthScale;
uniform float opacity;
uniform float near;
uniform float far;
uniform float sizeAttenuation;

varying vec4 vColor;
varying float vCounters;
varying float vSide;
varying float depth;

vec2 fix(vec4 i, float aspect) {
    vec2 res = i.xy / max(0.0001, i.w);
    res.x *= aspect;
    return res;
}

float map(float value, float inMin, float inMax, float outMin, float outMax) {
    return ((value - inMin) / (inMax - inMin) * (outMax - outMin) + outMin);
}

void main() {
    float safeResY = max(1.0, resolution.y);
    float aspect = resolution.x / safeResY;
    float pixelWidthRatio = 1.0 / (resolution.x * projectionMatrix[0][0]);

    vColor = vec4(color, opacity);
    vCounters = counters;
    vSide = side;

    mat4 m = projectionMatrix * modelViewMatrix;
    vec4 finalPosition = m * vec4(position, 1.0);
    vec4 prevPos = m * vec4(previous, 1.0);
    vec4 nextPos = m * vec4(next, 1.0);

    vec2 currentP = fix(finalPosition, aspect);
    vec2 prevP = fix(prevPos, aspect);
    vec2 nextP = fix(nextPos, aspect);

    float pixelWidth = finalPosition.w * pixelWidthRatio;
    float w = 1.8 * pixelWidth * lineWidth * width;

    if (sizeAttenuation == 1.0) {
        w = 1.8 * lineWidth * width * map(clamp((finalPosition.z - near) / far, 0.0, 1.0), 0.0, 1.0, 1.0, lineDepthScale);
    }

    // Minimum stroke visibility guarantee: never drop below 3.0 screen pixels
    float minW = 3.0 * pixelWidth;
    w = max(w, minW);

    vec2 dir;
    if (length(nextP - currentP) < 0.00001 && length(currentP - prevP) < 0.00001) {
        dir = vec2(1.0, 0.0);
    } else if (length(nextP - currentP) < 0.00001) {
        dir = normalize(currentP - prevP);
    } else if (length(currentP - prevP) < 0.00001) {
        dir = normalize(nextP - currentP);
    } else {
        vec2 dir1 = normalize(currentP - prevP);
        vec2 dir2 = normalize(nextP - currentP);
        vec2 sum = dir1 + dir2;
        if (length(sum) < 0.00001) {
            dir = vec2(-dir1.y, dir1.x);
        } else {
            dir = normalize(sum);
        }
    }

    vec2 normal = vec2(-dir.y, dir.x);
    normal.x /= aspect;
    normal *= 0.5 * w;

    vec4 offset = vec4(normal * side, 0.0, 1.0);
    finalPosition.xy += offset.xy;

    gl_Position = finalPosition;
    depth = gl_Position.z;
}
