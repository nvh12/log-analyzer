package com.nvh12.reaction.infrastructure.service.impl;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryTest {

    static class TransientException extends RuntimeException {
        TransientException(String message) {
            super(message);
        }
    }

    static class OtherException extends RuntimeException {
        OtherException(String message) {
            super(message);
        }
    }

    @Test
    void run_succeedsFirstTry_doesNotRetryOrCallOnExhausted() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger exhaustedCalls = new AtomicInteger();

        Retry.run(3, 1, TransientException.class,
                attempts::incrementAndGet,
                e -> exhaustedCalls.incrementAndGet());

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(exhaustedCalls.get()).isZero();
    }

    @Test
    void run_succeedsAfterRetries_doesNotCallOnExhausted() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger exhaustedCalls = new AtomicInteger();

        Retry.run(3, 1, TransientException.class, () -> {
            if (attempts.incrementAndGet() < 3) throw new TransientException("retry me");
        }, e -> exhaustedCalls.incrementAndGet());

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(exhaustedCalls.get()).isZero();
    }

    @Test
    void run_exhaustsAllAttempts_callsOnExhaustedWithLastException() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger exhaustedCalls = new AtomicInteger();

        Retry.run(3, 1, TransientException.class, () -> {
            attempts.incrementAndGet();
            throw new TransientException("always fails");
        }, e -> exhaustedCalls.incrementAndGet());

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(exhaustedCalls.get()).isEqualTo(1);
    }

    @Test
    void run_exceptionNotMatchingRetryOn_isRethrownImmediately() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> Retry.run(3, 1, TransientException.class, () -> {
            attempts.incrementAndGet();
            throw new OtherException("not retryable");
        }, e -> { throw new AssertionError("should not be called"); }))
                .isInstanceOf(OtherException.class)
                .hasMessage("not retryable");

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void run_interruptedDuringBackoff_stopsRetryingAndCallsOnExhausted() throws InterruptedException {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger exhaustedCalls = new AtomicInteger();

        Thread thread = new Thread(() -> Retry.run(5, 10_000, TransientException.class, () -> {
            attempts.incrementAndGet();
            throw new TransientException("always fails");
        }, e -> exhaustedCalls.incrementAndGet()));
        thread.start();
        // give the thread time to enter the sleep, then interrupt it
        Thread.sleep(100);
        thread.interrupt();
        thread.join(5_000);

        assertThat(thread.isAlive()).isFalse();
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(exhaustedCalls.get()).isEqualTo(1);
    }
}
