package com.nvh12.reaction.service.impl;

import java.util.function.Consumer;

final class Retry {

    private Retry() {}

    static <E extends RuntimeException> void run(
            int maxAttempts, long initialDelayMs,
            Class<E> retryOn, Runnable action, Consumer<E> onExhausted) {
        long delay = initialDelayMs;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException e) {
                if (!retryOn.isInstance(e)) throw e;
                last = e;
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    delay *= 2;
                }
            }
        }
        //noinspection unchecked
        onExhausted.accept((E) last);
    }
}
