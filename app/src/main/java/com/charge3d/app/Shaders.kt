package com.charge3d.app

/** GLSL ES 1.00 shaders. One full-screen quad; all visuals are drawn in the fragment shader. */
object Shaders {

    const val VERTEX = """
attribute vec2 aPos;
void main() { gl_Position = vec4(aPos, 0.0, 1.0); }
"""

    // Part 1: helpers, background and the 8 style backdrops.
    private const val FRAG_HEAD = """
precision highp float;
uniform vec2  uRes;
uniform float uTime;
uniform float uLevel;      // 0..1 battery level (smoothed)
uniform float uCharging;   // 1 while charging
uniform int   uStyle;
uniform int   uShape;
uniform int   uBg;
uniform int   uNC;         // number of colours 1..4
uniform vec3  uC0; uniform vec3 uC1; uniform vec3 uC2; uniform vec3 uC3;
uniform float uSpeed, uBright, uGlow, uPart, uSize;
uniform vec2  uRot;        // drag rotation
uniform vec2  uTap;        // last tap position (scene space)
uniform float uTapT;       // seconds since tap (large = none)
uniform int   uFx;         // double-tap effect 0..2

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
  vec2 i = floor(p), f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x),
             mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}
float fbm(vec2 p) {
  float s = 0.0, a = 0.5;
  for (int i = 0; i < 3; i++) { s += a * vnoise(p); p = p * 2.03 + 11.7; a *= 0.5; }
  return s;
}
vec2 rot(vec2 p, float a) { float c = cos(a), s = sin(a); return vec2(c * p.x - s * p.y, s * p.x + c * p.y); }
vec3 hueRot(vec3 c, float a) {
  const vec3 k = vec3(0.57735);
  float ca = cos(a), sa = sin(a);
  return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}
vec3 grad(float t) {
  t = clamp(t, 0.0, 1.0);
  if (uNC <= 1) return uC0;
  if (uNC == 2) return mix(uC0, uC1, t);
  if (uNC == 3) return t < 0.5 ? mix(uC0, uC1, t * 2.0) : mix(uC1, uC2, t * 2.0 - 1.0);
  float s = t * 3.0;
  if (s < 1.0) return mix(uC0, uC1, s);
  if (s < 2.0) return mix(uC1, uC2, s - 1.0);
  return mix(uC2, uC3, s - 2.0);
}

vec3 background(vec2 q, float t) {
  vec3 base = grad(0.0) * 0.06;
  if (uBg == 0) return vec3(0.0);
  if (uBg == 1) {
    float r = length(q);
    return mix(base * 1.6 + grad(1.0) * 0.05, vec3(0.0, 0.0, 0.01), smoothstep(0.0, 0.9, r));
  }
  if (uBg == 2) {
    vec2 g = floor(q * 60.0);
    float h = hash(g);
    float star = step(0.985, h) * (0.5 + 0.5 * sin(t * 2.0 + h * 40.0));
    return base + vec3(star) * 0.8;
  }
  vec2 gq = abs(fract(q * 12.0 + t * 0.05) - 0.5);
  float line = smoothstep(0.47, 0.5, max(gq.x, gq.y));
  return base + grad(0.5) * line * 0.12 * (1.0 - smoothstep(0.2, 0.9, length(q)));
}

// Animated glowing scenery behind the fill container, one per style.
vec3 backdrop(vec2 p, float t) {
  vec3 col = vec3(0.0);
  float r = length(p);
  float lvl = uLevel;
  if (uStyle == 0) {                       // Energy Core: glowing 3D plasma sphere
    float R = 0.5;
    if (r < R) {
      float z = sqrt(max(R * R - r * r, 0.0)) / R;
      vec2 n = rot(p / R, uRot.x + t * 0.15);
      float pl = fbm(n * 3.0 + vec2(t * 0.4, uRot.y * 2.0 + z * 2.0));
      float rim = pow(1.0 - z, 2.0);
      vec3 lightDir = normalize(vec3(-0.4, 0.5, 0.8));
      float diff = clamp(dot(vec3(p / R, z), lightDir), 0.0, 1.0);
      col = grad(pl * 0.9) * (0.10 + pl * 0.55 + diff * 0.45) + grad(0.9) * rim * 0.45;
    }
    col += grad(0.5) * exp(-max(r - R, 0.0) * 7.0) * 0.55 * (0.8 + 0.2 * sin(t * 3.0));
    float a = atan(p.y, p.x);
    col += grad(0.8) * pow(0.5 + 0.5 * sin(a * 8.0 + t * 1.5), 6.0) * exp(-r * 3.0) * 0.25 * uPart * 2.0;
  } else if (uStyle == 1) {                // Plasma Ring: rotating neon rings
    for (int k = 0; k < 3; k++) {
      float fk = float(k);
      vec2 pr = rot(p, fk * 1.05 + t * (0.35 + fk * 0.15) + uRot.x);
      float e = 0.28 + 0.65 * abs(sin(t * 0.25 + fk * 1.7 + uRot.y * 1.5));
      float d = abs(length(vec2(pr.x, pr.y / e)) - (0.5 + fk * 0.05)) * e;
      float a = atan(pr.y / e, pr.x);
      float bright = 0.45 + 0.55 * sin(a * 2.0 - t * 3.0 + fk);
      col += grad(fk / 2.0) * exp(-d * 55.0) * bright * 1.3;
    }
  } else if (uStyle == 2) {                // Liquid Battery: drifting colour blobs
    for (int k = 0; k < 4; k++) {
      float fk = float(k);
      vec2 c = vec2(sin(t * 0.3 + fk * 1.9) * 0.35, cos(t * 0.25 + fk * 2.7) * 0.4);
      col += grad(fk / 3.0) * exp(-length(p - c) * 3.5) * 0.5;
    }
  } else if (uStyle == 3) {                // Electric Pulse: lightning arcs
    for (int i = 0; i < 6; i++) {
      float fi = float(i);
      float a = fi * 1.0472 + floor(t * 2.0 + fi) * 0.7;
      vec2 d = vec2(cos(a), sin(a));
      float along = dot(p, d);
      float perp = dot(p, vec2(-d.y, d.x));
      perp += (vnoise(vec2(along * 12.0, t * 9.0 + fi * 5.0)) - 0.5) * 0.14 * along;
      float on = step(0.35, hash(vec2(floor(t * 8.0), fi)) + uPart * 0.5);
      col += grad(0.3 + fi * 0.1) * exp(-abs(perp) * 110.0) *
             smoothstep(0.3, 0.38, along) * smoothstep(0.95, 0.5, along) * on * 1.3;
    }
    col += grad(0.5) * exp(-max(r - 0.4, 0.0) * 8.0) * 0.3;
  } else if (uStyle == 4) {                // Holographic Battery: grid + scan sweep
    vec2 g = abs(fract(p * 9.0 + vec2(0.0, t * 0.1)) - 0.5);
    float line = smoothstep(0.46, 0.5, max(g.x, g.y));
    col += grad(0.4) * line * 0.35 * (1.0 - smoothstep(0.3, 0.9, r));
    float sweep = fract(t * 0.15) * 1.6 - 0.8;
    col += grad(0.9) * exp(-abs(p.y - sweep) * 25.0) * 0.5;
    col *= 0.8 + 0.2 * step(0.5, fract(p.y * 90.0));
    col += grad(0.5) * exp(-max(r - 0.45, 0.0) * 6.0) * 0.18;
  } else if (uStyle == 5) {                // Galaxy Charge: spiral galaxy
    vec2 pg = rot(p, uRot.x);
    float a = atan(pg.y, pg.x);
    float ang = a + r * 6.0 - t * 0.3;
    float arm = pow(0.5 + 0.5 * cos(ang * 2.0), 3.0);
    float dens = arm * exp(-r * 2.3) * (0.4 + fbm(vec2(ang * 1.5, r * 7.0)));
    col += grad(r * 1.8) * dens * 1.8;
    col += grad(0.1) * exp(-r * 8.0) * 0.8;
    vec2 sp = rot(p, -t * 0.05) * 38.0;
    float h = hash(floor(sp));
    float dotM = smoothstep(0.28, 0.0, length(fract(sp) - 0.5));
    col += vec3(step(1.0 - 0.04 * uPart * 2.0, h)) * dotM * (0.5 + 0.5 * sin(t * 3.0 + h * 50.0)) * exp(-r * 1.2);
  } else if (uStyle == 6) {                // Neon Wave: glowing sine ribbons
    for (int k = 0; k < 6; k++) {
      float fk = float(k);
      float amp = 0.05 + 0.07 * (0.3 + lvl);
      float y = sin(p.x * (3.0 + fk * 0.8) + t * (0.8 + fk * 0.2) + fk * 1.3 + uRot.x * 2.0) * amp
              + (fk - 2.5) * 0.09;
      col += grad(fk / 5.0) * exp(-abs(p.y - y) * 55.0) * 0.8;
    }
  } else {                                 // Quantum Energy: orbiting particles
    col += grad(0.5) * exp(-r * 9.0) * 0.7;
    int n = int(4.0 + uPart * 8.0);
    for (int i = 0; i < 12; i++) {
      if (i >= n) break;
      float fi = float(i);
      float orb = 0.22 + 0.032 * fi;
      float e = 0.35 + 0.6 * abs(sin(fi * 0.9 + uRot.y * 1.5 + t * 0.1));
      float tilt = fi * 0.9 + uRot.x;
      for (int j = 0; j < 4; j++) {
        float fj = float(j);
        float ang = t * (1.6 - fi * 0.07) + fi * 1.7 - fj * 0.12;
        vec2 pos = rot(vec2(cos(ang) * orb, sin(ang) * orb * e), tilt);
        col += grad(fi / 11.0) * exp(-length(p - pos) * (55.0 + fj * 30.0)) * (1.0 - fj * 0.22) * 1.6;
      }
    }
  }
  return col;
}
"""

