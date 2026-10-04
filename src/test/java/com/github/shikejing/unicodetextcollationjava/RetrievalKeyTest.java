package com.github.shikejing.unicodetextcollationjava;

import com.github.shikejing.unicodetextcollationjava.normalize.RetrievalKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for versioned retrieval keys used in de-duplication and lookup.
 */
class RetrievalKeyTest {

    private final TextProcessingService service = new TextProcessingService(1024);

    @Test
    void equivalentSpellingsShareARetrievalKey() {
        RetrievalKey k1 = service.retrievalKey("ＡＢＣ");
        RetrievalKey k2 = service.retrievalKey("abc");
        RetrievalKey k3 = service.retrievalKey("ＡｂＣ");
        assertEquals(k1, k2);
        assertEquals(k1, k3);
    }

    @Test
    void differentTextsHaveDifferentKeys() {
        assertNotEquals(service.retrievalKey("abc"), service.retrievalKey("abd"));
        assertNotEquals(service.retrievalKey("abc"), service.retrievalKey("ab"));
    }

    @Test
    void compatibilityVariantsShareAKey() {
        assertEquals(service.retrievalKey("ﬁ"), service.retrievalKey("fi"));
        assertEquals(service.retrievalKey("ｶﾞ"), service.retrievalKey("ガ"));
    }

    @Test
    void retrievalKeyIsDeterministic() {
        RetrievalKey first = service.retrievalKey("héllo ＡＢＣ");
        RetrievalKey second = service.retrievalKey("héllo ＡＢＣ");
        assertEquals(first, second);
        assertArrayEquals(first.bytes(), second.bytes());
    }

    @Test
    void retrievalKeyBase64RoundTrip() {
        RetrievalKey key = service.retrievalKey("hello");
        RetrievalKey restored = RetrievalKey.fromBase64(key.toBase64());
        assertEquals(key, restored);
        assertArrayEquals(key.bytes(), restored.bytes());
    }

    @Test
    void retrievalKeyHasBoundedLength() {
        RetrievalKey key = service.retrievalKey("some text");
        // 4 magic + 1 version + 32 digest = 37 bytes
        assertEquals(37, key.bytes().length);
    }

    @Test
    void retrievalKeyRejectsCorruptedFrame() {
        RetrievalKey key = service.retrievalKey("hello");
        byte[] bytes = key.bytes();
        bytes[0] = 'X';
        assertThrows(IllegalArgumentException.class, () -> RetrievalKey.fromBytes(bytes));
    }

    @Test
    void retrievalKeyRejectsUnsupportedVersion() {
        RetrievalKey key = service.retrievalKey("hello");
        byte[] bytes = key.bytes();
        bytes[4] = 99;
        assertThrows(IllegalArgumentException.class, () -> RetrievalKey.fromBytes(bytes));
    }

    @Test
    void emptyTextHasAStableKey() {
        RetrievalKey k1 = service.retrievalKey("");
        RetrievalKey k2 = service.retrievalKey("");
        assertEquals(k1, k2);
    }
}
