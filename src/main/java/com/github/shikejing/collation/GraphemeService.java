package com.github.shikejing.collation;

import org.springframework.stereotype.Service;

import com.ibm.icu.text.BreakIterator;

import java.util.Locale;

/**
 * Grapheme-cluster-aware segmentation, counting and truncation.
 *
 * <p>Boundary semantics (deterministic, documented):</p>
 * <ul>
 *   <li>A grapheme cluster is a user-perceived character: base letter plus
 *       any combining marks, emoji ZWJ sequences, regional-indicator pairs,
 *       etc. Clusters are never split.</li>
 *   <li>{@link #truncate} with {@code maxClusters <= 0} returns "".</li>
 *   <li>{@link #truncate} with {@code maxClusters >= graphemeCount} returns
 *       the input unchanged (same content).</li>
 *   <li>Otherwise the result contains exactly the first {@code maxClusters}
 *       clusters; the cut never falls inside a cluster or combining
 *       sequence.</li>
 *   <li>null input yields null for {@link #truncate} and 0 for
 *       {@link #graphemeCount}.</li>
 * </ul>
 */
@Service
public class GraphemeService {

    public int graphemeCount(String input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }
        BreakIterator it = iterator(input);
        int count = 0;
        for (int boundary = it.next(); boundary != BreakIterator.DONE; boundary = it.next()) {
            count++;
        }
        return count;
    }

    /**
     * Truncates to at most {@code maxClusters} grapheme clusters; never
     * splits a cluster or combining sequence.
     */
    public String truncate(String input, int maxClusters) {
        if (input == null) {
            return null;
        }
        if (maxClusters <= 0 || input.isEmpty()) {
            return "";
        }
        BreakIterator it = iterator(input);
        int count = 0;
        int boundary = it.next();
        while (boundary != BreakIterator.DONE && count < maxClusters) {
            count++;
            if (count == maxClusters) {
                return input.substring(0, boundary);
            }
            boundary = it.next();
        }
        return input;
    }

    private BreakIterator iterator(String input) {
        BreakIterator it = BreakIterator.getCharacterInstance(Locale.ROOT);
        it.setText(input);
        return it;
    }
}
