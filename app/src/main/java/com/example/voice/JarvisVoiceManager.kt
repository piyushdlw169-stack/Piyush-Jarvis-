package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "JarvisVoiceManager"

class JarvisVoiceManager(
    private val context: Context,
    private val onCommandTranscribed: (String) -> Unit,
    private val onSpeechError: (String) -> Unit
) {
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _audioRmsLevel = MutableStateFlow(0f)
    val audioRmsLevel: StateFlow<Float> = _audioRmsLevel.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(true)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val ukResult = tts?.setLanguage(Locale.UK)
                    if (ukResult == TextToSpeech.LANG_MISSING_DATA ||
                        ukResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        tts?.setLanguage(Locale.US)
                    }
                    tts?.setPitch(0.92f)
                    tts?.setSpeechRate(1.04f)
                    isTtsReady = true
                }
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }
            })
        }.onFailure { e ->
            Log.e(TAG, "Failed to initialize TextToSpeech", e)
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        _ttsEnabled.value = enabled
        if (!enabled) {
            stopSpeaking()
        }
    }

    fun speak(text: String) {
        if (!_ttsEnabled.value || text.isBlank() || !isTtsReady) return
        runCatching {
            val utteranceId = "jarvis_${System.currentTimeMillis()}"
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }.onFailure { e ->
            Log.e(TAG, "TTS speak error", e)
        }
    }

    fun stopSpeaking() {
        runCatching {
            tts?.stop()
            _isSpeaking.value = false
        }
    }

    fun startListening() {
        stopSpeaking()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onSpeechError("On-device SpeechRecognizer service is unavailable on this tablet image. Use the command bar or quick presets below.")
            return
        }

        runCatching {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createRecognitionListener())
                }
            }

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            _partialTranscript.value = ""
            _isListening.value = true
            speechRecognizer?.startListening(recognizerIntent)
        }.onFailure { e ->
            _isListening.value = false
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            onSpeechError(e.localizedMessage ?: "Failed to start voice recognition.")
        }
    }

    fun stopListening() {
        runCatching {
            speechRecognizer?.stopListening()
            _isListening.value = false
            _audioRmsLevel.value = 0f
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
            }

            override fun onBeginningOfSpeech() {
                _isListening.value = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                _audioRmsLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
                _audioRmsLevel.value = 0f
            }

            override fun onError(error: Int) {
                _isListening.value = false
                _audioRmsLevel.value = 0f
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No voice command detected. Please try speaking again, Sir."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Voice input timed out waiting for audio, Sir."
                    SpeechRecognizer.ERROR_AUDIO -> "Microphone audio capture error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice commands."
                    else -> "Voice recognition unavailable in this environment (code $error). You can type or tap any command directly."
                }
                onSpeechError(message)
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                _audioRmsLevel.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull()?.trim().orEmpty()
                _partialTranscript.value = ""
                if (transcript.isNotBlank()) {
                    onCommandTranscribed(transcript)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotBlank()) {
                    _partialTranscript.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun release() {
        runCatching {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        runCatching {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }
}
