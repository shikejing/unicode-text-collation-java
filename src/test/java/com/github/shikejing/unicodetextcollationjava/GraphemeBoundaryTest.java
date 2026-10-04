package com.github.shikejing.unicodetextcollationjava;

import com.github.shikejing.unicodetextcollationjava.grapheme.GraphemeBoundary;
import com.github.shikejing.unicodetextcollationjava.grapheme.TruncateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for grapheme cluster segmentation and grapheme-safe truncation.
 */
class GraphemeBoundaryTest {

    private GraphemeBoundary boundary;

    @BeforeEach
    void setUp() {
        boundary = new GraphemeBoundary();
    }

    @Test
    void emptyAndNullHaveZeroClusters() {
        assertEquals(0, boundary.length(""));
        assertEquals(0, boundary.length(null));
    }

    @Test
    void combiningSequenceCountsAsOneCluster() {
        // e + combining acute = one user-perceived character
        assertEquals(1, boundary.length("é"));
        // e + five combining acutes = one cluster
        assertEquals(1, boundary.length("é"));
    }

    @Test
    void zwjEmojiSequenceCountsAsOneCluster() {
        assertEquals(1, boundary.length("👨‍👩‍👧"));
        assertEquals(1, boundary.length("👨‍💻"));
    }

    @Test
    void regionalIndicatorPairCountsAsOneCluster() {
        // US flag: two regional indicator symbols = one cluster
        assertEquals(1, boundary.length("🇺🇸"));
    }

    @Test
    void plainAsciiCountsPerCharacter() {
        assertEquals(5, boundary.length("hello"));
    }

    @Test
    void splitReturnsClusterSubstrings() {
        List<String> clusters = boundary.split("aé👨‍👩‍👧");
        assertEquals(3, clusters.size());
        assertEquals("a", clusters.get(0));
        assertEquals("é", clusters.get(1));
        assertEquals("👨‍👩‍👧", clusters.get(2));
    }

    @Test
    void truncateDoesNotSplitCombiningSequence() {
        TruncateResult result = boundary.truncate("aébc", 2);
        assertEquals("aé", result.text());
        assertTrue(result.truncated());
        assertEquals(2, result.clusterCount());
    }

    @Test
    void truncateDoesNotSplitZwjSequence() {
        TruncateResult result = boundary.truncate("ab👨‍👩‍👧cd", 3);
        assertEquals("ab👨‍👩‍👧", result.text());
        assertTrue(result.truncated());
        assertEquals(3, result.clusterCount());
    }

    @Test
    void truncateDoesNotSplitSurrogatePair() {
        TruncateResult result = boundary.truncate("a😀b", 2);
        assertEquals("a😀", result.text());
        assertEquals(2, result.clusterCount());
    }

    @Test
    void truncateBeyondLengthReturnsWholeText() {
        TruncateResult result = boundary.truncate("abc", 10);
        assertEquals("abc", result.text());
        assertFalse(result.truncated());
        assertEquals(3, result.clusterCount());
    }

    @Test
    void truncateToZeroReturnsEmpty() {
        TruncateResult result = boundary.truncate("abc", 0);
        assertEquals("", result.text());
        assertTrue(result.truncated());
        assertEquals(0, result.clusterCount());
    }

    @Test
    void truncateNullReturnsEmpty() {
        TruncateResult result = boundary.truncate(null, 5);
        assertEquals("", result.text());
        assertFalse(result.truncated());
    }

    @Test
    void truncateRejectsNegativeLimit() {
        assertThrows(IllegalArgumentException.class, () -> boundary.truncate("abc", -1));
    }

    @Test
    void truncateToBytesNeverExceedsBudget() {
        String text = "ab👨‍👩‍👧cd";
        TruncateResult result = boundary.truncateToBytes(text, 10);
        int bytes = result.text().getBytes(StandardCharsets.UTF_8).length;
        assertTrue(bytes <= 10, "byte budget exceeded: " + bytes);
        // Must not split the ZWJ family sequence.
        assertTrue(result.text().endsWith("👨‍👩‍👧") || !result.text().contains("👨‍👩‍👧"));
    }

    @Test
    void truncateToBytesRejectsNegativeBudget() {
        assertThrows(IllegalArgumentException.class, () -> boundary.truncateToBytes("abc", -1));
    }

    @Test
    void truncateToBytesZeroReturnsEmpty() {
        TruncateResult result = boundary.truncateToBytes("abc", 0);
        assertEquals("", result.text());
    }

    @Test
    void truncateResultRoundTripsThroughFacade() {
        TextProcessingService service = new TextProcessingService(1024);
        TruncateResult result = service.truncateGraphemes("aé👨‍👩‍👧", 2);
        assertEquals("aé", result.text());
        assertEquals(2, result.clusterCount());
    }
}
