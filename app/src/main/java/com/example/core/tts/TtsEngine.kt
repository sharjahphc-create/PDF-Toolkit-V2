package com.example.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.core.common.IoDispatcher
import com.example.core.datastore.PreferencesRepository
import com.example.data.repository.LibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val libraryRepository: LibraryRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(TtsPlaybackState())
    val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var sleepTimerJob: Job? = null
    private var listeningTrackerJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_state.value.status == TtsStatus.SPEAKING) {
                    resumeOnFocusGain = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    pauseInternal(abandonFocus = focusChange == AudioManager.AUDIOFOCUS_LOSS)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain && _state.value.status == TtsStatus.PAUSED) {
                    resumeOnFocusGain = false
                    play()
                }
            }
        }
    }

    init {
        scope.launch {
            val prefs = preferencesRepository.preferences.first()
            _state.update {
                it.copy(
                    speechRate = prefs.ttsSpeechRate,
                    pitch = prefs.ttsPitch
                )
            }
        }
    }

    fun initializeIfNeeded() {
        val currentStatus = _state.value.status
        if (tts != null && currentStatus != TtsStatus.ERROR && currentStatus != TtsStatus.UNAVAILABLE) {
            return
        }
        _state.update { it.copy(status = TtsStatus.INITIALIZING, errorMessage = null) }
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    onTtsInitialized()
                } else {
                    _state.update {
                        it.copy(
                            status = TtsStatus.UNAVAILABLE,
                            errorMessage = "Text-to-Speech initialization failed (code $status)"
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    status = TtsStatus.UNAVAILABLE,
                    errorMessage = t.localizedMessage ?: "Text-to-Speech unavailable"
                )
            }
        }
    }

    private fun onTtsInitialized() {
        val engine = tts ?: return
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val idx = utteranceId?.removePrefix(UTTERANCE_PREFIX)?.toIntOrNull() ?: return
                _state.update {
                    it.copy(
                        status = TtsStatus.SPEAKING,
                        currentSentenceIndex = idx,
                        wordRangeInSentence = null,
                        errorMessage = null
                    )
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val idx = utteranceId?.removePrefix(UTTERANCE_PREFIX)?.toIntOrNull() ?: return
                if (idx == _state.value.currentSentenceIndex && start in 0..end) {
                    _state.update { it.copy(wordRangeInSentence = start until end) }
                }
            }

            override fun onDone(utteranceId: String?) {
                val idx = utteranceId?.removePrefix(UTTERANCE_PREFIX)?.toIntOrNull() ?: return
                val current = _state.value
                if (current.status != TtsStatus.SPEAKING) return
                val nextIndex = idx + 1
                if (nextIndex < current.sentences.size) {
                    _state.update {
                        it.copy(
                            currentSentenceIndex = nextIndex,
                            wordRangeInSentence = null
                        )
                    }
                    persistProgress(nextIndex)
                    speakSentenceAt(nextIndex)
                } else {
                    // Completed document playback
                    abandonAudioFocus()
                    stopListeningTracker()
                    _state.update {
                        it.copy(
                            status = TtsStatus.READY,
                            currentSentenceIndex = 0,
                            wordRangeInSentence = null
                        )
                    }
                    persistProgress(0)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onError(utteranceId, TextToSpeech.ERROR)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (_state.value.status == TtsStatus.PAUSED || _state.value.status == TtsStatus.READY) {
                    return
                }
                abandonAudioFocus()
                stopListeningTracker()
                _state.update {
                    it.copy(
                        status = TtsStatus.ERROR,
                        errorMessage = "TTS playback error ($errorCode)"
                    )
                }
            }
        })

        scope.launch {
            val prefs = preferencesRepository.preferences.first()
            val voices = loadOfflineFirstVoices(engine)
            val preferredVoice = voices.firstOrNull { it.name == prefs.ttsVoiceName }
                ?: voices.firstOrNull { it.localeTag == Locale.getDefault().toLanguageTag() }
                ?: voices.firstOrNull()

            preferredVoice?.let { applyVoiceToEngine(engine, it.name) }
            engine.setSpeechRate(prefs.ttsSpeechRate)
            engine.setPitch(prefs.ttsPitch)

            _state.update { current ->
                current.copy(
                    status = if (current.status == TtsStatus.SPEAKING) TtsStatus.SPEAKING else TtsStatus.READY,
                    availableVoices = voices,
                    selectedVoice = preferredVoice,
                    speechRate = prefs.ttsSpeechRate,
                    pitch = prefs.ttsPitch
                )
            }
        }
    }

    private fun loadOfflineFirstVoices(engine: TextToSpeech): List<TtsVoiceInfo> {
        val rawVoices: Set<Voice> = runCatching { engine.voices }.getOrNull().orEmpty()
        val offlineVoices = rawVoices
            .filter { !it.isNetworkConnectionRequired }
            .ifEmpty { rawVoices }
        return offlineVoices
            .sortedWith(compareBy<Voice> { it.locale.language != Locale.getDefault().language }
                .thenBy { it.locale.displayName }
                .thenBy { it.name })
            .map { voice ->
                TtsVoiceInfo(
                    id = voice.name,
                    name = voice.name,
                    localeTag = voice.locale.toLanguageTag(),
                    displayName = "${voice.locale.displayName} (${voice.name.takeLast(8)})",
                    requiresNetwork = voice.isNetworkConnectionRequired
                )
            }
    }

    private fun applyVoiceToEngine(engine: TextToSpeech, voiceName: String) {
        val target = runCatching { engine.voices }.getOrNull()?.firstOrNull { it.name == voiceName }
        if (target != null) {
            engine.voice = target
        } else {
            engine.language = Locale.getDefault()
        }
    }

    fun loadDocument(
        documentId: String,
        title: String,
        text: String,
        startSentenceIndex: Int = 0
    ) {
        initializeIfNeeded()
        if (_state.value.status == TtsStatus.SPEAKING) {
            tts?.stop()
            abandonAudioFocus()
            stopListeningTracker()
        }
        val sentences = TtsTextSegmenter.segment(text)
        val clampedIndex = if (sentences.isEmpty()) 0 else startSentenceIndex.coerceIn(0, sentences.lastIndex)
        _state.update { current ->
            val nextStatus = when (current.status) {
                TtsStatus.UNAVAILABLE, TtsStatus.INITIALIZING -> current.status
                else -> TtsStatus.READY
            }
            current.copy(
                status = nextStatus,
                documentId = documentId,
                documentTitle = title,
                sentences = sentences,
                currentSentenceIndex = clampedIndex,
                wordRangeInSentence = null,
                errorMessage = if (sentences.isEmpty()) "No speakable text found in document" else null
            )
        }
    }

    fun play() {
        val current = _state.value
        if (current.sentences.isEmpty()) return
        if (tts == null || current.status == TtsStatus.UNINITIALIZED) {
            initializeIfNeeded()
            return
        }
        if (current.status == TtsStatus.UNAVAILABLE) return
        if (!requestAudioFocus()) return

        _state.update { it.copy(status = TtsStatus.SPEAKING, errorMessage = null) }
        startListeningTracker()
        speakSentenceAt(current.currentSentenceIndex)
    }

    fun pause() {
        pauseInternal(abandonFocus = true)
    }

    private fun pauseInternal(abandonFocus: Boolean) {
        tts?.stop()
        if (abandonFocus) abandonAudioFocus()
        stopListeningTracker()
        _state.update { current ->
            if (current.status == TtsStatus.SPEAKING) {
                current.copy(status = TtsStatus.PAUSED, wordRangeInSentence = null)
            } else {
                current
            }
        }
        persistProgress(_state.value.currentSentenceIndex)
    }

    fun stop() {
        tts?.stop()
        abandonAudioFocus()
        stopListeningTracker()
        cancelSleepTimer()
        _state.update { current ->
            current.copy(
                status = if (current.status == TtsStatus.UNAVAILABLE) TtsStatus.UNAVAILABLE else TtsStatus.READY,
                currentSentenceIndex = 0,
                wordRangeInSentence = null
            )
        }
        persistProgress(0)
    }

    fun nextSentence() {
        val current = _state.value
        if (current.sentences.isEmpty()) return
        val next = (current.currentSentenceIndex + 1).coerceAtMost(current.sentences.lastIndex)
        seekToSentence(next)
    }

    fun previousSentence() {
        val current = _state.value
        if (current.sentences.isEmpty()) return
        val prev = (current.currentSentenceIndex - 1).coerceAtLeast(0)
        seekToSentence(prev)
    }

    fun seekToSentence(index: Int) {
        val current = _state.value
        if (current.sentences.isEmpty()) return
        val clamped = index.coerceIn(0, current.sentences.lastIndex)
        val wasSpeaking = current.status == TtsStatus.SPEAKING
        if (wasSpeaking) {
            tts?.stop()
        }
        _state.update { it.copy(currentSentenceIndex = clamped, wordRangeInSentence = null) }
        persistProgress(clamped)
        if (wasSpeaking) {
            speakSentenceAt(clamped)
        }
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.5f)
        tts?.setSpeechRate(clamped)
        _state.update { it.copy(speechRate = clamped) }
        scope.launch { preferencesRepository.setTtsSpeechRate(clamped) }
        if (_state.value.status == TtsStatus.SPEAKING) {
            tts?.stop()
            speakSentenceAt(_state.value.currentSentenceIndex)
        }
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.6f, 1.5f)
        tts?.setPitch(clamped)
        _state.update { it.copy(pitch = clamped) }
        scope.launch { preferencesRepository.setTtsPitch(clamped) }
        if (_state.value.status == TtsStatus.SPEAKING) {
            tts?.stop()
            speakSentenceAt(_state.value.currentSentenceIndex)
        }
    }

    fun selectVoice(voiceName: String) {
        val engine = tts ?: return
        val matched = _state.value.availableVoices.firstOrNull { it.name == voiceName } ?: return
        applyVoiceToEngine(engine, voiceName)
        _state.update { it.copy(selectedVoice = matched) }
        scope.launch { preferencesRepository.setTtsVoiceName(voiceName) }
        if (_state.value.status == TtsStatus.SPEAKING) {
            engine.stop()
            speakSentenceAt(_state.value.currentSentenceIndex)
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _state.update { it.copy(sleepTimer = SleepTimerState()) }
            return
        }
        val totalSeconds = minutes * 60
        _state.update {
            it.copy(
                sleepTimer = SleepTimerState(
                    isActive = true,
                    totalSeconds = totalSeconds,
                    remainingSeconds = totalSeconds
                )
            )
        }
        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1
                _state.update {
                    it.copy(
                        sleepTimer = it.sleepTimer.copy(
                            isActive = remaining > 0,
                            remainingSeconds = remaining
                        )
                    )
                }
            }
            pauseInternal(abandonFocus = true)
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _state.update { it.copy(sleepTimer = SleepTimerState()) }
    }

    private fun speakSentenceAt(index: Int) {
        val engine = tts ?: return
        val sentence = _state.value.sentences.getOrNull(index) ?: return
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "$UTTERANCE_PREFIX$index")
        }
        engine.speak(sentence.text, TextToSpeech.QUEUE_FLUSH, params, "$UTTERANCE_PREFIX$index")
    }

    private fun requestAudioFocus(): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()
        audioFocusRequest = req
        val result = audioManager.requestAudioFocus(req)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    private fun persistProgress(sentenceIndex: Int) {
        val docId = _state.value.documentId ?: return
        val currentSentence = _state.value.sentences.getOrNull(sentenceIndex)
        scope.launch {
            libraryRepository.updateTtsProgress(
                documentId = docId,
                ttsSentenceIndex = sentenceIndex,
                pageNumber = currentSentence?.pageNumber
            )
        }
    }

    private fun startListeningTracker() {
        listeningTrackerJob?.cancel()
        val docId = _state.value.documentId ?: return
        listeningTrackerJob = scope.launch {
            while (true) {
                delay(60_000L)
                if (_state.value.status == TtsStatus.SPEAKING) {
                    libraryRepository.recordTtsListeningMinute(docId)
                }
            }
        }
    }

    private fun stopListeningTracker() {
        listeningTrackerJob?.cancel()
        listeningTrackerJob = null
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val UTTERANCE_PREFIX = "pdf_toolkit_utt_"
    }
}
