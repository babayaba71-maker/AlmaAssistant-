package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlmaVoiceWaveformTest {

  @Test
  fun testVoiceStateTransitions() {
    val idle: VoiceState = VoiceState.Idle
    val listening: VoiceState = VoiceState.Listening
    val processing: VoiceState = VoiceState.Processing
    val success: VoiceState = VoiceState.Success("open lights")
    val error: VoiceState = VoiceState.Error("No command detected")

    assertTrue(idle is VoiceState.Idle)
    assertTrue(listening is VoiceState.Listening)
    assertTrue(processing is VoiceState.Processing)
    assertEquals("open lights", (success as VoiceState.Success).recognizedText)
    assertEquals("No command detected", (error as VoiceState.Error).message)
  }

  @Test
  fun testRmsDbNormalization() {
    // Test mapping of dB values (-2dB to 10dB) to normalized 0f..1f range
    fun normalizeRms(rmsdB: Float): Float {
      return ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
    }

    assertEquals(0f, normalizeRms(-5f), 0.001f)
    assertEquals(0f, normalizeRms(-2f), 0.001f)
    assertEquals(0.5f, normalizeRms(4f), 0.001f)
    assertEquals(1f, normalizeRms(10f), 0.001f)
    assertEquals(1f, normalizeRms(15f), 0.001f)
  }
}
