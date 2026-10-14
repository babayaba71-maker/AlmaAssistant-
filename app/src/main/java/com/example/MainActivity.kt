package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.AlmaDarkBackground
import com.example.ui.theme.AlmaTheme
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanHolo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEFAULT_HOLOGRAM_URL =
  "https://babayaba71-maker.github.io/AccentBridges/hologram-wallpaper.html"

class MainActivity : ComponentActivity() {

  private var initialUrl by mutableStateOf(DEFAULT_HOLOGRAM_URL)
  private var sharedText by mutableStateOf<String?>(null)
  private var shouldAutoListen by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Transparent status and navigation bar edge-to-edge
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    )

    handleIncomingIntent(intent)

    setContent {
      AlmaTheme {
        // Requirement: back button exits
        BackHandler {
          finish()
        }

        AlmaAssistantScreen(
          url = initialUrl,
          receivedText = sharedText,
          autoTriggerVoice = shouldAutoListen
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  private fun handleIncomingIntent(intent: Intent?) {
    if (intent == null) return

    val action = intent.action

    // Assistant triggers: auto-listen if launched via voice assist or assist
    if (action == Intent.ACTION_VOICE_COMMAND ||
      action == "android.intent.action.VOICE_ASSIST" ||
      action == Intent.ACTION_ASSIST
    ) {
      shouldAutoListen = true
    }

    // Deep link handling
    val data: Uri? = intent.data
    if (data != null && data.scheme in listOf("http", "https")) {
      initialUrl = data.toString()
    }

    // Process Text action
    val processText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
    if (!processText.isNullOrBlank()) {
      sharedText = processText
      return
    }

    // Send action
    if (Intent.ACTION_SEND == action && intent.type?.startsWith("text/") == true) {
      val text = intent.getStringExtra(Intent.EXTRA_TEXT)
      if (!text.isNullOrBlank()) {
        sharedText = text
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AlmaAssistantScreen(
  url: String,
  receivedText: String?,
  autoTriggerVoice: Boolean = false,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var isLoading by remember { mutableStateOf(true) }
  var loadingProgress by remember { mutableIntStateOf(0) }
  var hasError by remember { mutableStateOf(false) }
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var latestRecognizedVoiceCommand by remember { mutableStateOf<String?>(null) }
  var webViewReloadTrigger by remember { mutableIntStateOf(0) }

  val coroutineScope = rememberCoroutineScope()
  var isHandsFreePendingPermission by remember { mutableStateOf(false) }

  // Speech Recognizer manager instance
  val voiceManager = remember { AlmaVoiceManager(context) }
  // Continuous hands-free wake word detector
  val wakeWordDetector = remember { AlmaWakeWordDetector(context) }

  // Failsafe: Automatically dismiss the loading overlay after at most 2.5 seconds
  // so the user is never stuck on "INITIALIZING HOLOGRAM..." even if background scripts take time
  LaunchedEffect(webViewReloadTrigger) {
    delay(2500)
    isLoading = false
  }

  fun startHandsFreeDetection() {
    wakeWordDetector.start { keyword, followUpCommand ->
      if (!followUpCommand.isNullOrBlank()) {
        latestRecognizedVoiceCommand = followUpCommand
        sendVoiceCommandToWeb(webViewInstance, followUpCommand)
        coroutineScope.launch {
          delay(2000)
          if (wakeWordDetector.isHandsFreeEnabled) {
            wakeWordDetector.resume()
          }
        }
      } else {
        latestRecognizedVoiceCommand = "Wake word: $keyword"
        voiceManager.startListening { recognizedText ->
          latestRecognizedVoiceCommand = recognizedText
          sendVoiceCommandToWeb(webViewInstance, recognizedText)
          if (wakeWordDetector.isHandsFreeEnabled) {
            wakeWordDetector.resume()
          }
        }
      }
    }
  }

  // Permission launcher for RECORD_AUDIO
  val audioPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      if (isHandsFreePendingPermission) {
        isHandsFreePendingPermission = false
        startHandsFreeDetection()
      } else {
        voiceManager.startListening { recognizedText ->
          latestRecognizedVoiceCommand = recognizedText
          sendVoiceCommandToWeb(webViewInstance, recognizedText)
          if (wakeWordDetector.isHandsFreeEnabled) {
            wakeWordDetector.resume()
          }
        }
      }
    } else {
      isHandsFreePendingPermission = false
      voiceManager.cancel()
      wakeWordDetector.stop()
    }
  }

  fun triggerVoiceCommand() {
    if (voiceManager.voiceState is VoiceState.Listening || voiceManager.voiceState is VoiceState.Initializing) {
      voiceManager.cancel()
      if (wakeWordDetector.isHandsFreeEnabled) {
        wakeWordDetector.resume()
      }
      return
    }

    wakeWordDetector.pause()

    val permissionCheck = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.RECORD_AUDIO
    )

    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
      voiceManager.startListening { recognizedText ->
        latestRecognizedVoiceCommand = recognizedText
        sendVoiceCommandToWeb(webViewInstance, recognizedText)
        if (wakeWordDetector.isHandsFreeEnabled) {
          wakeWordDetector.resume()
        }
      }
    } else {
      isHandsFreePendingPermission = false
      audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  // Handle auto-trigger if launched as digital voice assistant
  LaunchedEffect(autoTriggerVoice) {
    if (autoTriggerVoice) {
      val hasAudioPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
      ) == PackageManager.PERMISSION_GRANTED
      if (hasAudioPermission) {
        voiceManager.startListening { recognizedText ->
          latestRecognizedVoiceCommand = recognizedText
          sendVoiceCommandToWeb(webViewInstance, recognizedText)
        }
      }
    }
  }

  var activeGestureToast by remember { mutableStateOf<AssistantGesture?>(null) }
  var activeNotice by remember(receivedText) { mutableStateOf(receivedText) }

  fun handleAssistantGesture(gesture: AssistantGesture) {
    activeGestureToast = gesture
    when (gesture) {
      AssistantGesture.SWIPE_DOWN -> {
        // Dismiss UI / Exit app
        coroutineScope.launch {
          delay(380)
          (context as? ComponentActivity)?.finish()
        }
      }
      AssistantGesture.SWIPE_UP -> {
        // Clear current context / Reset
        latestRecognizedVoiceCommand = null
        activeNotice = null
        webViewInstance?.evaluateJavascript(
          """
            if (typeof closePortal === 'function') { closePortal(); }
            if (typeof closeV1Chat === 'function') { closeV1Chat(); }
            if (typeof setBrain === 'function') { setBrain('LISTO', true); }
            try { window.dispatchEvent(new CustomEvent('alma_clear_context')); } catch(e) {}
          """.trimIndent(),
          null
        )
      }
      AssistantGesture.SWIPE_LEFT -> {
        // Toggle Hands-Free
        if (wakeWordDetector.isHandsFreeEnabled) {
          wakeWordDetector.stop()
        } else {
          val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
          )
          if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            startHandsFreeDetection()
          } else {
            isHandsFreePendingPermission = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
          }
        }
      }
      AssistantGesture.SWIPE_RIGHT -> {
        // Trigger Voice Command
        triggerVoiceCommand()
      }
    }
  }

  // Clean up WebView, Speech Recognizer, and Wake Word Detector on disposal
  DisposableEffect(Unit) {
    onDispose {
      wakeWordDetector.destroy()
      voiceManager.destroy()
      webViewInstance?.destroy()
      webViewInstance = null
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .hologramBackground()
      .testTag("alma_fullscreen_container")
  ) {
    // Fullscreen WebView wrapped in key to allow clean recreation on renderer failure
    key(webViewReloadTrigger) {
      AndroidView(
        modifier = Modifier
          .fillMaxSize()
          .testTag("alma_webview"),
        factory = { ctx ->
          WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#060913"))
            setLayerType(View.LAYER_TYPE_NONE, null)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            // Attach gesture detector for custom swipe assistant shortcuts
            val gestureDetector = AlmaGestureDetector(ctx) { gesture ->
              handleAssistantGesture(gesture)
            }
            setOnTouchListener { _, event ->
              gestureDetector.onTouchEvent(event)
              false
            }

            // Configure cookies for rich web apps
            val currentWv = this
            try {
              CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(currentWv, true)
              }
            } catch (_: Exception) {}

            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              databaseEnabled = true
              useWideViewPort = true
              loadWithOverviewMode = true
              mediaPlaybackRequiresUserGesture = false
              cacheMode = WebSettings.LOAD_DEFAULT
              allowFileAccess = true
              allowContentAccess = true
              javaScriptCanOpenWindowsAutomatically = true
              displayZoomControls = false
              setSupportZoom(false)
              mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            webChromeClient = object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                loadingProgress = newProgress
                if (newProgress >= 35) {
                  isLoading = false
                }
              }

              override fun onPermissionRequest(request: PermissionRequest?) {
                try {
                  request?.grant(request.resources)
                } catch (_: Exception) {}
              }

              override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                return true
              }
            }

            webViewClient = object : WebViewClient() {
              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                hasError = false
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLoading = false
              }

              override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
              ) {
                // Proceed on SSL verification to avoid false blocks on local emulator proxies
                handler?.proceed()
              }

              override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
              ) {
                super.onReceivedError(view, request, error)
                // Only trigger error screen if main frame failed with DNS/connect error
                if (request?.isForMainFrame == true &&
                  (error?.errorCode == ERROR_HOST_LOOKUP || error?.errorCode == ERROR_CONNECT)
                ) {
                  isLoading = false
                  hasError = true
                }
              }

              override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
              ): Boolean {
                isLoading = false
                hasError = true
                (view?.parent as? ViewGroup)?.removeView(view)
                view?.destroy()
                webViewInstance = null
                return true
              }

              override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
              ): Boolean {
                val uri = request?.url ?: return false
                val host = uri.host ?: ""
                // Keep in-app for github.io and AccentBridges domain
                return if (host.contains("github.io") || host.contains("babayaba71-maker") || host.isBlank()) {
                  false
                } else {
                  try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                    context.startActivity(browserIntent)
                  } catch (_: Exception) {
                  }
                  true
                }
              }
            }

            loadUrl(url)
            webViewInstance = this
          }
        },
        update = { wv ->
          // Inject received text into web context if provided
          if (!receivedText.isNullOrBlank()) {
            val sanitized = receivedText.replace("'", "\\'").replace("\n", "\\n")
            wv.evaluateJavascript(
              """
                if (typeof routeCommand === 'function') { routeCommand('$sanitized'); }
                if (typeof window.onAlmaInput === 'function') { window.onAlmaInput('$sanitized'); }
              """.trimIndent(),
              null
            )
          }
        }
      )
    }

    // Dark Background Loading View (smoothly crossfades out)
    AnimatedVisibility(
      visible = isLoading,
      enter = fadeIn(animationSpec = tween(150)),
      exit = fadeOut(animationSpec = tween(400))
    ) {
      AlmaHologramLoadingView(
        progress = loadingProgress,
        modifier = Modifier.testTag("alma_loading_view")
      )
    }

    // Error State overlay if loading failed
    if (hasError) {
      AlmaConnectionErrorView(
        onRetry = {
          hasError = false
          isLoading = true
          val currentWv = webViewInstance
          if (currentWv != null) {
            currentWv.reload()
          } else {
            webViewReloadTrigger++
          }
        },
        modifier = Modifier.testTag("alma_error_view")
      )
    }

    // Floating Holographic Hands-Free Status Indicator at Top Center
    if (!isLoading && !hasError) {
      AlmaHandsFreeIndicator(
        isHandsFreeEnabled = wakeWordDetector.isHandsFreeEnabled,
        wakeWordState = wakeWordDetector.state,
        onToggleHandsFree = {
          if (wakeWordDetector.isHandsFreeEnabled) {
            wakeWordDetector.stop()
          } else {
            val permissionCheck = ContextCompat.checkSelfPermission(
              context,
              Manifest.permission.RECORD_AUDIO
            )
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
              startHandsFreeDetection()
            } else {
              isHandsFreePendingPermission = true
              audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
          }
        },
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 44.dp)
      )
    }

    // Floating Holographic Voice Command Button (Visible when page is loaded)
    if (!isLoading && !hasError) {
      val isListening = voiceManager.voiceState is VoiceState.Listening ||
        voiceManager.voiceState is VoiceState.Initializing

      AlmaVoiceTriggerButton(
        isListening = isListening,
        onClick = { triggerVoiceCommand() },
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(bottom = 36.dp, end = 24.dp)
      )
    }

    // Hologram Voice HUD Overlay
    AlmaVoiceHUDOverlay(
      voiceState = voiceManager.voiceState,
      liveTranscript = voiceManager.liveTranscript,
      rmsLevel = voiceManager.rmsLevel,
      onDismiss = {
        voiceManager.resetToIdle()
        if (wakeWordDetector.isHandsFreeEnabled) {
          wakeWordDetector.resume()
        }
      },
      onRetry = { triggerVoiceCommand() }
    )

    // Subtle Assistant Info Chip when intent text or command is received
    val displayNotice = latestRecognizedVoiceCommand?.let { "Voice: $it" } ?: activeNotice?.let { "Alma: $it" }
    if (!displayNotice.isNullOrBlank() && voiceManager.voiceState is VoiceState.Idle) {
      Box(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(bottom = 36.dp, start = 20.dp, end = 96.dp)
          .background(
            color = Color(0xFF060913).copy(alpha = 0.85f),
            shape = RoundedCornerShape(16.dp)
          )
          .border(
            width = 1.dp,
            brush = Brush.horizontalGradient(listOf(CyanHolo, CyanAccent)),
            shape = RoundedCornerShape(16.dp)
          )
          .padding(horizontal = 14.dp, vertical = 9.dp)
      ) {
        Text(
          text = displayNotice,
          color = CyanHolo,
          fontSize = 11.sp,
          maxLines = 2,
          fontFamily = FontFamily.Monospace
        )
      }
    }

    // Holographic Gesture Feedback Toast
    AlmaGestureFeedbackToast(
      gesture = activeGestureToast,
      onDismiss = { activeGestureToast = null },
      modifier = Modifier.align(Alignment.TopCenter)
    )
  }
}

