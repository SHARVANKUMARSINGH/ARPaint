precision mediump float;

uniform int isShadowPass;
varying vec4 vColor;
varying float vCounters;
varying float vSide;
varying float depth;

void main() {
    float s = clamp(vSide, -1.0, 1.0);
    float absS = abs(s);

    if (isShadowPass == 1) {
        // Soft drop shadow feathered outwards from stroke center
        // Center has full shadow opacity, fading smoothly towards outer edges
        float shadowEdge = 1.0 - smoothstep(0.15, 1.0, absS);
        float shadowAlpha = clamp(vColor.a * shadowEdge * 0.42, 0.0, 1.0);

        if (shadowAlpha < 0.02) discard;

        gl_FragColor = vec4(0.02, 0.02, 0.04, shadowAlpha);
        return;
    }

    // ================= MAIN 3D VOLUMETRIC STROKE =================
    // 1. Semi-cylindrical 3D normal across ribbon cross-section
    float nx = s;
    float nz = sqrt(max(0.0, 1.0 - s * s));
    vec3 normal = normalize(vec3(nx, 0.20, nz));

    // 2. Directional key light from upper right
    vec3 lightDir = normalize(vec3(0.35, 0.65, 0.65));
    float diffuse = max(dot(normal, lightDir), 0.0);

    // 3. Crisp specular gloss highlight down the cylinder center
    vec3 viewDir = vec3(0.0, 0.0, 1.0);
    vec3 halfVec = normalize(lightDir + viewDir);
    float spec = pow(max(dot(normal, halfVec), 0.0), 16.0) * 0.45;

    // 4. Ambient Occlusion: slight darkening towards the extreme edges of the tube
    float rimAO = 1.0 - 0.35 * absS * absS;
    float lighting = 0.55 + 0.45 * diffuse * rimAO;

    // 5. Final vibrant lit color
    vec3 litColor = vColor.rgb * lighting + vec3(spec);

    // 6. Silky smooth anti-aliased edge falloff (outer 15% of ribbon width)
    float edgeAlpha = 1.0 - smoothstep(0.85, 1.0, absS);
    float alpha = clamp(vColor.a * edgeAlpha, 0.0, 1.0);

    if (alpha < 0.02) discard;

    gl_FragColor = vec4(litColor, alpha);
}
