package com.github.shikejing.collation;

import org.springframework.stereotype.Component;

/**
 * Deterministic handling of illegal input.
 *
 * <p>Rules (deterministic, never throw):</p>
 * <ul>
 *   <li>null or empty input → status {@link Status#EMPTY}, text "".</li>
 *   <li>unpaired surrogates (lone high/low surrogate) → each replaced with
 *       U+FFFD REPLACEMENT CHARACTER, status {@link Status#REPAIRED}.</li>
 *   <li>well-formed input → returned unchanged, status {@link Status#CLEAN}.</li>
 * </ul>
 *
 * <p>Note: a Java {@code String} cannot contain out-of-range code points
 * (it is UTF-16); the only malformed state representable is unpaired
 * surrogates, which is what this sanitizer repairs.</p>
 */
@Component
public class InputSanitizer {

    public static final char REPLACEMENT = '�';

    public SanitizeResult sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return new SanitizeResult(Status.EMPTY, "");
        }
        StringBuilder repaired = null;
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < len && Character.isLowSurrogate(input.charAt(i + 1))) {
                    if (repaired != null) {
                        repaired.append(c).append(input.charAt(i + 1));
                    }
                    i++; // consume the pair
                } else {
                    repaired = ensure(repaired, input, i);
                    repaired.append(REPLACEMENT);
                }
            } else if (Character.isLowSurrogate(c)) {
                repaired = ensure(repaired, input, i);
                repaired.append(REPLACEMENT);
            } else if (repaired != null) {
                repaired.append(c);
            }
        }
        if (repaired == null) {
            return new SanitizeResult(Status.CLEAN, input);
        }
        return new SanitizeResult(Status.REPAIRED, repaired.toString());
    }

    private StringBuilder ensure(StringBuilder sb, String input, int upTo) {
        if (sb == null) {
            sb = new StringBuilder(input.length() + 8);
            sb.append(input, 0, upTo);
        }
        return sb;
    }

    public enum Status {
        /** Input was well-formed and returned unchanged. */
        CLEAN,
        /** Input contained unpaired surrogates which were replaced by U+FFFD. */
        REPAIRED,
        /** Input was null or empty; result text is "". */
        EMPTY
    }

    public record SanitizeResult(Status status, String text) {
    }
}
