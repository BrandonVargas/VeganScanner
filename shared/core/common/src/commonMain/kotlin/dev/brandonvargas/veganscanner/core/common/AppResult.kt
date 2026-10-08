package dev.brandonvargas.veganscanner.core.common

/**
 * Result type used across layers so failures are values, not exceptions.
 * Keeps error handling explicit and exhaustive in ViewModels.
 */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> =
    also {
        if (it is AppResult.Success) action(it.value)
    }

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> =
    also {
        if (it is AppResult.Failure) action(it.error)
    }
