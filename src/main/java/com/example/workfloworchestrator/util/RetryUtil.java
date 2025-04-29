package com.example.workfloworchestrator.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Utility for handling retries with exponential backoff
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryUtil {

    @Value("${workflow.task.retry.initial-interval:1000}")
    private long initialIntervalMs;

    @Value("${workflow.task.retry.multiplier:2.0}")
    private double multiplier;

    @Value("${workflow.task.retry.max-interval:3600000}") // 1 hour max
    private long maxIntervalMs;

    /**
     * Calculate the next retry time using exponential backoff
     *
     * @param retryCount the current retry count
     * @return the next retry time
     */
    public LocalDateTime calculateNextRetryTime(int retryCount) {
        long delayMs = calculateExponentialBackoff(retryCount);
        return LocalDateTime.now().plusNanos(delayMs * 1_000_000);
    }

    /**
     * Calculate the delay for exponential backoff
     *
     * @param retryCount the current retry count
     * @return the delay in milliseconds
     */
    public long calculateExponentialBackoff(int retryCount) {
        // Calculate exponential backoff with jitter
        double exponentialPart = Math.pow(multiplier, retryCount);
        long delay = (long) (initialIntervalMs * exponentialPart);

        // Add some randomness (jitter) to avoid stampeding herds
        double jitter = 0.25; // 25% jitter
        double randomFactor = 1.0 + Math.random() * jitter;
        delay = (long) (delay * randomFactor);

        // Cap at max interval
        return Math.min(delay, maxIntervalMs);
    }
}