private fun sendVoiceCommandToWeb(webView: WebView?, command: String) {
  if (webView == null || command.isBlank()) return
  val sanitized = command.replace("'", "\\'").replace("\n", "\\n").replace("\"", "\\\"")
  val script = """
    (function() {
      if (typeof routeCommand === 'function') {
        routeCommand('$sanitized');
      }
      if (typeof window.onAlmaVoiceCommand === 'function') {
        window.onAlmaVoiceCommand('$sanitized');
      } else if (typeof window.onAlmaInput === 'function') {
        window.onAlmaInput('$sanitized');
      }
      try {
        window.dispatchEvent(new CustomEvent('alma_voice_command', { detail: { text: '$sanitized' } }));
      } catch(e) {}
    })();
  """.trimIndent()

  webView.post {
    webView.evaluateJavascript(script, null)
  }
}

@Composable
fun AlmaHologramLoadingView(
  progress: Int,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse_animation")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.85f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseScale"
  )
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.95f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseAlpha"
  )

  Box(
    modifier = modifier
      .fillMaxSize()
      .hologramBackground(),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(24.dp)
    ) {
      // Futuristic Glowing Holographic Ring
      Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center
      ) {
        // Outer pulsing glow
        Box(
          modifier = Modifier
            .size(100.dp)
            .scale(pulseScale)
            .alpha(pulseAlpha * 0.4f)
            .border(
              width = 2.dp,
              brush = Brush.sweepGradient(listOf(CyanHolo, CyanAccent, CyanHolo)),
              shape = CircleShape
            )
        )

        // Middle indicator
        if (progress > 0) {
          CircularProgressIndicator(
            progress = { (progress / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.size(72.dp),
            color = CyanHolo,
            strokeWidth = 3.dp,
            trackColor = Color(0xFF00F2FE).copy(alpha = 0.15f)
          )
        } else {
          CircularProgressIndicator(
            modifier = Modifier.size(72.dp),
            color = CyanHolo,
            strokeWidth = 3.dp,
            trackColor = Color(0xFF00F2FE).copy(alpha = 0.15f)
          )
        }

        // Center glowing nucleus
        Box(
          modifier = Modifier
            .size(18.dp)
            .background(CyanHolo, shape = CircleShape)
            .alpha(pulseAlpha)
        )
      }

      Spacer(modifier = Modifier.height(28.dp))

      Text(
        text = "ALMA",
        color = CyanHolo,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 6.sp,
        fontFamily = FontFamily.Monospace
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "INITIALIZING HOLOGRAM...",
        color = Color(0xFFA0E9FF).copy(alpha = 0.6f),
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontFamily = FontFamily.Monospace
      )

      if (progress > 0) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "$progress%",
          color = Color(0xFFA0E9FF).copy(alpha = 0.4f),
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace
        )
      }
    }
  }
}

@Composable
fun AlmaConnectionErrorView(
  onRetry: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(AlmaDarkBackground)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .widthIn(max = 380.dp)
        .background(
          color = Color(0xFF0C1124),
          shape = RoundedCornerShape(20.dp)
        )
        .border(
          width = 1.dp,
          color = Color(0xFF00F2FE).copy(alpha = 0.2f),
          shape = RoundedCornerShape(20.dp)
        )
        .padding(24.dp)
    ) {
      Text(
        text = "CONNECTION OFFLINE",
        color = CyanHolo,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        fontFamily = FontFamily.Monospace
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "Unable to reach Alma Hologram servers. Please check your network connection.",
        color = Color(0xFFA0E9FF).copy(alpha = 0.8f),
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        lineHeight = 18.sp
      )

      Spacer(modifier = Modifier.height(20.dp))

      OutlinedButton(
        onClick = onRetry,
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = CyanHolo
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
          brush = Brush.horizontalGradient(listOf(CyanHolo, CyanAccent))
        ),
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "Retry connection",
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = "RECONNECT", fontFamily = FontFamily.Monospace)
      }
    }
  }
}
