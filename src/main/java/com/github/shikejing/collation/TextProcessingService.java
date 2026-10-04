package com.github.shikejing.collation;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Thread-safe facade combining sanitization, normalization, folding and
 * key generation, backed by a bounded cache.
 *
 * <p>Concurrency guarantees:</p>
 * <ul>
 *   <li>All public methods are safe for concurrent use; concurrent callers
 *       processing the same or different texts never interfere.</li>
 *   <li>The cache is a bounded LRU (max {@value #MAX_CACHE_ENTRIES} entries);
 *       repeated inputs cannot grow it without limit.</li>
 *   <li>Cache hit and miss paths return identical results because the cache
 *       only stores outputs of the same pure computation.</li>
 * </ul>
 */
@Service
public class TextProcessingService {

    static final int MAX_CACHE_ENTRIES = 10_000;

    private final InputSanitizer sanitizer;
    private final NormalizationService normalizationService;
    private final CaseFoldingService caseFoldingService;
    private final SortKeyService sortKeyService;

    private final Map<String, String> cache =
            Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            });

    public TextProcessingService(InputSanitizer sanitizer,
                                 NormalizationService normalizationService,
                                 CaseFoldingService caseFoldingService,
                                 SortKeyService sortKeyService) {
        this.sanitizer = sanitizer;
        this.normalizationService = normalizationService;
        this.caseFoldingService = caseFoldingService;
        this.sortKeyService = sortKeyService;
    }

    /**
     * Sanitizes then NFC-normalizes the input for indexing. Null/empty
     * input yields "".
     */
    public String normalizeForIndex(String input) {
        return cached("norm|" + nullToEmpty(input),
                () -> normalizationService.normalize(
                        sanitizer.sanitize(input).text(), NormalizationForm.NFC));
    }

    /**
     * Search/dedup key: sanitized + NFKC + case-folded, version-tagged and
     * length-capped. See {@link SortKeyService#searchKey}.
     */
    public String searchKey(String input, int maxLength) {
        return cached("search|" + maxLength + "|" + nullToEmpty(input),
                () -> sortKeyService.searchKey(input, maxLength));
    }

    /**
     * Persistable sort key; see {@link SortKeyService#sortKey}.
     */
    public String sortKey(String input, Locale locale, CollationStrength strength, int maxLength) {
        Locale effective = locale != null ? locale : Locale.ROOT;
        return cached("sort|" + effective.toLanguageTag() + "|" + strength + "|" + maxLength
                        + "|" + nullToEmpty(input),
                () -> sortKeyService.sortKey(input, effective, strength, maxLength));
    }

    /**
     * Folded form of the input with traceability to the original.
     */
    public CaseFoldingService.FoldingResult fold(String input) {
        return caseFoldingService.fold(sanitizer.sanitize(input).text());
    }

    public long cacheSize() {
        return cache.size();
    }

    private String cached(String key, java.util.function.Supplier<String> computation) {
        String hit = cache.get(key);
        if (hit != null) {
            return hit;
        }
        String value = computation.get();
        cache.put(key, value);
        return value;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
