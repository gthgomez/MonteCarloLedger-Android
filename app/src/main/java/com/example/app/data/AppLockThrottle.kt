package com.example.app.data

/**
 * Pure throttle math for PIN brute-force protection.
 *
 * After [MAX_FAILURES_BEFORE_LOCKOUT] consecutive failures, lockout begins at
 * [BASE_BACKOFF_MS] and doubles each subsequent failure, capped at [MAX_BACKOFF_MS].
 * A successful unlock resets the counter.
 */
object AppLockThrottle {

    /** Failures before the first lockout kicks in. */
    const val MAX_FAILURES_BEFORE_LOCKOUT = 5

    /** Duration of the first lockout (30 seconds). */
    const val BASE_BACKOFF_MS = 30_000L

    /** Hard cap on lockout duration (15 minutes). */
    const val MAX_BACKOFF_MS = 15 * 60 * 1000L

    /**
     * Returns the backoff duration in milliseconds for the given cumulative
     * failure count. Returns 0 when the count is below the lockout threshold.
     *
     * Backoff schedule (after 5th failure):
     *   fail 5 →  30s
     *   fail 6 →  60s
     *   fail 7 → 120s
     *   fail 8 → 240s
     *   fail 9 → 480s
     *   fail 10+ → 900s (capped)
     */
    fun nextBackoffMs(failCount: Int): Long {
        if (failCount < MAX_FAILURES_BEFORE_LOCKOUT) return 0L
        val exponent = failCount - MAX_FAILURES_BEFORE_LOCKOUT
        val backoff = BASE_BACKOFF_MS * (1L shl exponent.coerceAtMost(10))
        return backoff.coerceAtMost(MAX_BACKOFF_MS)
    }

    /** True when the current time is before the lockout expiry. */
    fun isLockedOut(nowEpochMs: Long, lockoutUntilEpochMs: Long): Boolean =
        lockoutUntilEpochMs > nowEpochMs

    /** Milliseconds remaining in the current lockout (never negative). */
    fun remainingLockoutMs(nowEpochMs: Long, lockoutUntilEpochMs: Long): Long =
        (lockoutUntilEpochMs - nowEpochMs).coerceAtLeast(0L)

    data class ThrottleState(
        val failedAttempts: Int = 0,
        val lockoutUntilEpochMs: Long = 0L,
    )

    /** Record a failed attempt and return the new state. */
    fun onFailure(currentState: ThrottleState, nowEpochMs: Long): ThrottleState {
        val newCount = currentState.failedAttempts + 1
        val backoff = nextBackoffMs(newCount)
        return ThrottleState(
            failedAttempts = newCount,
            lockoutUntilEpochMs = if (backoff > 0) nowEpochMs + backoff else 0L,
        )
    }

    /** Reset after a successful unlock. */
    fun onSuccess(): ThrottleState = ThrottleState(failedAttempts = 0, lockoutUntilEpochMs = 0L)
}
