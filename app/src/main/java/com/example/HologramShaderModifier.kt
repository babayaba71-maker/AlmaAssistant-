package com.example

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import org.intellij.lang.annotations.Language
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * AGSL Hologram Shader for Android 13+ (API 33+).
 * Renders an animated hologram background effect featuring:
 * - Dynamic moving scanlines with subtle harmonic interference
 * - Soft radial and vertical blue-green (cyan/teal/emerald) glow
 * - Geometric hologram vignette framing
 */
@Language("AGSL")
private const val HOLOGRAM_AGSL_SRC = """
    uniform float2 iResolution;
    uniform float iTime;
    uniform float4 iGlowColor;    // Cyan/Teal glow
    uniform float4 iAccentColor;  // Emerald/Green glow
    uniform float4 iBaseColor;    // Deep dark background
    uniform float iScanlineDensity;
    uniform float iGlowIntensity;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution;

        // Animated vertical scanlines with sinusoidal drifting phase
        float scanline1 = sin(fragCoord.y * iScanlineDensity * 0.8 + iTime * 3.5) * 0.5 + 0.5;
        float scanline2 = sin(fragCoord.y * iScanlineDensity * 1.6 - iTime * 2.0) * 0.5 + 0.5;
        float scanline = mix(scanline1, scanline2, 0.45);
        scanline = pow(scanline, 2.2);

        // Subtle horizontal harmonic flicker/drift
        float waveDisplacement = sin(uv.y * 12.0 + iTime * 1.8) * 0.04;
        float2 center = float2(0.5 + waveDisplacement, 0.45);
        float dist = distance(uv, center);

        // Blue-green radial dispersion glow
        float radialGlow = 1.0 - smoothstep(0.0, 0.78, dist);
        radialGlow = pow(radialGlow, 1.4);

        // Top ambient blue-green projection beam
        float topBeam = (1.0 - uv.y) * 0.35 * (sin(iTime * 1.5) * 0.15 + 0.85);

        // Cybernetic screen vignette
        float vignette = uv.x * (1.0 - uv.x) * uv.y * (1.0 - uv.y) * 16.0;
        vignette = clamp(vignette, 0.0, 1.0);

        // Blend glow colors: mix blue-cyan with green-teal accent
        half4 mixedGlow = mix(iGlowColor, iAccentColor, clamp(radialGlow * 0.6 + uv.y * 0.4, 0.0, 1.0));

        // Composite intensity
        float totalGlow = (radialGlow * 0.55 + topBeam + scanline * 0.18) * iGlowIntensity * vignette;
        totalGlow = clamp(totalGlow, 0.0, 1.0);

        half4 finalColor = mix(iBaseColor, mixedGlow, totalGlow);

        // Fine grid scanline lines for crisp holographic texture
        float fineGrid = step(0.5, fract(fragCoord.y * 0.28)) * 0.035 * iGlowIntensity;
        finalColor.rgb += fineGrid * mixedGlow.rgb;

        return finalColor;
    }
"""

/**
 * Custom Compose Modifier that renders a futuristic 'hologram' effect on the background using
 * a runtime AGSL shader on Android 13+ (API 33+), with a high-performance Canvas shader
 * fallback on Android 12 (API 31-32) ensuring smooth performance across all devices.
 *
 * @param glowColor Primary holographic glow color (defaults to glowing Cyan #00F2FE)
 * @param accentColor Secondary holographic accent color (defaults to emerald/mint #00E676)
 * @param baseColor Dark backdrop color (defaults to deep hologram black-blue #060913)
 * @param scanlineDensity Multiplier controlling density and thickness of scanlines
 * @param scanlineSpeed Multiplier controlling animation drift speed
 * @param glowIntensity Overall intensity of the holographic blue-green glow
 * @param enabled Whether the hologram background effect is active
 */
