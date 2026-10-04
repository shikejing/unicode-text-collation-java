package com.github.shikejing.unicodetextcollationjava;

import com.github.shikejing.unicodetextcollationjava.collate.CollationConfig;
import com.github.shikejing.unicodetextcollationjava.collate.SortKey;
import com.github.shikejing.unicodetextcollationjava.normalize.FoldResult;
import com.github.shikejing.unicodetextcollationjava.normalize.NormalizationForm;
import com.github.shikejing.unicodetextcollationjava.normalize.RetrievalKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test that the Spring context loads and the text processing service is
 * wired and functional.
 */
@SpringBootTest
class ApplicationTests {

    @Autowired
    private TextProcessingService service;

    @Test
    void contextLoads() {
        assertNotNull(service);
    }

    @Test
    void serviceIsFunctionalThroughContext() {
        String normalized = service.normalize("é", NormalizationForm.NFC);
        assertEquals("é", normalized);

        FoldResult folded = service.fold("ＡＢＣ");
        assertEquals("abc", folded.folded());

        SortKey key = service.sortKey("héllo", CollationConfig.defaultConfig());
        assertNotNull(key);
        assertEquals(key, service.sortKey("héllo", CollationConfig.defaultConfig()));

        RetrievalKey retrievalKey = service.retrievalKey("ＡＢＣ");
        assertEquals(retrievalKey, service.retrievalKey("abc"));

        assertEquals(1, service.graphemeLength("é"));
    }
}
