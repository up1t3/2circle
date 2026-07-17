package com.twocircle.bike.common.outcome

/**
 * Domain-level outcome of any operation.
 *
 * Red line: UI never sees raw exceptions. Every boundary (DB, network, GPS, parsing,
 * routing engine) maps to a typed [Failure]. This keeps MVI state honest and lets
 * the UI render specific recovery actions (download region, grant permission, retry…).
 *
 * Usage pattern in UseCases / repositories:
 * ```
 * suspend fun load(): Outcome<List<Track>> = try {
 *     Outcome.Success(dao.all())
 * } catch (e: IOException) {
 *     Outcome.Failure(Failure.Storage.Inaccessible(cause = e))
 * }
 * ```
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val failure: com.twocircle.bike.common.outcome.Failure) :
        Outcome<Nothing>
}

/** True when the operation produced a value. */
inline fun <T> Outcome<T>.isOk(): Boolean = this is Outcome.Success

/** Map the success value; leave failures untouched. */
inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

/** FlatMap on success; propagate failure. */
inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

/** Unwrap to value or null. Use in tests only; production code should branch on type. */
fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

/** Run [block] only on success. */
inline fun <T> Outcome<T>.onSuccess(block: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) block(value)
    return this
}

/** Run [block] only on failure. */
inline fun <T> Outcome<T>.onFailure(block: (Failure) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) block(failure)
    return this
}
