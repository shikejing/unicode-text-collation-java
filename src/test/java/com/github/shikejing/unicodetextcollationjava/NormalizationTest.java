package com.github.shikejing.unicodetextcollationjava;

import com.github.shikejing.unicodetextcollationjava.normalize.InputValidity;
import com.github.shikejing.unicodetextcollationjava.normalize.NormalizationForm;
import com.github.shikejing.unicodetextcollationjava.normalize.TextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Unicode normalization semantics.
 */
class NormalizationTest {

    private TextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new TextNormalizer();
    }

    @Test
    void equivalentSpellingsNormalizeToSameNfc() {
        String composed = "é"; // U+00E9
        String decomposed = "é"; // U+0065 U+0301
        assertEquals(composed, normalizer.normalize(decomposed, NormalizationForm.NFC));
        assertEquals(composed, normalizer.normalize(composed, NormalizationForm.NFC));
    }

    @Test
    void nfdAndNfcAreInverse() {
        String nfc = normalizer.normalize("\u00E9", NormalizationForm.NFC);
        String nfd = normalizer.normalize("\u00E9", NormalizationForm.NFD);
        assertEquals("\u00E9", nfc);
        assertEquals("e\u0301", nfd);
        assertNotEquals(nfc, nfd);
        // NFD of the NFC form gives back the decomposed form
        assertEquals(nfd, normalizer.normalize(nfc, NormalizationForm.NFD));
        // NFC of the NFD form gives back the composed form
        assertEquals(nfc, normalizer.normalize(nfd, NormalizationForm.NFC));
    }

    @Test
    void normalizationIsIdempotent() {
        String text = "¨å∫ç∂ é Ａ  ﬁ";
        for (NormalizationForm form : NormalizationForm.values()) {
            String once = normalizer.normalize(text, form);
            String twice = normalizer.normalize(once, form);
            assertEquals(once, twice, "normalization must be idempotent for " + form);
        }
    }

    @Test
    void nfkcFoldsCompatibilityCharacters() {
        assertEquals("fi", normalizer.normalize("ﬁ", NormalizationForm.NFKC));
        assertEquals("A", normalizer.normalize("Ａ", NormalizationForm.NFKC));
        assertEquals("\u30AC", normalizer.normalize("ｶﾞ", NormalizationForm.NFKC));
    }

    @Test
    void nfkcAndNfkdAreIdempotentAndEquivalentAfterComposition() {
        String text = "㌀ ｶﾞ ﬁ";
        String nfkc = normalizer.normalize(text, NormalizationForm.NFKC);
        String nfkd = normalizer.normalize(text, NormalizationForm.NFKD);
        assertEquals(nfkc, normalizer.normalize(nfkc, NormalizationForm.NFKC));
        assertEquals(nfkd, normalizer.normalize(nfkd, NormalizationForm.NFKD));
        // NFKC of NFKD equals NFKC
        assertEquals(nfkc, normalizer.normalize(nfkd, NormalizationForm.NFKC));
    }

    @Test
    void emptyAndNullInputReturnEmpty() {
        assertEquals("", normalizer.normalize("", NormalizationForm.NFC));
        assertEquals("", normalizer.normalize(null, NormalizationForm.NFC));
    }

    @Test
    void unpairedSurrogateIsReplacedWithReplacementCharacter() {
        String result = normalizer.normalize("a\uD800b", NormalizationForm.NFC);
        assertEquals("a�b", result);
    }

    @Test
    void validSurrogatePairsArePreserved() {
        String emoji = "😀";
        assertEquals(emoji, normalizer.normalize(emoji, NormalizationForm.NFC));
    }

    @Test
    void validityReportsEmptyInput() {
        assertEquals(InputValidity.EMPTY, normalizer.validity(""));
        assertEquals(InputValidity.EMPTY, normalizer.validity((String) null));
    }

    @Test
    void validityReportsUnpairedSurrogate() {
        assertEquals(InputValidity.UNPAIRED_SURROGATE, normalizer.validity("a\uD800b"));
        assertEquals(InputValidity.UNPAIRED_SURROGATE, normalizer.validity("\uD800"));
    }

    @Test
    void validityReportsValidInput() {
        assertEquals(InputValidity.VALID, normalizer.validity("hello"));
        assertEquals(InputValidity.VALID, normalizer.validity("😀"));
    }

    @Test
    void codePointArrayRejectsOutOfRangeAndSurrogates() {
        String result = normalizer.normalizeCodePoints(
                new int[] {'a', 0xD800, 0x110000, 'b'}, NormalizationForm.NFC);
        assertEquals("a��b", result);
        assertEquals(InputValidity.OUT_OF_RANGE,
                normalizer.validity(new int[] {'a', 0x110000}));
        assertEquals(InputValidity.OUT_OF_RANGE,
                normalizer.validity(new int[] {'a', 0xD800}));
        assertEquals(InputValidity.VALID, normalizer.validity(new int[] {'a', 'b'}));
        assertEquals(InputValidity.EMPTY, normalizer.validity(new int[0]));
    }

    @Test
    void normalizeCodePointsDoesNotCrashOnEmpty() {
        assertEquals("", normalizer.normalizeCodePoints(new int[0], NormalizationForm.NFC));
        assertEquals("", normalizer.normalizeCodePoints(null, NormalizationForm.NFC));
    }

    @Test
    void defaultFormIsNfc() {
        assertEquals(normalizer.normalize("\u00E9", NormalizationForm.NFC),
                normalizer.normalize("\u00E9", null));
    }
}
