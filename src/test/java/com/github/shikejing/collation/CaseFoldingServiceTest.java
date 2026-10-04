package com.github.shikejing.collation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaseFoldingServiceTest {

    private final CaseFoldingService service = new CaseFoldingService();

    @Test
    void fullwidthAndHalfwidthFoldTogether() {
        assertEquals(service.fold("ＡＢＣ１２３").folded(), service.fold("abc123").folded());
        assertEquals("abc", service.fold("ＡｂＣ").folded());
    }

    @Test
    void specialCaseFoldingsApplied() {
        assertEquals("ss", service.fold("ß").folded());
        assertEquals("ss", service.fold("ẞ").folded());
        assertEquals("σ", service.fold("ς").folded());
        assertEquals("σ", service.fold("Σ").folded());
    }

    @Test
    void compatibilityCharactersFold() {
        assertEquals("fi", service.fold("ﬁ").folded());
        assertEquals("1", service.fold("①").folded());
    }

    @Test
    void resultIsTraceableToOriginal() {
        var result = service.fold("Ｓtraße");
        assertEquals("Ｓtraße", result.original());
        assertEquals("strasse", result.folded());
    }

    @Test
    void foldingIsIdempotent() {
        String text = "Ｓtraße ＡＢＣ ﬁle ①";
        String once = service.fold(text).folded();
        assertEquals(once, service.fold(once).folded());
    }

    @Test
    void nullAndEmptyHandled() {
        assertNull(service.fold(null).folded());
        assertEquals("", service.fold("").folded());
    }
}
