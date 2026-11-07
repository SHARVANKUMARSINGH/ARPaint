precision mediump float;

attribute vec3 position;
attribute vec3 previous;
attribute vec3 next;
attribute float side;
attribute float width;
attribute float counters;
attribute vec3 color;

uniform mat4 projectionMatrix;
uniform mat4 modelViewMatrix;
uniform vec2 resolution;
uniform float opacity;
uniform int isShadowPass;

varying vec4 vColor;
varying float vCounters;
varying float vSide;
varying float depth;

vec2 fix(vec4 i, float aspect) {
    vec2 res = i.xy / max(0.0001, i.w);
    res.x *= aspect;
    return res;
}

void main() {
    float aspect = resolution.x / resolution.y;
    vCounters = counters;
    vSide = side;

    vec3 currentPos = position;
    vec3 prevPosVec = previous;
    vec3 nextPosVec = next;

    // Drop shadow projection:
    // Cast shadow downward along -Y (gravity in AR space) and slight light offset
    if (isShadowPass == 1) {
        vec3 shadowOffset = vec3(0.010, -0.038, -0.010);
        currentPos += shadowOffset;
        prevPosVec += shadowOffset;
        nextPosVec += shadowOffset;
        vColor = vec4(0.02, 0.02, 0.04, 0.42);
    } else {
        vColor = vec4(color, opacity);
    }

    mat4 m = projectionMatrix * modelViewMatrix;
    vec4 finalPosition = m * vec4(currentPos, 1.0);
    vec4 prevPos = m * vec4(prevPosVec, 1.0);
    vec4 nextPos = m * vec4(nextPosVec, 1.0);

    vec2 currentP = fix(finalPosition, aspect);
    vec2 prevP = fix(prevPos, aspect);
    vec2 nextP = fix(nextPos, aspect);

    // Compute screen-space 2D ribbon direction
    vec2 dir;
    if (length(currentP - prevP) < 0.00001 && length(nextP - currentP) < 0.00001) {
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

    // True physical 3D thickness in AR space:
    // width=0.10 -> ~2.5mm, width=0.33 -> ~8.5mm, width=0.80 -> ~20mm, width=1.80 -> ~45mm
    float worldThickness = 0.025 * width;
    if (isShadowPass == 1) {
        worldThickness *= 1.40; // Soft shadow spreads wider
    }

    // Convert physical meters to clip space
    float clipThickness = worldThickness * projectionMatrix[1][1];

    // Guarantee minimum stroke visibility (min ~3.5 pixels) regardless of distance
    float pixelWidthRatio = 1.0 / (resolution.y * 0.5);
    float minClipWidth = 3.5 * pixelWidthRatio * finalPosition.w;
    float w = max(clipThickness, minClipWidth);

    vec4 offset = vec4(normal * side * 0.5 * w, 0.0, 0.0);
    finalPosition.xy += offset.xy;

    gl_Position = finalPosition;
    depth = gl_Position.z;
}
