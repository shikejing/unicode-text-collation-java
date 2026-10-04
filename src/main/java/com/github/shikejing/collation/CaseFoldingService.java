package com.github.shikejing.collation;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Case folding and compatibility equivalence handling.
 *
 * <p>Folding pipeline (deterministic, idempotent):</p>
 * <ol>
 *   <li>NFKC normalization — folds fullwidth/halfwidth forms, compatibility
 *       characters (ligatures, circled digits, etc.) to standard forms.</li>
 *   <li>Special full-folding mappings not covered by
 *       {@link String#toLowerCase} (e.g. ß → ss, ς → σ).</li>
 *   <li>Locale-independent lowercase ({@link Locale#ROOT}).</li>
 * </ol>
 *
 * <p>The returned {@link FoldingResult} retains the original text so every
 * folded value can be traced back to its source.</p>
 */
@Service
public class CaseFoldingService {

    /**
     * Special case-folding mappings applied before generic lowercasing.
     * Covers the common full-foldings that simple lowercasing misses.
     */
    private static final Map<String, String> SPECIAL_FOLDINGS = Map.of(
            "ß", "ss",   // LATIN SMALL LETTER SHARP S
            "ẞ", "ss",   // LATIN CAPITAL LETTER SHARP S
            "ς", "σ",    // GREEK SMALL FINAL SIGMA → SIGMA
            "ﬁ", "fi",   // (already handled by NFKC, kept for clarity)
            "ﬂ", "fl"
    );

    /**
     * Folds the input. Null yields a result with null original and null
     * folded value; empty input folds to empty.
     */
    public FoldingResult fold(String input) {
        if (input == null) {
            return new FoldingResult(null, null);
        }
        String nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(nfkc.length());
        int i = 0;
        while (i < nfkc.length()) {
            int cp = nfkc.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            String mapped = SPECIAL_FOLDINGS.get(ch);
            sb.append(mapped != null ? mapped : ch.toLowerCase(Locale.ROOT));
            i += Character.charCount(cp);
        }
        return new FoldingResult(input, sb.toString());
    }

    /**
     * Result of a folding operation; keeps the original text for traceability.
     */
    public record FoldingResult(String original, String folded) {
    }
}
