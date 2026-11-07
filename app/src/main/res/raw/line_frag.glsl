#extension GL_OES_standard_derivatives : enable
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
        // Soft Gaussian-like drop shadow penumbra
        // Feathered smoothly from center out to edges
        float shadowAlpha = smoothstep(1.0, 0.05, absS) * 0.42;

        // Soft caps at start and end of shadow
        float cap = smoothstep(0.0, 0.025, vCounters) * smoothstep(1.0, 0.975, vCounters);
        shadowAlpha *= cap;

        if (shadowAlpha < 0.01) discard;

        gl_FragColor = vec4(0.02, 0.02, 0.04, shadowAlpha);
        return;
    }

    // ================= MAIN 3D VOLUMETRIC STROKE =================
    // 1. Compute 3D cylinder normal across ribbon cross-section
    float nx = s;
    float nz = sqrt(max(0.0, 1.0 - s * s));
    vec3 normal = normalize(vec3(nx, 0.25, nz));

    // 2. Studio directional key light (top-right-front)
    vec3 lightDir = normalize(vec3(0.35, 0.70, 0.60));
    float diffuse = max(dot(normal, lightDir), 0.0);

    // 3. Specular highlight running down the center spine of the 3D tube
    vec3 viewDir = vec3(0.0, 0.0, 1.0);
    vec3 halfVec = normalize(lightDir + viewDir);
    float spec = pow(max(dot(normal, halfVec), 0.0), 20.0) * 0.55;

    // 4. Ambient Occlusion / Rim Shadow at edges of tube
    float rimAO = smoothstep(1.0, 0.15, absS);
    float lighting = 0.50 + 0.50 * diffuse * rimAO;

    // 5. Rich, vibrant final color with specular shine
    vec3 litColor = vColor.rgb * lighting + vec3(spec);

    // 6. Anti-aliased outer edge (soft 12% falloff for silky smooth edges)
    float alpha = vColor.a * smoothstep(1.0, 0.88, absS);

    // 7. Smooth rounded cap ends
    float cap = smoothstep(0.0, 0.018, vCounters) * smoothstep(1.0, 0.982, vCounters);
    alpha *= cap;

    if (alpha < 0.01) discard;

    gl_FragColor = vec4(litColor, alpha);
}
