package io.github.satoru2727.miteru.ui

sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Success<T>(
        val value: T,
        val fromCache: Boolean = false,
        val cachedAt: Long = 0L,
    ) : LoadState<T>

    data class Failure(val error: Throwable) : LoadState<Nothing>
}
