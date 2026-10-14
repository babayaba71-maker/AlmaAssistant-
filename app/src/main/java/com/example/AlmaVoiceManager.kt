package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed class VoiceState {
  object Idle : VoiceState()
  object Initializing : VoiceState()
  object Listening : VoiceState()
  object Processing : VoiceState()
  data class Success(val recognizedText: String) : VoiceState()
  data class Error(val message: String) : VoiceState()
}

class AlmaVoiceManager(private val context: Context) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private var speechRecognizer: SpeechRecognizer? = null

  var voiceState by mutableStateOf<VoiceState>(VoiceState.Idle)
    private set

  var liveTranscript by mutableStateOf("")
    private set

  var rmsLevel by mutableFloatStateOf(0f)
    private set

  val isAvailable: Boolean
    get() = SpeechRecognizer.isRecognitionAvailable(context)

  fun startListening(onCommandRecognized: (String) -> Unit) {
    mainHandler.post {
      try {
        if (!isAvailable) {
          voiceState = VoiceState.Error("Speech recognition not available on this device")
          return@post
        }

        // Clean up previous instance if needed
        speechRecognizer?.destroy()
        speechRecognizer = null

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        liveTranscript = ""
        rmsLevel = 0f
        voiceState = VoiceState.Initializing

        recognizer.setRecognitionListener(object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {
            voiceState = VoiceState.Listening
          }

          override fun onBeginningOfSpeech() {
            voiceState = VoiceState.Listening
          }

          override fun onRmsChanged(rmsdB: Float) {
            // Map rmsdB (-2 to 10 dB typically) to normalized 0f..1f range
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            rmsLevel = normalized
          }

          override fun onBufferReceived(buffer: ByteArray?) {}

          override fun onEndOfSpeech() {
            voiceState = VoiceState.Processing
            rmsLevel = 0f
          }

          override fun onError(error: Int) {
            val errorMessage = mapErrorCode(error)
            voiceState = VoiceState.Error(errorMessage)
            rmsLevel = 0f
          }

          override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val topMatch = matches?.firstOrNull()?.trim()

            if (!topMatch.isNullOrBlank()) {
              liveTranscript = topMatch
              voiceState = VoiceState.Success(topMatch)
              onCommandRecognized(topMatch)
            } else {
              voiceState = VoiceState.Error("No command detected")
            }
            rmsLevel = 0f
          }

          override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = partial?.firstOrNull()?.trim()
            if (!partialText.isNullOrBlank()) {
              liveTranscript = partialText
            }
          }

          override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
          )
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
          putExtra(RecognizerIntent.EXTRA_PROMPT, "Alma is listening...")
        }

        recognizer.startListening(intent)
      } catch (e: Exception) {
        voiceState = VoiceState.Error(e.message ?: "Failed to initiate voice recognition")
      }
    }
  }

  fun stopListening() {
    mainHandler.post {
      try {
        speechRecognizer?.stopListening()
      } catch (_: Exception) {}
    }
  }

  fun cancel() {
    mainHandler.post {
      try {
        speechRecognizer?.cancel()
      } catch (_: Exception) {}
      voiceState = VoiceState.Idle
      liveTranscript = ""
      rmsLevel = 0f
    }
  }

  fun resetToIdle() {
    voiceState = VoiceState.Idle
    liveTranscript = ""
    rmsLevel = 0f
  }

  fun destroy() {
    mainHandler.post {
      try {
        speechRecognizer?.destroy()
      } catch (_: Exception) {}
      speechRecognizer = null
      voiceState = VoiceState.Idle
    }
  }

  private fun mapErrorCode(errorCode: Int): String {
    return when (errorCode) {
      SpeechRecognizer.ERROR_AUDIO -> "Audio recording issue"
      SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error"
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
      SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition"
      SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out"
      SpeechRecognizer.ERROR_NO_MATCH -> "No command detected"
      SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy"
      SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input heard"
      else -> "Recognition issue (code $errorCode)"
    }
  }
}