fun Modifier.hologramBackground(
  glowColor: Color = Color(0xFF00F2FE),
  accentColor: Color = Color(0xFF00E676),
  baseColor: Color = Color(0xFF060913),
  scanlineDensity: Float = 1.5f,
  scanlineSpeed: Float = 1.0f,
  glowIntensity: Float = 0.55f,
  enabled: Boolean = true
): Modifier = if (!enabled) this else composed {
  val infiniteTransition = rememberInfiniteTransition(label = "hologram_shader_anim")

  // Seamless continuous time animation (0 to 100 seconds looping)
  val animationTime by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 100f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = (100000 / scanlineSpeed.coerceAtLeast(0.1f)).toInt(), easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "hologram_time"
  )

  // Floating scanline phase for the fallback canvas renderer
  val scanlinePhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = (2 * PI).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "scanline_phase"
  )

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    val runtimeShader = remember {
      try {
        RuntimeShader(HOLOGRAM_AGSL_SRC)
      } catch (_: Throwable) {
        null
      }
    }

    if (runtimeShader != null) {
      Modifier.drawWithCache {
        val width = size.width
        val height = size.height

        if (width <= 0f || height <= 0f) {
          onDrawBehind {
            drawRect(color = baseColor)
          }
        } else {
          try {
            runtimeShader.setFloatUniform("iResolution", width, height)
            runtimeShader.setFloatUniform("iTime", animationTime)
            runtimeShader.setFloatUniform(
              "iGlowColor",
              glowColor.red,
              glowColor.green,
              glowColor.blue,
              glowColor.alpha
            )
            runtimeShader.setFloatUniform(
              "iAccentColor",
              accentColor.red,
              accentColor.green,
              accentColor.blue,
              accentColor.alpha
            )
            runtimeShader.setFloatUniform(
              "iBaseColor",
              baseColor.red,
              baseColor.green,
              baseColor.blue,
              baseColor.alpha
            )
            runtimeShader.setFloatUniform("iScanlineDensity", scanlineDensity)
            runtimeShader.setFloatUniform("iGlowIntensity", glowIntensity)

            val shaderBrush = ShaderBrush(runtimeShader)
            onDrawBehind {
              drawRect(brush = shaderBrush)
            }
          } catch (_: Throwable) {
            // In case of unsupported GPU shader features, seamlessly fallback
            onDrawBehind {
              drawHologramFallback(
                glowColor = glowColor,
                accentColor = accentColor,
                baseColor = baseColor,
                scanlinePhase = scanlinePhase,
                glowIntensity = glowIntensity
              )
            }
          }
        }
      }
    } else {
      Modifier.drawBehind {
        drawHologramFallback(
          glowColor = glowColor,
          accentColor = accentColor,
          baseColor = baseColor,
          scanlinePhase = scanlinePhase,
          glowIntensity = glowIntensity
        )
      }
    }
  } else {
    // Android 12 (API 31/32) Canvas Fallback
    Modifier.drawBehind {
      drawHologramFallback(
        glowColor = glowColor,
        accentColor = accentColor,
        baseColor = baseColor,
        scanlinePhase = scanlinePhase,
        glowIntensity = glowIntensity
      )
    }
  }
}

/**
 * Fallback procedural renderer for Android 12 and unsupported GPUs.
 * Mimics the AGSL shader with multi-stop radial blue-green gradients and animated scanlines.
 */
private fun DrawScope.drawHologramFallback(
  glowColor: Color,
  accentColor: Color,
  baseColor: Color,
  scanlinePhase: Float,
  glowIntensity: Float
) {
  val w = size.width
  val h = size.height

  // 1. Dark base
  drawRect(color = baseColor)

  // 2. Central blue-green holographic ambient dispersion
  val centerGlow = Brush.radialGradient(
    colors = listOf(
      glowColor.copy(alpha = 0.28f * glowIntensity),
      accentColor.copy(alpha = 0.16f * glowIntensity),
      Color.Transparent
    ),
    center = Offset(w * 0.5f, h * 0.45f),
    radius = (w * 0.65f).coerceAtLeast(100f)
  )
  drawRect(brush = centerGlow)

  // 3. Top holographic projection beam (subtle teal-green glow)
  val topBeam = Brush.verticalGradient(
    colors = listOf(
      accentColor.copy(alpha = 0.18f * glowIntensity),
      glowColor.copy(alpha = 0.08f * glowIntensity),
      Color.Transparent
    ),
    startY = 0f,
    endY = h * 0.5f
  )
  drawRect(brush = topBeam)

  // 4. Procedural scanlines with smooth animated phase shift
  val scanlineStep = 6.dp.toPx()
  var y = (scanlinePhase * 2f) % scanlineStep
  while (y <= h) {
    val progressY = y / h
    val scanlineAlpha = (sin(progressY * PI).toFloat().pow(1.2f) * 0.08f * glowIntensity).coerceIn(0f, 0.15f)
    if (scanlineAlpha > 0.005f) {
      drawLine(
        color = glowColor.copy(alpha = scanlineAlpha),
        start = Offset(0f, y),
        end = Offset(w, y),
        strokeWidth = 1.5.dp.toPx()
      )
    }
    y += scanlineStep
  }

  // 5. Border vignette shadow to frame the hologram display
  val vignette = Brush.radialGradient(
    colors = listOf(
      Color.Transparent,
      baseColor.copy(alpha = 0.35f),
      baseColor.copy(alpha = 0.75f)
    ),
    center = Offset(w * 0.5f, h * 0.5f),
    radius = (w * 0.75f).coerceAtLeast(150f)
  )
  drawRect(brush = vignette)
}
