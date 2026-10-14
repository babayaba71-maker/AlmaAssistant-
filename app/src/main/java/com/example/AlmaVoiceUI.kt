package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.AlmaDarkBackground
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanHolo
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun AlmaVoiceTriggerButton(
  isListening: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.35f,
    animationSpec = infiniteRepeatable(
      animation = tween(900, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "micScale"
  )
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.6f,
    targetValue = 0.1f,
    animationSpec = infiniteRepeatable(
      animation = tween(900, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "micAlpha"
  )

  Box(
    modifier = modifier
      .size(64.dp),
    contentAlignment = Alignment.Center
  ) {
    if (isListening) {
      // Outer glowing pulse ring
      Box(
        modifier = Modifier
          .size(64.dp)
          .scale(pulseScale)
          .alpha(pulseAlpha)
          .border(
            width = 2.dp,
            brush = Brush.radialGradient(listOf(CyanHolo, Color.Transparent)),
            shape = CircleShape
          )
      )
    }

    // Main Floating Voice Button
    Box(
      modifier = Modifier
        .size(56.dp)
        .clip(CircleShape)
        .background(
          brush = Brush.radialGradient(
            colors = if (isListening) {
              listOf(Color(0xFF00E5FF), Color(0xFF0A2239))
            } else {
              listOf(Color(0xFF0D1B2A), Color(0xFF060913))
            }
          )
        )
        .border(
          width = 1.5.dp,
          brush = Brush.linearGradient(listOf(CyanHolo, CyanAccent)),
          shape = CircleShape
        )
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = ripple(bounded = true, color = CyanHolo),
          onClick = onClick
        )
        .testTag("alma_voice_button"),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.Mic,
        contentDescription = if (isListening) "Listening for voice command" else "Trigger voice command",
        tint = if (isListening) Color.Black else CyanHolo,
        modifier = Modifier.size(28.dp)
      )
    }
  }
}

