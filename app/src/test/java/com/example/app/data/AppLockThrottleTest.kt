package com.example.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockThrottleTest {

    // --- nextBackoffMs ---

    @Test
    fun `nextBackoffMs returns 0 for failures below threshold`() {
        for (count in 0 until AppLockThrottle.MAX_FAILURES_BEFORE_LOCKOUT) {
            assertEquals("failCount=$count should have no lockout", 0L, AppLockThrottle.nextBackoffMs(count))
        }
    }

    @Test
    fun `nextBackoffMs returns base backoff at threshold`() {
        assertEquals(AppLockThrottle.BASE_BACKOFF_MS, AppLockThrottle.nextBackoffMs(5))
    }

    @Test
    fun `nextBackoffMs doubles each failure after threshold`() {
        assertEquals(30_000L, AppLockThrottle.nextBackoffMs(5))
        assertEquals(60_000L, AppLockThrottle.nextBackoffMs(6))
        assertEquals(120_000L, AppLockThrottle.nextBackoffMs(7))
        assertEquals(240_000L, AppLockThrottle.nextBackoffMs(8))
        assertEquals(480_000L, AppLockThrottle.nextBackoffMs(9))
    }

    @Test
    fun `nextBackoffMs caps at max backoff`() {
        assertEquals(AppLockThrottle.MAX_BACKOFF_MS, AppLockThrottle.nextBackoffMs(10))
        assertEquals(AppLockThrottle.MAX_BACKOFF_MS, AppLockThrottle.nextBackoffMs(11))
        assertEquals(AppLockThrottle.MAX_BACKOFF_MS, AppLockThrottle.nextBackoffMs(50))
        assertEquals(AppLockThrottle.MAX_BACKOFF_MS, AppLockThrottle.nextBackoffMs(Int.MAX_VALUE))
    }

    // --- isLockedOut ---

    @Test
    fun `isLockedOut false when lockoutUntil is in the past`() {
        assertFalse(AppLockThrottle.isLockedOut(nowEpochMs = 5000L, lockoutUntilEpochMs = 3000L))
    }

    @Test
    fun `isLockedOut false when lockoutUntil equals now`() {
        assertFalse(AppLockThrottle.isLockedOut(nowEpochMs = 3000L, lockoutUntilEpochMs = 3000L))
    }

    @Test
    fun `isLockedOut true when lockoutUntil is in the future`() {
        assertTrue(AppLockThrottle.isLockedOut(nowEpochMs = 3000L, lockoutUntilEpochMs = 5000L))
    }

    @Test
    fun `isLockedOut false when lockoutUntil is zero`() {
        assertFalse(AppLockThrottle.isLockedOut(nowEpochMs = 3000L, lockoutUntilEpochMs = 0L))
    }

    // --- remainingLockoutMs ---

    @Test
    fun `remainingLockoutMs returns difference when locked out`() {
        assertEquals(2000L, AppLockThrottle.remainingLockoutMs(nowEpochMs = 3000L, lockoutUntilEpochMs = 5000L))
    }

    @Test
    fun `remainingLockoutMs returns 0 when not locked out`() {
        assertEquals(0L, AppLockThrottle.remainingLockoutMs(nowEpochMs = 5000L, lockoutUntilEpochMs = 3000L))
    }

    @Test
    fun `remainingLockoutMs returns 0 when lockoutUntil is zero`() {
        assertEquals(0L, AppLockThrottle.remainingLockoutMs(nowEpochMs = 3000L, lockoutUntilEpochMs = 0L))
    }

    // --- onFailure / onSuccess state transitions ---

    @Test
    fun `onFailure increments counter and sets lockout when threshold reached`() {
        val now = 10_000L
        val state = AppLockThrottle.ThrottleState(failedAttempts = 4, lockoutUntilEpochMs = 0L)
        val next = AppLockThrottle.onFailure(state, now)

        assertEquals(5, next.failedAttempts)
        assertEquals(now + AppLockThrottle.BASE_BACKOFF_MS, next.lockoutUntilEpochMs)
    }

    @Test
    fun `onFailure below threshold does not set lockout`() {
        val now = 10_000L
        val state = AppLockThrottle.ThrottleState(failedAttempts = 2, lockoutUntilEpochMs = 0L)
        val next = AppLockThrottle.onFailure(state, now)

        assertEquals(3, next.failedAttempts)
        assertEquals(0L, next.lockoutUntilEpochMs)
    }

    @Test
    fun `onFailure from initial state increments to 1 with no lockout`() {
        val state = AppLockThrottle.onFailure(AppLockThrottle.onSuccess(), 10_000L)
        assertEquals(1, state.failedAttempts)
        assertEquals(0L, state.lockoutUntilEpochMs)
    }

    @Test
    fun `onSuccess resets counter and lockout`() {
        val state = AppLockThrottle.ThrottleState(failedAttempts = 7, lockoutUntilEpochMs = 99_000L)
        val reset = AppLockThrottle.onSuccess()
        assertEquals(0, reset.failedAttempts)
        assertEquals(0L, reset.lockoutUntilEpochMs)
    }

    @Test
    fun `onSuccess followed by failures starts fresh`() {
        val now = 10_000L
        val afterSuccess = AppLockThrottle.onSuccess()
        // Simulate 5 failures after reset
        var state = afterSuccess
        repeat(5) { state = AppLockThrottle.onFailure(state, now) }
        assertEquals(5, state.failedAttempts)
        assertEquals(now + AppLockThrottle.BASE_BACKOFF_MS, state.lockoutUntilEpochMs)
    }

    @Test
    fun `lockout doubles on repeated failures beyond threshold`() {
        val now = 10_000L
        var state = AppLockThrottle.ThrottleState(failedAttempts = 4, lockoutUntilEpochMs = 0L)

        // 5th failure: 30s lockout
        state = AppLockThrottle.onFailure(state, now)
        assertEquals(now + 30_000L, state.lockoutUntilEpochMs)

        // 6th failure (still locked out, but onFailure still advances): 60s from now
        // Important: we use the same `now` — in practice time moves forward,
        // but the throttle math is stateless and depends only on failCount.
        state = AppLockThrottle.onFailure(state, now)
        assertEquals(6, state.failedAttempts)
        assertEquals(now + 60_000L, state.lockoutUntilEpochMs)
    }
}
