package com.subflow.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    private lateinit var sourceSpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var translationCheck: CheckBox
    private lateinit var fontSeek: SeekBar
    private lateinit var opacitySeek: SeekBar
    private lateinit var fontValue: TextView
    private lateinit var opacityValue: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            updateStatus()
            if (granted) startBubbleService()
        }

    private val notificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsManager = SettingsManager(this)

        bindViews()
        setupSpinners()
        loadSettingsIntoUi()
        wireUi()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun bindViews() {
        sourceSpinner = findViewById(R.id.sourceSpinner)
        targetSpinner = findViewById(R.id.targetSpinner)
        translationCheck = findViewById(R.id.translationCheck)
        fontSeek = findViewById(R.id.fontSeek)
        opacitySeek = findViewById(R.id.opacitySeek)
        fontValue = findViewById(R.id.fontValue)
        opacityValue = findViewById(R.id.opacityValue)
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
    }

    private fun setupSpinners() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            SupportedLanguages.labels()
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        sourceSpinner.adapter = adapter
        targetSpinner.adapter = adapter
    }

    private fun loadSettingsIntoUi() {
        val settings = settingsManager.load()
        sourceSpinner.setSelection(SupportedLanguages.indexOfTag(settings.sourceLanguageTag))
        targetSpinner.setSelection(SupportedLanguages.indexOfTag(settings.targetLanguageTag))
        translationCheck.isChecked = settings.translationEnabled
        fontSeek.progress = settings.fontSizeSp
        opacitySeek.progress = settings.bubbleOpacityPercent
        fontValue.text = "${settings.fontSizeSp} sp"
        opacityValue.text = "${settings.bubbleOpacityPercent}%"
    }

    private fun wireUi() {
        fontSeek.max = 40
        opacitySeek.max = 100

        if (fontSeek.progress < 14) fontSeek.progress = 22
        if (opacitySeek.progress < 30) opacitySeek.progress = 82

        fontSeek.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val fixed = progress.coerceAtLeast(14)
                if (fixed != progress) fontSeek.progress = fixed
                fontValue.text = "$fixed sp"
                saveCurrentSettings()
            }
        })

        opacitySeek.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val fixed = progress.coerceIn(30, 100)
                if (fixed != progress) opacitySeek.progress = fixed
                opacityValue.text = "$fixed%"
                saveCurrentSettings()
            }
        })

        translationCheck.setOnCheckedChangeListener { _, _ -> saveCurrentSettings() }

        startButton.setOnClickListener {
            saveCurrentSettings()
            ensurePermissionsAndStart()
        }

        stopButton.setOnClickListener {
            val intent = Intent(this, FloatingBubbleService::class.java).apply {
                action = FloatingBubbleService.ACTION_STOP
            }
            stopService(intent)
            statusText.text = "Servicio detenido."
        }
    }

    private fun saveCurrentSettings() {
        val existing = settingsManager.load()
        val settings = existing.copy(
            sourceLanguageTag = SupportedLanguages.tagAt(sourceSpinner.selectedItemPosition),
            targetLanguageTag = SupportedLanguages.tagAt(targetSpinner.selectedItemPosition),
            translationEnabled = translationCheck.isChecked,
            fontSizeSp = fontSeek.progress.coerceAtLeast(14),
            bubbleOpacityPercent = opacitySeek.progress.coerceIn(30, 100)
        )
        settingsManager.save(settings)
    }

    private fun ensurePermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            statusText.text = "Activa ‘Mostrar sobre otras apps’ y regresa."
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        startBubbleService()
    }

    private fun startBubbleService() {
        val intent = Intent(this, FloatingBubbleService::class.java).apply {
            action = FloatingBubbleService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        statusText.text = "SubFlow activo. Puedes abrir Chrome."
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val micOk = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        statusText.text = buildString {
            append("Permisos actuales:\n")
            append("• Superposición: ")
            append(if (overlayOk) "OK" else "Pendiente")
            append("\n• Micrófono: ")
            append(if (micOk) "OK" else "Pendiente")
            append("\n\nUso recomendado: reproduce el video con volumen suficiente y el teléfono cerca del audio.")
        }
    }
}

abstract class SimpleSeekBarListener : SeekBar.OnSeekBarChangeListener {
    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
}
