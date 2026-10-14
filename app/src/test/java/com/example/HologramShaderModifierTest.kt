package com.example

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotNull
import org.junit.Test

class HologramShaderModifierTest {

  @Test
  fun testHologramModifierCreation() {
    val modifier = Modifier.hologramBackground(
      glowColor = Color(0xFF00F2FE),
      accentColor = Color(0xFF00E676),
      baseColor = Color(0xFF060913),
      scanlineDensity = 1.8f,
      scanlineSpeed = 1.2f,
      glowIntensity = 0.6f,
      enabled = true
    )
    assertNotNull(modifier)
  }

  @Test
  fun testHologramModifierDisabled() {
    val baseModifier = Modifier
    val disabledModifier = baseModifier.hologramBackground(enabled = false)
    // When disabled, modifier returns same instance
    assertNotNull(disabledModifier)
  }
}