    // Part 2: the fill container (battery / drum / circle) and main().
    private const val FRAG_MAIN = """
void main() {
  vec2 R = uRes;
  float m = min(R.x, R.y);
  vec2 q = (gl_FragCoord.xy - 0.5 * R) / m;
  vec2 p = q / uSize;
  float t = uTime * uSpeed;
  float px = 1.5 / (m * uSize);

  vec3 col = background(q, uTime);
  col += backdrop(p, t) * uGlow;

  // ---- fill container ----
  float inside = 0.0, sd = 1.0, surfY = 0.0, ry = 0.0, s = 0.0, hN = 0.0, extraRim = 0.0;
  float yMin = -0.30, yMax = 0.30;
  if (uShape == 0) {                       // battery
    vec2 b = abs(p - vec2(0.0, -0.02)) - vec2(0.20 - 0.04, 0.34 - 0.04);
    float body = length(max(b, 0.0)) + min(max(b.x, b.y), 0.0) - 0.04;
    vec2 c = abs(p - vec2(0.0, 0.375)) - vec2(0.07 - 0.015, 0.03 - 0.015);
    float cap = length(max(c, 0.0)) + min(max(c.x, c.y), 0.0) - 0.015;
    sd = min(body, cap);
    yMin = -0.36; yMax = 0.32;
  } else if (uShape == 1) {                // drum (cylinder)
    ry = 0.075;
    float rx = 0.22;
    s = sqrt(max(1.0 - (p.x * p.x) / (rx * rx), 0.0));
    sd = max(abs(p.x) - rx, abs(p.y) - (0.30 + ry * s));
    extraRim = exp(-abs(length(vec2(p.x / rx, (p.y - 0.30) / ry)) - 1.0) * 14.0) * step(-0.0001, rx - abs(p.x));
    yMin = -0.30; yMax = 0.30;
  } else {                                 // circle
    sd = length(p) - 0.30;
    yMin = -0.30; yMax = 0.30;
  }
  inside = 1.0 - smoothstep(-px, px, sd);
  float rim = exp(-abs(sd) / px * 0.35) * 0.9;

  float lvl = clamp(uLevel, 0.0, 1.0);
  float amp = (uStyle == 2) ? 0.016 : 0.008;
  float wave = sin(p.x * 16.0 + t * 2.2) * amp + sin(p.x * 9.0 - t * 1.6) * amp * 0.6;
  surfY = mix(yMin, yMax, lvl) + wave * smoothstep(0.0, 0.03, lvl) * smoothstep(1.0, 0.97, lvl);
  float filled;
  if (uShape == 1) filled = 1.0 - smoothstep(-px, px, p.y - (surfY + ry * s));
  else filled = 1.0 - smoothstep(-px, px, p.y - surfY);
  hN = clamp((p.y - yMin) / (yMax - yMin), 0.0, 1.0);

  // glass body
  vec3 glass = grad(0.5) * 0.10 + vec3(0.02);
  col = mix(col, col * 0.4 + glass, inside * 0.85);

  // liquid / energy fill
  vec3 fill = grad(hN) * (0.85 + 0.25 * sin(p.y * 10.0 - t * 2.0));
  if (uStyle == 3) fill *= 0.7 + 0.5 * vnoise(vec2(p.y * 14.0, t * 12.0));
  if (uStyle == 4) fill *= 0.6 + 0.4 * step(0.5, fract(p.y * 55.0 - t * 0.5));
  if (uStyle == 5 || uStyle == 7) { vec2 sq = vec2(p.x * 40.0, p.y * 40.0 - t * 3.0);
    fill += vec3(step(0.97, hash(floor(sq))) * smoothstep(0.3, 0.0, length(fract(sq) - 0.5))) * 0.8; }
  // bubbles rising through the fill
  int nb = int(2.0 + uPart * 10.0);
  for (int i = 0; i < 12; i++) {
    if (i >= nb) break;
    float fi = float(i);
    float sx = (hash(vec2(fi, 3.1)) - 0.5) * 0.36;
    float sp = 0.06 + hash(vec2(fi, 7.7)) * 0.1;
    float by = mix(yMin, surfY, fract(t * sp + hash(vec2(fi, 1.3))));
    float rad = 0.008 + hash(vec2(fi, 9.2)) * 0.012;
    float bd = length(p - vec2(sx * (uShape == 2 ? 0.8 : 1.0), by));
    fill += vec3(1.0) * smoothstep(rad, rad * 0.4, bd) * 0.5;
  }
  float surfLine = exp(-abs(p.y - surfY) * 70.0);
  fill += vec3(1.0) * surfLine * 0.9;
  if (uShape == 1) {                       // lighter ellipse on top of the drum liquid
    float e = length(vec2(p.x / 0.22, (p.y - surfY) / ry));
    fill = mix(fill, fill * 1.4 + 0.15, (1.0 - smoothstep(0.96, 1.0, e)));
  }
  col = mix(col, fill * uBright, inside * filled * 0.92);

  // rim light + glow
  vec3 rimCol = grad(0.5 + 0.5 * sin(t * 0.5)) * 1.2 + 0.2;
  col += rimCol * (rim + extraRim * inside * 0.6) * (0.6 + 0.5 * uGlow);
  col += rimCol * exp(-max(sd, 0.0) * 14.0) * 0.18 * uGlow * (1.0 - inside);

  // circle: outer progress ring that grows with the level
  if (uShape == 2) {
    float a = atan(p.x, p.y);                    // 0 at top, clockwise
    float prog = (a < 0.0 ? a + 6.28318 : a) / 6.28318;
    float ringD = abs(length(p) - 0.345);
    float on = step(prog, lvl);
    col += grad(prog) * exp(-ringD * 160.0) * (0.25 + 0.9 * on);
  }

  // tap shockwave
  if (uTapT < 1.2) {
    float d = abs(length(p - uTap) - uTapT * 0.9);
    col += grad(0.7) * exp(-d * 35.0) * (1.0 - uTapT / 1.2) * 1.5;
  }

  // double-tap effects
  float fx = float(uFx);
  if (fx > 0.5 && fx < 1.5) col = hueRot(col, t * 0.6);
  if (fx > 1.5) { col = hueRot(col, q.y * 3.0 + t * 0.4); col += 0.05 * sin(vec3(0.0, 2.0, 4.0) + t * 2.0); }

  col *= uBright;
  col *= 1.0 - 0.35 * smoothstep(0.5, 1.1, length(q));
  gl_FragColor = vec4(clamp(col, 0.0, 1.5), 1.0);
}
"""

    val FRAGMENT: String = FRAG_HEAD + FRAG_MAIN
}
