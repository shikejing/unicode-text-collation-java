package com.github.shikejing.collation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TextProcessingServiceConcurrencyTest {

    private TextProcessingService newService() {
        InputSanitizer sanitizer = new InputSanitizer();
        return new TextProcessingService(sanitizer, new NormalizationService(),
                new CaseFoldingService(),
                new SortKeyService(sanitizer, new CaseFoldingService(), new CollationService()));
    }

    @Test
    void concurrentAccessToSameAndDifferentTextsIsConsistent() throws Exception {
        TextProcessingService service = newService();
        List<String> texts = List.of("Ｓtraße", "Café", "Café", "東京", "😀 test", "résumé");
        var results = new ConcurrentHashMap<String, String>();
        var executor = Executors.newFixedThreadPool(16);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            String text = texts.get(i % texts.size());
            tasks.add(() -> {
                String key = service.searchKey(text, 512);
                String prev = results.putIfAbsent(text, key);
                if (prev != null) {
                    assertEquals(prev, key, "concurrent results diverged for " + text);
                }
                return null;
            });
        }
        executor.invokeAll(tasks);
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        // every distinct text produced exactly one key, equal to a fresh computation
        assertEquals(texts.size(), results.size());
        TextProcessingService fresh = newService();
        for (String text : texts) {
            assertEquals(fresh.searchKey(text, 512), results.get(text));
        }
    }

    @Test
    void cacheHitAndMissPathsReturnIdenticalResults() {
        TextProcessingService service = newService();
        String text = "Ｓtraße ＡＢＣ";
        String miss = service.searchKey(text, 512);
        String hit = service.searchKey(text, 512);
        assertEquals(miss, hit);
        assertEquals(miss, service.normalizeForIndex(text) == null ? null : service.searchKey(text, 512));
    }

    @Test
    void cacheIsBounded() {
        TextProcessingService service = newService();
        for (int i = 0; i < TextProcessingService.MAX_CACHE_ENTRIES + 5000; i++) {
            service.searchKey("unique-text-" + i, 512);
        }
        assertTrue(service.cacheSize() <= TextProcessingService.MAX_CACHE_ENTRIES,
                "cache grew beyond bound: " + service.cacheSize());
    }

    @Test
    void illegalInputDoesNotCrashAndIsDeterministic() {
        TextProcessingService service = newService();
        String nasty = "\uD800 lone \uDC00 surrogates �";
        String k1 = service.searchKey(nasty, 512);
        String k2 = service.searchKey(nasty, 512);
        assertEquals(k1, k2);
        assertNotNull(service.normalizeForIndex(null));
        assertEquals("v" + SortKeyService.RULES_VERSION + "|", service.searchKey(null, 512));
        assertNotNull(service.sortKey(nasty, Locale.ROOT, CollationStrength.TERTIARY, 512));
    }
}
