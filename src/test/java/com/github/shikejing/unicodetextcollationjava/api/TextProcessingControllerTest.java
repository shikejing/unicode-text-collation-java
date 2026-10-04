package com.github.shikejing.unicodetextcollationjava.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the REST API endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TextProcessingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void normalizeEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/normalize")
                        .param("form", "NFC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"é\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("é"))
                .andExpect(jsonPath("$.form").value("NFC"))
                .andExpect(jsonPath("$.validity").value("VALID"));
    }

    @Test
    void foldEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/fold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"ＡＢＣ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folded").value("abc"))
                .andExpect(jsonPath("$.original").value("ＡＢＣ"));
    }

    @Test
    void graphemeLengthEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/grapheme/length")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"é👨‍👩‍👧\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusters").value(2));
    }

    @Test
    void graphemeTruncateEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/grapheme/truncate")
                        .param("maxClusters", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"ab👨‍👩‍👧cd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("ab👨‍👩‍👧"))
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.clusterCount").value(3));
    }

    @Test
    void sortKeyEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/sortKey")
                        .param("text", "héllo")
                        .param("locale", "")
                        .param("strength", "TERTIARY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyBase64").isNotEmpty())
                .andExpect(jsonPath("$.locale").value(""))
                .andExpect(jsonPath("$.strength").value("TERTIARY"));
    }

    @Test
    void compareEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/compare")
                        .param("a", "ä")
                        .param("b", "z")
                        .param("locale", "sv-SE")
                        .param("strength", "PRIMARY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void retrievalKeyEndpoint() throws Exception {
        String key1 = mockMvc.perform(get("/api/v1/retrievalKey").param("text", "ＡＢＣ"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String key2 = mockMvc.perform(get("/api/v1/retrievalKey").param("text", "abc"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Equivalent texts produce the same retrieval key.
        org.junit.jupiter.api.Assertions.assertEquals(key1, key2);
    }

    @Test
    void validityEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/validity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"a\\uD800b\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validity").value("UNPAIRED_SURROGATE"));
    }
}
