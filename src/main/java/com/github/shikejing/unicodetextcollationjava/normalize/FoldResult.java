package com.github.shikejing.unicodetextcollationjava.normalize;

/**
 * Result of a case fold with traceability back to the original text.
 *
 * @param original    the original input text
 * @param folded      the folded text
 * @param sourceIndex for each code point of the folded text, the index of the
 *                    source code point in the original text it was derived from
 */
public record FoldResult(String original, String folded, int[] sourceIndex) {
}
