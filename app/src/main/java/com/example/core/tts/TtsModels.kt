package com.example.core.tts

enum class TtsStatus {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    SPEAKING,
    PAUSED,
    ERROR,
    UNAVAILABLE
}

data class TtsSentence(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val pageNumber: Int = 1
)

data class TtsVoiceInfo(
    val id: String,
    val name: String,
    val localeTag: String,
    val displayName: String,
    val requiresNetwork: Boolean
)

data class SleepTimerState(
    val isActive: Boolean = false,
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0
)

data class TtsPlaybackState(
    val status: TtsStatus = TtsStatus.UNINITIALIZED,
    val documentId: String? = null,
    val documentTitle: String = "",
    val sentences: List<TtsSentence> = emptyList(),
    val currentSentenceIndex: Int = 0,
    val wordRangeInSentence: IntRange? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val selectedVoice: TtsVoiceInfo? = null,
    val availableVoices: List<TtsVoiceInfo> = emptyList(),
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val errorMessage: String? = null
) {
    val currentSentence: TtsSentence?
        get() = sentences.getOrNull(currentSentenceIndex)

    val progressFraction: Float
        get() = if (sentences.isEmpty()) 0f
        else ((currentSentenceIndex + 1).toFloat() / sentences.size.toFloat()).coerceIn(0f, 1f)
}
