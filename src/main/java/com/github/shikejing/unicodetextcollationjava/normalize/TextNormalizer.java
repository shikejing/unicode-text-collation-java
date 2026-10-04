package com.github.shikejing.unicodetextcollationjava.normalize;

import com.ibm.icu.text.Normalizer2;

/**
 * Unicode normalization service.
 * <p>
 * Supports the four standard Unicode normalization forms ({@link NormalizationForm#NFC},
 * {@link NormalizationForm#NFD}, {@link NormalizationForm#NFKC}, {@link NormalizationForm#NFKD}).
 * All operations are deterministic and idempotent: normalizing an already normalized
 * string returns an equal string, and different but canonically equivalent spellings
 * normalize to the same result.
 * <p>
 * Invalid input is handled deterministically: unpaired surrogates and out-of-range
 * code points are replaced with {@code U+FFFD} before normalization, and the
 * {@link #validity(String)} / {@link #validity(int[])} methods report the problem
 * so callers can distinguish it from valid input.
 */
public class TextNormalizer {

    /** Replacement character substituted for invalid input sequences. */
    public static final char REPLACEMENT_CHAR = '�';

    /**
     * Normalizes the given text to the requested form.
     *
     * @param text the text to normalize; {@code null} is treated as empty
     * @param form the normalization form; {@code null} defaults to {@link NormalizationForm#NFC}
     * @return the normalized text (never {@code null})
     */
    public String normalize(String text, NormalizationForm form) {
        NormalizationForm effectiveForm = form == null ? NormalizationForm.NFC : form;
        String sanitized = sanitize(text);
        Normalizer2 normalizer = switch (effectiveForm) {
            case NFC -> Normalizer2.getNFCInstance();
            case NFD -> Normalizer2.getNFDInstance();
            case NFKC -> Normalizer2.getNFKCInstance();
            case NFKD -> Normalizer2.getNFKDInstance();
        };
        return normalizer.normalize(sanitized);
    }

    /**
     * Checks the validity of a Java string.
     *
     * @return {@link InputValidity#EMPTY} for {@code null}/empty input,
     *         {@link InputValidity#UNPAIRED_SURROGATE} if an unpaired surrogate is found,
     *         {@link InputValidity#VALID} otherwise
     */
    public InputValidity validity(String text) {
        if (text == null || text.isEmpty()) {
            return InputValidity.EMPTY;
        }
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (isSurrogate(cp)) {
                return InputValidity.UNPAIRED_SURROGATE;
            }
            i += Character.charCount(cp);
        }
        return InputValidity.VALID;
    }

    /**
     * Normalizes an array of code points. Out-of-range code points
     * ({@code > U+10FFFF}, negative values or surrogate code points) are replaced
     * with {@code U+FFFD} before normalization.
     */
    public String normalizeCodePoints(int[] codePoints, NormalizationForm form) {
        if (codePoints == null || codePoints.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(codePoints.length);
        for (int cp : codePoints) {
            if (isOutOfRange(cp) || isSurrogate(cp)) {
                sb.append(REPLACEMENT_CHAR);
            } else {
                sb.appendCodePoint(cp);
            }
        }
        return normalize(sb.toString(), form);
    }

    /**
     * Checks the validity of a code-point array.
     */
    public InputValidity validity(int[] codePoints) {
        if (codePoints == null || codePoints.length == 0) {
            return InputValidity.EMPTY;
        }
        for (int cp : codePoints) {
            if (isOutOfRange(cp) || isSurrogate(cp)) {
                return InputValidity.OUT_OF_RANGE;
            }
        }
        return InputValidity.VALID;
    }

    /**
     * Replaces unpaired surrogates with {@code U+FFFD}. Valid surrogate pairs
     * (supplementary characters) are left untouched.
     */
    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        boolean hasSurrogate = false;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isSurrogate(text.charAt(i))) {
                hasSurrogate = true;
                break;
            }
        }
        if (!hasSurrogate) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (isSurrogate(cp)) {
                sb.append(REPLACEMENT_CHAR);
            } else {
                sb.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    private static boolean isSurrogate(int cp) {
        return cp >= 0xD800 && cp <= 0xDFFF;
    }

    private static boolean isOutOfRange(int cp) {
        return cp < 0 || cp > 0x10FFFF;
    }
}
