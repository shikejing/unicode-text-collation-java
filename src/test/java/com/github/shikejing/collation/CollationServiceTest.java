package com.github.shikejing.collation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class CollationServiceTest {

    private final CollationService service = new CollationService();

    @Test
    void primaryStrengthIgnoresCaseAndAccents() {
        var cmp = service.comparator(Locale.ROOT, CollationStrength.PRIMARY);
        assertEquals(0, cmp.compare("resume", "résumé"));
        assertEquals(0, cmp.compare("Apple", "apple"));
    }

    @Test
    void secondaryStrengthDistinguishesAccents() {
        var cmp = service.comparator(Locale.ROOT, CollationStrength.SECONDARY);
        assertTrue(cmp.compare("resume", "résumé") != 0);
        assertEquals(0, cmp.compare("Apple", "apple"));
    }

    @Test
    void tertiaryStrengthDistinguishesCase() {
        var cmp = service.comparator(Locale.ROOT, CollationStrength.TERTIARY);
        assertTrue(cmp.compare("Apple", "apple") != 0);
    }

    @Test
    void canonicallyEquivalentStringsCompareEqual() {
        var cmp = service.comparator(Locale.ROOT, CollationStrength.IDENTICAL);
        assertEquals(0, cmp.compare("é", "é"));
    }

    @Test
    void sortingIsDeterministicAcrossRuns() {
        List<String> input = List.of("banana", "Ápple", "apple", "äpple", "Banana", "中文", "日本語", "한국어");
        List<String> first = service.sort(input, Locale.ROOT, CollationStrength.TERTIARY);
        for (int i = 0; i < 5; i++) {
            assertEquals(first, service.sort(input, Locale.ROOT, CollationStrength.TERTIARY));
        }
        // shuffled input must produce the same order
        List<String> shuffled = List.of("한국어", "apple", "中文", "Banana", "äpple", "日本語", "Ápple", "banana");
        assertEquals(first, service.sort(shuffled, Locale.ROOT, CollationStrength.TERTIARY));
    }

    @Test
    void localeConfigurationChangesOrder() {
        // In Swedish, 'ä' sorts after 'z'; in ROOT/German-like order it sorts near 'a'.
        List<String> input = List.of("zoo", "ära", "apa");
        List<String> sv = service.sort(input, Locale.forLanguageTag("sv"), CollationStrength.PRIMARY);
        List<String> root = service.sort(input, Locale.ROOT, CollationStrength.PRIMARY);
        assertEquals(List.of("apa", "ära", "zoo"), root);
        assertEquals(List.of("apa", "zoo", "ära"), sv);
    }

    @Test
    void multilingualTextSortsConsistently() {
        List<String> input = List.of("東京", "北京", "서울", "München", "munchen", "Zürich");
        List<String> sorted = service.sort(input, Locale.ROOT, CollationStrength.SECONDARY);
        assertEquals(input.size(), sorted.size());
        // re-sorting the sorted list is a fixed point
        assertEquals(sorted, service.sort(sorted, Locale.ROOT, CollationStrength.SECONDARY));
    }

    @Test
    void sortKeyBytesRespectComparisonOrder() {
        List<String> input = List.of("delta", "alpha", "charlie", "bravo");
        List<String> sorted = service.sort(input, Locale.ROOT, CollationStrength.TERTIARY);
        for (int i = 0; i + 1 < sorted.size(); i++) {
            byte[] a = service.sortKey(sorted.get(i), Locale.ROOT, CollationStrength.TERTIARY);
            byte[] b = service.sortKey(sorted.get(i + 1), Locale.ROOT, CollationStrength.TERTIARY);
            assertTrue(compareBytes(a, b) < 0);
        }
    }

    private int compareBytes(byte[] a, byte[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int cmp = Byte.compareUnsigned(a[i], b[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length, b.length);
    }
}