@Composable
fun AlmaVoiceHUDOverlay(
  voiceState: VoiceState,
  liveTranscript: String,
  rmsLevel: Float,
  onDismiss: () -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier
) {
  // Auto-dismiss success after 2 seconds
  LaunchedEffect(voiceState) {
    if (voiceState is VoiceState.Success) {
      delay(2200)
      onDismiss()
    }
  }

  AnimatedVisibility(
    visible = voiceState !is VoiceState.Idle,
    enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 3 },
    exit = fadeOut(tween(250)) + slideOutVertically(tween(200)) { it / 3 }
  ) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.55f))
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = {
            if (voiceState is VoiceState.Error || voiceState is VoiceState.Success) {
              onDismiss()
            }
          }
        )
        .testTag("alma_voice_hud"),
      contentAlignment = Alignment.BottomCenter
    ) {
      // Ambient subtle horizon wave across the screen when actively listening
      if (voiceState is VoiceState.Listening) {
        AmbientListeningHorizonWave(
          rmsLevel = rmsLevel,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(180.dp)
        )
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 28.dp)
          .widthIn(max = 500.dp)
          .clip(RoundedCornerShape(24.dp))
          .hologramBackground(
            glowColor = CyanHolo,
            accentColor = CyanAccent,
            baseColor = Color(0xF2070B16),
            scanlineDensity = 2.0f,
            glowIntensity = 0.45f
          )
          .border(
            width = 1.dp,
            brush = Brush.horizontalGradient(
              listOf(CyanHolo.copy(alpha = 0.8f), CyanAccent.copy(alpha = 0.3f), CyanHolo.copy(alpha = 0.8f))
            ),
            shape = RoundedCornerShape(24.dp)
          )
          .padding(20.dp)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {} // Consume inner click
          )
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Top Status Header and Dismiss Button
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (voiceState is VoiceState.Listening) {
                MiniWaveformEqualizer(
                  rmsLevel = rmsLevel,
                  modifier = Modifier.padding(end = 8.dp)
                )
              } else {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .background(
                      color = when (voiceState) {
                        is VoiceState.Processing -> CyanAccent
                        is VoiceState.Success -> Color(0xFF00E676)
                        is VoiceState.Error -> Color(0xFFFF5252)
                        else -> CyanHolo
                      },
                      shape = CircleShape
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
              }
              Text(
                text = when (voiceState) {
                  is VoiceState.Initializing -> "INITIALIZING SPEECH..."
                  is VoiceState.Listening -> "LISTENING TO ALMA..."
                  is VoiceState.Processing -> "PROCESSING COMMAND..."
                  is VoiceState.Success -> "COMMAND ACCEPTED"
                  is VoiceState.Error -> "RECOGNITION FAILED"
                  else -> "VOICE ASSISTANT"
                },
                color = when (voiceState) {
                  is VoiceState.Error -> Color(0xFFFF5252)
                  is VoiceState.Success -> Color(0xFF00E676)
                  else -> CyanHolo
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
              )
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .size(36.dp)
                .testTag("alma_voice_cancel_button")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel voice listening",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Subtle Waveform Animation Visualizer when Actively Listening or Processing
          if (voiceState is VoiceState.Listening || voiceState is VoiceState.Processing) {
            SubtleWaveformAnimation(
              rmsLevel = rmsLevel,
              isListening = voiceState is VoiceState.Listening,
              isProcessing = voiceState is VoiceState.Processing,
              modifier = Modifier.testTag("listening_waveform_indicator")
            )
            Spacer(modifier = Modifier.height(12.dp))
            HolographicWaveform(
              rmsLevel = rmsLevel,
              isProcessing = voiceState is VoiceState.Processing
            )
            Spacer(modifier = Modifier.height(16.dp))
          }

          // Live Transcription / Message Display
          val displayText = when (voiceState) {
            is VoiceState.Success -> "\"${voiceState.recognizedText}\""
            is VoiceState.Error -> voiceState.message
            else -> if (liveTranscript.isNotBlank()) "\"$liveTranscript\"" else "Speak your command clearly..."
          }

          Text(
            text = displayText,
            color = when (voiceState) {
              is VoiceState.Error -> Color(0xFFFF8A80)
              is VoiceState.Success -> Color.White
              else -> if (liveTranscript.isNotBlank()) Color.White else Color(0x88A0E9FF)
            },
            fontSize = 17.sp,
            fontWeight = if (voiceState is VoiceState.Success) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
          )

          // Action button if error
          if (voiceState is VoiceState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF132238))
                .border(1.dp, CyanHolo.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Retry speech command",
                tint = CyanHolo,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "TRY AGAIN",
                color = CyanHolo,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun MiniWaveformEqualizer(
  rmsLevel: Float,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "mini_eq_anim")
  val b1 by infiniteTransition.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.95f,
    animationSpec = infiniteRepeatable(
      animation = tween(420, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bar1"
  )
  val b2 by infiniteTransition.animateFloat(
    initialValue = 0.85f,
    targetValue = 0.25f,
    animationSpec = infiniteRepeatable(
      animation = tween(360, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bar2"
  )
  val b3 by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(480, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bar3"
  )
  val b4 by infiniteTransition.animateFloat(
    initialValue = 0.9f,
    targetValue = 0.3f,
    animationSpec = infiniteRepeatable(
      animation = tween(390, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bar4"
  )

  Row(
    modifier = modifier.height(14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.5.dp)
  ) {
    val bars = listOf(b1, b2, b3, b4)
    bars.forEach { baseFrac ->
      val dynamicFrac = ((baseFrac * 0.45f + rmsLevel * 0.75f)).coerceIn(0.2f, 1f)
      Box(
        modifier = Modifier
          .width(2.5.dp)
          .height((14.dp * dynamicFrac).coerceAtLeast(3.dp))
          .clip(RoundedCornerShape(1.dp))
          .background(
            Brush.verticalGradient(listOf(CyanHolo, CyanAccent))
          )
      )
    }
  }
}

@Composable
fun SubtleWaveformAnimation(
  rmsLevel: Float,
  isListening: Boolean,
  isProcessing: Boolean = false,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "subtle_wave_anim")

  val phase1 by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = (2 * PI).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(2200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "wave_phase1"
  )

  val phase2 by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = -(2 * PI).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(1700, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "wave_phase2"
  )

  val phase3 by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = (2 * PI).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(3100, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "wave_phase3"
  )

  val pulseGlow by infiniteTransition.animateFloat(
    initialValue = 0.45f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1100, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_glow"
  )

  val targetAmp = when {
    isProcessing -> 0.35f * pulseGlow
    isListening -> (rmsLevel * 0.75f + 0.22f).coerceIn(0.18f, 1f)
    else -> 0.1f
  }

  val animatedAmp by animateFloatAsState(
    targetValue = targetAmp,
    animationSpec = tween(120, easing = FastOutSlowInEasing),
    label = "amp"
  )

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(64.dp)
      .clip(RoundedCornerShape(18.dp))
      .background(
        Brush.verticalGradient(
          listOf(
            Color(0x2200182E),
            Color(0x44041020),
            Color(0x2200182E)
          )
        )
      )
      .border(
        width = 1.dp,
        brush = Brush.horizontalGradient(
          listOf(
            Color.Transparent,
            CyanHolo.copy(alpha = 0.35f * pulseGlow),
            CyanAccent.copy(alpha = 0.55f * pulseGlow),
            CyanHolo.copy(alpha = 0.35f * pulseGlow),
            Color.Transparent
          )
        ),
        shape = RoundedCornerShape(18.dp)
      )
      .testTag("subtle_waveform_animation"),
    contentAlignment = Alignment.Center
  ) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .testTag("listening_waveform_indicator")
    ) {
      val w = size.width
      val h = size.height
      val midY = h / 2f
      val maxAmp = (h / 2f) * 0.8f * animatedAmp

      // Subtle radial aura behind the waveform
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            CyanHolo.copy(alpha = 0.2f * animatedAmp * pulseGlow),
            Color.Transparent
          ),
          center = Offset(w / 2f, midY),
          radius = w * 0.35f
        ),
        radius = w * 0.35f,
        center = Offset(w / 2f, midY)
      )

      // Center Reference Guide Line
      drawLine(
        brush = Brush.horizontalGradient(
          listOf(
            Color.Transparent,
            CyanHolo.copy(alpha = 0.15f),
            CyanAccent.copy(alpha = 0.3f),
            CyanHolo.copy(alpha = 0.15f),
            Color.Transparent
          )
        ),
        start = Offset(0f, midY),
        end = Offset(w, midY),
        strokeWidth = 1.dp.toPx()
      )

      // Tertiary Ambient Wave (Slow deep harmonic)
      val path3 = Path().apply {
        moveTo(0f, midY)
        val step = 3f
        var x = 0f
        while (x <= w) {
          val progress = x / w
          val envelope = sin((progress * PI).coerceIn(0.0, PI)).toFloat().pow(1.6f)
          val y = midY + sin(progress * 2.2 * PI + phase3).toFloat() * (maxAmp * 0.45f) * envelope
          lineTo(x, y)
          x += step
        }
      }
      drawPath(
        path = path3,
        brush = Brush.horizontalGradient(
          listOf(
            Color.Transparent,
            Color(0xFF0288D1).copy(alpha = 0.3f * pulseGlow),
            Color(0xFF00E5FF).copy(alpha = 0.45f * pulseGlow),
            Color(0xFF0288D1).copy(alpha = 0.3f * pulseGlow),
            Color.Transparent
          )
        ),
        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
      )

      // Harmonic Secondary Wave (Gaussian envelope for smooth tapering)
      val path2 = Path().apply {
        moveTo(0f, midY)
        val step = 3f
        var x = 0f
        while (x <= w) {
          val progress = x / w
          val envelope = sin((progress * PI).coerceIn(0.0, PI)).toFloat().pow(1.5f)
          val y = midY + sin(progress * 4.4 * PI + phase2).toFloat() * (maxAmp * 0.62f) * envelope
          lineTo(x, y)
          x += step
        }
      }
      drawPath(
        path = path2,
        brush = Brush.horizontalGradient(
          listOf(
            Color.Transparent,
            CyanAccent.copy(alpha = 0.35f * pulseGlow),
            Color(0xFF80D8FF).copy(alpha = 0.65f * pulseGlow),
            CyanAccent.copy(alpha = 0.35f * pulseGlow),
            Color.Transparent
          )
        ),
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
      )

      // Primary Flowing Waveform
      val path1 = Path().apply {
        moveTo(0f, midY)
        val step = 3f
        var x = 0f
        while (x <= w) {
          val progress = x / w
          val envelope = sin((progress * PI).coerceIn(0.0, PI)).toFloat().pow(1.4f)
          val y = midY + sin(progress * 3.2 * PI + phase1).toFloat() * maxAmp * envelope
          lineTo(x, y)
          x += step
        }
      }
      drawPath(
        path = path1,
        brush = Brush.horizontalGradient(
          listOf(
            Color.Transparent,
            CyanHolo.copy(alpha = 0.8f),
            Color.White.copy(alpha = 0.98f),
            CyanHolo.copy(alpha = 0.8f),
            Color.Transparent
          )
        ),
        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
      )

      // Glowing audio nodes along the wave
      val nodePositions = listOf(0.32f, 0.50f, 0.68f)
      nodePositions.forEach { p ->
        val envelope = sin((p * PI).coerceIn(0.0, PI)).toFloat().pow(1.4f)
        val nodeY = midY + sin(p * 3.2 * PI + phase1).toFloat() * maxAmp * envelope
        val nodeX = w * p

        drawCircle(
          color = CyanHolo.copy(alpha = 0.35f * animatedAmp),
          radius = 7.dp.toPx() * animatedAmp.coerceAtLeast(0.6f),
          center = Offset(nodeX, nodeY)
        )
        drawCircle(
          color = Color.White,
          radius = 2.4.dp.toPx(),
          center = Offset(nodeX, nodeY)
        )
      }
    }
  }
}

