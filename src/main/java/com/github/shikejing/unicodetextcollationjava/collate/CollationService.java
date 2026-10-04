package com.github.shikejing.unicodetextcollationjava.collate;

import com.github.shikejing.unicodetextcollationjava.cache.BoundedCache;
import com.github.shikejing.unicodetextcollationjava.grapheme.GraphemeBoundary;
import com.github.shikejing.unicodetextcollationjava.normalize.TextNormalizer;
import com.ibm.icu.text.CollationKey;
import com.ibm.icu.text.Collator;

/**
 * Multilingual collation and sort key generation service.
 * <p>
 * Sorting is layered by strength: {@link CollationStrength#PRIMARY} ignores accent and
 * case, {@link CollationStrength#SECONDARY} is accent-sensitive but case-insensitive,
 * {@link CollationStrength#TERTIARY} (the default) is accent- and case-sensitive, and
 * {@link CollationStrength#IDENTICAL} distinguishes everything. The locale is fully
 * configurable through {@link CollationConfig}.
 * <p>
 * Ordering is deterministic: the same input with the same configuration always produces
 * the same sort key bytes and the same relative order, across threads and JVM executions.
 * <p>
 * Generated keys have a bounded length: the input is truncated at a grapheme cluster
 * boundary before key generation (see {@link #MAX_INPUT_GRAPHEMES}), so keys never grow
 * without bound. Ordering is guaranteed for the retained prefix; two distinct texts that
 * share a long identical prefix may compare equal after truncation.
 */
public class CollationService {

    /** Maximum number of grapheme clusters retained when generating a sort key. */
    public static final int MAX_INPUT_GRAPHEMES = 512;
    /** Hard upper bound on the raw collation key length in bytes. */
    public static final int MAX_KEY_BYTES = 8192;

    private final BoundedCache<String, Collator> collatorCache = new BoundedCache<>(64);
    private final TextNormalizer normalizer = new TextNormalizer();
    private final GraphemeBoundary graphemeBoundary = new GraphemeBoundary();

    /**
     * Generates a versioned sort key for the given text and configuration.
     */
    public SortKey getSortKey(String text, CollationConfig config) {
        CollationConfig effectiveConfig = config == null ? CollationConfig.defaultConfig() : config;
        String sanitized = normalizer.sanitize(text);
        Collator collator = collatorFor(effectiveConfig);
        String keyInput = truncateForKey(sanitized, collator);
        byte[] raw = rawKey(collator, keyInput);
        return new SortKey(effectiveConfig, raw);
    }

    /**
     * Compares two texts under the given collation configuration.
     *
     * @return a negative integer, zero, or a positive integer as {@code a} is less than,
     *         equal to, or greater than {@code b}
     */
    public int compare(String a, String b, CollationConfig config) {
        CollationConfig effectiveConfig = config == null ? CollationConfig.defaultConfig() : config;
        String sa = normalizer.sanitize(a);
        String sb = normalizer.sanitize(b);
        return collatorFor(effectiveConfig).compare(sa, sb);
    }

    private byte[] rawKey(Collator collator, String text) {
        CollationKey key = collator.getCollationKey(text);
        byte[] bytes = key.toByteArray();
        if (bytes.length <= MAX_KEY_BYTES) {
            return bytes;
        }
        // Last resort: truncate the key bytes. This can only happen with unusually long
        // keys; the input truncation below normally keeps keys well below this limit.
        byte[] truncated = new byte[MAX_KEY_BYTES];
        System.arraycopy(bytes, 0, truncated, 0, MAX_KEY_BYTES);
        return truncated;
    }

    /**
     * Truncates the input at a grapheme cluster boundary so the generated key stays
     * within bounds. Tries the configured limit first and halves it if the resulting
     * key would still exceed {@link #MAX_KEY_BYTES}.
     */
    private String truncateForKey(String text, Collator collator) {
        int limit = MAX_INPUT_GRAPHEMES;
        String candidate = graphemeBoundary.truncate(text, limit).text();
        while (candidate.length() > 0 && collator.getCollationKey(candidate).toByteArray().length > MAX_KEY_BYTES) {
            limit = limit / 2;
            if (limit == 0) {
                return "";
            }
            candidate = graphemeBoundary.truncate(text, limit).text();
        }
        return candidate;
    }

    private Collator collatorFor(CollationConfig config) {
        String cacheKey = config.localeTag() + '|' + config.strength().ordinal() + '|'
                + config.versionTag();
        Collator cached = collatorCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Collator collator = Collator.getInstance(config.toULocale());
        collator.setStrength(config.icuStrength());
        collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
        // Freeze the collator: a frozen collator is safe for concurrent use (ICU
        // serializes key generation through an internal lock). Without freezing, ICU
        // shares a single collation buffer across threads, which corrupts sort keys.
        if (!collator.isFrozen()) {
            collator.freeze();
        }
        collatorCache.put(cacheKey, collator);
        return collator;
    }
}
