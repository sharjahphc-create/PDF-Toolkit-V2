package com.example.core.common

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>
}

data class OperationProgress(
    val progress: Float = 0f,
    val statusText: String = "",
    val isIndeterminate: Boolean = false,
    val isCancellable: Boolean = true
)