@Composable
fun SubtleAudioWaveformVisualizer(
  rmsLevel: Float,
  isListening: Boolean,
  isProcessing: Boolean = false,
  modifier: Modifier = Modifier
) {
  SubtleWaveformAnimation(
    rmsLevel = rmsLevel,
    isListening = isListening,
    isProcessing = isProcessing,
    modifier = modifier
  )
}

@Composable
fun AmbientListeningHorizonWave(
  rmsLevel: Float,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "horizon_anim")
  val phase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = (2 * PI).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(3200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "horizon_phase"
  )

  val animatedAmp by animateFloatAsState(
    targetValue = (rmsLevel * 0.7f + 0.2f).coerceIn(0.15f, 1f),
    animationSpec = tween(140, easing = FastOutSlowInEasing),
    label = "horizon_amp"
  )

  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val baseY = h * 0.72f
    val waveAmp = (h * 0.24f) * animatedAmp

    val path = Path().apply {
      moveTo(0f, h)
      lineTo(0f, baseY)
      val step = 6f
      var x = 0f
      while (x <= w) {
        val progress = x / w
        val y = baseY + sin((progress * 2.8 * PI + phase)).toFloat() * waveAmp
        lineTo(x, y)
        x += step
      }
      lineTo(w, h)
      close()
    }

    drawPath(
      path = path,
      brush = Brush.verticalGradient(
        colors = listOf(
          CyanHolo.copy(alpha = 0.22f * animatedAmp),
          CyanAccent.copy(alpha = 0.1f * animatedAmp),
          Color.Transparent
        ),
        startY = baseY - waveAmp,
        endY = h
      )
    )
  }
}

