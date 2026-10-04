package com.github.shikejing.collation;

import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Generates persistable, cross-process-stable sort keys and search keys.
 *
 * <p>Key format: {@code v<rulesVersion>|<config>|<payload>}.</p>
 * <ul>
 *   <li>The leading version tag binds each key to the normalization/folding
 *       rules that produced it. If those rules change, the version is bumped
 *       and old keys are detectable by their prefix instead of silently
 *       comparing wrong.</li>
 *   <li>Sort keys carry the locale tag and strength so keys from different
 *       configurations never collide.</li>
 *   <li>Sort-key payloads are lowercase hex of the collation-key bytes, so
 *       plain lexicographic string comparison of keys reproduces collation
 *       order and works across processes and languages.</li>
 *   <li>Keys are truncated to {@code maxLength} characters; truncation never
 *       splits a surrogate pair.</li>
 * </ul>
 */
@Service
public class SortKeyService {

    /**
     * Rules version. MUST be bumped whenever normalization forms, folding
     * mappings or key formats change, so previously persisted keys are
     * recognized as stale instead of silently mis-comparing.
     */
    public static final int RULES_VERSION = 1;

    private static final int DEFAULT_MAX_LENGTH = 512;
    private static final int ABSOLUTE_MAX_LENGTH = 4096;

    private final InputSanitizer sanitizer;
    private final CaseFoldingService caseFoldingService;
    private final CollationService collationService;

    public SortKeyService(InputSanitizer sanitizer,
                          CaseFoldingService caseFoldingService,
                          CollationService collationService) {
        this.sanitizer = sanitizer;
        this.caseFoldingService = caseFoldingService;
        this.collationService = collationService;
    }

    /**
     * Generates a persistable sort key. Lexicographic comparison of keys
     * produced with the same (version, locale, strength) reproduces the
     * collation order of the original texts.
     */
    public String sortKey(String input, Locale locale, CollationStrength strength, int maxLength) {
        Locale effective = locale != null ? locale : Locale.ROOT;
        String sanitized = sanitizer.sanitize(input).text();
        byte[] keyBytes = collationService.sortKey(sanitized, effective, strength);
        String header = "v" + RULES_VERSION + "|" + effective.toLanguageTag()
                + "|" + strength.name() + "|";
        return cap(header + toHex(keyBytes), maxLength);
    }

    /**
     * Generates a search/dedup key: sanitized, NFKC-normalized and
     * case-folded text with a version prefix. Equal keys mean the inputs
     * are equivalent under the current folding rules.
     */
    public String searchKey(String input, int maxLength) {
        String sanitized = sanitizer.sanitize(input).text();
        String folded = caseFoldingService.fold(sanitized).folded();
        return cap("v" + RULES_VERSION + "|" + folded, maxLength);
    }

    /**
     * Returns true if {@code key} was generated under the current rules
     * version; stale keys must be regenerated, never silently reused.
     */
    public boolean isCurrent(String key) {
        return key != null && key.startsWith("v" + RULES_VERSION + "|");
    }

    private String cap(String key, int maxLength) {
        int limit = maxLength > 0 ? Math.min(maxLength, ABSOLUTE_MAX_LENGTH) : DEFAULT_MAX_LENGTH;
        if (key.length() <= limit) {
            return key;
        }
        int end = limit;
        // Never split a surrogate pair at the cut point.
        if (end > 0 && Character.isHighSurrogate(key.charAt(end - 1))) {
            end--;
        }
        return key.substring(0, end);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
