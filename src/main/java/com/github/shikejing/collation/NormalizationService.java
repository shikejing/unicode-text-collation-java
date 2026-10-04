package com.github.shikejing.collation;

import org.springframework.stereotype.Service;

import java.text.Normalizer;

/**
 * Normalizes text to a configured Unicode normalization form.
 *
 * <p>Semantics: for any input, {@code normalize} is idempotent —
 * normalizing already-normalized text returns an equal string. Canonically
 * or compatibility-equivalent spellings of the same text map to the same
 * output under the chosen form.</p>
 */
@Service
public class NormalizationService {

    /**
     * Normalizes {@code input} to {@code form}. Null input yields null;
     * empty input yields empty.
     */
    public String normalize(String input, NormalizationForm form) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return Normalizer.normalize(input, toJdkForm(form));
    }

    /**
     * Returns true if {@code input} is already normalized to {@code form}.
     */
    public boolean isNormalized(String input, NormalizationForm form) {
        if (input == null) {
            return true;
        }
        return Normalizer.isNormalized(input, toJdkForm(form));
    }

    private Normalizer.Form toJdkForm(NormalizationForm form) {
        return switch (form) {
            case NFC -> Normalizer.Form.NFC;
            case NFD -> Normalizer.Form.NFD;
            case NFKC -> Normalizer.Form.NFKC;
            case NFKD -> Normalizer.Form.NFKD;
        };
    }
}
