package com.github.shikejing.unicodetextcollationjava.grapheme;

import com.ibm.icu.text.BreakIterator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Grapheme cluster segmentation and grapheme-safe truncation.
 * <p>
 * A grapheme cluster is a user-perceived character: a base character plus any combining
 * marks, emoji modifier sequences or ZWJ sequences that belong with it. All operations
 * work on <em>extended grapheme clusters</em> as defined by Unicode UAX #29.
 * <p>
 * Boundary behavior:
 * <ul>
 *   <li>truncation never splits a grapheme cluster, a combining sequence or a
 *       surrogate pair;</li>
 *   <li>{@code null} input is treated as empty;</li>
 *   <li>an unpaired surrogate counts as one cluster (it cannot be split further);</li>
 *   <li>a negative cluster limit is rejected with {@link IllegalArgumentException}.</li>
 * </ul>
 * Instances are thread-safe: a fresh {@link BreakIterator} is created per call.
 */
public class GraphemeBoundary {

    /**
     * Returns the number of grapheme clusters in the text.
     */
    public int length(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        BreakIterator iterator = characterIterator(text);
        int count = 0;
        int boundary = iterator.next();
        while (boundary != BreakIterator.DONE) {
            count++;
            boundary = iterator.next();
        }
        return count;
    }

    /**
     * Truncates the text to at most {@code maxClusters} grapheme clusters.
     *
     * @param text        the text to truncate; {@code null} is treated as empty
     * @param maxClusters maximum number of grapheme clusters; must not be negative
     * @return the truncation result (never {@code null})
     * @throws IllegalArgumentException if {@code maxClusters} is negative
     */
    public TruncateResult truncate(String text, int maxClusters) {
        if (maxClusters < 0) {
            throw new IllegalArgumentException("maxClusters must not be negative: " + maxClusters);
        }
        if (text == null || text.isEmpty() || maxClusters == 0) {
            return new TruncateResult("", text != null && !text.isEmpty(), 0);
        }
        BreakIterator iterator = characterIterator(text);
        int start = iterator.first();
        int end = iterator.next();
        int kept = 0;
        while (end != BreakIterator.DONE && kept < maxClusters) {
            kept++;
            end = iterator.next();
        }
        if (end == BreakIterator.DONE) {
            return new TruncateResult(text, false, kept);
        }
        int cut = iterator.previous();
        return new TruncateResult(text.substring(0, cut), true, kept);
    }

    /**
     * Truncates the text so that its UTF-8 byte length does not exceed
     * {@code maxUtf8Bytes}, never splitting a grapheme cluster.
     *
     * @param text         the text to truncate; {@code null} is treated as empty
     * @param maxUtf8Bytes maximum UTF-8 byte length; must not be negative
     * @return the truncation result (never {@code null})
     * @throws IllegalArgumentException if {@code maxUtf8Bytes} is negative
     */
    public TruncateResult truncateToBytes(String text, int maxUtf8Bytes) {
        if (maxUtf8Bytes < 0) {
            throw new IllegalArgumentException("maxUtf8Bytes must not be negative: " + maxUtf8Bytes);
        }
        if (text == null || text.isEmpty() || maxUtf8Bytes == 0) {
            return new TruncateResult("", text != null && !text.isEmpty(), 0);
        }
        BreakIterator iterator = characterIterator(text);
        int start = iterator.first();
        int end = iterator.next();
        int bytes = 0;
        int kept = 0;
        while (end != BreakIterator.DONE) {
            String cluster = text.substring(start, end);
            int clusterBytes = cluster.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + clusterBytes > maxUtf8Bytes) {
                break;
            }
            bytes += clusterBytes;
            kept++;
            start = end;
            end = iterator.next();
        }
        if (end == BreakIterator.DONE) {
            return new TruncateResult(text, false, kept);
        }
        return new TruncateResult(text.substring(0, start), true, kept);
    }

    /**
     * Splits the text into its grapheme clusters.
     *
     * @return an unmodifiable list of the grapheme cluster substrings
     */
    public List<String> split(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> clusters = new ArrayList<>();
        BreakIterator iterator = characterIterator(text);
        int start = iterator.first();
        int end = iterator.next();
        while (end != BreakIterator.DONE) {
            clusters.add(text.substring(start, end));
            start = end;
            end = iterator.next();
        }
        return List.copyOf(clusters);
    }

    private static BreakIterator characterIterator(String text) {
        BreakIterator iterator = BreakIterator.getCharacterInstance();
        iterator.setText(text);
        return iterator;
    }
}
