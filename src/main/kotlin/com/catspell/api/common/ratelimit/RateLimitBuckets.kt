package com.catspell.api.common.ratelimit

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.Executor

/**
 * Shared per-key Bucket4j store for every per-email, per-reporter and per-IP throttle (WR-10, D-03, D-18). Keys are
 * attacker-chosen (random emails, rotating IPv6 addresses), so the store is a Caffeine cache bounded by [maxKeys]
 * instead of an unbounded map.
 *
 * Entries expire after one refill [window] without access. That is lossless: a key idle for a full window would have
 * refilled to [capacity] anyway, so evicting it and later building a fresh, full bucket changes nothing. Write-based
 * expiry is deliberately not used: it would evict and reset the bucket of a key that is being hammered right now,
 * which fails open. The residual risk is size-based eviction under heavy key churn (T-18-05); Caffeine's W-TinyLFU
 * admission favors frequently hit keys, which keeps an active attacker's bucket resident.
 *
 * Limits are per application instance. Distributed buckets are deferred.
 *
 * [ticker] and [executor] exist so tests can drive cache time and run maintenance on the calling thread.
 */
class RateLimitBuckets(
    private val capacity: Long,
    private val window: Duration,
    maxKeys: Long,
    ticker: Ticker = Ticker.systemTicker(),
    executor: Executor? = null
) {

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
        require(!window.isZero && !window.isNegative) { "window must be positive, was $window" }
        require(maxKeys > 0) { "maxKeys must be positive, was $maxKeys" }
    }

    private val cache: Cache<String, Bucket> = Caffeine.newBuilder()
        .maximumSize(maxKeys)
        .expireAfterAccess(window)
        .ticker(ticker)
        .apply { if (executor != null) executor(executor) }
        .build()

    /** The bucket for [key], created atomically on first use (at most once per key while it stays cached). */
    fun bucketFor(key: String): Bucket = cache.get(key) {
        val bandwidth = Bandwidth.builder()
            .capacity(capacity)
            .refillIntervally(capacity, window)
            .build()
        Bucket.builder().addLimit(bandwidth).build()
    }

    internal fun estimatedSize(): Long = cache.estimatedSize()

    internal fun cleanUp() = cache.cleanUp()
}
