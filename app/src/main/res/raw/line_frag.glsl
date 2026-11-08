precision mediump float;

uniform float visibility;
uniform float alphaTest;

varying vec4 vColor;
varying float depth;
varying float vCounters;
varying float vSide;

void main() {
    vec4 c = vColor;
    float absS = abs(clamp(vSide, -1.0, 1.0));

    // Silky anti-aliased edge falloff on the outer 12% of the ribbon
    float edgeAlpha = 1.0 - smoothstep(0.88, 1.0, absS);
    c.a *= edgeAlpha;

    if (c.a < alphaTest) discard;

    gl_FragColor = c;
    gl_FragColor.a *= step(vCounters, visibility);
}
