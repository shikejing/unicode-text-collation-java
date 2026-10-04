package com.github.shikejing.unicodetextcollationjava.normalize;

/**
 * Describes the validity of input text passed to the processing service.
 * <p>
 * Invalid input never causes a failure: the service substitutes the Unicode
 * replacement character {@code U+FFFD} for the offending sequence and reports
 * the kind of problem through this enum so callers can distinguish the cause.
 */
public enum InputValidity {
    /** Input is valid (non-empty, no surrogate or code-point problems). */
    VALID,
    /** Input is {@code null} or empty. */
    EMPTY,
    /** Input contains an unpaired surrogate ({@code U+D800..U+DFFF} as a code point). */
    UNPAIRED_SURROGATE,
    /** Input contains a code point outside the valid Unicode range ({@code > U+10FFFF}). */
    OUT_OF_RANGE
}
