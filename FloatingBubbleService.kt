package com.subflow.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var subtitleText: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var restartHandler: Handler? = null
    private var translationHelper: TranslationHelper? = null
    private lateinit var settingsManager: SettingsManager

    private var lastRawText: String = ""
    private var translateSequence = 0

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        restartHandler = Handler(Looper.getMainLooper())
        startForeground(NOTIFICATION_ID, buildNotification("SubFlow activo"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startOverlayAndRecognition()
        }
        return START_STICKY
    }

    private fun startOverlayAndRecognition() {
        val settings = settingsManager.load()
        ensureBubble(settings)
        subtitleText?.text = if (settings.translationEnabled) {
            "SubFlow iniciando…\nPreparando traducción y subtítulos."
        } else {
            "SubFlow iniciando…\nEscuchando audio ambiente."
        }

        if (settings.translationEnabled) {
            translationHelper?.close()
            translationHelper = TranslationHelper(
                context = this,
                sourceLanguageTag = settings.sourceLanguageTag,
                targetLanguageTag = settings.targetLanguageTag
            )
            translationHelper?.downloadIfNeeded(
                onReady = {
                    subtitleText?.text = "Modelo listo. Escuchando…"
                    startRecognizer(settings.sourceLanguageTag)
                },
                onError = { error ->
                    subtitleText?.text = "No se descargó el modelo. Mostrando texto original.\n$error"
                    startRecognizer(settings.sourceLanguageTag)
                }
            )
        } else {
            startRecognizer(settings.sourceLanguageTag)
        }
    }

    private fun ensureBubble(settings: AppSettings) {
        if (bubbleView != null) {
            subtitleText?.textSize = settings.fontSizeSp.toFloat()
            bubbleView?.alpha = settings.bubbleOpacityPercent / 100f
            return
        }

        bubbleView = LayoutInflater.from(this).inflate(R.layout.view_bubble, null)
        subtitleText = bubbleView?.findViewById(R.id.subtitleText)
        subtitleText?.textSize = settings.fontSizeSp.toFloat()
        bubbleView?.alpha = settings.bubbleOpacityPercent / 100f

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = settings.bubblePosX
            y = settings.bubblePosY
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubbleView?.setOnTouchListener { _, event ->
            val params = bubbleParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleView, params)
                    settingsManager.save(settings.copy(bubblePosX = params.x, bubblePosY = params.y))
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun startRecognizer(languageTag: String) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            subtitleText?.text = "Falta permiso de micrófono."
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            subtitleText?.text = "Este teléfono no tiene reconocimiento de voz disponible."
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    subtitleText?.text = "Escuchando…"
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    subtitleText?.text = "Reintentando subtitulado…"
                    scheduleRestart(languageTag, 700L)
                }

                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    handleRecognizedText(text, isFinal = true, languageTag = languageTag)
                    scheduleRestart(languageTag, 300L)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    handleRecognizedText(text, isFinal = false, languageTag = languageTag)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        beginListening(languageTag)
    }

    private fun beginListening(languageTag: String) {
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(recognizerIntent)
    }

    private fun handleRecognizedText(text: String, isFinal: Boolean, languageTag: String) {
        if (text.isBlank() || text == lastRawText) return
        lastRawText = text

        val settings = settingsManager.load()
        val currentSequence = ++translateSequence

        if (!settings.translationEnabled || settings.sourceLanguageTag == settings.targetLanguageTag) {
            subtitleText?.text = text
            updateNotification(text)
            return
        }

        translationHelper?.translate(
            text = text,
            onResult = { translated ->
                if (currentSequence != translateSequence) return@translate
                subtitleText?.text = translated
                updateNotification(translated)
            },
            onError = { error ->
                if (currentSequence != translateSequence) return@translate
                subtitleText?.text = text
                updateNotification(text)
            }
        ) ?: run {
            subtitleText?.text = text
            updateNotification(text)
        }
    }

    private fun scheduleRestart(languageTag: String, delayMs: Long) {
        restartHandler?.removeCallbacksAndMessages(null)
        restartHandler?.postDelayed({ beginListening(languageTag) }, delayMs)
    }

    private fun buildNotification(content: String): Notification {
        createNotificationChannel()
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_subflow_notification)
            .setContentTitle("SubFlow")
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(0, "Abrir", pendingIntent)
            .addAction(0, "Detener", stopPendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(content.take(60)))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SubFlow",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        restartHandler?.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        speechRecognizer = null
        translationHelper?.close()
        translationHelper = null
        bubbleView?.let {
            windowManager.removeView(it)
        }
        bubbleView = null
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val CHANNEL_ID = "subflow_channel"
        private const val NOTIFICATION_ID = 4040
        const val ACTION_START = "com.subflow.app.START"
        const val ACTION_STOP = "com.subflow.app.STOP"
    }
}
