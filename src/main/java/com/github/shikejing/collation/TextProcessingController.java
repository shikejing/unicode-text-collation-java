package com.github.shikejing.collation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * HTTP facade over the text processing services for search/dedup pipelines.
 */
@RestController
@RequestMapping("/api/text")
public class TextProcessingController {

    private final TextProcessingService processingService;
    private final CollationService collationService;
    private final GraphemeService graphemeService;

    public TextProcessingController(TextProcessingService processingService,
                                    CollationService collationService,
                                    GraphemeService graphemeService) {
        this.processingService = processingService;
        this.collationService = collationService;
        this.graphemeService = graphemeService;
    }

    @PostMapping("/normalize")
    public String normalize(@RequestBody String text) {
        return processingService.normalizeForIndex(text);
    }

    @PostMapping("/search-key")
    public String searchKey(@RequestBody String text,
                            @RequestParam(defaultValue = "512") int maxLength) {
        return processingService.searchKey(text, maxLength);
    }

    @PostMapping("/sort-key")
    public String sortKey(@RequestBody String text,
                          @RequestParam(defaultValue = "") String locale,
                          @RequestParam(defaultValue = "TERTIARY") CollationStrength strength,
                          @RequestParam(defaultValue = "512") int maxLength) {
        return processingService.sortKey(text, parseLocale(locale), strength, maxLength);
    }

    @PostMapping("/sort")
    public List<String> sort(@RequestBody List<String> texts,
                             @RequestParam(defaultValue = "") String locale,
                             @RequestParam(defaultValue = "TERTIARY") CollationStrength strength) {
        return collationService.sort(texts, parseLocale(locale), strength);
    }

    @PostMapping("/truncate")
    public String truncate(@RequestBody String text,
                           @RequestParam int maxClusters) {
        return graphemeService.truncate(text, maxClusters);
    }

    private Locale parseLocale(String tag) {
        return tag == null || tag.isBlank() ? Locale.ROOT : Locale.forLanguageTag(tag);
    }
}
