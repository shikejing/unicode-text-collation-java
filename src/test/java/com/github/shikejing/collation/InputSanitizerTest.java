package com.github.shikejing.collation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputSanitizerTest {

    private final InputSanitizer sanitizer = new InputSanitizer();

    @Test
    void cleanInputPassesThroughUnchanged() {
        var result = sanitizer.sanitize("Hello 世界 😀");
        assertEquals(InputSanitizer.Status.CLEAN, result.status());
        assertEquals("Hello 世界 😀", result.text());
    }

    @Test
    void unpairedSurrogatesAreReplacedDeterministically() {
        String lone = "a\uD800b\uDC00c";
        var result = sanitizer.sanitize(lone);
        assertEquals(InputSanitizer.Status.REPAIRED, result.status());
        assertEquals("a�b�c", result.text());
        // deterministic: same input, same output
        assertEquals(result.text(), sanitizer.sanitize(lone).text());
    }

    @Test
    void validSurrogatePairsArePreserved() {
        var result = sanitizer.sanitize("😀");
        assertEquals(InputSanitizer.Status.CLEAN, result.status());
        assertEquals("😀", result.text());
    }

    @Test
    void nullAndEmptyYieldEmptyStatus() {
        assertEquals(InputSanitizer.Status.EMPTY, sanitizer.sanitize(null).status());
        assertEquals("", sanitizer.sanitize(null).text());
        assertEquals(InputSanitizer.Status.EMPTY, sanitizer.sanitize("").status());
    }

    @Test
    void repairedOutputIsDistinguishableFromClean() {
        // A repaired result differs from any clean result for the same length
        var repaired = sanitizer.sanitize("\uD800");
        var clean = sanitizer.sanitize("�");
        assertEquals(InputSanitizer.Status.REPAIRED, repaired.status());
        assertEquals(InputSanitizer.Status.CLEAN, clean.status());
        // same replacement text but different status → distinguishable
        assertEquals(repaired.text(), clean.text());
        assertNotEquals(repaired.status(), clean.status());
    }
}
