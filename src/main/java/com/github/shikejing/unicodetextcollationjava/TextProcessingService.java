package com.github.shikejing.unicodetextcollationjava;

import com.github.shikejing.unicodetextcollationjava.cache.BoundedCache;
import com.github.shikejing.unicodetextcollationjava.collate.CollationConfig;
import com.github.shikejing.unicodetextcollationjava.collate.CollationService;
import com.github.shikejing.unicodetextcollationjava.collate.SortKey;
import com.github.shikejing.unicodetextcollationjava.grapheme.GraphemeBoundary;
import com.github.shikejing.unicodetextcollationjava.grapheme.TruncateResult;
import com.github.shikejing.unicodetextcollationjava.normalize.FoldResult;
import com.github.shikejing.unicodetextcollationjava.normalize.InputValidity;
import com.github.shikejing.unicodetextcollationjava.normalize.NormalizationForm;
import com.github.shikejing.unicodetextcollationjava.normalize.RetrievalKey;
import com.github.shikejing.unicodetextcollationjava.normalize.TextFolder;
import com.github.shikejing.unicodetextcollationjava.normalize.TextNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Facade for multilingual text normalization, folding, collation and grapheme-safe
 * processing with bounded, thread-safe caching.
 * <p>
 * All operations are deterministic: the same input and configuration always produce the
 * same result, for the same thread, across threads and across JVM executions. The caches
 * are bounded LRU caches, so repeated or distinct inputs cannot grow memory without
 * bound, and hit and miss paths return identical results.
 */
@Service
public class TextProcessingService {

    private final TextNormalizer normalizer = new TextNormalizer();
    private final TextFolder folder = new TextFolder();
    private final CollationService collationService = new CollationService();
    private final GraphemeBoundary graphemeBoundary = new GraphemeBoundary();

    private final BoundedCache<String, String> normalizeCache;
    private final BoundedCache<String, FoldResult> foldCache;
    private final BoundedCache<String, SortKey> sortKeyCache;

    public TextProcessingService(
            @Value("${text.cache.max-size:1024}") int cacheMaxSize) {
        this.normalizeCache = new BoundedCache<>(cacheMaxSize);
        this.foldCache = new BoundedCache<>(cacheMaxSize);
        this.sortKeyCache = new BoundedCache<>(cacheMaxSize);
    }

    /**
     * Normalizes the text to the given form. Equivalent spellings normalize to the same
     * result; normalizing an already normalized string is idempotent.
     */
    public String normalize(String text, NormalizationForm form) {
        String key = (form == null ? NormalizationForm.NFC.name() : form.name()) + '\n'
                + (text == null ? "" : text);
        return normalizeCache.getOrCompute(key, k -> normalizer.normalize(text, form));
    }

    /**
     * Folds the text (NFKC + full case fold) with traceability back to the original.
     */
    public FoldResult fold(String text) {
        String key = text == null ? "" : text;
        return foldCache.getOrCompute(key, k -> folder.fold(text));
    }

    /**
     * Checks the validity of the input text.
     */
    public InputValidity validity(String text) {
        return normalizer.validity(text);
    }

    /**
     * Generates a versioned sort key for the given text and configuration.
     */
    public SortKey sortKey(String text, CollationConfig config) {
        CollationConfig effectiveConfig = config == null ? CollationConfig.defaultConfig() : config;
        String key = effectiveConfig.localeTag() + '|' + effectiveConfig.strength().name() + '|'
                + effectiveConfig.versionTag() + '\n' + (text == null ? "" : text);
        return sortKeyCache.getOrCompute(key, k -> collationService.getSortKey(text, effectiveConfig));
    }

    /**
     * Compares two texts under the given collation configuration.
     */
    public int compare(String a, String b, CollationConfig config) {
        return collationService.compare(a, b, config);
    }

    /**
     * Returns the number of grapheme clusters in the text.
     */
    public int graphemeLength(String text) {
        return graphemeBoundary.length(text);
    }

    /**
     * Truncates the text to at most {@code maxClusters} grapheme clusters, never
     * splitting a cluster or combining sequence.
     */
    public TruncateResult truncateGraphemes(String text, int maxClusters) {
        return graphemeBoundary.truncate(text, maxClusters);
    }

    /**
     * Truncates the text so its UTF-8 byte length does not exceed
     * {@code maxUtf8Bytes}, never splitting a grapheme cluster.
     */
    public TruncateResult truncateToBytes(String text, int maxUtf8Bytes) {
        return graphemeBoundary.truncateToBytes(text, maxUtf8Bytes);
    }

    /**
     * Generates a versioned retrieval key for de-duplication and lookup. Texts that are
     * equivalent under normalization, case folding and compatibility folding share a key.
     */
    public RetrievalKey retrievalKey(String text) {
        FoldResult folded = fold(text);
        return RetrievalKey.ofFolded(folded.folded());
    }
}
