package com.github.shikejing.collation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphemeServiceTest {

    private final GraphemeService service = new GraphemeService();

    @Test
    void combiningSequenceIsOneCluster() {
        String combining = "é"; // e + combining acute
        assertEquals(1, service.graphemeCount(combining));
        assertEquals(combining, service.truncate(combining, 1));
        assertEquals("", service.truncate(combining, 0));
    }

    @Test
    void emojiZwjSequenceIsOneCluster() {
        String family = "👨‍👩‍👧‍👦"; // family emoji, 4 emoji joined by ZWJ
        assertEquals(1, service.graphemeCount(family));
        assertEquals(family, service.truncate(family, 1));
    }

    @Test
    void regionalIndicatorFlagIsOneCluster() {
        String flag = "🇨🇳";
        assertEquals(1, service.graphemeCount(flag));
        assertEquals(flag, service.truncate(flag, 1));
    }

    @Test
    void truncationNeverSplitsClusters() {
        String text = "aéb👨‍👩‍👧‍👦c";
        assertEquals(5, service.graphemeCount(text));
        assertEquals("aé", service.truncate(text, 2));
        assertEquals("aéb👨‍👩‍👧‍👦", service.truncate(text, 4));
        assertEquals(text, service.truncate(text, 5));
        assertEquals(text, service.truncate(text, 100));
    }

    @Test
    void boundaryBehaviorIsDeterministic() {
        assertEquals("", service.truncate("hello", 0));
        assertEquals("", service.truncate("hello", -3));
        assertEquals("", service.truncate("", 5));
        assertNull(service.truncate(null, 5));
        assertEquals(0, service.graphemeCount(null));
        assertEquals(0, service.graphemeCount(""));
    }
}
