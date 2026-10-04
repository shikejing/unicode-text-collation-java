package com.github.shikejing.unicodetextcollationjava;

import com.github.shikejing.unicodetextcollationjava.collate.CollationConfig;
import com.github.shikejing.unicodetextcollationjava.collate.CollationService;
import com.github.shikejing.unicodetextcollationjava.collate.CollationStrength;
import com.github.shikejing.unicodetextcollationjava.collate.SortKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for multilingual, layered collation and versioned sort keys.
 */
class CollationServiceTest {

    private CollationService collation;

    @BeforeEach
    void setUp() {
        collation = new CollationService();
    }

    @Test
    void primaryStrengthIgnoresAccentAndCase() {
        CollationConfig primary = config("", CollationStrength.PRIMARY);
        assertEquals(0, collation.compare("ä", "a", primary));
        assertEquals(0, collation.compare("ä", "Ä", primary));
        assertEquals(0, collation.compare("A", "a", primary));
    }

    @Test
    void secondaryStrengthIsAccentSensitiveButCaseInsensitive() {
        CollationConfig secondary = config("", CollationStrength.SECONDARY);
        assertTrue(collation.compare("ä", "a", secondary) > 0);
        assertEquals(0, collation.compare("ä", "Ä", secondary));
    }

    @Test
    void tertiaryStrengthIsAccentAndCaseSensitive() {
        CollationConfig tertiary = config("", CollationStrength.TERTIARY);
        assertTrue(collation.compare("ä", "a", tertiary) > 0);
        assertTrue(collation.compare("ä", "Ä", tertiary) < 0);
        // ICU root locale orders lowercase before uppercase
        assertTrue(collation.compare("a", "A", tertiary) < 0);
        assertTrue(collation.compare("A", "a", tertiary) > 0);
    }

    @Test
    void germanPrimaryTreatsEszettAsSs() {
        CollationConfig dePrimary = config("de-DE", CollationStrength.PRIMARY);
        assertEquals(0, collation.compare("ß", "ss", dePrimary));
    }

    @Test
    void germanSecondaryDistinguishesEszett() {
        CollationConfig deSecondary = config("de-DE", CollationStrength.SECONDARY);
        assertNotEquals(0, collation.compare("ß", "ss", deSecondary));
    }

    @Test
    void localeChangesOrdering() {
        // In Swedish, ä sorts after z; in German, ä sorts near a.
        CollationConfig sv = config("sv-SE", CollationStrength.PRIMARY);
        CollationConfig de = config("de-DE", CollationStrength.PRIMARY);
        assertTrue(collation.compare("ä", "z", sv) > 0);
        assertTrue(collation.compare("ä", "z", de) < 0);
    }

    @Test
    void identicalStrengthDistinguishesEverything() {
        CollationConfig identical = config("", CollationStrength.IDENTICAL);
        assertNotEquals(0, collation.compare("ä", "a", identical));
        assertNotEquals(0, collation.compare("ä", "Ä", identical));
    }

    @Test
    void sortKeyIsDeterministic() {
        CollationConfig config = CollationConfig.defaultConfig();
        SortKey first = collation.getSortKey("héllo wörld", config);
        SortKey second = collation.getSortKey("héllo wörld", config);
        assertEquals(first, second);
        assertArrayEquals(first.rawKeyBytes(), second.rawKeyBytes());
    }

    @Test
    void sortKeyOrderingMatchesComparison() {
        CollationConfig config = CollationConfig.defaultConfig();
        String[] words = {"apple", "äpfel", "banana", "Apple", "cherry"};
        Arrays.sort(words, (a, b) -> collation.compare(a, b, config));
        SortKey[] keys = new SortKey[words.length];
        for (int i = 0; i < words.length; i++) {
            keys[i] = collation.getSortKey(words[i], config);
        }
        for (int i = 0; i < words.length - 1; i++) {
            assertTrue(keys[i].compareTo(keys[i + 1]) <= 0,
                    "key order must match comparison order at " + i);
        }
    }

    @Test
    void sortKeyBase64RoundTrip() {
        CollationConfig config = CollationConfig.defaultConfig();
        SortKey key = collation.getSortKey("héllo", config);
        SortKey restored = SortKey.fromBase64(key.toBase64());
        assertEquals(key, restored);
        assertArrayEquals(key.bytes(), restored.bytes());
        assertEquals(config, restored.config());
    }

    @Test
    void sortKeyFromBytesRejectsCorruptedFrame() {
        CollationConfig config = CollationConfig.defaultConfig();
        SortKey key = collation.getSortKey("hello", config);
        byte[] bytes = key.bytes();
        // Corrupt a locale/config byte, which is covered by the configuration identity hash.
        bytes[9] ^= 0x01;
        assertThrows(IllegalArgumentException.class, () -> SortKey.fromBytes(bytes));
    }

    @Test
    void sortKeyFromBytesRejectsBadMagic() {
        CollationConfig config = CollationConfig.defaultConfig();
        SortKey key = collation.getSortKey("hello", config);
        byte[] bytes = key.bytes();
        bytes[0] = 'X';
        assertThrows(IllegalArgumentException.class, () -> SortKey.fromBytes(bytes));
    }

    @Test
    void sortKeyFromBytesRejectsUnsupportedVersion() {
        CollationConfig config = CollationConfig.defaultConfig();
        SortKey key = collation.getSortKey("hello", config);
        byte[] bytes = key.bytes();
        bytes[4] = 99; // unsupported version
        assertThrows(IllegalArgumentException.class, () -> SortKey.fromBytes(bytes));
    }

    @Test
    void sortKeysFromDifferentConfigsAreNotComparable() {
        SortKey tertiary = collation.getSortKey("hello", config("", CollationStrength.TERTIARY));
        SortKey primary = collation.getSortKey("hello", config("", CollationStrength.PRIMARY));
        assertThrows(IllegalArgumentException.class, () -> tertiary.compareTo(primary));
    }

    @Test
    void sortKeyLengthIsBounded() {
        CollationConfig config = CollationConfig.defaultConfig();
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 10_000; i++) {
            longText.append("abc");
        }
        SortKey key = collation.getSortKey(longText.toString(), config);
        assertTrue(key.rawKeyBytes().length <= CollationService.MAX_KEY_BYTES,
                "key must be bounded: " + key.rawKeyBytes().length);
    }

    @Test
    void sameInputProducesSameOrderAcrossRuns() {
        CollationConfig config = CollationConfig.defaultConfig();
        List<String> input = Arrays.asList("z", "a", "ä", "A", "ß", "ss", "é", "e");
        List<String> first = new ArrayList<>(input);
        List<String> second = new ArrayList<>(input);
        first.sort((a, b) -> collation.compare(a, b, config));
        second.sort((a, b) -> collation.compare(a, b, config));
        assertEquals(first, second);
    }

    @Test
    void emptyAndNullInputDoNotCrash() {
        CollationConfig config = CollationConfig.defaultConfig();
        assertEquals(0, collation.compare("", "", config));
        assertEquals(0, collation.compare(null, null, config));
        SortKey key = collation.getSortKey("", config);
        assertEquals(key, collation.getSortKey(null, config));
    }

    private static CollationConfig config(String locale, CollationStrength strength) {
        return new CollationConfig(locale, strength, CollationConfig.VERSION);
    }
}
