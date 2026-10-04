package com.github.shikejing.unicodetextcollationjava.grapheme;

/**
 * Result of a grapheme-safe truncation.
 *
 * @param text       the truncated text (never splits a grapheme cluster)
 * @param truncated   {@code true} if the input was longer than the limit
 * @param clusterCount number of grapheme clusters in the result
 */
public record TruncateResult(String text, boolean truncated, int clusterCount) {
}
