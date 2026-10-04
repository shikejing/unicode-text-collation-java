package com.github.shikejing.unicodetextcollationjava;

import com.github.shikejing.unicodetextcollationjava.cache.BoundedCache;
import com.github.shikejing.unicodetextcollationjava.collate.CollationConfig;
import com.github.shikejing.unicodetextcollationjava.collate.SortKey;
import com.github.shikejing.unicodetextcollationjava.normalize.FoldResult;
import com.github.shikejing.unicodetextcollationjava.normalize.NormalizationForm;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for concurrent processing and bounded cache behavior.
 */
class ConcurrencyTest {

    @Test
    void concurrentProcessingDoesNotInterfere() throws Exception {
        TextProcessingService service = new TextProcessingService(256);
        int threads = 16;
        int iterations = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                tasks.add(() -> {
                    for (int i = 0; i < iterations; i++) {
                        String text = "text-" + (threadId % 4) + "-" + (i % 50);
                        String normalized = service.normalize(text, NormalizationForm.NFKC);
                        FoldResult folded = service.fold(text);
                        SortKey key = service.sortKey(text, CollationConfig.defaultConfig());
                        // Results must be deterministic and self-consistent.
                        assertEquals(normalized, service.normalize(text, NormalizationForm.NFKC));
                        assertEquals(folded.folded(), service.fold(text).folded());
                        assertEquals(key, service.sortKey(text, CollationConfig.defaultConfig()));
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get(); // propagates any assertion failure
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cacheStaysBoundedUnderDistinctInputs() {
        BoundedCache<String, String> cache = new BoundedCache<>(128);
        for (int i = 0; i < 10_000; i++) {
            cache.put("key-" + i, "value-" + i);
        }
        assertTrue(cache.size() <= 128, "cache must be bounded: " + cache.size());
    }

    @Test
    void cacheHitAndMissPathsReturnSameResult() {
        BoundedCache<String, String> cache = new BoundedCache<>(128);
        AtomicInteger computations = new AtomicInteger();
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 50; i++) {
                String key = "k-" + i;
                String value = cache.getOrCompute(key, k -> {
                    computations.incrementAndGet();
                    return "v-" + k;
                });
                assertEquals("v-" + key, value);
            }
        }
        // First round computes 50; subsequent rounds hit the cache.
        assertEquals(50, computations.get());
    }

    @Test
    void concurrentCacheAccessIsSafe() throws Exception {
        BoundedCache<String, String> cache = new BoundedCache<>(64);
        int threads = 32;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                tasks.add(() -> {
                    for (int i = 0; i < 500; i++) {
                        String key = "k-" + (i % 200);
                        String value = cache.getOrCompute(key, k2 -> "v-" + k2);
                        assertEquals("v-" + key, value);
                    }
                    return null;
                });
            }
            for (Future<Void> future : executor.invokeAll(tasks)) {
                future.get();
            }
            assertTrue(cache.size() <= 64);
        } finally {
            executor.shutdownNow();
        }
    }
}