@Composable
fun HolographicWaveform(
  rmsLevel: Float,
  isProcessing: Boolean,
  modifier: Modifier = Modifier
) {
  val barCount = 11
  val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(48.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    for (i in 0 until barCount) {
      val offset = (i - (barCount / 2f)) / (barCount / 2f)
      val centerFactor = 1f - kotlin.math.abs(offset) * 0.45f

      val animHeight by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
          animation = tween(400 + (i * 65), easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse
        ),
        label = "bar_$i"
      )

      val dynamicHeightFraction = if (isProcessing) {
        animHeight * centerFactor
      } else {
        ((rmsLevel * 0.85f + animHeight * 0.25f) * centerFactor).coerceIn(0.12f, 1f)
      }

      Box(
        modifier = Modifier
          .padding(horizontal = 3.dp)
          .width(4.dp)
          .height((40.dp * dynamicHeightFraction).coerceAtLeast(6.dp))
          .clip(RoundedCornerShape(2.dp))
          .background(
            brush = Brush.verticalGradient(
              listOf(
                CyanHolo,
                CyanAccent
              )
            )
          )
      )
    }
  }
}

@Composable
fun AlmaHandsFreeIndicator(
  isHandsFreeEnabled: Boolean,
  wakeWordState: WakeWordState,
  onToggleHandsFree: () -> Unit,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "hands_free_anim")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1100, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "hf_alpha"
  )

  val isListening = wakeWordState is WakeWordState.Listening
  val isDetected = wakeWordState is WakeWordState.Detected

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(20.dp))
      .background(
        color = when {
          isDetected -> Color(0xE60D382A)
          isHandsFreeEnabled -> Color(0xD9060E1A)
          else -> Color(0xB30B1220)
        }
      )
      .border(
        width = 1.dp,
        brush = when {
          isDetected -> Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF69F0AE)))
          isHandsFreeEnabled -> Brush.horizontalGradient(listOf(CyanHolo, CyanAccent))
          else -> Brush.horizontalGradient(listOf(Color(0x3300F2FE), Color(0x1100F2FE)))
        },
        shape = RoundedCornerShape(20.dp)
      )
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = true, color = CyanHolo),
        onClick = onToggleHandsFree
      )
      .padding(horizontal = 14.dp, vertical = 8.dp)
      .testTag("alma_hands_free_badge")
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      // Dynamic Glowing Audio Pulse Dot
      Box(
        modifier = Modifier
          .size(8.dp)
          .scale(if (isListening) (1f + ((wakeWordState as? WakeWordState.Listening)?.rmsLevel ?: 0f) * 0.9f) else 1f)
          .background(
            color = when {
              isDetected -> Color(0xFF00E676)
              isHandsFreeEnabled -> CyanHolo.copy(alpha = pulseAlpha)
              else -> Color(0xFF64748B)
            },
            shape = CircleShape
          )
      )

      Spacer(modifier = Modifier.width(8.dp))

      Text(
        text = when {
          isDetected -> "WAKE: ${(wakeWordState as WakeWordState.Detected).keyword.uppercase()}"
          isHandsFreeEnabled -> "HANDS-FREE ON • \"Hey Alma\""
          else -> "HANDS-FREE: OFF"
        },
        color = when {
          isDetected -> Color(0xFFE8F5E9)
          isHandsFreeEnabled -> CyanHolo
          else -> Color(0xFF94A3B8)
        },
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        fontFamily = FontFamily.Monospace
      )
    }
  }
}

