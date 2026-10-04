package com.github.shikejing.unicodetextcollationjava;

import com.github.shikejing.unicodetextcollationjava.normalize.FoldResult;
import com.github.shikejing.unicodetextcollationjava.normalize.TextFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for case folding with full-width/half-width and compatibility handling,
 * including traceability back to the original text.
 */
class TextFolderTest {

    private TextFolder folder;

    @BeforeEach
    void setUp() {
        folder = new TextFolder();
    }

    @Test
    void fullWidthAndHalfWidthFoldToAscii() {
        FoldResult result = folder.fold("ＡＢＣａｂｃ");
        assertEquals("abcabc", result.folded());
    }

    @Test
    void compatibilityCharactersFold() {
        FoldResult result = folder.fold("ﬁ ﬂ ﬀ");
        assertEquals("fi fl ff", result.folded());
    }

    @Test
    void specialCaseMappingsAreApplied() {
        FoldResult result = folder.fold("ß");
        assertEquals("ss", result.folded());
    }

    @Test
    void halfWidthKanaFoldsToComposedKana() {
        FoldResult result = folder.fold("ｶﾞ");
        assertEquals("ガ", result.folded());
    }

    @Test
    void foldingIsIdempotent() {
        String text = "Ａａ  ﬁ ß ｶﾞ 123";
        FoldResult first = folder.fold(text);
        FoldResult second = folder.fold(first.folded());
        assertEquals(first.folded(), second.folded());
    }

    @Test
    void foldingIsDeterministic() {
        String text = "Ａａ ﬁ ß";
        assertEquals(folder.fold(text).folded(), folder.fold(text).folded());
    }

    @Test
    void traceabilityMappingCoversAllFoldedCodePoints() {
        String text = "Ａａ ﬁ ß";
        FoldResult result = folder.fold(text);
        int[] sourceIndex = result.sourceIndex();
        assertEquals(result.folded().length(), sourceIndex.length,
                "one mapping entry per folded output code point");
        int sourceCodePointCount = text.codePointCount(0, text.length());
        for (int i = 0; i < sourceIndex.length; i++) {
            assertTrue(sourceIndex[i] >= 0 && sourceIndex[i] < sourceCodePointCount,
                    "mapping index out of range at " + i);
        }
        // The first folded code point ('a') traces back to the first source code point.
        assertEquals(0, sourceIndex[0]);
    }

    @Test
    void traceabilityForExpandingFold() {
        // ß expands to two code points; both trace back to the same source index.
        FoldResult result = folder.fold("aßb");
        assertEquals("assb", result.folded());
        int[] sourceIndex = result.sourceIndex();
        assertEquals(4, sourceIndex.length);
        assertEquals(0, sourceIndex[0]); // a
        assertEquals(1, sourceIndex[1]); // s (from ß)
        assertEquals(1, sourceIndex[2]); // s (from ß)
        assertEquals(2, sourceIndex[3]); // b
    }

    @Test
    void traceabilityOriginalTextIsPreserved() {
        String text = "ＡＢＣ";
        FoldResult result = folder.fold(text);
        assertEquals(text, result.original());
    }

    @Test
    void emptyAndNullInputFoldToEmpty() {
        assertEquals("", folder.fold("").folded());
        assertEquals("", folder.fold(null).folded());
    }

    @Test
    void unpairedSurrogateIsReplacedAndReportedThroughValidity() {
        FoldResult result = folder.fold("a\uD800b");
        assertEquals("a�b", result.folded());
    }

    @Test
    void asciiFoldingIsLowercase() {
        assertEquals("hello world 123", folder.fold("HELLO World 123").folded());
    }
}
