package com.example

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed class WakeWordState {
  object Inactive : WakeWordState()
  data class Listening(val rmsLevel: Float) : WakeWordState()
  data class Detected(val keyword: String, val followUpCommand: String?) : WakeWordState()
  data class Error(val message: String) : WakeWordState()
}

class AlmaWakeWordDetector(private val context: Context) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private var speechRecognizer: SpeechRecognizer? = null

  var isHandsFreeEnabled by mutableStateOf(false)
    private set

  var state by mutableStateOf<WakeWordState>(WakeWordState.Inactive)
    private set

  var lastDetectedKeyword by mutableStateOf<String?>(null)
    private set

  var liveHeardText by mutableStateOf("")
    private set

  private var isPaused = false
  private var onDetectedCallback: ((String, String?) -> Unit)? = null

  // Supported wake keywords for hands-free activation
  private val wakeKeywords = listOf(
    "hey alma",
    "ey alma",
    "ok alma",
    "oye alma",
    "alma",
    "ey valerie",
    "hey valerie",
    "oye valerie",
    "valerie"
  )

  fun start(onDetected: (keyword: String, followUpCommand: String?) -> Unit) {
    onDetectedCallback = onDetected
    isHandsFreeEnabled = true
    isPaused = false
    restartListeningInternal(0)
  }

  fun stop() {
    isHandsFreeEnabled = false
    isPaused = false
    stopRecognizerInternal()
    state = WakeWordState.Inactive
    liveHeardText = ""
  }

  fun pause() {
    isPaused = true
    stopRecognizerInternal()
  }

  fun resume() {
    if (isHandsFreeEnabled && isPaused) {
      isPaused = false
      restartListeningInternal(150)
    }
  }

  fun destroy() {
    stop()
    speechRecognizer?.destroy()
    speechRecognizer = null
  }

  private fun stopRecognizerInternal() {
    mainHandler.removeCallbacksAndMessages(null)
    try {
      speechRecognizer?.cancel()
      speechRecognizer?.destroy()
    } catch (_: Exception) {}
    speechRecognizer = null
  }

  private fun restartListeningInternal(delayMs: Long) {
    if (!isHandsFreeEnabled || isPaused) return

    mainHandler.removeCallbacksAndMessages(null)
    mainHandler.postDelayed({
      if (!isHandsFreeEnabled || isPaused) return@postDelayed
      initiateRecognizer()
    }, delayMs)
  }

  private fun initiateRecognizer() {
    if (!isHandsFreeEnabled || isPaused) return

    try {
      if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        state = WakeWordState.Error("Speech recognizer unavailable")
        return
      }

      speechRecognizer?.destroy()
      val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
      speechRecognizer = recognizer

      state = WakeWordState.Listening(0f)

      recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
          if (!isPaused && isHandsFreeEnabled) {
            state = WakeWordState.Listening(0f)
          }
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
          if (!isPaused && isHandsFreeEnabled) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            state = WakeWordState.Listening(normalized)
          }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
          // Normal timeout or no match in continuous listening: cleanly loop back
          if (isHandsFreeEnabled && !isPaused) {
            restartListeningInternal(300)
          }
        }

        override fun onResults(results: Bundle?) {
          val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          val recognized = matches?.firstOrNull() ?: ""
          if (recognized.isNotBlank()) {
            liveHeardText = recognized
            val match = checkForWakeKeyword(recognized)
            if (match != null) {
              handleWakeWordDetected(match.first, match.second)
              return
            }
          }

          // Restart listening loop
          if (isHandsFreeEnabled && !isPaused) {
            restartListeningInternal(250)
          }
        }

        override fun onPartialResults(partialResults: Bundle?) {
          val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          val partial = matches?.firstOrNull() ?: ""
          if (partial.isNotBlank()) {
            liveHeardText = partial
            val match = checkForWakeKeyword(partial)
            if (match != null) {
              handleWakeWordDetected(match.first, match.second)
            }
          }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
      })

      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
      }

      recognizer.startListening(intent)
    } catch (_: Exception) {
      if (isHandsFreeEnabled && !isPaused) {
        restartListeningInternal(1000)
      }
    }
  }

  private fun checkForWakeKeyword(text: String): Pair<String, String?>? {
    val clean = text.lowercase()
      .normalizeSpanishAccents()
      .trim()

    for (keyword in wakeKeywords) {
      val normKeyword = keyword.normalizeSpanishAccents()
      if (clean == normKeyword) {
        return Pair(keyword, null)
      }
      if (clean.startsWith("$normKeyword ") || clean.startsWith("$normKeyword,")) {
        val remainder = clean.substring(normKeyword.length).trim(',', '.', ':', ';', ' ')
        return Pair(keyword, remainder.ifBlank { null })
      }
      if (clean.contains(" $normKeyword")) {
        val idx = clean.indexOf(" $normKeyword") + (" $normKeyword").length
        val remainder = clean.substring(idx).trim(',', '.', ':', ';', ' ')
        return Pair(keyword, remainder.ifBlank { null })
      }
    }
    return null
  }

  private fun handleWakeWordDetected(keyword: String, command: String?) {
    triggerHapticPulse()
    lastDetectedKeyword = keyword
    state = WakeWordState.Detected(keyword, command)
    pause() // Pause continuous listening so command execution or voice dialog takes over
    onDetectedCallback?.invoke(keyword, command)
  }

  private fun triggerHapticPulse() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator?.vibrate(
          VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
        )
      } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(120)
      }
    } catch (_: Exception) {}
  }

  private fun String.normalizeSpanishAccents(): String {
    return this.replace('á', 'a')
      .replace('é', 'e')
      .replace('í', 'i')
      .replace('ó', 'o')
      .replace('ú', 'u')
      .replace('ñ', 'n')
  }
}
