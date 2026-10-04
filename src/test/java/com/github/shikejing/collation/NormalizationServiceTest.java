package com.github.shikejing.collation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NormalizationServiceTest {

    private final NormalizationService service = new NormalizationService();

    @Test
    void canonicallyEquivalentFormsNormalizeToSameResult() {
        String composed = "é";          // U+00E9
        String decomposed = "é";       // U+0065 U+0301
        assertNotEquals(composed, decomposed);
        assertEquals(service.normalize(composed, NormalizationForm.NFC),
                service.normalize(decomposed, NormalizationForm.NFC));
        assertEquals(service.normalize(composed, NormalizationForm.NFD),
                service.normalize(decomposed, NormalizationForm.NFD));
    }

    @Test
    void normalizationIsIdempotent() {
        String text = "Café ｶﾀｶﾅ ①";
        for (NormalizationForm form : NormalizationForm.values()) {
            String once = service.normalize(text, form);
            String twice = service.normalize(once, form);
            assertEquals(once, twice, "form " + form);
            assertTrue(service.isNormalized(once, form));
        }
    }

    @Test
    void compatibilityEquivalentsUnifyUnderNfkc() {
        assertEquals(service.normalize("①", NormalizationForm.NFKC),
                service.normalize("1", NormalizationForm.NFKC));
        assertEquals(service.normalize("ＡＢＣ", NormalizationForm.NFKC),
                service.normalize("ABC", NormalizationForm.NFKC));
    }

    @Test
    void nullAndEmptyPassThrough() {
        assertNull(service.normalize(null, NormalizationForm.NFC));
        assertEquals("", service.normalize("", NormalizationForm.NFC));
        assertTrue(service.isNormalized(null, NormalizationForm.NFC));
    }
}
