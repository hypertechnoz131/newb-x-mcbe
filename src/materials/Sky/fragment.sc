#ifndef INSTANCING
  $input v_worldPos, v_underwaterRainTimeDay
#endif

#include <bgfx_shader.sh>

#ifndef INSTANCING
  #include <newb/main.sh>
  uniform vec4 TimeOfDay;
  uniform vec4 FogColor;
  uniform vec4 FogAndDistanceControl;

  #ifdef NL_SKY_BLOCK_CLOUDS
    SAMPLER2D_AUTOREG(s_BlockCloudNoise);
    #ifndef NL_BLOCK_CLOUD_STEPS
      #define NL_BLOCK_CLOUD_STEPS 3
    #endif
    #ifndef NL_BLOCK_CLOUD_SCALE
      #define NL_BLOCK_CLOUD_SCALE 2.5
    #endif
    #ifndef NL_BLOCK_CLOUD_COVER
      #define NL_BLOCK_CLOUD_COVER 0.35
    #endif
    #ifndef NL_BLOCK_CLOUD_BASE
      #define NL_BLOCK_CLOUD_BASE 1.0
    #endif
    #ifndef NL_BLOCK_CLOUD_THICK
      #define NL_BLOCK_CLOUD_THICK 0.03
    #endif
    #ifndef NL_BLOCK_CLOUD_BEVEL
      #define NL_BLOCK_CLOUD_BEVEL 0.45
    #endif
    #ifndef NL_BLOCK_CLOUD_SQUARE
      #define NL_BLOCK_CLOUD_SQUARE 0.7
    #endif
    #ifndef NL_BLOCK_CLOUD_SOFT
      #define NL_BLOCK_CLOUD_SOFT 0.02
    #endif
    #ifndef NL_BLOCK_CLOUD_SHARP
      #define NL_BLOCK_CLOUD_SHARP 10.0
    #endif
    #ifndef NL_BLOCK_CLOUD_OPACITY
      #define NL_BLOCK_CLOUD_OPACITY 0.95
    #endif
    #ifndef NL_BLOCK_CLOUD_SPEED
      #define NL_BLOCK_CLOUD_SPEED 0.01
    #endif
    #ifndef NL_BLOCK_CLOUD_NIGHT
      #define NL_BLOCK_CLOUD_NIGHT 0.12
    #endif
    #ifndef NL_BLOCK_CLOUD_LIGHT_COL
      #define NL_BLOCK_CLOUD_LIGHT_COL vec3(1.15, 1.22, 1.32)
    #endif
    #ifndef NL_BLOCK_CLOUD_SHADE_COL
      #define NL_BLOCK_CLOUD_SHADE_COL vec3(0.8, 0.9, 1.0)
    #endif
    #ifndef NL_BLOCK_CLOUD_SUN_TINT
      #define NL_BLOCK_CLOUD_SUN_TINT 0.12
    #endif

    #define HTZ_CLOUD_INV_TEX (1.0/256.0)

    // Converted cloud-noise sampling from the supplied standalone GLSL.
    float blockOcc(highp vec2 q) {
      highp vec2 c = q - 0.1;
      highp vec2 i = floor(c);
      vec2 f = clamp((fract(c) - 0.5)*NL_BLOCK_CLOUD_SHARP + 0.5, 0.0, 1.0);
      return texture2D(s_BlockCloudNoise, (i + f + 0.5)*HTZ_CLOUD_INV_TEX).r;
    }

    vec4 BlockClouds(vec3 viewDir, vec3 sd, nl_skycolor skycol, nl_environment env, highp float time, float jitter) {
      if (viewDir.y < 0.1) {
        return vec4(0.0, 0.0, 0.0, 0.0);
      }

      const int steps = NL_BLOCK_CLOUD_STEPS;
      highp vec2 base = sd.xz/max(viewDir.y, 0.0001)*NL_BLOCK_CLOUD_SCALE;
      highp vec2 wind = vec2(time*NL_BLOCK_CLOUD_SPEED, time*NL_BLOCK_CLOUD_SPEED*0.4);

      float daylight = smoothstep(-0.2, 0.25, env.dayFactor);
      vec3 lightDir = mix(env.moonDir, env.sunDir, step(0.0, env.dayFactor));
      vec2 L = normalize(lightDir.xz + vec2(0.0001))*0.1;

      float dawn = 1.0 - env.dayFactor*env.dayFactor;
      dawn *= dawn*dawn;
      dawn *= 1.0 - smoothstep(0.0, 0.3, env.dayFactor);

      vec3 sunCol = mix(mix(NL_NOON_SUNLIGHT_COL, vec3_splat(1.0), 0.45), NL_DAWN_SUNLIGHT_COL, dawn);
      float bright = mix(NL_BLOCK_CLOUD_NIGHT, 1.0, daylight)*(1.0 - 0.35*env.rainFactor);
      vec3 lightCol = NL_BLOCK_CLOUD_LIGHT_COL*mix(vec3_splat(1.0), sunCol, mix(NL_BLOCK_CLOUD_SUN_TINT, 0.5, dawn))*bright;
      vec3 shadeCol = NL_BLOCK_CLOUD_SHADE_COL*mix(vec3_splat(1.0), skycol.horizon*1.4, 0.35)*bright;

      float acc = 0.0;
      float w0 = 0.0;
      float count = 0.0;
      vec3 wallAcc = vec3(0.0, 0.0, 0.0);

      for (int i = 0; i < steps; i++) {
        float h = (float(i) + 0.5)/float(steps);
        highp vec2 q = base*(1.0 + NL_BLOCK_CLOUD_THICK*h) + wind;

        float o = blockOcc(q);
        float m = smoothstep(0.2 - NL_BLOCK_CLOUD_SOFT, 0.5 + NL_BLOCK_CLOUD_SOFT, o);
        count += m;

        float a = m*(1.0 - acc);
        if (a > 0.001) {
          float lit = clamp(0.5 - (blockOcc(q + L) - o)*1.2, 0.0, 1.0);
          float wk = clamp(0.25 + 0.65*h + 0.35*(lit - 0.5), 0.0, 1.0);
          float first = (i == 0) ? 1.0 : 0.0;
          w0 += a*first;
          wallAcc += a*(1.0 - first)*mix(shadeCol, lightCol, wk);
          acc += a;
        }
      }

      if (acc < 0.001) {
        return vec4(0.0, 0.0, 0.0, 0.0);
      }

      float c01 = count/float(steps);
      vec3 under = mix(lightCol*0.92, shadeCol, pow(clamp((c01 - 0.08)/0.6, 0.0, 1.0), 0.8));
      float fade = smoothstep(0.05, 0.25, viewDir.y);
      float opacity = NL_BLOCK_CLOUD_OPACITY*fade;
      return vec4((wallAcc + w0*under)*opacity, acc*opacity);
    }
  #endif