@Composable
fun AlmaGestureFeedbackToast(
  gesture: AssistantGesture?,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  LaunchedEffect(gesture) {
    if (gesture != null) {
      delay(1600)
      onDismiss()
    }
  }

  AnimatedVisibility(
    visible = gesture != null,
    enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it },
    exit = fadeOut(tween(220)) + slideOutVertically(tween(180)) { -it },
    modifier = modifier
  ) {
    if (gesture != null) {
      val (label, detail) = when (gesture) {
        AssistantGesture.SWIPE_DOWN -> "DISMISSING ALMA" to "Closing Assistant UI"
        AssistantGesture.SWIPE_UP -> "CONTEXT CLEARED" to "Resetting conversation & portal state"
        AssistantGesture.SWIPE_LEFT -> "HANDS-FREE TOGGLED" to "Wake word listening updated"
        AssistantGesture.SWIPE_RIGHT -> "VOICE ACTIVATED" to "Opening microphone input"
      }

      val iconText = when (gesture) {
        AssistantGesture.SWIPE_DOWN -> "↓"
        AssistantGesture.SWIPE_UP -> "↑"
        AssistantGesture.SWIPE_LEFT -> "←"
        AssistantGesture.SWIPE_RIGHT -> "→"
      }

      Box(
        modifier = Modifier
          .padding(top = 96.dp, start = 24.dp, end = 24.dp)
          .clip(RoundedCornerShape(18.dp))
          .background(Color(0xF0070F1E))
          .border(
            width = 1.dp,
            brush = Brush.horizontalGradient(listOf(CyanHolo, CyanAccent)),
            shape = RoundedCornerShape(18.dp)
          )
          .padding(horizontal = 18.dp, vertical = 12.dp)
          .testTag("alma_gesture_toast"),
        contentAlignment = Alignment.Center
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Text(
            text = iconText,
            color = CyanHolo,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )

          Spacer(modifier = Modifier.width(12.dp))

          Column {
            Text(
              text = label,
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = detail,
              color = Color(0xFF94A3B8),
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace
            )
          }
        }
      }
    }
  }
}


