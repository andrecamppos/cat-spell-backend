package com.catspell.api.common

import com.catspell.api.common.ratelimit.RateLimitBuckets
import com.github.benmanes.caffeine.cache.Ticker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/**
 * WR-10 / T-18-04 / T-18-06: the shared per-key bucket store is bounded by maxKeys and expires a key only after one
 * full refill window without access, so an idle key comes back full while an active key is never reset mid-window.
 * A fake Ticker drives cache time and a same-thread executor runs maintenance inline, so every case is deterministic.
 * Pure class, no Spring.
 */
class RateLimitBucketsTest {

    private val window = Duration.ofHours(1)
    private val nanos = AtomicLong(1_000_000_000L)
    private val fakeTicker = Ticker { nanos.get() }
    private val sameThread = Executor { it.run() }

    private fun buckets(capacity: Long = 3, maxKeys: Long = 100) =
        RateLimitBuckets(capacity, window, maxKeys, fakeTicker, sameThread)

    private fun advance(duration: Duration) {
        nanos.addAndGet(duration.toNanos())
    }

    @Test
    fun `the same key returns the same bucket and different keys return different buckets`() {
        val store = buckets()

        assertSame(store.bucketFor("a@example.com"), store.bucketFor("a@example.com"), "a key must map to one bucket")
        assertNotSame(store.bucketFor("a@example.com"), store.bucketFor("b@example.com"), "keys must not share a bucket")
    }

    @Test
    fun `a bucket allows exactly capacity tokens in one window`() {
        val store = buckets(capacity = 3)

        repeat(3) { assertTrue(store.bucketFor("k").tryConsume(1), "request ${it + 1} of 3 must be allowed") }
        assertFalse(store.bucketFor("k").tryConsume(1), "request 4 must be refused in the same window")
    }

    @Test
    fun `a key idle for one full window is evicted and comes back as a fresh full bucket`() {
        val store = buckets(capacity = 3)
        val original = store.bucketFor("idle")
        repeat(3) { original.tryConsume(1) }

        advance(window.plusNanos(1))
        store.cleanUp()

        assertEquals(0, store.estimatedSize(), "the idle key must be evicted after one full window")
        val fresh = store.bucketFor("idle")
        assertNotSame(original, fresh, "an evicted key must get a new bucket")
        assertEquals(3, fresh.availableTokens, "the new bucket must start full")
    }

    @Test
    fun `a key accessed within the window is kept so its bucket is never reset mid-window`() {
        val store = buckets(capacity = 3)
        val original = store.bucketFor("active")

        advance(window.minusSeconds(1))
        store.bucketFor("active")
        advance(window.minusSeconds(1))
        store.cleanUp()

        assertSame(original, store.bucketFor("active"), "access must extend the entry's life (expireAfterAccess)")
    }

    @Test
    fun `the store never holds more than maxKeys entries after maintenance`() {
        val store = buckets(maxKeys = 10)

        repeat(1_000) { store.bucketFor("key-$it@example.com") }
        store.cleanUp()

        assertTrue(store.estimatedSize() <= 10, "estimatedSize ${store.estimatedSize()} must be at most maxKeys 10")
    }

    @Test
    fun `construction rejects a non-positive capacity, window or maxKeys`() {
        assertThrows(IllegalArgumentException::class.java) { RateLimitBuckets(0, window, 10) }
        assertThrows(IllegalArgumentException::class.java) { RateLimitBuckets(3, Duration.ZERO, 10) }
        assertThrows(IllegalArgumentException::class.java) { RateLimitBuckets(3, Duration.ofSeconds(-1), 10) }
        assertThrows(IllegalArgumentException::class.java) { RateLimitBuckets(3, window, 0) }
    }
}
