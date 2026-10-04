package com.github.shikejing.collation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class SortKeyServiceTest {

    private final SortKeyService service = new SortKeyService(
            new InputSanitizer(), new CaseFoldingService(), new CollationService());

    @Test
    void sortKeysCompareLexicographicallyAcrossProcesses() {
        // Keys are plain strings; lexicographic comparison reproduces order.
        String k1 = service.sortKey("alpha", Locale.ROOT, CollationStrength.TERTIARY, 512);
        String k2 = service.sortKey("beta", Locale.ROOT, CollationStrength.TERTIARY, 512);
        assertTrue(k1.compareTo(k2) < 0);
        // Same input always yields the identical key (cross-process stability).
        assertEquals(k1, service.sortKey("alpha", Locale.ROOT, CollationStrength.TERTIARY, 512));
    }

    @Test
    void equivalentInputsProduceEqualSearchKeys() {
        assertEquals(service.searchKey("Ｓtraße", 512), service.searchKey("strasse", 512));
        assertEquals(service.searchKey("é", 512), service.searchKey("é", 512));
    }

    @Test
    void keysCarryVersionTagAndStaleKeysAreDetected() {
        String key = service.searchKey("hello", 512);
        assertTrue(key.startsWith("v" + SortKeyService.RULES_VERSION + "|"));
        assertTrue(service.isCurrent(key));
        assertFalse(service.isCurrent("v999|hello"));
        assertFalse(service.isCurrent(null));
    }

    @Test
    void keyLengthIsCapped() {
        String longText = "abcdefghijklmnopqrstuvwxyz".repeat(20);
        String key = service.searchKey(longText, 64);
        assertTrue(key.length() <= 64);
        String sortKey = service.sortKey(longText, Locale.ROOT, CollationStrength.TERTIARY, 100);
        assertTrue(sortKey.length() <= 100);
        // absolute cap applies even when a larger limit is requested
        String huge = service.searchKey(longText.repeat(50), Integer.MAX_VALUE);
        assertTrue(huge.length() <= 4096);
    }

    @Test
    void truncationNeverSplitsSurrogatePair() {
        // emoji (surrogate pair) right at the cut boundary
        String text = "ab😀cd";
        String key = service.searchKey(text, 5); // "v1|" + "ab" + half emoji would hit the cap
        assertFalse(key.isEmpty());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isHighSurrogate(c)) {
                assertTrue(i + 1 < key.length() && Character.isLowSurrogate(key.charAt(i + 1)),
                        "unpaired high surrogate at " + i);
            }
        }
    }

    @Test
    void differentConfigsProduceDifferentKeys() {
        String a = service.sortKey("test", Locale.ROOT, CollationStrength.PRIMARY, 512);
        String b = service.sortKey("test", Locale.ROOT, CollationStrength.TERTIARY, 512);
        String c = service.sortKey("test", Locale.GERMAN, CollationStrength.PRIMARY, 512);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void sortKeyOrderMatchesCollationOrder() {
        List<String> words = List.of("alpha", "bravo", "charlie", "delta");
        List<String> byKey = words.stream()
                .sorted((x, y) -> service.sortKey(x, Locale.ROOT, CollationStrength.TERTIARY, 512)
                        .compareTo(service.sortKey(y, Locale.ROOT, CollationStrength.TERTIARY, 512)))
                .toList();
        assertEquals(words, byKey);
    }
}
