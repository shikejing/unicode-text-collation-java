package com.github.shikejing.unicodetextcollationjava.api;

import com.github.shikejing.unicodetextcollationjava.TextProcessingService;
import com.github.shikejing.unicodetextcollationjava.collate.CollationConfig;
import com.github.shikejing.unicodetextcollationjava.collate.CollationStrength;
import com.github.shikejing.unicodetextcollationjava.collate.SortKey;
import com.github.shikejing.unicodetextcollationjava.grapheme.TruncateResult;
import com.github.shikejing.unicodetextcollationjava.normalize.FoldResult;
import com.github.shikejing.unicodetextcollationjava.normalize.InputValidity;
import com.github.shikejing.unicodetextcollationjava.normalize.NormalizationForm;
import com.github.shikejing.unicodetextcollationjava.normalize.RetrievalKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for multilingual text normalization, folding, collation and grapheme-safe
 * processing. All endpoints are deterministic and safe for concurrent use.
 */
@RestController
@RequestMapping("/api/v1")
public class TextProcessingController {

    private final TextProcessingService service;

    public TextProcessingController(TextProcessingService service) {
        this.service = service;
    }

    /** Request body carrying a text. */
    public record TextRequest(String text) {
    }

    /** Response for normalization. */
    public record NormalizeResponse(String result, String form, String validity) {
    }

    /** Response for folding. */
    public record FoldResponse(String original, String folded, int[] sourceIndex) {
    }

    /** Response for a sort key. */
    public record SortKeyResponse(String keyBase64, String locale, String strength) {
    }

    /** Response for a comparison. */
    public record CompareResponse(int result) {
    }

    /** Response for grapheme length. */
    public record GraphemeLengthResponse(int clusters) {
    }

    /** Response for grapheme truncation. */
    public record TruncateResponse(String text, boolean truncated, int clusterCount) {
    }

    /** Response for a retrieval key. */
    public record RetrievalKeyResponse(String keyBase64) {
    }

    /** Response for input validity. */
    public record ValidityResponse(String validity) {
    }

    /**
     * Normalizes the text to the given form (NFC by default).
     */
    @PostMapping("/normalize")
    public NormalizeResponse normalize(@RequestBody TextRequest request,
                                       @RequestParam(defaultValue = "NFC") String form) {
        NormalizationForm normalizationForm = NormalizationForm.valueOf(form);
        String text = request == null ? null : request.text();
        String result = service.normalize(text, normalizationForm);
        InputValidity validity = service.validity(text);
        return new NormalizeResponse(result, normalizationForm.name(), validity.name());
    }

    /**
     * Folds the text (NFKC + full case fold) with traceability.
     */
    @PostMapping("/fold")
    public FoldResponse fold(@RequestBody TextRequest request) {
        String text = request == null ? null : request.text();
        FoldResult result = service.fold(text);
        return new FoldResponse(result.original(), result.folded(), result.sourceIndex());
    }

    /**
     * Returns the number of grapheme clusters in the text.
     */
    @PostMapping("/grapheme/length")
    public GraphemeLengthResponse graphemeLength(@RequestBody TextRequest request) {
        String text = request == null ? null : request.text();
        return new GraphemeLengthResponse(service.graphemeLength(text));
    }

    /**
     * Truncates the text to at most {@code maxClusters} grapheme clusters.
     */
    @PostMapping("/grapheme/truncate")
    public TruncateResponse truncate(@RequestBody TextRequest request,
                                     @RequestParam(defaultValue = "10") int maxClusters) {
        String text = request == null ? null : request.text();
        TruncateResult result = service.truncateGraphemes(text, maxClusters);
        return new TruncateResponse(result.text(), result.truncated(), result.clusterCount());
    }

    /**
     * Generates a versioned sort key for the text and configuration.
     */
    @GetMapping("/sortKey")
    public SortKeyResponse sortKey(@RequestParam String text,
                                    @RequestParam(defaultValue = "") String locale,
                                    @RequestParam(defaultValue = "TERTIARY") String strength) {
        CollationConfig config = new CollationConfig(locale, CollationStrength.valueOf(strength),
                CollationConfig.VERSION);
        SortKey key = service.sortKey(text, config);
        return new SortKeyResponse(key.toBase64(), config.localeTag(), config.strength().name());
    }

    /**
     * Compares two texts under the given collation configuration.
     */
    @GetMapping("/compare")
    public CompareResponse compare(@RequestParam String a,
                                   @RequestParam String b,
                                   @RequestParam(defaultValue = "") String locale,
                                   @RequestParam(defaultValue = "TERTIARY") String strength) {
        CollationConfig config = new CollationConfig(locale, CollationStrength.valueOf(strength),
                CollationConfig.VERSION);
        return new CompareResponse(service.compare(a, b, config));
    }

    /**
     * Generates a versioned retrieval key for de-duplication and lookup.
     */
    @GetMapping("/retrievalKey")
    public RetrievalKeyResponse retrievalKey(@RequestParam String text) {
        RetrievalKey key = service.retrievalKey(text);
        return new RetrievalKeyResponse(key.toBase64());
    }

    /**
     * Checks the validity of the input text.
     */
    @PostMapping("/validity")
    public ValidityResponse validity(@RequestBody TextRequest request) {
        String text = request == null ? null : request.text();
        return new ValidityResponse(service.validity(text).name());
    }
}