#endif

void main() {
  #ifndef INSTANCING
    vec3 viewDir = normalize(v_worldPos);

    nl_environment env;
    env.end = false;
    env.nether = false;
    env.underwater = v_underwaterRainTimeDay.x > 0.5;
    env.rainFactor = v_underwaterRainTimeDay.y;
    env.dayFactor = v_underwaterRainTimeDay.w;
    env.fogCol = FogColor.rgb;
    env = calculateSunParams(env, TimeOfDay.x);

    nl_skycolor skycol = nlOverworldSkyColors(env);

    vec3 skyColor = nlRenderSky(skycol, env, -viewDir, v_underwaterRainTimeDay.z, true);
    #ifdef NL_SHOOTING_STAR
      skyColor += NL_SHOOTING_STAR*nlRenderShootingStar(viewDir, env.fogCol, v_underwaterRainTimeDay.z);
    #endif
    #ifdef NL_GALAXY_STARS
      skyColor += NL_GALAXY_STARS*nlRenderGalaxy(viewDir, env.fogCol, env, v_underwaterRainTimeDay.z);
    #endif

    #ifdef NL_SKY_BLOCK_CLOUDS
    if (!env.underwater) {
      float cj = fract(52.9829189*fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
      vec3 sdb = vec3(-viewDir.x, viewDir.y, -viewDir.z);
      vec4 cb = BlockClouds(viewDir, sdb, skycol, env, v_underwaterRainTimeDay.z, cj);
      skyColor = skyColor*(1.0 - cb.a) + cb.rgb;
    }
    #endif

    skyColor = colorCorrection(skyColor);

    gl_FragColor = vec4(skyColor, 1.0);
  #else
    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
  #endif
}
